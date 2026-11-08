package com.nanugil.app.share

/**
 * 차량 한 대의 이번 그룹 운행 기록.
 *
 * 연료량은 AAOS `FUEL_LEVEL`과 같은 mL 단위, 금액은 원 단위 정수로 받는다.
 * 부동소수점 오차를 피하려고 모든 값을 [Long]으로 다룬다.
 * 같은 [Long] 인자가 나란히 있어 순서를 헷갈리기 쉬우므로 이름 있는 인자로 만드는 것을 권장한다.
 *
 * @property vehicleId 차량 식별자. 그룹 안에서 겹치지 않아야 한다.
 * @property driverId 운전자(이 차의 비용을 실제로 낸 사람) 식별자.
 * @property passengerIds 이 차에 탄 동승자 식별자 목록. 운전자는 넣지 않는다.
 * @property startFuelMl 그룹 운행 출발 시 연료량(mL).
 * @property refueledMl 운행 중 주유한 양의 합계(mL). 주유하지 않았으면 0.
 * @property endFuelMl 도착 시 연료량(mL).
 * @property fuelPriceWonPerLiter 적용 유가(원/L). 오피넷에서 고른 근처 또는 최저가 유가.
 * @property tollWon 통행료(원). `tollFare` 예상액이나 운전자가 수정한 값.
 */
data class VehicleTrip(
    val vehicleId: String,
    val driverId: String,
    val passengerIds: List<String> = emptyList(),
    val startFuelMl: Long,
    val refueledMl: Long = 0,
    val endFuelMl: Long,
    val fuelPriceWonPerLiter: Long,
    val tollWon: Long = 0,
)

/**
 * 한 사람이 다른 사람에게 보낼 금액.
 *
 * @property fromId 보내는 사람.
 * @property toId 받는 사람.
 * @property amountWon 보낼 금액(원). 항상 0보다 크다.
 */
data class Transfer(
    val fromId: String,
    val toId: String,
    val amountWon: Long,
)

/**
 * 정산 결과.
 *
 * @property vehicleCostsWon 차량 식별자별 비용(주유비 추정 + 통행료).
 * @property totalCostWon 전체 차량 비용 합계.
 * @property perPersonShareWon 1인 부담액. [SettlementCalculator.settle]의 `roundingUnitWon` 단위로 내림한 값.
 * @property remainderWon 1인 부담액을 내림하면서 남은 금액. 받을 사람들이 나눠서 덜 받는 방식으로 흡수한다.
 * @property balancesWon 사람별 정산 잔액. 양수는 받을 돈, 음수는 보낼 돈. 합계는 항상 0이다.
 * @property transfers 잔액을 맞추는 송금 목록.
 */
data class Settlement(
    val vehicleCostsWon: Map<String, Long>,
    val totalCostWon: Long,
    val perPersonShareWon: Long,
    val remainderWon: Long,
    val balancesWon: Map<String, Long>,
    val transfers: List<Transfer>,
)

/**
 * 주행 데이터로 차량별 비용을 구하고 1/N 정산 송금 목록을 만드는 순수 계산기.
 *
 * Android 의존성이 없어 JVM 단위 테스트로 검증한다.
 * 잘못된 입력(음수 값, 중복 인원 등)은 조용히 보정하지 않고 [IllegalArgumentException]으로 거부한다.
 * 보정하면 센서 오류나 입력 누락이 운전자 손해로 숨겨지기 때문이다.
 */
object SettlementCalculator {

    private const val ML_PER_LITER = 1_000L

