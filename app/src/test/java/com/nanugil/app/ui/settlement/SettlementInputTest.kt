package com.nanugil.app.ui.settlement

import com.nanugil.app.share.SettlementCalculator
import com.nanugil.app.share.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettlementInputTest {

    @Test
    fun toTrips_convertsLitersAndNames() {
        val trips = toTrips(
            listOf(CarInput("지수", " 민호, 서연 ,", "40.5", "", "30", "1,000")),
            "1,700",
        )
        val trip = trips.single()
        assertEquals("지수", trip.driverId)
        assertEquals(listOf("민호", "서연"), trip.passengerIds)
        assertEquals(40_500L, trip.startFuelMl)
        assertEquals(0L, trip.refueledMl)
        assertEquals(30_000L, trip.endFuelMl)
        assertEquals(1_700L, trip.fuelPriceWonPerLiter)
        assertEquals(1_000L, trip.tollWon)
    }

    @Test
    fun defaultScreenExample_settlesAsShown() {
        val trips = toTrips(
            listOf(
                CarInput("지수", "민호, 서연", "40", "0", "30", "5000"),
                CarInput("현우", "다은", "30", "0", "25", "3500"),
            ),
            "1700",
        )
        val result = SettlementCalculator.settle(trips)
        assertEquals(6_800L, result.perPersonShareWon)
        assertEquals(
            listOf(
                Transfer("민호", "지수", 6_800),
                Transfer("서연", "지수", 6_800),
                Transfer("다은", "현우", 5_200),
                Transfer("다은", "지수", 1_600),
            ),
            result.transfers,
        )
    }

    @Test
    fun missingOrInvalidInput_rejectedWithMessage() {
        val blankEnd = assertThrows(IllegalArgumentException::class.java) {
            toTrips(listOf(CarInput("지수", "", "40", "0", "", "0")), "1700")
        }
        assertEquals("1번 차 도착 연료 값을 입력하세요.", blankEnd.message)
        assertThrows(IllegalArgumentException::class.java) { toTrips(listOf(CarInput("", "", "40", "0", "30", "0")), "1700") }
        assertThrows(IllegalArgumentException::class.java) { toTrips(listOf(CarInput("지수", "", "4o", "0", "30", "0")), "1700") }
        assertThrows(IllegalArgumentException::class.java) { toTrips(listOf(CarInput("지수", "", "40", "0", "30", "0")), "") }
    }
}
