package com.nanugil.app.data.pay

import android.content.Context
import java.net.URI

/**
 * 운전자가 붙여 넣은 카카오페이 송금 링크를 확인해 공백을 걷어 돌려준다.
 * 카카오페이는 개인 간 송금 API가 없어, 운전자 본인의 송금 링크(카카오페이 앱 → 송금 → QR/송금코드)를 QR로 보여 준다.
 * QR을 찍으면 그 주소가 바로 열리므로 https이고 kakaopay.com 주소인 것만 받는다.
 *
 * @throws IllegalArgumentException 카카오페이 링크가 아닐 때. 메시지는 화면에 그대로 보여 줄 문장이다.
 */
fun normalizeKakaoPayLink(text: String): String {
    val trimmed = text.trim()
    val host = runCatching { URI(trimmed) }.getOrNull()?.takeIf { it.scheme == "https" }?.host
    require(host != null && (host == "kakaopay.com" || host.endsWith(".kakaopay.com"))) {
        "카카오페이 송금 링크(https://qr.kakaopay.com/…)를 붙여 넣어 주세요."
    }
    return trimmed
}

/** 이 차 운전자의 카카오페이 송금 링크. 앱을 처음 켤 때 한 번 등록한다. */
class PayLinkStore(context: Context) {
    private val prefs = context.getSharedPreferences("pay_link", Context.MODE_PRIVATE)

    val link: String? get() = prefs.getString(KEY_LINK, null)

    /** 처음 실행 때 등록을 물어봤는지. 건너뛰었으면 앱을 켤 때마다 묻지 않고, 정산 요청할 때 다시 묻는다. */
    val asked: Boolean get() = prefs.getBoolean(KEY_ASKED, false)

    /** @throws IllegalArgumentException 카카오페이 링크가 아닐 때. */
    fun save(text: String) {
        prefs.edit().putString(KEY_LINK, normalizeKakaoPayLink(text)).putBoolean(KEY_ASKED, true).apply()
    }

    fun skip() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    private companion object {
        const val KEY_LINK = "kakaopay_link"
        const val KEY_ASKED = "asked"
    }
}
