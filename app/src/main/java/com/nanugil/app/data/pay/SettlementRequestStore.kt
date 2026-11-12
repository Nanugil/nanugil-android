package com.nanugil.app.data.pay

import android.content.Context
import android.util.Log
import com.nanugil.app.share.SettlementRequest
import org.json.JSONException

/**
 * 보낸 정산 요청을 기기에 저장한다. 정산 요청을 누르면 운행 기록을 지우므로,
 * 앱이 다시 켜져도 송금 화면과 받음 체크가 남아 있어야 한다.
 */
class SettlementRequestStore(context: Context) {
    private val prefs = context.getSharedPreferences("settlement_request", Context.MODE_PRIVATE)

    fun load(): SettlementRequest? {
        val json = prefs.getString(KEY, null) ?: return null
        return try {
            SettlementRequest.fromJson(json)
        } catch (e: JSONException) {
            Log.w("SettlementRequestStore", "저장된 정산 요청을 읽지 못했습니다", e)
            null
        }
    }

    fun save(request: SettlementRequest) {
        prefs.edit().putString(KEY, request.toJson()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "current"
    }
}
