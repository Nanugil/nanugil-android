package com.nanugil.app.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class KatecConverterTest {

    // 기대값은 같은 KATEC 정의를 Krüger 급수(다른 알고리즘)로 계산해 교차 검증한 값이다.
    @Test
    fun `서울시청 위경도를 KATEC으로 바꾼다`() {
        val katec = KatecConverter.toKatec(Wgs84Point(37.5665, 126.9780))
        assertEquals(309_910.1, katec.x, 1.0)
        assertEquals(552_073.3, katec.y, 1.0)
    }

    @Test
    fun `제주처럼 중앙자오선에서 먼 곳도 맞는다`() {
        val katec = KatecConverter.toKatec(Wgs84Point(33.4996, 126.5312))
        assertEquals(263_722.2, katec.x, 1.0)
        assertEquals(101_365.1, katec.y, 1.0)
    }

    @Test
    fun `왕복 변환 오차는 1cm 이내다`() {
        val points = listOf(
            Wgs84Point(37.5665, 126.9780),
            Wgs84Point(37.8315, 127.5105),
            Wgs84Point(35.1796, 129.0756),
        )
        points.forEach { p ->
            val back = KatecConverter.toWgs84(KatecConverter.toKatec(p))
            assertEquals(p.latitude, back.latitude, 1e-7)
            assertEquals(p.longitude, back.longitude, 1e-7)
        }
    }
}
