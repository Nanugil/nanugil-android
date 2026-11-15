package com.nanugil.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementCalculatorTest {

    /** docs/vehicle-api-research.md "정산 예시"와 같은 값. */
    private val docExample = listOf(
        VehicleTrip(
            vehicleId = "A", driverId = "a", passengerIds = listOf("p1", "p2", "p3"),
            startFuelMl = 42_000, refueledMl = 0, endFuelMl = 33_000,
            fuelPriceWonPerLiter = 1_700, tollWon = 6_700,
        ),
        VehicleTrip(
            vehicleId = "B", driverId = "b", passengerIds = listOf("p4", "p5", "p6"),
            startFuelMl = 20_000, refueledMl = 30_000, endFuelMl = 35_000,
            fuelPriceWonPerLiter = 1_700, tollWon = 6_500,
        ),
    )

    @Test
    fun usedFuel_includesRefuel() {
        assertEquals(15_000L, SettlementCalculator.usedFuelMl(20_000, 30_000, 35_000))
    }

    @Test
    fun usedFuel_negativeIsRejected() {
        // 주유량 입력이 빠지면 도착 연료가 출발보다 많아진다
        assertThrows(IllegalArgumentException::class.java) {
            SettlementCalculator.usedFuelMl(20_000, 0, 35_000)
        }
    }

    @Test
    fun usedFuel_negativeInputIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SettlementCalculator.usedFuelMl(-1, 0, 0)
        }
    }

    @Test
    fun fuelCost_roundsHalfUpToWon() {
        assertEquals(15_300L, SettlementCalculator.fuelCostWon(9_000, 1_700))
        assertEquals(2L, SettlementCalculator.fuelCostWon(1, 1_500)) // 1.5원 → 2원
        assertEquals(1L, SettlementCalculator.fuelCostWon(1, 1_499)) // 1.499원 → 1원
        assertEquals(0L, SettlementCalculator.fuelCostWon(0, 1_700))
    }

    @Test
    fun fuelCost_overflowIsRejected() {
        assertThrows(ArithmeticException::class.java) {
            SettlementCalculator.fuelCostWon(Long.MAX_VALUE, 2)
        }
    }

    @Test
    fun settle_matchesDocExample() {
        val result = SettlementCalculator.settle(docExample)

        assertEquals(mapOf("A" to 22_000L, "B" to 32_000L), result.vehicleCostsWon)
        assertEquals(54_000L, result.totalCostWon)
        assertEquals(6_750L, result.perPersonShareWon)
        assertEquals(0L, result.surplusWon)
        assertEquals(15_250L, result.balancesWon["a"])
        assertEquals(25_250L, result.balancesWon["b"])
        (1..6).forEach { assertEquals(-6_750L, result.balancesWon["p$it"]) }
    }

    @Test
    fun settle_passengersPayFullShareToOwnDriver_thenDriversSettle() {
        val result = SettlementCalculator.settle(docExample)

        // A 동승자 3명 = 20,250원이 A 몫 15,250원보다 많으므로 A가 남는 5,000원을 B에게 넘긴다
        val expected = listOf(
            Transfer("p1", "a", 6_750),
            Transfer("p2", "a", 6_750),
            Transfer("p3", "a", 6_750),
            Transfer("p4", "b", 6_750),
            Transfer("p5", "b", 6_750),
            Transfer("p6", "b", 6_750),
            Transfer("a", "b", 5_000),
        )
        assertEquals(expected, result.transfers)
        assertTransfersBalance(result)
    }

    @Test
    fun settle_driversOnly_cheaperDriverPaysOther() {
        val trips = listOf(
            VehicleTrip("A", "a", startFuelMl = 0, endFuelMl = 0, fuelPriceWonPerLiter = 0, tollWon = 30_000),
            VehicleTrip("B", "b", startFuelMl = 0, endFuelMl = 0, fuelPriceWonPerLiter = 0, tollWon = 10_000),
        )

        val result = SettlementCalculator.settle(trips)

        assertEquals(20_000L, result.perPersonShareWon)
        assertEquals(listOf(Transfer("b", "a", 10_000)), result.transfers)
    }

    @Test
    fun settle_driverWhoPaidLessThanShare_sendsMoney() {
        val trips = listOf(
            VehicleTrip("A", "a", listOf("p1"), 0, 0, 0, 0, tollWon = 40_000),
            VehicleTrip("B", "b", listOf("p2"), 0, 0, 0, 0, tollWon = 0),
        )

        val result = SettlementCalculator.settle(trips)

        assertEquals(10_000L, result.perPersonShareWon)
        assertEquals(-10_000L, result.balancesWon["b"])
        assertEquals(30_000L, result.balancesWon["a"])
        // p2도 자기 차 운전자 b에게 보내고, b가 받은 돈까지 a에게 넘긴다
        val expected = listOf(
            Transfer("p1", "a", 10_000),
            Transfer("p2", "b", 10_000),
            Transfer("b", "a", 20_000),
        )
        assertEquals(expected, result.transfers)
        assertTransfersBalance(result)
    }

    @Test
    fun settle_shareRoundsUpToTenWon_surplusGoesToDriver() {
        // 10,001 ÷ 3 = 3,333.67원 → 3,340원. 더 걷힌 19원(0.19%)은 운전자 몫
        val trips = listOf(VehicleTrip("A", "a", listOf("p1", "p2"), tollWon = 10_001))

        val result = SettlementCalculator.settle(trips)

        assertEquals(3_340L, result.perPersonShareWon)
        assertEquals(19L, result.surplusWon)
        assertEquals(6_680L, result.balancesWon["a"]) // 10,020 − 3,340
        assertEquals(listOf(3_340L), result.transfers.map { it.amountWon }.distinct())
        assertTransfersBalance(result)
    }

    @Test
    fun settle_roundingUnitRoundsUp() {
        // 54,003 ÷ 4 = 13,500.75원 → 100원 단위 13,600원. 더 걷힌 397원은 0.73%라 1% 안
        val trips = listOf(VehicleTrip("A", "a", listOf("p1", "p2", "p3"), tollWon = 54_003))

        val result = SettlementCalculator.settle(trips, roundingUnitWon = 100)

        assertEquals(13_600L, result.perPersonShareWon)
        assertEquals(397L, result.surplusWon)
        assertTransfersBalance(result)
    }

    @Test
    fun settle_surplusOverOnePercent_roundsUpToOneWonInstead() {
        // 33 ÷ 3 = 11원. 100원 단위로 올리면 267원을 더 걷어 1%를 넘으니 1원 단위로 올린다
        val trips = listOf(
            VehicleTrip("A", "a", listOf("p1"), tollWon = 30),
            VehicleTrip("B", "b", tollWon = 3),
        )

        val result = SettlementCalculator.settle(trips, roundingUnitWon = 100)

        assertEquals(11L, result.perPersonShareWon)
        assertEquals(0L, result.surplusWon)
        assertEquals(mapOf("a" to 19L, "p1" to -11L, "b" to -8L), result.balancesWon)
        assertTransfersBalance(result)
    }

    @Test
    fun settle_surplusIsSplitByDriverCost() {
        // 54,003 ÷ 8 = 6,750.38원 → 6,760원, 더 걷힌 77원을 a·b가 비용 비율대로 나눈다(b가 1원 더)
        val trips = listOf(
            VehicleTrip("A", "a", listOf("p1", "p2", "p3"), tollWon = 27_000),
            VehicleTrip("B", "b", listOf("p4", "p5", "p6"), tollWon = 27_003),
        )

        val result = SettlementCalculator.settle(trips)

        assertEquals(6_760L, result.perPersonShareWon)
        assertEquals(77L, result.surplusWon)
        assertEquals(20_278L, result.balancesWon["a"]) // 27,038 − 6,760
        assertEquals(20_282L, result.balancesWon["b"]) // 27,042 − 6,760
        assertTransfersBalance(result)
    }

    @Test
    fun settle_keepsElectricityBelowOneWon_untilTheEnd() {
        // 12.345 kWh × 325.6원 = 4,019.532원. 원 단위로 먼저 반올림하지 않고 정산 끝에서 올린다
        val ev = VehicleTrip("A", "a", listOf("p1", "p2"), startBatteryWh = 60_000, endBatteryWh = 47_655, electricityWonPerMwh = 325_600)
        assertEquals(4_019_532L, SettlementCalculator.vehicleCostMilliWon(ev))

        val result = SettlementCalculator.settle(listOf(ev))

        assertEquals(1_340L, result.perPersonShareWon) // 1,339.84원 → 1,340원
        assertEquals(4_020L, result.vehicleCostsWon["A"])
        assertEquals(2_680L, result.balancesWon["a"])
        assertTransfersBalance(result)
    }

    @Test
    fun settle_zeroCost_hasNoTransfers() {
        val trips = listOf(VehicleTrip("A", "a", listOf("p1"), 10_000, 0, 10_000, 1_700))

        val result = SettlementCalculator.settle(trips)

        assertEquals(0L, result.totalCostWon)
        assertTrue(result.transfers.isEmpty())
    }

    @Test
    fun settle_blankIdRejected() {
        val trips = listOf(VehicleTrip("A", "a", listOf(" "), 0, 0, 0, 0))
        assertThrows(IllegalArgumentException::class.java) { SettlementCalculator.settle(trips) }
    }

    @Test
    fun settle_secondPass_matchesLargestBalancesFirst() {
        // 1인 100원. a +75, b +75, c −20, d −30, p −100. p가 c에게 100원을 보내 c는 −120이 된다
        val trips = listOf(
            VehicleTrip("A", "a", startFuelMl = 0, endFuelMl = 0, fuelPriceWonPerLiter = 0, tollWon = 175),
            VehicleTrip("B", "b", startFuelMl = 0, endFuelMl = 0, fuelPriceWonPerLiter = 0, tollWon = 175),
            VehicleTrip("C", "c", listOf("p"), 0, 0, 0, 0, tollWon = 80),
            VehicleTrip("D", "d", startFuelMl = 0, endFuelMl = 0, fuelPriceWonPerLiter = 0, tollWon = 70),
        )

        val result = SettlementCalculator.settle(trips)

        val expected = listOf(
            Transfer("p", "c", 100),
            Transfer("c", "a", 75),
            Transfer("c", "b", 45),
            Transfer("d", "b", 30),
        )
        assertEquals(expected, result.transfers)
        assertTransfersBalance(result)
    }

    @Test
    fun settle_ties_areDeterministicByInputOrder() {
        val trips = listOf(
            VehicleTrip("A", "a", listOf("p1"), 0, 0, 0, 0, tollWon = 10_000),
            VehicleTrip("B", "b", listOf("p2"), 0, 0, 0, 0, tollWon = 10_001),
        )

        val first = SettlementCalculator.settle(trips)
        val second = SettlementCalculator.settle(trips)

        assertEquals(first, second)
        assertTransfersBalance(first)
    }

    @Test
    fun settle_emptyTripsRejected() {
        assertThrows(IllegalArgumentException::class.java) { SettlementCalculator.settle(emptyList()) }
    }

    @Test
    fun settle_duplicatedMemberRejected() {
        val trips = listOf(
            VehicleTrip("A", "a", listOf("p1"), 0, 0, 0, 0),
            VehicleTrip("B", "b", listOf("p1"), 0, 0, 0, 0),
        )
        assertThrows(IllegalArgumentException::class.java) { SettlementCalculator.settle(trips) }
    }

    @Test
    fun settle_driverListedAsOwnPassengerRejected() {
        val trips = listOf(VehicleTrip("A", "a", listOf("a"), 0, 0, 0, 0))
        assertThrows(IllegalArgumentException::class.java) { SettlementCalculator.settle(trips) }
    }

    @Test
    fun settle_duplicatedVehicleRejected() {
        val trips = listOf(
            VehicleTrip("A", "a", startFuelMl = 0, endFuelMl = 0, fuelPriceWonPerLiter = 0),
            VehicleTrip("A", "b", startFuelMl = 0, endFuelMl = 0, fuelPriceWonPerLiter = 0),
        )
        assertThrows(IllegalArgumentException::class.java) { SettlementCalculator.settle(trips) }
    }

    @Test
    fun settle_negativeTollRejected() {
        val trips = listOf(VehicleTrip("A", "a", startFuelMl = 0, endFuelMl = 0, fuelPriceWonPerLiter = 0, tollWon = -1))
        assertThrows(IllegalArgumentException::class.java) { SettlementCalculator.settle(trips) }
    }

    @Test
    fun settle_invalidRoundingUnitRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SettlementCalculator.settle(docExample, roundingUnitWon = 0)
        }
    }

    /** 송금을 모두 반영하면 모든 사람의 잔액이 0이 되는지 확인한다. */
    private fun assertTransfersBalance(result: Settlement) {
        val remaining = result.balancesWon.toMutableMap()
        result.transfers.forEach {
            assertTrue("송금액은 양수여야 함: $it", it.amountWon > 0)
            remaining[it.fromId] = remaining.getValue(it.fromId) + it.amountWon
            remaining[it.toId] = remaining.getValue(it.toId) - it.amountWon
        }
        assertTrue("남은 잔액: $remaining", remaining.values.all { it == 0L })
    }
}
