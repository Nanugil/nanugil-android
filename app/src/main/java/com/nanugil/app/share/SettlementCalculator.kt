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
 * @property startBatteryWh 출발 시 배터리 잔량(Wh). 전기차·하이브리드만. Pleos `getEvBatteryLevel` 단위.
 * @property chargedWh 운행 중 충전한 양의 합계(Wh). 충전하지 않았으면 0.
 * @property endBatteryWh 도착 시 배터리 잔량(Wh).
 * @property electricityWonPerMwh 충전 단가(원/MWh = 원/kWh × 1000). 공식 공공 충전요금이 325.6원/kWh처럼 소수라서 1000배로 받는다.
 * @property tollWon 통행료(원). `tollFare` 예상액이나 운전자가 수정한 값.
 *
 * 연료차는 배터리 값을, 전기차는 연료 값을 0으로 둔다. 하이브리드는 둘 다 넣는다.
 */
data class VehicleTrip(
    val vehicleId: String,
    val driverId: String,
    val passengerIds: List<String> = emptyList(),
    val startFuelMl: Long = 0,
    val refueledMl: Long = 0,
    val endFuelMl: Long = 0,
    val fuelPriceWonPerLiter: Long = 0,
    val startBatteryWh: Long = 0,
    val chargedWh: Long = 0,
    val endBatteryWh: Long = 0,
    val electricityWonPerMwh: Long = 0,
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
 * @property vehicleCostsWon 차량 식별자별 비용(주유비 추정 + 충전비 추정 + 통행료), 원 단위 반올림.
 * @property totalCostWon 전체 차량 비용 합계(소수까지 더한 뒤 원 단위 반올림).
 * @property perPersonShareWon 1인 부담액. [SettlementCalculator.settle]의 `roundingUnitWon` 단위로 올림한 값.
 * @property surplusWon 1인 부담액을 올리면서 더 걷힌 금액(원 단위 반올림). 운전자들이 비용 비율대로 나눠 받는다. 전체 비용의 1%를 넘지 않는다.
 * @property balancesWon 사람별 정산 잔액. 양수는 받을 돈, 음수는 보낼 돈. 합계는 항상 0이다.
 * @property transfers 잔액을 맞추는 송금 목록.
 */
data class Settlement(
    val vehicleCostsWon: Map<String, Long>,
    val totalCostWon: Long,
    val perPersonShareWon: Long,
    val surplusWon: Long,
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
    private const val MILLI = 1_000L

    /** 올림으로 더 걷는 돈은 전체 비용의 이 비율(%)까지만. 넘으면 1원 단위로 올린다. */
    private const val MAX_SURPLUS_PERCENT = 1L

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

    /**
     * 이번 운행에 쓴 전력량(Wh) = 출발 배터리 + 충전량 − 도착 배터리.
     *
     * @throws IllegalArgumentException 값이 음수이거나 결과가 음수일 때(충전량 누락이나 센서 오류 의심).
     */
    fun usedEnergyWh(startBatteryWh: Long, chargedWh: Long, endBatteryWh: Long): Long {
        require(startBatteryWh >= 0) { "출발 배터리는 0 이상이어야 합니다: $startBatteryWh" }
        require(chargedWh >= 0) { "충전량은 0 이상이어야 합니다: $chargedWh" }
        require(endBatteryWh >= 0) { "도착 배터리는 0 이상이어야 합니다: $endBatteryWh" }
        val used = Math.addExact(startBatteryWh, chargedWh) - endBatteryWh
        require(used >= 0) {
            "사용 전력이 음수입니다(출발 $startBatteryWh + 충전 $chargedWh < 도착 $endBatteryWh Wh). 충전량 누락이나 센서 오류를 확인하세요."
        }
        return used
    }

    /** 차량 비용(원) = 주유비 추정 + 충전비 추정 + 통행료, 원 단위 반올림. 화면 표시용이고 정산은 [vehicleCostMilliWon]으로 한다. */
    fun vehicleCostWon(trip: VehicleTrip): Long = Math.addExact(vehicleCostMilliWon(trip), MILLI / 2) / MILLI

    /**
     * 차량 비용을 1/1000원 단위로. 충전비(325.6원/kWh × 12.345kWh = 4,019.53원)처럼 원 아래가 생겨도 정산 끝까지 버리지 않는다.
     * 주유비 = 사용 연료(mL) × 유가(원/L)는 그대로 1/1000원, 충전비 = 사용 전력(Wh) × 단가(원/MWh) ÷ 1,000은 1/1000원 아래 반올림.
     */
    fun vehicleCostMilliWon(trip: VehicleTrip): Long {
        require(trip.tollWon >= 0) { "통행료는 0 이상이어야 합니다: ${trip.vehicleId}" }
        val usedMl = usedFuelMl(trip.startFuelMl, trip.refueledMl, trip.endFuelMl)
        require(trip.fuelPriceWonPerLiter >= 0) { "유가는 0 이상이어야 합니다: ${trip.fuelPriceWonPerLiter}" }
        val fuel = Math.multiplyExact(usedMl, trip.fuelPriceWonPerLiter)
        require(trip.electricityWonPerMwh >= 0) { "충전 단가는 0 이상이어야 합니다: ${trip.vehicleId}" }
        val usedWh = usedEnergyWh(trip.startBatteryWh, trip.chargedWh, trip.endBatteryWh)
        val electricity = Math.addExact(Math.multiplyExact(usedWh, trip.electricityWonPerMwh), MILLI / 2) / MILLI
        return Math.addExact(Math.addExact(fuel, electricity), Math.multiplyExact(trip.tollWon, MILLI))
    }

    /**
     * 그룹 전체를 정산한다.
     *
     * 1. 차량별 비용을 원 아래(1/1000원)까지 구해 운전자가 낸 돈으로 본다.
     * 2. 1인 부담액 = 전체 비용 ÷ 전체 인원을 [roundingUnitWon] 단위로 올린다. 송금액이 원 아래로 쪼개지지 않게 하려는 것이다.
     *    올려서 더 걷히는 돈은 전체 비용의 1%까지만 둔다. 넘으면(부담액이 아주 작을 때) 1원 단위로 올린다.
     *    더 걷힌 돈은 운전자들이 비용 비율대로 나눠 받는다. 그래서 운전자는 반올림 때문에 손해 보지 않는다.
     * 3. 동승자는 모두 똑같이 1인 부담액 전액을 자기 차 운전자에게 보낸다. 동승자가 보낼 상대는 항상 한 명이다.
     *    그다음 운전자끼리 남은 잔액을 금액이 큰 사람끼리 먼저 짝지어 정산한다.
     *    그래서 운전자가 다른 차 몫까지 받아 넘겨주는 경우가 생긴다.
     *
     * @param trips 그룹의 차량 기록. 비어 있으면 안 되고, 한 사람이 두 차에 들어가면 안 된다.
     * @param roundingUnitWon 1인 부담액을 올리는 단위(원). 10이면 10원 단위로 올린다.
     * @throws IllegalArgumentException 입력이 잘못되었을 때.
     */
    fun settle(trips: List<VehicleTrip>, roundingUnitWon: Long = 10): Settlement {
        require(trips.isNotEmpty()) { "정산할 차량이 없습니다." }
        require(roundingUnitWon >= 1) { "올림 단위는 1원 이상이어야 합니다: $roundingUnitWon" }
        validateMembers(trips)

        val costsMilli = trips.associate { it.vehicleId to vehicleCostMilliWon(it) }
        val totalMilli = costsMilli.values.fold(0L) { acc, cost -> Math.addExact(acc, cost) }

        // 입력 순서(운전자, 동승자 순)를 유지해 결과를 결정적으로 만든다.
        val members = trips.flatMap { listOf(it.driverId) + it.passengerIds }
        val headCount = members.size.toLong()
        val share = shareWon(totalMilli, headCount, roundingUnitWon)
        val collected = Math.multiplyExact(share, headCount)

        val balances = members.associateWith { -share }.toMutableMap()
        val reimbursed = splitByCost(collected, trips.map { costsMilli.getValue(it.vehicleId) })
        trips.forEachIndexed { i, trip -> balances[trip.driverId] = balances.getValue(trip.driverId) + reimbursed[i] }

        return Settlement(
            vehicleCostsWon = costsMilli.mapValues { (_, milli) -> (milli + MILLI / 2) / MILLI },
            totalCostWon = (totalMilli + MILLI / 2) / MILLI,
            perPersonShareWon = share,
            surplusWon = (collected * MILLI - totalMilli + MILLI / 2) / MILLI,
            balancesWon = balances,
            transfers = buildTransfers(trips, balances),
        )
    }

    // 1인 부담액: [unit]원 단위 올림. 더 걷히는 돈이 1%를 넘으면 1원 단위 올림.
    private fun shareWon(totalMilli: Long, headCount: Long, unit: Long): Long {
        fun ceilTo(unitWon: Long): Long {
            val step = Math.multiplyExact(Math.multiplyExact(headCount, MILLI), unitWon)
            return (totalMilli + step - 1) / step * unitWon
        }
        val share = ceilTo(unit)
        val surplusMilli = Math.multiplyExact(Math.multiplyExact(share, headCount), MILLI) - totalMilli
        return if (surplusMilli * 100 <= totalMilli * MAX_SURPLUS_PERCENT) share else ceilTo(1)
    }

    /**
     * 걷은 돈 [collectedWon]을 운전자들에게 비용 비율대로 원 단위로 나눈다(합계가 정확히 [collectedWon]).
     * 나누어떨어지지 않는 몇 원은 원 아래가 큰 사람부터(동점이면 입력 순서) 1원씩 더 준다.
     */
    private fun splitByCost(collectedWon: Long, costsMilli: List<Long>): List<Long> {
        val total = costsMilli.sum()
        if (total == 0L) return costsMilli.map { 0L }
        val exact = costsMilli.map { Math.multiplyExact(it, collectedWon) }
        val parts = exact.map { it / total }.toMutableList()
        val left = (collectedWon - parts.sum()).toInt()
        exact.indices.sortedByDescending { exact[it] % total }.take(left).forEach { parts[it] += 1 }
        return parts
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

    private fun buildTransfers(trips: List<VehicleTrip>, balances: Map<String, Long>): List<Transfer> {
        val remaining = LinkedHashMap(balances)
        val transfers = mutableListOf<Transfer>()

        fun pay(from: String, to: String, amount: Long = minOf(-remaining.getValue(from), remaining.getValue(to))) {
            if (amount <= 0) return
            transfers += Transfer(from, to, amount)
            remaining[from] = remaining.getValue(from) + amount
            remaining[to] = remaining.getValue(to) - amount
        }

        // 1차: 동승자 → 자기 차 운전자, 부담액 전액. 동승자 잔액은 늘 −부담액이다
        trips.forEach { trip -> trip.passengerIds.forEach { pay(it, trip.driverId, -remaining.getValue(it)) } }

        // 2차: 운전자끼리. 잔액이 큰 사람끼리 먼저 짝지어 한 사람이 여러 번 나눠 보내는 일을 줄인다(동점이면 입력 순서)
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
