package com.nanugil.app.ui.navi

import com.nanugil.app.share.SettlementCalculator
import com.nanugil.app.ui.settlement.PriceChoice
import com.nanugil.app.ui.settlement.PriceSource
import com.nanugil.app.ui.settlement.SettlementState
import com.nanugil.app.ui.settlement.VehicleType
import com.nanugil.app.ui.settlement.name
import com.nanugil.app.ui.settlement.costMissing
import com.nanugil.app.ui.settlement.toTrips
import com.nanugil.app.ui.settlement.won

/**
 * 정산 세션 값을 #13의 08 도착·09 정산 요청 보냄 화면 모양으로 바꾼다.
 * 08은 운전자별 받을 돈과 "동승자 n명 각자 보냄" 한 줄로 줄여 보여 준다(와이어프레임 v5).
 */
internal fun SettlementState.toUi(payLink: String?): SettlementUi {
    val (result, message) = settled
    // 서버 운행에서 아직 비용을 저장하지 않은 다른 차. 그 차를 0원으로 정산하지 않고 "기다리는 중"으로 보여 준다.
    fun waitingAt(i: Int) = tripId != null && (i > 0 || !iDrive) && this.cars[i].costMissing
    val waiting = this.cars.indices.filter(::waitingAt).takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { i -> "${this.cars[i].name(i)}(${this.cars[i].driver})" }?.let { "$it 비용 저장을 기다리는 중" }
    val names = cars.map { it.driver.trim() }
    val passengers = cars.sumOf { c -> c.passengers.split(',').count { it.isNotBlank() } }
    val cars = cars.mapIndexed { i, c ->
        val toll = runCatching { won(c.tollWon, "", required = false) }.getOrDefault(0)
        // 다른 차를 기다리는 동안에도 값이 있는 차는 그 차 비용을 보여 준다
        val alone = { runCatching { SettlementCalculator.vehicleCostWon(toTrips(listOf(c)).single()) }.getOrDefault(0) }
        val cost = result?.vehicleCostsWon?.get("car${i + 1}") ?: if (waiting != null) alone() else 0
        val energy = when (c.type) {
            VehicleType.ELECTRIC -> "충전"
            VehicleType.HYBRID -> "주유·충전"
            else -> "주유"
        }
        // 내 차 통행료가 내비 예상액 그대로면 그렇다고 알린다(값 수정에서 고칠 수 있다)
        val tollLabel = when {
            i != 0 || estimatedTollWon != toll -> "통행료"
            tollDiscountPercent > 0 -> "통행료(내비 예상·전기차 ${tollDiscountPercent}% 할인)"
            else -> "통행료(내비 예상)"
        }
        CarCost(c.name(i), fuelWon = (cost - toll).coerceAtLeast(0), tollWon = toll, energyLabel = energy, tollLabel = tollLabel, waiting = waitingAt(i))
    }
    val share = result?.perPersonShareWon ?: 0
    val lines = buildList {
        names.forEachIndexed { i, name ->
            add(MoneyLine(name.take(1), name, "${this@toUi.cars[i].name(i)} 운전", result?.balancesWon?.get(name) ?: 0, highlight = i == 0))
        }
        if (passengers > 0) add(MoneyLine("$passengers", "동승자 ${passengers}명", "자기 차 운전자에게 각자 보냄", -share, highlight = false))
    }
    val myStation = this.cars.firstOrNull()?.type?.opinetProduct?.let(chosen::get) ?: chosen.values.firstOrNull()
    val basisDetail = when {
        priceSource == PriceSource.NEARBY && myStation != null && choice != PriceChoice.NEARBY_AVERAGE ->
            "${myStation.name} · %,d원/L".format(myStation.priceWon)
        priceSource == PriceSource.NEARBY -> null
        else -> priceSource.text
    }
    return SettlementUi(
        // 보낸 요청이 있으면 그때 금액(09). 요청 뒤에 값을 고쳐도 보낸 금액은 바뀌지 않는다.
        perPersonWon = request?.perPersonWon?.takeIf { it > 0 } ?: share,
        totalWon = result?.totalCostWon ?: if (waiting != null) cars.filterNot { it.waiting }.sumOf { it.totalWon } else 0,
        people = names.size + passengers,
        cars = cars,
        basis = when (choice) {
            PriceChoice.NEARBY_AVERAGE -> PriceBasis.AVERAGE
            PriceChoice.NEARBY_LOWEST -> PriceBasis.LOWEST
            PriceChoice.PICKED -> null
        },
        lines = lines,
        sumCheckWon = result?.balancesWon?.values?.filter { it > 0 }?.sum() ?: 0,
        // 내가 받을 돈은 보낼 사람 이름, 내가 보낼 돈은 받을 사람 이름으로
        payments = request?.transfers.orEmpty().map { t ->
            val incoming = t.toId == request?.myId
            val other = if (incoming) t.fromId else t.toId
            PaymentStatus(other.take(1), other, t.done, t.amountWon, incoming)
        },
        basisDetail = basisDetail,
        problem = message.takeIf { waiting == null }, // 기다리는 차의 "값을 입력하세요"는 사용자가 고칠 일이 아니다
        myName = request?.myId ?: me,
        payLink = payLink,
        serverNote = serverNote,
        waiting = waiting,
    )
}