    /**
     * 이번 운행에 쓴 연료량(mL) = 출발 연료량 + 주유량 − 도착 연료량.
     *
     * @throws IllegalArgumentException 값이 음수이거나 결과가 음수일 때(주유량 누락이나 센서 오류 의심).
     */
    fun usedFuelMl(startFuelMl: Long, refueledMl: Long, endFuelMl: Long): Long {
        require(startFuelMl >= 0) { "출발 연료량은 0 이상이어야 합니다: $startFuelMl" }
        require(refueledMl >= 0) { "주유량은 0 이상이어야 합니다: $refueledMl" }
        require(endFuelMl >= 0) { "도착 연료량은 0 이상이어야 합니다: $endFuelMl" }
        val used = Math.addExact(startFuelMl, refueledMl) - endFuelMl
        require(used >= 0) {
            "사용 연료가 음수입니다(출발 $startFuelMl + 주유 $refueledMl < 도착 $endFuelMl mL). 주유량 누락이나 센서 오류를 확인하세요."
        }
        return used
    }

    /**
     * 주유비 추정(원) = 사용 연료(mL) × 유가(원/L) ÷ 1000, 원 단위 반올림.
     */
    fun fuelCostWon(usedFuelMl: Long, fuelPriceWonPerLiter: Long): Long {
        require(usedFuelMl >= 0) { "사용 연료는 0 이상이어야 합니다: $usedFuelMl" }
        require(fuelPriceWonPerLiter >= 0) { "유가는 0 이상이어야 합니다: $fuelPriceWonPerLiter" }
        val milliWon = Math.multiplyExact(usedFuelMl, fuelPriceWonPerLiter)
        return Math.addExact(milliWon, ML_PER_LITER / 2) / ML_PER_LITER
    }

    /** 차량 비용(원) = 주유비 추정 + 통행료. */
    fun vehicleCostWon(trip: VehicleTrip): Long {
        require(trip.tollWon >= 0) { "통행료는 0 이상이어야 합니다: ${trip.vehicleId}" }
        val used = usedFuelMl(trip.startFuelMl, trip.refueledMl, trip.endFuelMl)
        return Math.addExact(fuelCostWon(used, trip.fuelPriceWonPerLiter), trip.tollWon)
    }

    /**
     * 그룹 전체를 정산한다.
     *
     * 1. 차량별 비용을 구해 운전자가 낸 돈으로 본다.
     * 2. 1인 부담액 = 전체 비용 ÷ 전체 인원을 [roundingUnitWon] 단위로 내림한다.
     *    내림으로 남은 금액은 받을 사람들이 나눠서 덜 받는다. 원 단위 처리 정책은 팀에서 아직 정하지 않아 단위만 바꿀 수 있게 했다.
     * 3. 같은 차 동승자는 그 차 운전자가 받을 돈이 남아 있는 만큼 먼저 그 운전자에게 보낸다.
     *    운전자가 오히려 보낼 사람이면 동승자를 운전자에게 거쳐 보내게 하지 않는다(운전자가 남의 돈을 대신 전달하지 않도록).
     *    남은 잔액은 금액이 큰 사람끼리 먼저 짝지어 보낸다.
     *    비용을 적게 낸 운전자도 부담액보다 덜 냈으면 다른 운전자에게 보낸다.
     *
     * @param trips 그룹의 차량 기록. 비어 있으면 안 되고, 한 사람이 두 차에 들어가면 안 된다.
     * @param roundingUnitWon 1인 부담액의 단위(원). 1이면 원 단위, 10이면 10원 단위로 내림한다.
     * @throws IllegalArgumentException 입력이 잘못되었을 때.
     */
    fun settle(trips: List<VehicleTrip>, roundingUnitWon: Long = 1): Settlement {
        require(trips.isNotEmpty()) { "정산할 차량이 없습니다." }
        require(roundingUnitWon >= 1) { "반올림 단위는 1원 이상이어야 합니다: $roundingUnitWon" }
        validateMembers(trips)

        val vehicleCosts = trips.associate { it.vehicleId to vehicleCostWon(it) }
        val total = vehicleCosts.values.fold(0L) { acc, cost -> Math.addExact(acc, cost) }

        // 입력 순서(운전자, 동승자 순)를 유지해 결과를 결정적으로 만든다.
        val members = trips.flatMap { listOf(it.driverId) + it.passengerIds }
        val headCount = members.size.toLong()
        val share = total / headCount / roundingUnitWon * roundingUnitWon
        val remainder = total - share * headCount

        val balances = members.associateWith { -share }.toMutableMap()
        trips.forEach { balances[it.driverId] = balances.getValue(it.driverId) + vehicleCosts.getValue(it.vehicleId) }

        absorbRemainder(balances, remainder)

        return Settlement(
            vehicleCostsWon = vehicleCosts,
            totalCostWon = total,
            perPersonShareWon = share,
            remainderWon = remainder,
            balancesWon = balances,
            transfers = buildTransfers(trips, balances),
        )
    }

