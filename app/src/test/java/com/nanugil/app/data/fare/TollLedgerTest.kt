package com.nanugil.app.data.fare

import org.junit.Assert.assertEquals
import org.junit.Test

class TollLedgerTest {

    @Test
    fun straightRoute_arrives_paysWholeFare() {
        val l = TollLedger().update(1, 10_000, 100_000).update(1, null, 30_000).update(1, null, 0).arrive()
        assertEquals(10_000L, l.totalWon)
    }

    @Test
    fun rerouteHalfway_keepsHalfOfOldRoute_plusNewRoute() {
        val l = TollLedger().update(1, 10_000, 100_000).update(1, 10_000, 50_000) // 절반 달림
            .update(2, 4_000, 60_000) // 재탐색: 남은 길 통행료 4,000원
            .update(2, null, 0).arrive()
        assertEquals(9_000L, l.totalWon)
    }

    @Test
    fun rerouteToFreeRoad_dropsRestOfTheToll() {
        val l = TollLedger().update(1, 10_000, 100_000).update(1, null, 50_000).update(2, 0, 60_000).arrive()
        assertEquals(5_000L, l.totalWon)
    }

    @Test
    fun newRouteFareArrivesLate_usesTheNewFare() {
        // 재탐색 직후 잠깐은 전 경로 값이 남아 있다가 새 통행료가 온다
        val l = TollLedger().update(1, 10_000, 100_000).update(1, null, 50_000)
            .update(2, 10_000, 50_000).update(2, 2_000, 55_000).arrive()
        assertEquals(7_000L, l.totalWon)
    }

    @Test
    fun guidanceCancelled_countsOnlyWhatWasDriven() {
        val l = TollLedger().update(1, 8_000, 80_000).update(1, null, 60_000)
        assertEquals(2_000L, l.totalWon)
    }

    @Test
    fun appRestarted_continuesTheSameRoute() {
        val saved = TollLedger().update(5, 10_000, 100_000).update(5, null, 40_000).copy(routeVersion = null)
        val l = saved.update(0, 10_000, 40_000).update(0, null, 0).arrive() // 내비 연결이 새로 0번부터
        assertEquals(10_000L, l.totalWon)
    }

    @Test
    fun arriveTwice_doesNotCountAgain() {
        val l = TollLedger().update(1, 3_000, 10_000).arrive().arrive()
        assertEquals(3_000L, l.totalWon)
    }
}
