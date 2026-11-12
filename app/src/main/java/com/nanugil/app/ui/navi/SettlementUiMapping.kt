package com.nanugil.app.ui.navi

import com.nanugil.app.ui.settlement.PriceChoice
import com.nanugil.app.ui.settlement.PriceSource
import com.nanugil.app.ui.settlement.SettlementState
import com.nanugil.app.ui.settlement.VehicleType
import com.nanugil.app.ui.settlement.won

/**
 * 정산 세션 값을 #13의 08 도착·09 정산 요청 보냄 화면 모양으로 바꾼다.
 * 08은 운전자별 받을 돈과 "동승자 n명 각자 보냄" 한 줄로 줄여 보여 준다(와이어프레임 v5).
 */
internal fun SettlementState.toUi(payLink: String?): SettlementUi {
    val (result, message) = settled
    val names = cars.map { it.driver.trim() }
    val passengers = cars.sumOf { c -> c.passengers.split(',').count { it.isNotBlank() } }
    val cars = cars.mapIndexed { i, c ->
        val toll = runCatching { won(c.tollWon, "", required = false) }.getOrDefault(0)
        val cost = result?.vehicleCostsWon?.get("car${i + 1}") ?: 0
        val energy = when (c.type) {
            VehicleType.ELECTRIC -> "충전"
            VehicleType.HYBRID -> "주유·충전"
            else -> "주유"
        }
        CarCost("${'A' + i}차", fuelWon = (cost - toll).coerceAtLeast(0), tollWon = toll, energyLabel = energy)
    }
    val share = result?.perPersonShareWon ?: 0
    val lines = buildList {
        names.forEachIndexed { i, name ->
            add(MoneyLine(name.take(1), name, "${'A' + i}차 운전", result?.balancesWon?.get(name) ?: 0, highlight = i == 0))
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
        totalWon = result?.totalCostWon ?: 0,
        people = names.size + passengers,
        cars = cars,
        basis = when (choice) {
            PriceChoice.NEARBY_AVERAGE -> PriceBasis.AVERAGE
            PriceChoice.NEARBY_LOWEST -> PriceBasis.LOWEST
            PriceChoice.PICKED -> null
        },
        lines = lines,
        sumCheckWon = result?.balancesWon?.values?.filter { it > 0 }?.sum() ?: 0,
        payments = request?.transfers.orEmpty().map { t ->
            val toMe = t.toId == request?.myId
            PaymentStatus(t.fromId.take(1), if (toMe) t.fromId else "${t.fromId} → ${t.toId}", t.done, t.amountWon)
        },
        basisDetail = basisDetail,
        problem = message,
        myName = request?.myId ?: myDriver,
        payLink = payLink,
        serverNote = serverNote,
    )
}
