package com.nanugil.app.data.route

import com.nanugil.app.core.geo.Wgs84Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 회전 안내 한 건. [pointIndex]는 경로선(path)에서 이 안내가 일어나는 지점. */
data class RouteGuide(
    val pointIndex: Int,
    /** 네이버 안내 코드 (1 직진, 2 좌회전, 3 우회전, 6 유턴, 87 경유지, 88 목적지 등) */
    val type: Int,
    /** 예: "'테헤란로' 방면으로 우회전" */
    val instruction: String,
) {
    val kind: TurnKind
        get() = when {
            type == 88 -> TurnKind.DESTINATION
            type == 87 -> TurnKind.WAYPOINT
            "유턴" in instruction || type == 6 -> TurnKind.UTURN
            "좌회전" in instruction || "왼쪽" in instruction -> TurnKind.LEFT
            "우회전" in instruction || "오른쪽" in instruction -> TurnKind.RIGHT
            else -> TurnKind.STRAIGHT
        }
}

enum class TurnKind { STRAIGHT, LEFT, RIGHT, UTURN, WAYPOINT, DESTINATION }

/** 길안내용 경로 한 벌. */
data class PlannedRoute(
    val path: List<Wgs84Point>,
    val guides: List<RouteGuide>,
    val distanceMeters: Int,
    val durationMillis: Long,
    val tollFareWon: Int,
    /** 경유지가 경로선에서 놓인 지점 (경유지 순서대로) */
    val waypointPointIndexes: List<Int>,
)

class DirectionsException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * 네이버 Directions 5(운전 경로) 클라이언트. 우리 네이버 지도에 경로선을 그리는 데만 쓴다.
 * 실제 턴바이턴 안내는 내장 내비가 하므로, 이 선은 "대략 이 길로 간다"를 보여 주는 용도다.
 *
 * TODO(서버): Client Secret이 앱에 들어가 있다. 테스트용이며, 출시 전에 서버 프록시로 옮긴다 (ADR-002).
 */
class NaverDirectionsClient(
    private val clientId: String,
    private val clientSecret: String,
    private val baseUrl: String = "https://maps.apigw.ntruss.com/map-direction/v1/driving",
) {

    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    /** 출발 → 경유지(최대 5개) → 목적지 경로의 좌표 목록. */
    suspend fun drivingPath(
        start: Wgs84Point,
        goal: Wgs84Point,
        waypoints: List<Wgs84Point> = emptyList(),
    ): List<Wgs84Point> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            throw DirectionsException("경로선 키가 없습니다. local.properties에 NAVER_MAP_CLIENT_SECRET을 넣어 주세요.")
        }
        val params = buildMap {
            put("start", start.toParam())
            put("goal", goal.toParam())
            if (waypoints.isNotEmpty()) put("waypoints", waypoints.take(MAX_WAYPOINTS).joinToString("|") { it.toParam() })
            put("option", "traoptimal")
        }
        parsePath(get(params))
    }

    /**
     * 길안내용 경로 전체: 경로선 + 회전 안내 목록 + 요약(거리·시간·통행료·경유지 위치).
     * 우리 앱이 직접 턴바이턴 안내를 할 때 쓴다 (내장 내비를 거치지 않음).
     */
    suspend fun drivingRoute(
        start: Wgs84Point,
        goal: Wgs84Point,
        waypoints: List<Wgs84Point> = emptyList(),
    ): PlannedRoute = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            throw DirectionsException("경로 키가 없습니다. local.properties에 NAVER_MAP_CLIENT_SECRET을 넣어 주세요.")
        }
        val params = buildMap {
            put("start", start.toParam())
            put("goal", goal.toParam())
            if (waypoints.isNotEmpty()) put("waypoints", waypoints.take(MAX_WAYPOINTS).joinToString("|") { it.toParam() })
            put("option", "traoptimal")
        }
        parseRoute(get(params))
    }

    internal fun parseRoute(body: String): PlannedRoute {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw DirectionsException("경로 응답을 읽을 수 없습니다: ${body.take(120)}", e)
        }
        if (json.optInt("code", -1) != 0) {
            throw DirectionsException(json.optString("message", "경로를 찾지 못했습니다"))
        }
        val route = json.optJSONObject("route")?.optJSONArray("traoptimal")?.optJSONObject(0)
            ?: throw DirectionsException("경로가 비어 있습니다")
        val path = parsePath(body)
        val summary = route.optJSONObject("summary")
        val guidesJson = route.optJSONArray("guide")
        val guides = (0 until (guidesJson?.length() ?: 0)).map { i ->
            val g = guidesJson!!.getJSONObject(i)
            RouteGuide(
                pointIndex = g.optInt("pointIndex"),
                type = g.optInt("type"),
                instruction = g.optString("instructions"),
            )
        }
        val waypointsJson = summary?.optJSONArray("waypoints")
        val waypointIndexes = (0 until (waypointsJson?.length() ?: 0)).map { i ->
            waypointsJson!!.getJSONObject(i).optInt("pointIndex")
        }
        return PlannedRoute(
            path = path,
            guides = guides,
            distanceMeters = summary?.optInt("distance") ?: 0,
            durationMillis = summary?.optLong("duration") ?: 0L,
            tollFareWon = summary?.optInt("tollFare") ?: 0,
            waypointPointIndexes = waypointIndexes,
        )
    }

    private fun get(params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = (URL("$baseUrl?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
            setRequestProperty("X-NCP-APIGW-API-KEY-ID", clientId)
            setRequestProperty("X-NCP-APIGW-API-KEY", clientSecret)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                android.util.Log.w("NaverDirections", "HTTP $code query=$query body=$err")
                throw DirectionsException("경로 조회 실패 HTTP $code ${err.take(120)}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: DirectionsException) {
            throw e
        } catch (e: IOException) {
            throw DirectionsException("경로 서버 연결 실패: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parsePath(body: String): List<Wgs84Point> {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw DirectionsException("경로 응답을 읽을 수 없습니다: ${body.take(120)}", e)
        }
        // code 0 = 성공. 1 = 출발지와 도착지가 같음 등은 선을 안 그리면 된다.
        if (json.optInt("code", -1) != 0) {
            throw DirectionsException(json.optString("message", "경로를 찾지 못했습니다"))
        }
        val path = json.optJSONObject("route")
            ?.optJSONArray("traoptimal")
            ?.optJSONObject(0)
            ?.optJSONArray("path")
            ?: return emptyList()
        // 좌표는 [경도, 위도] 순서다.
        return (0 until path.length()).map { i ->
            val p = path.getJSONArray(i)
            Wgs84Point(latitude = p.getDouble(1), longitude = p.getDouble(0))
        }
    }

    private fun Wgs84Point.toParam() = "$longitude,$latitude"

    private companion object {
        const val MAX_WAYPOINTS = 5
    }
}
