package com.nanugil.app.ui.settlement

import com.nanugil.app.data.server.TripMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettlementServerTest {

    private fun driver(id: String, fuel: String, vararg cost: Pair<String, Long>) = cost.toMap().let { c ->
        TripMember(
            tripId = "t", memberId = id, vehicleDriverMemberId = id, vehicleLabel = "$id 차", fuelType = fuel,
            startFuelMl = c["startFuel"], endFuelMl = c["endFuel"], refueledMl = c["refuel"], appliedPrice = c["price"]?.toInt(),
            startBatteryWh = c["startBat"], endBatteryWh = c["endBat"], chargedWh = c["charged"],
            appliedPricePerKwh = c["kwh"]?.toDouble(), tollEstimated = c["toll"]?.toInt(),
        )
    }
    private fun rider(id: String, car: String) = TripMember(tripId = "t", memberId = id, vehicleDriverMemberId = car)

    private val names = mapOf("a" to "김민수", "b" to "이서준", "p1" to "정예린", "p2" to "박지우", "p3" to "최하은")

    @Test
    fun tripMembers_becomeCars_withMyCarFirst() {
        val members = listOf(
            driver("b", "GASOLINE", "startFuel" to 50_000, "endFuel" to 20_000, "price" to 1_804, "toll" to 15_000),
            rider("p2", "b"), rider("p3", "b"),
            driver("a", "EV"), // 내 차: 비용은 아직 서버에 없다(이 기기 운행 기록으로 채운다)
            rider("p1", "a"),
        )
        val cars = carsFromTrip(members, names, myMemberId = "a")

        assertEquals(listOf("김민수", "이서준"), cars.map { it.driver })
        assertEquals(VehicleType.ELECTRIC, cars[0].type)
        assertEquals("정예린", cars[0].passengers)
        assertEquals("", cars[0].startBatteryKwh)
        assertEquals("박지우, 최하은", cars[1].passengers)
        assertEquals("50", cars[1].startFuelL)
        assertEquals("20", cars[1].endFuelL)
        assertEquals("1804", cars[1].fuelPrice)
        assertEquals("15000", cars[1].tollWon)
    }

    @Test
    fun otherDriverWithoutSavedCost_showsWhatIsMissing() {
        val cars = carsFromTrip(listOf(driver("a", "EV", "startBat" to 70_000, "endBat" to 50_000, "kwh" to 348), driver("b", "DIESEL")), names, "a")
        val message = settleOrMessage(cars).second
        assertEquals("2번 차 출발 연료 값을 입력하세요.", message)
    }

    @Test
    fun refresh_fillsOnlyCarsStillWithoutCost() {
        val local = carsFromTrip(listOf(driver("a", "EV"), driver("b", "DIESEL"), driver("c", "GASOLINE")), names + ("c" to "최운전"), "a")
            .let { cars -> cars.mapIndexed { i, c -> if (i == 2) c.copy(startFuelL = "30", endFuelL = "25") else c } } // c차는 사용자가 직접 넣음
        val fresh = carsFromTrip(
            listOf(
                driver("a", "EV", "startBat" to 1), driver("b", "DIESEL", "startFuel" to 40_000, "endFuel" to 35_000),
                driver("c", "GASOLINE", "startFuel" to 50_000), driver("d", "EV"),
            ),
            names + ("c" to "최운전") + ("d" to "새운전"), "a",
        )
        val merged = withSavedCosts(local, fresh)

        assertEquals("", merged[0].startBatteryKwh) // 내 차는 이 기기 값
        assertEquals("40", merged[1].startFuelL) // 나중에 저장된 비용
        assertEquals("30", merged[2].startFuelL) // 고친 값은 그대로
        assertEquals("새운전", merged[3].driver)
    }

    @Test
    fun refresh_matchesCarsByMemberId_evenWithSameDriverName() {
        // 운전자 둘이 이름이 같다(김민수). 이름으로 맞추면 b의 비용이 c 자리에 들어간다
        val sameName = mapOf("a" to "나", "b" to "김민수", "c" to "김민수")
        val local = carsFromTrip(listOf(driver("a", "EV"), driver("b", "DIESEL"), driver("c", "GASOLINE")), sameName, "a")
        val fresh = carsFromTrip(
            listOf(driver("a", "EV"), driver("b", "DIESEL"), driver("c", "GASOLINE", "startFuel" to 30_000, "endFuel" to 20_000)),
            sameName, "a",
        )
        val merged = withSavedCosts(local, fresh)

        assertEquals(listOf("a", "b", "c"), merged.map { it.memberId })
        assertEquals("", merged[1].startFuelL) // b는 아직 저장 전
        assertEquals("30", merged[2].startFuelL) // c의 비용은 c 자리에
    }

    @Test
    fun myCar_becomesCostInput() {
        val ev = CarInput(VehicleType.ELECTRIC, "김민수", electricityPrice = "348.4", startBatteryKwh = "77.4", chargedKwh = "15", endBatteryKwh = "72", tollWon = "10000")
        val cost = ev.toCostInput(PriceChoice.NEARBY_LOWEST)
        assertEquals(77_400L, cost.startBatteryWh)
        assertEquals(15_000L, cost.chargedWh)
        assertEquals(72_000L, cost.endBatteryWh)
        assertEquals(348.4, cost.appliedPricePerKwh!!, 1e-9)
        assertEquals(10_000, cost.tollEstimated)
        assertEquals(0L, cost.startFuelMl)
        assertNull(cost.priceBasis) // 전기차는 유가 기준이 없다
        assertNull(cost.fuelCalcMethod)

        val gas = CarInput(VehicleType.GASOLINE, "이서준", fuelPrice = "1804", startFuelL = "50", endFuelL = "20")
        assertEquals("CHEAPEST", gas.toCostInput(PriceChoice.NEARBY_LOWEST).priceBasis)
        assertNull(gas.toCostInput(PriceChoice.NEARBY_AVERAGE).priceBasis)
        assertEquals(1_804, gas.toCostInput(PriceChoice.PICKED).appliedPrice)
    }

    @Test
    fun toll_estimateAndDriverFix_goToSeparateColumns() {
        val car = CarInput(VehicleType.GASOLINE, "김민수", fuelPrice = "1804", startFuelL = "50", endFuelL = "20", tollWon = "9000")
        car.toCostInput(PriceChoice.PICKED, estimatedToll = 9_000).let {
            assertEquals(9_000, it.tollEstimated)
            assertNull(it.tollAdjusted) // 고치지 않음
        }
        car.copy(tollWon = "7500").toCostInput(PriceChoice.PICKED, estimatedToll = 9_000).let {
            assertEquals(9_000, it.tollEstimated)
            assertEquals(7_500, it.tollAdjusted)
        }
        assertNull(car.toCostInput(PriceChoice.PICKED).tollAdjusted) // 내비 안내 없이 직접 넣은 값
    }
}
