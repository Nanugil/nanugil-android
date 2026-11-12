package com.nanugil.app.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

/** 단위는 L로 읽으면 된다(기준 2). */
class RefillDetectorTest {

    private fun run(vararg levels: Long) = levels.fold(RefillDetector()) { d, l -> d.update(l, threshold = 2) }

    @Test
    fun noRefill_recordsStartAndLatest() {
        val d = run(40, 38, 35, 30)
        assertEquals(40L, d.start)
        assertEquals(30L, d.latest)
        assertEquals(0L, d.added)
    }

    @Test
    fun gradualRefill_isCountedInFull() {
        // 주유기는 1씩 올라간다. 기준 2를 넘은 뒤 끝까지 센다: 20 → 50 = 30
        val d = run(*longArrayOf(25, 20) + (21L..50L).toList().toLongArray())
        assertEquals(30L, d.added)
    }

    @Test
    fun twoRefills_areAdded() {
        // 20 → 50 (+30), 쓰다가 45 → 60 (+15)
        val d = run(30, 20, 35, 50, 49, 45, 60, 58)
        assertEquals(45L, d.added)
        assertEquals(30L, d.start)
        assertEquals(58L, d.latest)
    }

    @Test
    fun smallRiseBelowThreshold_isIgnored() {
        // 센서 흔들림·회생 제동
        val d = run(40, 39, 40, 38, 39, 37)
        assertEquals(0L, d.added)
    }
}
