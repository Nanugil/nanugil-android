package com.nanugil.app.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class AlongRouteTest {

    // 북쪽으로 1km 간 뒤 동쪽으로 1km 가는 ㄱ자 경로 (서울시청 근처)
    private val start = Wgs84Point(37.5665, 126.9780)
    private val corner = start.offset(0.0, 1_000.0)
    private val end = corner.offset(90.0, 1_000.0)
    private val path = listOf(start, corner, end)
    private val cumulative = cumulativeDistances(path)

    @Test
    fun `누적 거리는 구간 길이를 더한 값이다`() {
        assertEquals(0.0, cumulative[0], 0.0)
        assertEquals(1_000.0, cumulative[1], 5.0)
        assertEquals(2_000.0, cumulative[2], 10.0)
    }

    @Test
    fun `경로를 따라 간 지점과 방향을 구한다`() {
        val (p, bearing) = pointAlong(path, cumulative, 500.0)
        assertEquals(500.0, start.distanceTo(p), 5.0)
        assertEquals(0.0, bearing, 1.0)

        val (q, bearing2) = pointAlong(path, cumulative, 1_500.0)
        assertEquals(500.0, corner.distanceTo(q), 5.0)
        assertEquals(90.0, bearing2, 1.0)
    }

    @Test
    fun `경로 밖 거리는 양 끝에 붙인다`() {
        assertEquals(0.0, start.distanceTo(pointAlong(path, cumulative, -300.0).first), 0.5)
        assertEquals(0.0, end.distanceTo(pointAlong(path, cumulative, 9_999.0).first), 0.5)
    }

    @Test
    fun `경로 옆 위치는 가장 가까운 구간에 투영한다`() {
        // 첫 구간 700m 지점에서 서쪽으로 40m 벗어난 위치
        val beside = pointAlong(path, cumulative, 700.0).first.offset(270.0, 40.0)
        assertEquals(700.0, distanceAlong(path, cumulative, beside), 5.0)
    }

    @Test
    fun `투영한 거리로 다시 찾으면 같은 지점이다`() {
        val p = pointAlong(path, cumulative, 1_234.0).first
        assertEquals(1_234.0, distanceAlong(path, cumulative, p), 1.0)
    }
}
