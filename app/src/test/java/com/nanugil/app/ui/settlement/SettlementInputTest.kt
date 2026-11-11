package com.nanugil.app.ui.settlement

import com.nanugil.app.data.fuel.FuelProduct
import com.nanugil.app.share.SettlementCalculator
import com.nanugil.app.share.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettlementInputTest {

    private fun gasoline(driver: String, passengers: String, start: String, end: String, toll: String = "0", refueled: String = "0") =
        CarInput(VehicleType.GASOLINE, driver, passengers, fuelPrice = "1700", startFuelL = start, refueledL = refueled, endFuelL = end, tollWon = toll)

    @Test
    fun toTrips_convertsLitersAndNames() {
        val trip = toTrips(listOf(gasoline("지수", " 민호, 서연 ,", "40.5", "30", toll = "1,000", refueled = ""))).single()
        assertEquals("지수", trip.driverId)
        assertEquals(listOf("민호", "서연"), trip.passengerIds)
        assertEquals(40_500L, trip.startFuelMl)
        assertEquals(0L, trip.refueledMl)
        assertEquals(30_000L, trip.endFuelMl)
        assertEquals(1_700L, trip.fuelPriceWonPerLiter)
        assertEquals(1_000L, trip.tollWon)
        assertEquals(0L, trip.startBatteryWh)
        assertEquals(0L, trip.electricityWonPerMwh)
    }

    @Test
    fun defaultScreenExample_settlesAsShown() {
        val result = SettlementCalculator.settle(toTrips(exampleCars(VehicleType.GASOLINE)))
        assertEquals(6_800L, result.perPersonShareWon)
        assertEquals(
            listOf(
                Transfer("민호", "지수", 6_800),
                Transfer("서연", "지수", 6_800),
                Transfer("다은", "현우", 6_800),
                Transfer("현우", "지수", 1_600),
            ),
            result.transfers,
        )
    }

    @Test
    fun electric_usesBatteryOnly() {
        val trip = toTrips(listOf(CarInput(VehicleType.ELECTRIC, "지수", startBatteryKwh = "70", endBatteryKwh = "50.5", electricityPrice = "325.6"))).single()
        assertEquals(70_000L, trip.startBatteryWh)
        assertEquals(50_500L, trip.endBatteryWh)
        assertEquals(325_600L, trip.electricityWonPerMwh)
        assertEquals(0L, trip.startFuelMl)
        assertEquals(0L, trip.fuelPriceWonPerLiter)
        // 연료 칸에 뭐가 있어도 전기차는 무시한다.
        val ignored = toTrips(listOf(CarInput(VehicleType.ELECTRIC, "지수", startFuelL = "abc", startBatteryKwh = "1", endBatteryKwh = "0"))).single()
        assertEquals(0L, ignored.startFuelMl)
    }

    @Test
    fun hybrid_batteryOptional_fuelRequired() {
        val trip = toTrips(listOf(CarInput(VehicleType.HYBRID, "현우", fuelPrice = "1700", startFuelL = "40", endFuelL = "35"))).single()
        assertEquals(5_000L, trip.startFuelMl - trip.endFuelMl)
        assertEquals(0L, trip.startBatteryWh)
        assertThrows(IllegalArgumentException::class.java) {
            toTrips(listOf(CarInput(VehicleType.HYBRID, "현우", startFuelL = "", endFuelL = "35")))
        }
    }

    @Test
    fun exampleCars_settleForEveryType() {
        // 차종 선택 화면에서 무엇을 골라도 예시 값으로 바로 계산이 돼야 한다.
        VehicleType.entries.forEach { type ->
            val result = SettlementCalculator.settle(toTrips(exampleCars(type)))
            assert(result.totalCostWon > 0) { "$type 예시 값 비용이 0" }
        }
    }

    @Test
    fun withType_resetsFuelPriceToTypeDefault() {
        val car = CarInput(VehicleType.GASOLINE, "지수").withType(VehicleType.DIESEL)
        assertEquals(VehicleType.DIESEL, car.type)
        assertEquals("1600", car.fuelPrice)
    }

    @Test
    fun withType_usesOpinetAverageWhenLoaded() {
        val prices = mapOf(FuelProduct.GASOLINE to 1712, FuelProduct.DIESEL to 1583)
        assertEquals("1583", CarInput(VehicleType.GASOLINE).withType(VehicleType.DIESEL, prices).fuelPrice)
        assertEquals("1712", CarInput(VehicleType.DIESEL).withType(VehicleType.HYBRID, prices).fuelPrice) // 하이브리드 = 휘발유
        // 평균가에 없는 유종은 예시값
        assertEquals("1950", CarInput().withType(VehicleType.PREMIUM_GASOLINE, prices).fuelPrice)
        assertEquals("", CarInput().withType(VehicleType.ELECTRIC, prices).fuelPrice)
    }

    @Test
    fun missingOrInvalidInput_rejectedWithMessage() {
        val blankEnd = assertThrows(IllegalArgumentException::class.java) { toTrips(listOf(gasoline("지수", "", "40", ""))) }
        assertEquals("1번 차 도착 연료 값을 입력하세요.", blankEnd.message)
        val blankBattery = assertThrows(IllegalArgumentException::class.java) {
            toTrips(listOf(CarInput(VehicleType.ELECTRIC, "지수", startBatteryKwh = "70")))
        }
        assertEquals("1번 차 도착 배터리 값을 입력하세요.", blankBattery.message)
        assertThrows(IllegalArgumentException::class.java) { toTrips(listOf(gasoline("", "", "40", "30"))) }
        assertThrows(IllegalArgumentException::class.java) { toTrips(listOf(gasoline("지수", "", "4o", "30"))) }
        assertThrows(IllegalArgumentException::class.java) {
            toTrips(listOf(CarInput(VehicleType.GASOLINE, "지수", fuelPrice = "", startFuelL = "40", endFuelL = "30")))
        }
    }
}
