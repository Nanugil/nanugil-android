package com.nanugil.app.data.server

import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerGroupCarsTest {

    private val start = Wgs84Point(37.5665, 126.9780)

    @Test
    fun `운전자만 멤버 ID 순으로 A B C 글자를 받는다`() {
        val members = listOf(
            TripMember("t", memberId = "m3", vehicleDriverMemberId = "m3"),
            TripMember("t", memberId = "m1", vehicleDriverMemberId = "m1"),
            TripMember("t", memberId = "p9", vehicleDriverMemberId = "m1"), // 동승자
        )
        assertEquals(mapOf("m1" to "A", "m3" to "B"), carLabels(members))
    }

    @Test
    fun `처음엔 정차, 움직이면 속도를 내고 주행이 된다`() {
        val m = MyCarMotion()
        assertEquals(0 to DriveStatus.STOPPED, m.update(start, 0))
        // 1초에 25m → 90 km/h
        val (speed, status) = m.update(start.offset(0.0, 25.0), 1_000)
        assertEquals(90.0, speed.toDouble(), 1.0)
        assertEquals(DriveStatus.DRIVING, status)
    }

    @Test
    fun `서고 30초가 지나야 정차가 된다`() {
        val m = MyCarMotion()
        val moved = start.offset(0.0, 25.0)
        m.update(start, 0)
        m.update(moved, 1_000)
        // 위치가 3초 넘게 그대로면 속도 0, 그래도 30초 전까지는 주행 유지
        assertEquals(0 to DriveStatus.DRIVING, m.update(moved, 5_000))
        assertEquals(0 to DriveStatus.DRIVING, m.update(moved, 34_000))
        assertEquals(0 to DriveStatus.STOPPED, m.update(moved, 35_000))
    }

    @Test
    fun `3에서 5 km_h 사이는 이전 상태를 유지한다`() {
        val m = MyCarMotion()
        m.update(start, 0)
        // 1초에 1.1m → 약 4 km/h: 정차 그대로
        assertEquals(DriveStatus.STOPPED, m.update(start.offset(0.0, 1.1), 1_000).second)
    }
}
