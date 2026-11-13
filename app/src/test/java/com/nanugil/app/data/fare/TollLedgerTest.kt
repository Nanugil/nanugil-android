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

    // 아래는 내비가 요금소 위치를 알려 줄 때: 100km 경로, 20km 지점 고속도로 진입 ~ 80km 지점 마지막 요금소
    private val span = 20_000 to 80_000

    @Test
    fun rerouteBeforeEnteringExpressway_paysNothing() {
        val l = TollLedger().update(1, 10_000, 100_000, span).update(1, null, 85_000, 5_000 to 65_000)
            .update(2, 0, 70_000).arrive() // 무료 도로로 재탐색
        assertEquals(0L, l.totalWon)
    }

    @Test
    fun rerouteHalfwayOnExpressway_paysHalf() {
        val l = TollLedger().update(1, 10_000, 100_000, span)
            .update(1, null, 50_000, 30_000 to 30_000) // 진입 IC는 목록에서 사라지고 남은 요금소만
            .update(2, 0, 45_000).arrive()
        assertEquals(5_000L, l.totalWon)
    }

    @Test
    fun rerouteAfterLastTollGate_paysAll() {
        val l = TollLedger().update(1, 10_000, 100_000, span).update(1, null, 15_000, null) // 요금소를 다 지남
            .update(2, 0, 16_000).arrive()
        assertEquals(10_000L, l.totalWon)
    }

    @Test
    fun singleMainlineGate_paysWhenPassed() {
        // 개방식: 본선 요금소 하나(30km 지점)
        val before = TollLedger().update(1, 1_800, 60_000, 30_000 to 30_000).update(1, null, 31_000, 1_000 to 1_000)
        assertEquals(0L, before.totalWon)
        assertEquals(1_800L, before.update(1, null, 29_000, null).totalWon)
    }

    @Test
    fun evDiscount_andHundredWonUnits() {
        assertEquals(30, evTollDiscountPercent(2026))
        assertEquals(0, evTollDiscountPercent(2028))
        assertEquals(7_000L, tollWon(10_000, 30))
        assertEquals(4_700L, tollWon(4_650))
        assertEquals(3_300L, tollWon(4_700, 30)) // 3,290원 → 100원 단위
    }

    @Test
    fun arriveTwice_doesNotCountAgain() {
        val l = TollLedger().update(1, 3_000, 10_000).arrive().arrive()
        assertEquals(3_000L, l.totalWon)
    }
}
