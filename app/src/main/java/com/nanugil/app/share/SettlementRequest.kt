package com.nanugil.app.share

import org.json.JSONArray
import org.json.JSONObject

/**
 * 송금 한 건과 운전자가 받았다고 체크했는지.
 * 카카오페이는 송금 결과를 알려 주지 않으므로 [done]은 사람이 직접 체크한다.
 */
data class RequestedTransfer(
    val fromId: String,
    val toId: String,
    val amountWon: Long,
    val done: Boolean = false,
)

/**
 * '정산 요청'을 누른 순간의 정산 결과. 이후 값을 고쳐도 바뀌지 않도록 송금 목록을 따로 들고 있다.
 *
 * @property myId 이 기기 사용자. [transfers]는 이 사람이 받거나 보내는 송금만 든다.
 *   받을 돈이 있으면 송금 링크 QR과 받은 사람 체크를, 보낼 돈이 있으면 송금 안내를 보여 준다(운전자는 둘 다일 수 있다).
 * @property perPersonWon 요청할 때의 1인 부담액. 요청 뒤에 값을 고쳐도 09 화면은 이 금액을 보여 준다.
 */
data class SettlementRequest(
    val myId: String,
    val transfers: List<RequestedTransfer>,
    val perPersonWon: Long = 0,
) {
    val isComplete: Boolean get() = transfers.all { it.done }

    fun toggled(index: Int): SettlementRequest =
        copy(transfers = transfers.mapIndexed { i, t -> if (i == index) t.copy(done = !t.done) else t })

    fun toJson(): String = JSONObject()
        .put("myId", myId)
        .put("perPerson", perPersonWon)
        .put("transfers", JSONArray(transfers.map {
            JSONObject().put("from", it.fromId).put("to", it.toId).put("amount", it.amountWon).put("done", it.done)
        }))
        .toString()

    companion object {
        fun of(settlement: Settlement, myId: String) = SettlementRequest(
            myId,
            settlement.transfers.filter { it.fromId == myId || it.toId == myId }.map { RequestedTransfer(it.fromId, it.toId, it.amountWon) },
            settlement.perPersonShareWon,
        )

        fun fromJson(json: String): SettlementRequest {
            val o = JSONObject(json)
            val list = o.getJSONArray("transfers")
            return SettlementRequest(
                myId = o.getString("myId"),
                transfers = (0 until list.length()).map { i ->
                    val t = list.getJSONObject(i)
                    RequestedTransfer(t.getString("from"), t.getString("to"), t.getLong("amount"), t.getBoolean("done"))
                },
                // 1인 금액을 저장하기 전에 보낸 요청에는 없다
                perPersonWon = o.optLong("perPerson", 0),
            )
        }
    }
}
