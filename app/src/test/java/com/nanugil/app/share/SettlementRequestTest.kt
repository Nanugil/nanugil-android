package com.nanugil.app.share

import com.nanugil.app.data.pay.normalizeKakaoPayLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementRequestTest {

    private val settlement = SettlementCalculator.settle(
        listOf(
            VehicleTrip("A", "지수", listOf("민호"), tollWon = 10_000),
            VehicleTrip("B", "현우", listOf("다은"), tollWon = 2_000),
        ),
    )

    @Test
    fun request_keepsTransfersAndTracksChecks() {
        val request = SettlementRequest.of(settlement, myId = "지수")
        // 지수가 받거나 보내는 송금만 든다
        assertEquals(settlement.transfers.filter { it.fromId == "지수" || it.toId == "지수" }, request.transfers.map { Transfer(it.fromId, it.toId, it.amountWon) })
        assertTrue(request.transfers.isNotEmpty())
        assertFalse(request.isComplete)

        val allDone = request.transfers.indices.fold(request) { r, i -> r.toggled(i) }
        assertTrue(allDone.isComplete)
        // 다시 누르면 체크가 풀린다
        assertFalse(allDone.toggled(0).isComplete)
    }

    @Test
    fun request_survivesJsonRoundTrip() {
        val request = SettlementRequest.of(settlement, myId = "지수").toggled(1)
        assertEquals(request, SettlementRequest.fromJson(request.toJson()))
    }

    @Test
    fun kakaoPayLink_onlyHttpsKakaoPay() {
        assertEquals("https://qr.kakaopay.com/Ej8abc", normalizeKakaoPayLink("  https://qr.kakaopay.com/Ej8abc \n"))
        listOf(
            "http://qr.kakaopay.com/Ej8abc", // https만
            "https://qr.kakaopay.com.evil.com/x", // 도메인 끝이 kakaopay.com이어야
            "https://evilkakaopay.com/x",
            "qr.kakaopay.com/Ej8abc",
            "",
        ).forEach { bad ->
            assertThrows(bad, IllegalArgumentException::class.java) { normalizeKakaoPayLink(bad) }
        }
    }
}
