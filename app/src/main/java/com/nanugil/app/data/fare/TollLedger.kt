package com.nanugil.app.data.fare

import android.content.Context
import com.nanugil.app.data.navi.NaviState
import kotlin.math.roundToLong

/**
 * 경로가 바뀌어도 실제로 지나온 만큼의 통행료를 모은다. 정산의 내 차 통행료(내비 예상)로 쓴다.
 *
 * 내비 경로 하나(안내 시작·재탐색·경유지 변경마다 새 경로, [NaviState.routeVersion])를 구간으로 본다.
 * 경로가 바뀌면 지난 구간은 달린 비율만큼만 확정하고, 새 경로의 예상 통행료로 다시 센다. 도착하면 마지막 구간은 전액.
 * 예: 1만 원 경로를 절반 달리고 무료 도로로 재탐색해 도착 → 5,000원.
 *
 * shortcut: 구간 안에서는 남은 거리 비율로 나눈다(내비가 요금소별 요금을 주지 않는다). 실제 하이패스 금액과 다를 수 있어 정산에서 고칠 수 있다.
 *
 * @property routeVersion 지금 구간의 경로 번호. null이면 다음에 받은 번호를 이 구간 것으로 이어 받는다(앱을 다시 켰을 때).
 * @property fareWon 지금 구간 경로의 예상 통행료
 * @property plannedM 지금 구간 경로의 전체 거리(본 남은 거리 중 가장 큰 값)
 */
data class TollLedger(
    val committedWon: Long = 0,
    val routeVersion: Int? = null,
    val fareWon: Int = 0,
    val plannedM: Int = 0,
    val remainM: Int = 0,
) {
    /** 지금까지 낸 것으로 보는 통행료 */
    val totalWon: Long get() = committedWon + soFarWon()

    /** 안내 중 내비 상태 하나를 반영한다. 값이 없으면(null) 전 값을 그대로 둔다. */
    fun update(version: Int, fare: Int?, remain: Int?): TollLedger {
        val segment = when (routeVersion) {
            null, version -> this
            else -> TollLedger(committedWon = totalWon) // 새 경로: 지난 구간은 달린 만큼만 확정
        }
        return segment.copy(
            routeVersion = version,
            fareWon = fare ?: segment.fareWon,
            plannedM = maxOf(segment.plannedM, remain ?: 0),
            remainM = remain ?: segment.remainM,
        )
    }

    /** 목적지 도착: 마지막 구간은 끝까지 달렸으니 전액. */
    fun arrive() = TollLedger(committedWon = committedWon + fareWon, routeVersion = routeVersion)

    private fun soFarWon(): Long {
        if (plannedM <= 0) return 0
        val ratio = ((plannedM - remainM).toDouble() / plannedM).coerceIn(0.0, 1.0)
        return (fareWon * ratio).roundToLong()
    }
}

/**
 * [TollLedger]를 안내 중 내비 상태로 채우고, 앱을 껐다 켜도 이어지도록 저장한다.
 * 정산 요청을 보내면 [reset]으로 새 운행을 시작한다([com.nanugil.app.data.vehicle.TripEnergyRecorder]와 같다).
 */
class TollRecorder(context: Context) {
    private val prefs = context.getSharedPreferences("trip_toll", Context.MODE_PRIVATE)

    // 경로 번호는 내비 연결마다 0부터 다시 센다. 저장된 구간은 다음 번호로 이어 받는다.
    @Volatile var ledger: TollLedger = TollLedger(
        committedWon = prefs.getLong("committed", 0),
        fareWon = prefs.getInt("fare", 0),
        plannedM = prefs.getInt("planned", 0),
        remainM = prefs.getInt("remain", 0),
    )
        private set

    fun update(navi: NaviState) {
        if (navi.guiding) save(ledger.update(navi.routeVersion, navi.tollFareWon, navi.remainDistanceMeters))
    }

    fun arrive() = save(ledger.arrive())

    /** 새 내비 연결(화면을 새로 열 때). 경로 번호가 0부터 다시 시작해도 같은 구간으로 이어지게 한다. */
    fun reconnect() {
        ledger = ledger.copy(routeVersion = null)
    }

    fun reset() {
        ledger = TollLedger()
        prefs.edit().clear().apply()
    }

    private fun save(l: TollLedger) {
        if (l == ledger) return
        ledger = l
        prefs.edit()
            .putLong("committed", l.committedWon)
            .putInt("fare", l.fareWon)
            .putInt("planned", l.plannedM)
            .putInt("remain", l.remainM)
            .apply()
    }
}
