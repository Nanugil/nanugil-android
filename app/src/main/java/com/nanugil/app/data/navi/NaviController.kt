package com.nanugil.app.data.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nanugil.app.core.geo.Wgs84Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 화면에 보여 줄 내장 내비 상태. */
data class NaviState(
    val connected: Boolean = false,
    val guiding: Boolean = false,
    val destinationName: String? = null,
    /** 지도에 목적지 마커를 그릴 좌표 */
    val destinationPosition: Wgs84Point? = null,
    /** 목적지까지 남은 거리(m) */
    val remainDistanceMeters: Int? = null,
    /** 목적지까지 남은 시간(초) */
    val remainTimeSeconds: Int? = null,
    /** 경로 예상 통행료(원). 정산(R4)에 쓴다. */
    val tollFareWon: Int? = null,
    val waypointNames: List<String> = emptyList(),
    /** 경유지 좌표 (경로선용, [waypointNames]와 같은 순서) */
    val waypointPositions: List<Wgs84Point> = emptyList(),
    /** 경유지까지 남은 거리(m) (경로 타임라인 표시용, [waypointNames]와 같은 순서) */
    val waypointRemainMeters: List<Int> = emptyList(),
    /** 내장 내비가 아는 내 차 현재 위치 (경로선 출발점) */
    val currentPosition: Wgs84Point? = null,
    /** 차 진행 방향(도, 북=0) */
    val heading: Double = 0.0,
    /** 다음 회전 안내 (차량 템플릿 화면용) */
    val nextTurn: NaviTurn? = null,
    /** 그다음 회전 (TMAP식 작은 보조 안내) */
    val afterNextTurn: NaviTurn? = null,
    /** 마지막 이벤트·오류를 사람이 읽을 수 있게 */
    val message: String? = null,
)

/** 내장 내비가 알려 준 다음 회전. */
data class NaviTurn(
    val type: TurnType,
    val distanceMeters: Double,
    val description: String,
)

/** 길안내 목적지·경유지로 넘길 장소. */
data class NaviPlace(
    val name: String,
    val position: Wgs84Point,
    val address: String = "",
)

/**
 * Pleos NaviHelper SDK를 감싼다 (ADR-001: 실제 턴바이턴 안내는 차량 내장 내비가 한다).
 *
 * - 길안내 시작: [startRoute] → 내장 내비가 목적지까지 안내
 * - 합류 주유소: [addMeetupStop] → 안내 중이면 경유지 추가 + 재탐색, 아니면 그곳으로 바로 안내
 *
 * NaviHelper 콜백은 바인더 스레드에서 오므로 상태는 [StateFlow]로만 내보낸다.
 */
class NaviController(context: Context) {

    private val appContext = context.applicationContext
    private val naviHelper = NaviHelper(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 우리가 경유지로 넣은 장소 이름 → 좌표 (내비가 좌표를 0,0으로 돌려줄 때 대신 쓴다) */
    private val addedStops = java.util.concurrent.ConcurrentHashMap<String, Wgs84Point>()
    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            _state.update {
                it.copy(
                    guiding = true,
                    destinationName = routeStartInfo.name.ifBlank { it.destinationName },
                    remainDistanceMeters = routeStartInfo.distance,
                    remainTimeSeconds = routeStartInfo.duration,
                    tollFareWon = routeStartInfo.tollFare,
                    message = "길안내 시작",
                )
            }
            refreshRoute()
        }

