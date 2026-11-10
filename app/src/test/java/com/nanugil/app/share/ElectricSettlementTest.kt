package com.nanugil.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 전기차·하이브리드 정산: 사용 전력(Wh) × 충전 단가(원/kWh) ÷ 1000. */
class ElectricSettlementTest {

    @Test
    fun electricVehicle_costIsUsedEnergyTimesUnitPrice() {
        // 70 kWh → 50 kWh, 충전 없음: 20 kWh × 350원 = 7,000원 + 통행료 3,000원
        val ev = VehicleTrip(
            vehicleId = "ev",
            driverId = "지수",
            startBatteryWh = 70_000,
            endBatteryWh = 50_000,
            electricityWonPerKwh = 350,
            tollWon = 3_000,
        )
        assertEquals(10_000L, SettlementCalculator.vehicleCostWon(ev))
    }

    @Test
    fun charging_addsToUsedEnergy() {
        // 30 + 충전 40 − 도착 50 = 20 kWh
        val ev = VehicleTrip(
            vehicleId = "ev",
            driverId = "지수",
            startBatteryWh = 30_000,
            chargedWh = 40_000,
            endBatteryWh = 50_000,
            electricityWonPerKwh = 350,
        )
        assertEquals(7_000L, SettlementCalculator.vehicleCostWon(ev))
    }

    @Test
    fun hybrid_addsFuelAndElectricity() {
        // 연료 5 L × 1,700 = 8,500 + 전력 2 kWh × 350 = 700 + 통행료 1,000
        val hybrid = VehicleTrip(
            vehicleId = "hv",
            driverId = "현우",
            startFuelMl = 40_000,
            endFuelMl = 35_000,
            fuelPriceWonPerLiter = 1_700,
            startBatteryWh = 10_000,
            endBatteryWh = 8_000,
            electricityWonPerKwh = 350,
            tollWon = 1_000,
        )
        assertEquals(10_200L, SettlementCalculator.vehicleCostWon(hybrid))
    }

    @Test
    fun electricCost_roundsToWon() {
        // 1,001 Wh × 347원/kWh = 347.347원 → 347원
        val ev = VehicleTrip(vehicleId = "ev", driverId = "a", startBatteryWh = 1_001, electricityWonPerKwh = 347)
        assertEquals(347L, SettlementCalculator.vehicleCostWon(ev))
    }

    @Test
    fun negativeEnergyUse_rejected() {
        val ev = VehicleTrip(
            vehicleId = "ev",
            driverId = "지수",
            startBatteryWh = 30_000,
            endBatteryWh = 50_000,
            electricityWonPerKwh = 350,
        )
        assertThrows(IllegalArgumentException::class.java) { SettlementCalculator.vehicleCostWon(ev) }
    }

    @Test
    fun mixedGroup_settlesAcrossFuelAndElectric() {
        val fuelCar = VehicleTrip(
            vehicleId = "car1", driverId = "지수", passengerIds = listOf("민호"),
            startFuelMl = 40_000, endFuelMl = 30_000, fuelPriceWonPerLiter = 1_700,
        ) // 17,000
        val ev = VehicleTrip(
            vehicleId = "car2", driverId = "현우", passengerIds = listOf("다은"),
            startBatteryWh = 70_000, endBatteryWh = 50_000, electricityWonPerKwh = 350,
        ) // 7,000
        val result = SettlementCalculator.settle(listOf(fuelCar, ev))
        assertEquals(24_000L, result.totalCostWon)
        assertEquals(6_000L, result.perPersonShareWon)
    }
}