    private fun validateMembers(trips: List<VehicleTrip>) {
        val vehicleIds = trips.map { it.vehicleId }
        require(vehicleIds.none { it.isBlank() }) { "차량 식별자가 비어 있습니다." }
        require(vehicleIds.size == vehicleIds.toSet().size) { "차량 식별자가 중복되었습니다: $vehicleIds" }
        val members = trips.flatMap { listOf(it.driverId) + it.passengerIds }
        require(members.none { it.isBlank() }) { "운전자나 동승자 식별자가 비어 있습니다." }
        val duplicated = members.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(duplicated.isEmpty()) { "한 사람이 여러 번 들어 있습니다: $duplicated" }
    }

    /**
     * 내림으로 남은 금액만큼 받을 사람들의 잔액을 줄여 잔액 합계를 0으로 맞춘다.
     *
     * 받을 사람들에게 나머지를 고르게 나눠 줄이고, 나누어떨어지지 않는 몇 원은 많이 받을 사람부터(동점이면 입력 순서) 1원씩 더 줄인다.
     * 한 사람의 잔액은 0 아래로 내리지 않는다. 잔액 합계가 remainder이므로 받을 돈의 합계는 항상 remainder 이상이라 모두 흡수된다.
     */
    private fun absorbRemainder(balances: MutableMap<String, Long>, remainder: Long) {
        var left = remainder
        while (left > 0) {
            val creditors = balances.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key }
            check(creditors.isNotEmpty()) { "나머지 ${left}원을 흡수할 사람이 없습니다." }
            val each = left / creditors.size
            if (each == 0L) {
                creditors.take(left.toInt()).forEach { balances[it] = balances.getValue(it) - 1 }
                left = 0
            } else {
                for (creditor in creditors) {
                    val cut = minOf(each, balances.getValue(creditor))
                    balances[creditor] = balances.getValue(creditor) - cut
                    left -= cut
                }
            }
        }
    }

    private fun buildTransfers(trips: List<VehicleTrip>, balances: Map<String, Long>): List<Transfer> {
        val remaining = LinkedHashMap(balances)
        val transfers = mutableListOf<Transfer>()

        fun pay(from: String, to: String) {
            val amount = minOf(-remaining.getValue(from), remaining.getValue(to))
            if (amount <= 0) return
            transfers += Transfer(from, to, amount)
            remaining[from] = remaining.getValue(from) + amount
            remaining[to] = remaining.getValue(to) - amount
        }

        // 1차: 같은 차 동승자 → 그 차 운전자 (운전자가 받을 돈이 있는 만큼만)
        trips.forEach { trip -> trip.passengerIds.forEach { pay(it, trip.driverId) } }

        // 2차: 남은 잔액이 큰 사람끼리 먼저 짝지어 한 사람이 여러 번 나눠 보내는 일을 줄인다(동점이면 입력 순서)
        val debtors = remaining.filterValues { it < 0 }.entries.sortedBy { it.value }.map { it.key }
        val creditors = remaining.filterValues { it > 0 }.entries.sortedByDescending { it.value }.map { it.key }
        for (debtor in debtors) {
            for (creditor in creditors) {
                if (remaining.getValue(debtor) == 0L) break
                if (remaining.getValue(creditor) == 0L) continue
                pay(debtor, creditor)
            }
        }

        check(remaining.values.all { it == 0L }) { "정산 잔액이 0이 되지 않았습니다: $remaining" }
        return transfers
    }
}
