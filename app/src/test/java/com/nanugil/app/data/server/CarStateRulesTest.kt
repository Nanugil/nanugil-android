package com.nanugil.app.data.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarStateRulesTest {

    private fun msg(carId: String = "a", status: DriveStatus = DriveStatus.DRIVING, sentAt: Long) =
        CarStateMessage(carId, 37.5, 127.0, 60, 10, 50, status, sentAt)

    @Test
    fun `주행 중에는 3초마다 보낸다`() {
        val last = msg(sentAt = 0)
        assertTrue(shouldSendCarState(null, last))
        assertFalse(shouldSendCarState(last, msg(sentAt = 2_999)))
        assertTrue(shouldSendCarState(last, msg(sentAt = 3_000)))
    }

    @Test
    fun `정차로 바뀌는 순간 보내고, 정차 중에는 30초마다 보낸다`() {
        val driving = msg(sentAt = 0)
        val stopped = msg(status = DriveStatus.STOPPED, sentAt = 1_000)
        assertTrue(shouldSendCarState(driving, stopped))
        assertFalse(shouldSendCarState(stopped, msg(status = DriveStatus.STOPPED, sentAt = 30_999)))
        assertTrue(shouldSendCarState(stopped, msg(status = DriveStatus.STOPPED, sentAt = 31_000)))
        assertTrue(shouldSendCarState(stopped, msg(sentAt = 2_000))) // 다시 출발하면 바로
    }

    @Test
    fun `정차 중에는 화면이 안 불러도 30초 지나면 다시 보낸다`() {
        val stopped = msg(status = DriveStatus.STOPPED, sentAt = 0)
        assertFalse(stoppedResendDue(null, lastSentAt = 0, now = 60_000)) // 보낸 적 없음
        assertFalse(stoppedResendDue(stopped, lastSentAt = 1_000, now = 30_999))
        assertTrue(stoppedResendDue(stopped, lastSentAt = 1_000, now = 31_000))
        assertTrue(stoppedResendDue(msg(status = DriveStatus.REST_AREA, sentAt = 0), lastSentAt = 0, now = 30_000))
        assertFalse(stoppedResendDue(msg(sentAt = 0), lastSentAt = 0, now = 60_000)) // 주행 중은 화면이 보냄
    }

    @Test
    fun `늦게 온 옛 메시지는 버린다`() {
        val board = CarBoard()
        assertTrue(board.put(msg(sentAt = 5_000), receivedAt = 0))
        assertFalse(board.put(msg(sentAt = 4_000), receivedAt = 1))
        assertEquals(5_000L, board.messages.single().sentAt)
    }

    @Test
    fun `1분 넘게 소식 없는 차는 빠진다`() {
        val board = CarBoard(staleAfterMs = 60_000)
        board.put(msg(carId = "a", sentAt = 0), receivedAt = 0)
        board.put(msg(carId = "b", sentAt = 0), receivedAt = 30_000)
        assertFalse(board.dropStale(now = 60_000))
        assertTrue(board.dropStale(now = 60_001))
        assertEquals(listOf("b"), board.messages.map { it.carId })
    }
}
