package com.nanugil.app.data.navi

import android.content.Context
import android.util.Log
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.distanceTo
import com.nanugil.app.data.location.GpsFix
import com.nanugil.app.data.route.DirectionsException
import com.nanugil.app.data.route.NaverDirectionsClient
import com.nanugil.app.data.route.PlannedRoute
import com.nanugil.app.data.route.RouteGuide
import com.nanugil.app.data.route.TurnKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * 나누길 자체 길안내 (내장 내비를 거치지 않음).
 *
 * - 경로: 네이버 Directions 5 (경로선 + 회전 안내 목록 + 거리·시간·통행료)
 * - 위치: GPS ([onGps]를 1초마다 부른다)
 * - 매 위치마다 경로 위 진행 지점을 찾아 남은 거리·시간, 다음 회전과 거리, 경유지까지 거리를 계산한다
 * - 경로를 벗어나면(60m 이상, 3번 연속) 지금 위치에서 다시 탐색한다
 * - 회전 300m·50m 전, 출발·경유지·도착 때 음성으로 알린다
 * - 진행 중인 안내는 파일에 저장해 앱을 껐다 켜도 이어진다
 *
 * 화면은 기존과 같은 [NaviState]를 받는다 (connected는 항상 true).
 */
class OwnNavigator(
    context: Context,
    private val directions: NaverDirectionsClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NaviState(connected = true))
    val state: StateFlow<NaviState> = _state.asStateFlow()

    /** 지도에 그릴 경로선 */
    private val _path = MutableStateFlow<List<Wgs84Point>>(emptyList())
    val path: StateFlow<List<Wgs84Point>> = _path.asStateFlow()

    private val voice = VoiceGuide(context)
    private val sessionFile = File(context.filesDir, SESSION_FILE)

    private var destination: NaviPlace? = null
    private val waypoints = mutableListOf<NaviPlace>()
    private var route: PlannedRoute? = null
    private var cumulative = DoubleArray(0)
    private var progressIndex = 0
    private var lastFix: GpsFix? = null
    private var offRouteCount = 0
    private var lastRerouteAt = 0L
    private var fetchJob: Job? = null
    private var message: String? = null
    /** 이미 말한 안내 ("안내번호-거리단계") — 같은 안내를 두 번 말하지 않게 */
    private val announced = mutableSetOf<String>()

    init {
        restore()
    }

    // ---- 화면에서 부르는 것 ----

    /** 목적지로 길안내 시작 (지금 GPS 위치에서 경로 탐색) */
    fun start(dest: NaviPlace) {
        destination = dest
        waypoints.clear()
        clearRoute()
        message = "경로를 찾는 중…"
        publish()
        fetchRoute(announceStart = true)
    }

    /** 합류 지점을 첫 번째 경유지로 넣고 다시 탐색. 안내 중이 아니면 그곳으로 바로 안내 */
    fun addWaypoint(place: NaviPlace) {
        if (destination == null) {
            start(place)
            return
        }
        if (waypoints.none { it.name == place.name }) waypoints.add(0, place)
        message = "${place.name} 경유 경로를 찾는 중…"
        publish()
        fetchRoute(announce = "${place.name}을 경유지로 추가했습니다")
    }

    fun stop() {
        if (destination == null) return
        voice.speak("경로 안내를 종료합니다")
        endSession(message = null)
    }

    /** GPS 위치가 올 때마다 부른다 */
    fun onGps(fix: GpsFix) {
        lastFix = fix
        if (destination == null) {
            _state.value = _state.value.copy(currentPosition = fix.position, heading = fix.headingDegrees)
            return
        }
        if (route == null) {
            if (fetchJob?.isActive != true) fetchRoute(announceStart = false)
            _state.value = _state.value.copy(currentPosition = fix.position, heading = fix.headingDegrees)
            return
        }
        update(fix)
    }

    fun release() {
        voice.shutdown()
    }

    // ---- 경로 탐색 ----

    private fun fetchRoute(announceStart: Boolean = false, announce: String? = null) {
        val dest = destination ?: return
        val start = lastFix?.position
        if (start == null) {
            message = "GPS 위치를 기다리는 중…"
            publish()
            return
        }
        fetchJob?.cancel()
        fetchJob = scope.launch {
            try {
                val planned = directions.drivingRoute(start, dest.position, waypoints.map { it.position })
                setRoute(planned)
                message = null
                save()
                when {
                    announceStart -> voice.speak("${dest.name}까지 경로 안내를 시작합니다")
                    announce != null -> voice.speak(announce)
                }
                lastFix?.let(::update) ?: publish()
            } catch (e: DirectionsException) {
                Log.w(TAG, "경로 탐색 실패", e)
                message = "경로 탐색 실패: ${e.message}"
                publish()
            }
        }
    }

    private fun setRoute(planned: PlannedRoute) {
        route = planned
        val p = planned.path
        cumulative = DoubleArray(p.size).also { acc ->
            for (i in 1 until p.size) acc[i] = acc[i - 1] + p[i - 1].distanceTo(p[i])
        }
        progressIndex = 0
        offRouteCount = 0
        announced.clear()
        _path.value = p
    }

    private fun clearRoute() {
        fetchJob?.cancel()
        route = null
        cumulative = DoubleArray(0)
        progressIndex = 0
        announced.clear()
        _path.value = emptyList()
    }

    // ---- 매 위치 계산 ----

    private fun update(fix: GpsFix) {
        val r = route ?: return
        val path = r.path
        if (path.size < 2 || cumulative.size != path.size) return

        // 1) 경로 위 진행 지점: 직전 지점 근처부터 찾는다 (경로가 겹치는 곳에서 뒤로 튀지 않게)
        var (idx, dist) = nearest(path, fix.position, (progressIndex - 10).coerceAtLeast(0), (progressIndex + 400).coerceAtMost(path.lastIndex))
        if (dist > OFF_ROUTE_M) {
            val (gIdx, gDist) = nearest(path, fix.position, 0, path.lastIndex)
            if (gDist < dist) {
                idx = gIdx
                dist = gDist
            }
        }

        // 2) 경로 이탈 → 재탐색
        if (dist > OFF_ROUTE_M) {
            offRouteCount++
            val now = System.currentTimeMillis()
            if (offRouteCount >= OFF_ROUTE_COUNT && now - lastRerouteAt > REROUTE_COOLDOWN_MS && fetchJob?.isActive != true) {
                lastRerouteAt = now
                offRouteCount = 0
                voice.speak("경로를 벗어나 다시 탐색합니다")
                message = "경로 재탐색 중…"
                fetchRoute()
            }
        } else {
            offRouteCount = 0
            progressIndex = idx
        }

        val here = progressIndex
        val remain = (cumulative.last() - cumulative[here]).coerceAtLeast(0.0)

        // 3) 도착
        if (remain < ARRIVE_M && dist < OFF_ROUTE_M) {
            voice.speak("목적지에 도착했습니다. 안내를 종료합니다")
            endSession(message = "목적지 도착")
            return
        }

        // 4) 경유지: 지나쳤으면 목록에서 뺀다
        var wpIndexes = r.waypointPointIndexes
        while (waypoints.isNotEmpty() && wpIndexes.isNotEmpty() &&
            cumulative[wpIndexes.first().coerceIn(0, path.lastIndex)] - cumulative[here] < ARRIVE_WAYPOINT_M
        ) {
            val passed = waypoints.removeAt(0)
            voice.speak("경유지 ${passed.name}에 도착했습니다")
            wpIndexes = wpIndexes.drop(1)
            route = r.copy(waypointPointIndexes = wpIndexes)
            save()
        }
        val wpRemain = wpIndexes.map { (cumulative[it.coerceIn(0, path.lastIndex)] - cumulative[here]).roundToInt().coerceAtLeast(0) }

        // 5) 다음 회전 안내
        val guides = (route ?: r).guides
        val nextIndex = guides.indexOfFirst { it.pointIndex > here }
        val next = guides.getOrNull(nextIndex)
        val nextDist = next?.let { (cumulative[it.pointIndex.coerceIn(0, path.lastIndex)] - cumulative[here]).roundToInt() }
        if (next != null && nextDist != null) announce(nextIndex, next, nextDist)

        val totalDist = cumulative.last().takeIf { it > 0 } ?: 1.0
        val remainSec = (r.durationMillis / 1000.0 * remain / totalDist).roundToInt()

        _state.value = NaviState(
            connected = true,
            guiding = true,
            destinationName = destination?.name,
            destinationPosition = destination?.position,
            remainDistanceMeters = remain.roundToInt(),
            remainTimeSeconds = remainSec,
            tollFareWon = r.tollFareWon,
            waypointNames = waypoints.map { it.name },
            waypointPositions = waypoints.map { it.position },
            waypointRemainMeters = wpRemain,
            currentPosition = fix.position,
            heading = fix.headingDegrees,
            nextGuide = next?.let { GuideBanner(it.kind, it.instruction.cleanInstruction(), nextDist ?: 0) },
            message = message,
        )
    }

    /** 회전 300m 전, 50m 전에 한 번씩 말한다 */
    private fun announce(index: Int, guide: RouteGuide, distance: Int) {
        if (guide.kind == TurnKind.DESTINATION) return // 도착은 따로 말한다
        val text = guide.instruction.cleanInstruction()
        when {
            distance <= NEAR_M && announced.add("$index-near") -> voice.speak(text)
            distance in (NEAR_M + 1)..FAR_M && announced.add("$index-far") -> voice.speak("${distance.roundTo50()}미터 앞, $text")
        }
    }

    private fun endSession(message: String?) {
        destination = null
        waypoints.clear()
        clearRoute()
        sessionFile.delete()
        _state.value = NaviState(
            connected = true,
            guiding = false,
            currentPosition = lastFix?.position,
            heading = lastFix?.headingDegrees ?: 0.0,
            message = message,
        )
    }

    /** 경로 계산 전 상태 (목적지는 정했지만 경로가 아직 없음) */
    private fun publish() {
        val dest = destination
        _state.value = _state.value.copy(
            connected = true,
            guiding = dest != null,
            destinationName = dest?.name,
            destinationPosition = dest?.position,
            waypointNames = waypoints.map { it.name },
            waypointPositions = waypoints.map { it.position },
            currentPosition = lastFix?.position ?: _state.value.currentPosition,
            message = message,
        )
    }

    // ---- 앱을 껐다 켜도 이어지게 저장 ----

    private fun save() {
        val dest = destination ?: return
        val r = route ?: return
        try {
            val json = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put("destination", dest.toJson())
                .put("waypoints", JSONArray(waypoints.map { it.toJson() }))
                .put("path", r.path.joinToString(";") { "${it.latitude},${it.longitude}" })
                .put("guides", JSONArray(r.guides.map { JSONObject().put("p", it.pointIndex).put("t", it.type).put("i", it.instruction) }))
                .put("distance", r.distanceMeters)
                .put("duration", r.durationMillis)
                .put("toll", r.tollFareWon)
                .put("wp", JSONArray(r.waypointPointIndexes))
            sessionFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "안내 저장 실패", e)
        }
    }

    private fun restore() {
        if (!sessionFile.exists()) return
        try {
            val json = JSONObject(sessionFile.readText())
            if (System.currentTimeMillis() - json.optLong("savedAt") > SESSION_TTL_MS) {
                sessionFile.delete()
                return
            }
            destination = json.getJSONObject("destination").toPlace()
            val wps = json.optJSONArray("waypoints") ?: JSONArray()
            for (i in 0 until wps.length()) waypoints += wps.getJSONObject(i).toPlace()
            val path = json.getString("path").split(';').mapNotNull { pair ->
                val parts = pair.split(',')
                if (parts.size != 2) null else Wgs84Point(parts[0].toDouble(), parts[1].toDouble())
            }
            val guidesJson = json.getJSONArray("guides")
            val guides = (0 until guidesJson.length()).map { i ->
                val g = guidesJson.getJSONObject(i)
                RouteGuide(g.getInt("p"), g.getInt("t"), g.getString("i"))
            }
            val wpJson = json.optJSONArray("wp") ?: JSONArray()
            setRoute(
                PlannedRoute(
                    path = path,
                    guides = guides,
                    distanceMeters = json.optInt("distance"),
                    durationMillis = json.optLong("duration"),
                    tollFareWon = json.optInt("toll"),
                    waypointPointIndexes = (0 until wpJson.length()).map { wpJson.getInt(it) },
                ),
            )
            publish()
            Log.i(TAG, "진행 중이던 안내를 이어서 합니다: ${destination?.name}")
        } catch (e: Exception) {
            Log.w(TAG, "저장된 안내를 읽지 못해 지웁니다", e)
            sessionFile.delete()
        }
    }

    private fun NaviPlace.toJson() = JSONObject().put("name", name).put("lat", position.latitude).put("lng", position.longitude)

    private fun JSONObject.toPlace() = NaviPlace(getString("name"), Wgs84Point(getDouble("lat"), getDouble("lng")))

    private companion object {
        const val TAG = "OwnNavigator"
        const val SESSION_FILE = "navi_session.json"
        /** 하루 넘게 지난 안내는 이어 하지 않는다 */
        const val SESSION_TTL_MS = 12 * 60 * 60 * 1000L

        const val OFF_ROUTE_M = 60.0
        const val OFF_ROUTE_COUNT = 3
        const val REROUTE_COOLDOWN_MS = 10_000L
        const val ARRIVE_M = 40.0
        const val ARRIVE_WAYPOINT_M = 40.0
        const val FAR_M = 300
        const val NEAR_M = 60

        fun nearest(path: List<Wgs84Point>, pos: Wgs84Point, from: Int, to: Int): Pair<Int, Double> {
            var best = from
            var bestDist = Double.MAX_VALUE
            for (i in from..to) {
                val d = pos.distanceTo(path[i])
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            return best to bestDist
        }

        /** 안내 문구의 따옴표를 지워 읽기 좋게: "'테헤란로' 방면으로 우회전" → "테헤란로 방면으로 우회전" */
        fun String.cleanInstruction() = replace("'", "").trim()

        fun Int.roundTo50() = ((this + 25) / 50) * 50
    }
}
