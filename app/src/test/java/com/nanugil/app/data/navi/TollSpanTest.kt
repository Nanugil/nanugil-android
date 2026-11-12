package com.nanugil.app.data.navi

import ai.pleos.playground.navi.constants.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TollSpanTest {

    private fun t(type: TurnType, m: Double) = NaviTurn(type, m, "")

    @Test
    fun spanRunsFromExpresswayEntryToLastTollGate() {
        // 실제 에뮬레이터 경로 일부: 성남IC 진입 → 성남·남양주 톨게이트 → 도착
        val turns = listOf(
            t(TurnType.TURN_LEFT, 80.0), t(TurnType.TURN_ENTER_IC_C, 520.0), t(TurnType.TURN_TOLL, 1_000.0),
            t(TurnType.TURN_EXIT_IC_R, 5_500.0), t(TurnType.TURN_TOLL, 7_500.0), t(TurnType.TURN_ARRIVE, 3_000.0),
        )
        assertEquals(600 to 14_600, tollSpanAhead(turns))
    }

    @Test
    fun noTollGate_noSpan() {
        assertNull(tollSpanAhead(listOf(t(TurnType.TURN_ENTER_IC_R, 100.0), t(TurnType.TURN_ARRIVE, 900.0))))
    }
}