        override fun onReRouteEnded() {
            _state.update { it.copy(message = "경로 재탐색 완료") }
            refreshRoute()
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            // 안내 중이 아닐 때도 빈 목적지(이름 없음, 0m)가 온다 → 안내 없음으로 본다.
            if (destinationInfo.name.isBlank() && destinationInfo.remainDistance <= 0) {
                _state.update {
                    it.copy(
                        guiding = false,
                        destinationName = null,
                        destinationPosition = null,
                        remainDistanceMeters = null,
                        remainTimeSeconds = null,
                        tollFareWon = null,
                        waypointNames = emptyList(),
                        waypointPositions = emptyList(),
                        waypointRemainMeters = emptyList(),
                        nextTurn = null,
                        afterNextTurn = null,
                    )
                }
                return
            }
            _state.update {
                it.copy(
                    guiding = true,
                    destinationName = destinationInfo.name,
                    destinationPosition = Wgs84Point(destinationInfo.latitude, destinationInfo.longitude),
                    remainDistanceMeters = destinationInfo.remainDistance,
                    remainTimeSeconds = destinationInfo.remainTime,
                    // 에뮬레이터 내비는 DestinationInfo.tollFare를 0으로 준다 → 경로 시작/경유지 변경 때 받은 값을 유지
                    tollFareWon = destinationInfo.tollFare.takeIf { fare -> fare > 0 } ?: it.tollFareWon,
                )
            }
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            val added = waypointChangedInfo.cmdType == WaypointCmdType.ADD
            _state.update {
                it.copy(
                    tollFareWon = waypointChangedInfo.routeStartInfo.tollFare,
                    message = if (added) "${waypointChangedInfo.routeStartInfo.name} 경유지 추가됨" else "경유지 삭제됨",
                )
            }
            refreshRoute()
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            _state.update {
                it.copy(
                    waypointNames = waypointInfoList.map { w -> w.name },
                    waypointPositions = waypointInfoList.mapNotNull { w -> w.resolvePosition() },
                    waypointRemainMeters = waypointInfoList.map { w -> w.remainDistance },
                )
            }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            _state.update {
                it.copy(
                    currentPosition = Wgs84Point(currentLocationInfo.latitude, currentLocationInfo.longitude),
                    heading = currentLocationInfo.heading,
                )
            }
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            val turns = tbtInfoList.map { t -> NaviTurn(t.type, t.distance, t.description) }
            _state.update { it.copy(nextTurn = turns.getOrNull(0), afterNextTurn = turns.getOrNull(1)) }
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            _state.update { it.copy(message = "경유지 도착") }
            refreshRoute()
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            // TODO(정산): 여기서 정산 화면을 띄운다 (vehicle-api-research.md 팀장 항목)
            _state.update {
                it.copy(guiding = false, waypointNames = emptyList(), waypointPositions = emptyList(),
                    waypointRemainMeters = emptyList(), message = "목적지 도착")
            }
        }

        // 내장 내비는 경로 미리보기 → 안내 시작으로 넘어갈 때도 onRouteEnded를 보낸다.
        // 그래서 종료·취소 이벤트로 상태를 지우지 않고, 실제 목적지가 남아 있는지 다시 물어본다.
        override fun onRouteCancelled() {
            refreshRoute()
        }

