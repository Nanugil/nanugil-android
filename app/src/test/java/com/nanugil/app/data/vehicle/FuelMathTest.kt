package com.nanugil.app.data.vehicle

import com.nanugil.app.ui.settlement.decimalToMilli
import com.nanugil.app.ui.settlement.milliToDecimalText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FuelMathTest {

    @Test
    fun percentTimesCapacity_givesMl() {
        assertEquals(30_000L, fuelMlFromPercent(50, 60_000))
        assertEquals(0L, fuelMlFromPercent(0, 60_000))
        assertEquals(60_000L, fuelMlFromPercent(100, 60_000))
        // 52 L 탱크의 33% = 17.16 L
        assertEquals(17_160L, fuelMlFromPercent(33, 52_000))
        // 소수 mL는 반올림: 1,001 mL의 50% = 500.5 → 501
        assertEquals(501L, fuelMlFromPercent(50, 1_001))
    }

    @Test
    fun outOfRange_rejected() {
        assertThrows(IllegalArgumentException::class.java) { fuelMlFromPercent(-1, 60_000) }
        assertThrows(IllegalArgumentException::class.java) { fuelMlFromPercent(101, 60_000) }
        assertThrows(IllegalArgumentException::class.java) { fuelMlFromPercent(50, 0) }
    }

    @Test
    fun milliToDecimalText_roundTripsThroughInput() {
        assertEquals("30", milliToDecimalText(30_000))
        assertEquals("17.16", milliToDecimalText(17_160))
        assertEquals("0.501", milliToDecimalText(501))
        assertEquals("0", milliToDecimalText(0))
        listOf(0L, 501L, 17_160L, 30_000L, 65_432L).forEach {
            assertEquals(it, decimalToMilli(milliToDecimalText(it), "연료", required = true))
        }
    }
}
