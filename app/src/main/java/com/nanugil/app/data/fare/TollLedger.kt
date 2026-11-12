package com.nanugil.app.data.fare

import android.content.Context
import com.nanugil.app.data.navi.NaviState
import kotlin.math.roundToLong

/**
 * 경로가 바뀌어도 실제로 지나온 만큼의 통행료를 모은다. 정산의 내 차 통행료(내비 예상)로 쓴다.
 *
 * 내비 경로 하나(안내 시작·재탐색·경유지 변경마다 새 경로, [NaviState.routeVersion])를 구간으로 본다.
 * 경로가 바뀌면 지난 구간은 낸 만큼만 확정하고, 새 경로의 예상 통행료로 다시 센다. 도착하면 마지막 구간은 전액.
 *
 * 낸 만큼: 통행료는 요금소를 지날 때 낸다(폐쇄식은 진출 요금소에서 진입~진출 거리만큼, 개방식은 본선 요금소).
 * 그래서 유료 구간(첫 고속도로 진입 ~ 마지막 요금소) 안에서 달린 비율로 본다. 들어가기 전에 경로를 바꾸면 0원,
 * 마지막 요금소를 지났으면 전액. 내비가 요금소를 알려 주지 않으면 경로 전체 거리 비율로 나눈다.
 * 예: 1만 원 경로의 유료 구간을 절반 달리고 무료 도로로 재탐색해 도착 → 5,000원.
 *
 * shortcut: 유료 구간 안에서는 거리 비율로 나눈다(폐쇄식은 거리 비례라 맞고, 요금소별 요금은 내비가 주지 않는다). 실제 하이패스 금액과 다를 수 있어 정산에서 고칠 수 있다.
 *
 * 거리는 모두 목적지까지 남은 거리(m) 기준이다.
 * @property routeVersion 지금 구간의 경로 번호. null이면 다음에 받은 번호를 이 구간 것으로 이어 받는다(앱을 다시 켰을 때).
 * @property fareWon 지금 구간 경로의 예상 통행료
 * @property plannedM 지금 구간 경로의 전체 거리(본 남은 거리 중 가장 큰 값)
 * @property tollFromM 유료 구간 시작(첫 고속도로 진입·요금소). 모르면 null.
 * @property tollToM 유료 구간 끝(마지막 요금소). 모르면 null.
 */
data class TollLedger(
    val committedWon: Long = 0,
    val routeVersion: Int? = null,
    val fareWon: Int = 0,
    val plannedM: Int = 0,
    val remainM: Int = 0,
    val tollFromM: Int? = null,
    val tollToM: Int? = null,
) {
    /** 지금까지 낸 것으로 보는 통행료(할인 전) */
    val totalWon: Long get() = committedWon + (fareWon * paidRatio()).roundToLong()

    /**
     * 안내 중 내비 상태 하나를 반영한다. 값이 없으면(null) 전 값을 그대로 둔다.
     * @param spanAhead 지금 위치에서 유료 구간 시작·끝까지 거리 ([NaviState.tollSpanAheadM])
     */
    fun update(version: Int, fare: Int?, remain: Int?, spanAhead: Pair<Int, Int>? = null): TollLedger {
        val segment = when (routeVersion) {
            null, version -> this
            else -> TollLedger(committedWon = totalWon) // 새 경로: 지난 구간은 낸 만큼만 확정
        }
        val r = remain ?: segment.remainM
        val from = spanAhead?.let { r - it.first }
        val to = spanAhead?.let { r - it.second }
        return segment.copy(
            routeVersion = version,
            fareWon = fare ?: segment.fareWon,
            plannedM = maxOf(segment.plannedM, r),
            remainM = r,
            // 유료 구간에 들어선 뒤에는 진입 지점이 회전 목록에서 사라지니 처음 본 값을 지킨다
            tollFromM = segment.tollFromM?.takeIf { it >= r } ?: from ?: segment.tollFromM,
            tollToM = to ?: segment.tollToM,
        )
    }

    /** 목적지 도착: 마지막 구간은 끝까지 달렸으니 전액. */
    fun arrive() = TollLedger(committedWon = committedWon + fareWon, routeVersion = routeVersion)

    private fun paidRatio(): Double {
        val from = tollFromM
        val to = tollToM
        if (from == null || to == null) {
            return if (plannedM > 0) ((plannedM - remainM).toDouble() / plannedM).coerceIn(0.0, 1.0) else 0.0
        }
        return when {
            remainM <= to -> 1.0 // 마지막 요금소를 지남
            remainM >= from || from <= to -> 0.0 // 아직 유료 구간 전
            else -> (from - remainM).toDouble() / (from - to)
        }
    }
}

/**
 * 하이패스 친환경차(전기·수소) 통행료 감면율. 2027년 12월 31일까지 해마다 줄어든다(정부24 "친환경차 고속도로 통행료 감면").
 * 하이브리드는 대상이 아니다.
 * shortcut: 감면 단말기를 등록한 차로 보고, 내비 예상액은 할인 전 금액으로 본다. 정책이 바뀌면 이 표를 고친다.
 */
fun evTollDiscountPercent(year: Int): Int = when (year) {
    2025 -> 40
    2026 -> 30
    2027 -> 20
    else -> 0
}

/** 통행료는 100원 단위로 받는다. 할인을 빼고 100원 단위로 맞춘다. */
fun tollWon(won: Long, discountPercent: Int = 0): Long =
    (won * (100 - discountPercent) / 100.0 / 100).roundToLong() * 100

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
        tollFromM = prefs.getInt("toll_from", -1).takeIf { it >= 0 },
        tollToM = prefs.getInt("toll_to", -1).takeIf { it >= 0 },
    )
        private set

    fun update(navi: NaviState) {
        if (navi.guiding) save(ledger.update(navi.routeVersion, navi.tollFareWon, navi.remainDistanceMeters, navi.tollSpanAheadM))
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
            .putInt("toll_from", l.tollFromM ?: -1)
            .putInt("toll_to", l.tollToM ?: -1)
            .apply()
    }
}