        override fun onRouteEnded() {
            refreshRoute()
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
            _state.update { it.copy(message = errorCode.toKorean()) }
        }
    }

    /** 액티비티 onCreate에서 한 번 부른다. */
    fun connect() {
        naviHelper.addListener(listener)
        naviHelper.initialize(
            onConnected = {
                _state.update { it.copy(connected = true, message = null) }
                // 앱을 켜기 전에 이미 안내 중이었다면 목적지 정보가 onDestinationInfo로 온다.
                refreshRoute()
            },
            onFailed = { e ->
                Log.e(TAG, "NaviHelper 연결 실패", e)
                _state.update { it.copy(connected = false, message = "내장 내비 연결 실패: ${e.message}") }
            },
        )
    }

    /** 액티비티 onDestroy에서 부른다. */
    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        naviHelper.removeListener(listener)
        naviHelper.release()
    }

    /** 내장 내비로 목적지 길안내를 시작한다. */
    fun startRoute(destination: NaviPlace) {
        _state.update {
            it.copy(
                destinationName = destination.name,
                destinationPosition = destination.position,
                message = "${destination.name} 경로 요청 중…",
            )
        }
        withNaviOnScreen { safely("경로 요청") { naviHelper.requestRoute(destination.toRouteInfo()) } }
    }

    /**
     * 내장 내비는 화면(액티비티)이 한 번도 안 떠 있으면 앱의 화면·경로 요청을 "navi not ready"로 버린다
     * (부팅 직후 에뮬레이터에서 확인). 그래서 내비 앱을 먼저 앞으로 띄우고 잠깐 뒤에 요청을 보낸다.
     * 어차피 검색·경로 미리보기는 내비 화면에서 이어지므로 사용자 흐름도 같다.
     */
    private fun withNaviOnScreen(request: () -> Unit) {
        val launch = appContext.packageManager.getLaunchIntentForPackage(NAVI_PACKAGE)
        if (launch == null) {
            request()
            return
        }
        try {
            appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "내비 앱 실행 실패", e)
            request()
            return
        }
        mainHandler.postDelayed({ request() }, NAVI_READY_DELAY_MS)
    }

    /**
     * 그룹 합류 주유소를 내 차 경로에 넣는다.
     * 안내 중이면 첫 번째 경유지로 추가하고 재탐색, 안내 중이 아니면 그 주유소로 바로 안내한다.
     */
    fun addMeetupStop(stop: NaviPlace) {
        if (!_state.value.guiding) {
            startRoute(stop)
            return
        }
        addedStops[stop.name] = stop.position
        _state.update { it.copy(message = "${stop.name} 경유지 추가 중…") }
        safely("경유지 추가") {
            naviHelper.addWaypoint(
                RequestWaypointInfo(
                    latitude = stop.position.latitude,
                    longitude = stop.position.longitude,
                    poiName = stop.name,
                    waypointIndex = WaypointIndex.FIRST,
                    poiId = "",
                    address = stop.address,
                    poiSubId = "",
                ),
            )
            naviHelper.requestReRoute()
        }
    }

    /** 내장 내비의 장소 검색 화면을 키워드와 함께 연다. 사용자가 고른 곳으로 내비가 경로를 잡는다. */
    fun searchPlace(keyword: String) {
        if (keyword.isBlank()) return
        val query = keyword.trim()
        _state.update { it.copy(message = "내비에서 '$query' 검색 중…") }
        withNaviOnScreen {
            safely("장소 검색") { naviHelper.requestNavigationScreen(NavigationScreenRequest.SearchPlace(query)) }
        }
    }

    /** 내장 내비 안내 화면을 앞으로 띄운다. */
    fun openNaviScreen() {
        withNaviOnScreen {
            safely("내비 화면 열기") { naviHelper.requestNavigationScreen(NavigationScreenRequest.Foreground) }
        }
    }

    fun cancelRoute() {
        safely("안내 취소") { naviHelper.cancelRoute() }
    }

    /**
     * 주행 중 화면 갱신용: 현재 위치, 다음 회전, 남은 거리·시간을 다시 받는다.
     * 차량 템플릿 화면이 보이는 동안 1초마다 부른다 (경유지는 바뀔 때만 refreshRoute로).
     */
    fun requestLiveInfo() {
        if (!_state.value.connected) return
        safely("주행 정보 조회") {
            naviHelper.getCurrentLocationInfo()
            if (_state.value.guiding) {
                naviHelper.getTBTInfo()
                naviHelper.getDestinationInfo()
            }
        }
    }

    /** 내장 내비의 현재 목적지·경유지를 다시 받아 온다. 앱이 다시 보일 때(onResume)도 부른다. */
    fun refreshRoute() {
        if (!_state.value.connected) return
        safely("경로 정보 조회") {
            naviHelper.getCurrentLocationInfo()
            naviHelper.getDestinationInfo()
            naviHelper.getWaypointInfo()
        }
    }

    /** 권한 누락(SecurityException)이나 미연결 상태에서 앱이 죽지 않게 감싼다. */
    private inline fun safely(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "$action 실패", e)
            _state.update { it.copy(message = "$action 실패: ${e.message}") }
        }
    }

    /**
     * 에뮬레이터 내비는 경유지 좌표(latitude/longitude)를 0,0으로 돌려준다.
     * 표시 좌표 → 우리가 경유지로 넣을 때 쓴 좌표 순으로 대신 쓰고, 그래도 없으면 버린다(경로선 요청이 400으로 실패하므로).
     */
    private fun WaypointInfo.resolvePosition(): Wgs84Point? {
        if (latitude != 0.0 || longitude != 0.0) return Wgs84Point(latitude, longitude)
        val dLat = displayLatitude
        val dLng = displayLongitude
        if (dLat != null && dLng != null && (dLat != 0.0 || dLng != 0.0)) return Wgs84Point(dLat, dLng)
        return addedStops[name]
    }

    private fun NaviPlace.toRouteInfo() = RouteInfo(
        latitude = position.latitude,
        longitude = position.longitude,
        poiName = name,
        poiId = "",
        address = address,
        poiSubId = "",
    )

    private fun NaviErrorCode.toKorean() = when (this) {
        NaviErrorCode.CAUSE_LOW_BATTERY -> "배터리 부족 경고"
        NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> "경로가 최대 거리를 넘습니다"
        NaviErrorCode.CAUSE_SEARCH_ROUTE -> "경로 탐색 서버 오류"
        NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> "목적지가 너무 가깝습니다"
        NaviErrorCode.CAUSE_SEARCH_TRIP -> "트립 서버 오류"
    }

    private companion object {
        const val TAG = "NaviController"
        /** Pleos 내장 내비 앱 */
        const val NAVI_PACKAGE = "ai.umos.maps.android.navigation.app"
        /** 내비 화면이 뜨고 요청을 받을 수 있을 때까지 기다리는 시간 */
        const val NAVI_READY_DELAY_MS = 1_200L
    }
}
