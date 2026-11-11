package com.nanugil.app.data.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
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
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
    /** 다음 회전 안내 (내장 내비 getTBTInfo) */
    val nextTurn: NaviTurn? = null,
    /** 그다음 회전 (작은 보조 안내) */
    val afterNextTurn: NaviTurn? = null,
    /** 마지막 이벤트·오류를 사람이 읽을 수 있게 */
    val message: String? = null,
    /** 내장 내비 경로가 새로 잡힐 때마다(안내 시작·재탐색·경유지 변경) 1씩 오른다. 경로선 다시 그리기용. */
    val routeVersion: Int = 0,
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
 * Pleos NaviHelper SDK를 감싼다 (ADR-002: 지도는 네이버, 실제 길안내·음성·재탐색은 차량 내장 내비).
 *
 * - 길안내 시작: [startRoute] → 내장 내비가 목적지까지 안내
 * - 합류 주유소: [addMeetupStop] → 안내 중이면 경유지 추가 + 재탐색, 아니면 그곳으로 바로 안내
 * - 우리 화면 표시용: [requestLiveInfo]로 다음 회전(TBT)·남은 거리·시간·현재 위치를 받는다
 *
 * 경로 요청·경유지 추가 때는 내장 내비 화면이 앞으로 튀어나온다(에뮬레이터 확인).
 * [returnTo]를 주면 요청을 보낸 뒤 그 화면으로 다시 돌아온다. 안내는 내비가 화면 뒤에서 계속한다.
 *
 * NaviHelper 콜백은 바인더 스레드에서 오므로 상태는 [StateFlow]로만 내보낸다.
 */
class NaviController(
    context: Context,
    private val returnTo: Class<out Activity>? = null,
) {

    private val appContext = context.applicationContext
    private val naviHelper = NaviHelper(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 우리가 경유지로 넣은 장소 이름 → 좌표 (내비가 좌표를 0,0으로 돌려줄 때 대신 쓴다) */
    private val addedStops = java.util.concurrent.ConcurrentHashMap<String, Wgs84Point>()
    private val _state = MutableStateFlow(NaviState())
    /** 이 시각(elapsedRealtime)까지는 내비가 경로를 다시 잡는 중으로 본다 */
    @Volatile private var replanUntil = 0L

    // ---- 내비 화면을 띄우지 않고 요청하기 ----
    /** 내장 내비가 알려 준 상태. FREE_DRIVE·ROUTE_DRIVE면 화면 뒤에서도 요청을 받는다. */
    @Volatile private var naviStatus: NavigationStatus? = null
    /** 이번 실행에서 내비가 우리 요청·조회에 응답한 적이 있는지 (상태 이벤트가 안 와도 준비된 것으로 본다) */
    @Volatile private var naviResponded = false
    /** 이 시각까지 우리 화면이 가려지면 내비가 튀어나온 것으로 보고 바로 돌아온다 */
    @Volatile private var popWatchUntil = 0L
    /** 경로를 요청하고 안내 시작(onRouteStarted)을 기다리는 중인지 */
    @Volatile private var waitingForStart = false
    /** 안내가 안 시작돼서 내비 화면을 띄워 둔 상태인지 (시작되면 바로 돌아온다) */
    @Volatile private var returnWhenStarted = false

    /**
     * 내비가 요청을 받을 수 있는지.
     * "navi not ready"는 부팅 뒤 내비 화면이 한 번도 안 떴을 때만 생긴다. 그래서 이번 부팅에서 내비가
     * 준비된 걸 한 번 봤으면 기억해 두고(앱을 다시 켜도 유지), 다음 부팅 전까지는 내비를 띄우지 않는다.
     */
    private val naviReady: Boolean
        get() = when (naviStatus) {
            NavigationStatus.FREE_DRIVE, NavigationStatus.ROUTE_DRIVE -> true
            NavigationStatus.INACTIVE, NavigationStatus.INITIALIZING -> false
            null -> naviResponded || readyBoot() == bootCount()
        }

    private val prefs = appContext.getSharedPreferences("navi_controller", Context.MODE_PRIVATE)

    private fun bootCount(): Int =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun readyBoot(): Int = prefs.getInt(KEY_READY_BOOT, -2)

    /** 내비가 이번 부팅에서 준비됐다는 걸 기억한다 */
    private fun markNaviReady() {
        naviResponded = true
        val boot = bootCount()
        if (boot >= 0 && readyBoot() != boot) prefs.edit().putInt(KEY_READY_BOOT, boot).apply()
    }

    /** 내비가 요청을 받아 처리했다는 신호 */
    private fun onNaviAck() = markNaviReady()
    val state: StateFlow<NaviState> = _state.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            onNaviAck()
            waitingForStart = false
            mainHandler.removeCallbacksAndMessages(START_WATCH_TOKEN)
            if (returnWhenStarted) {
                returnWhenStarted = false
                Log.d(TAG, "안내 시작됨 → 나누길 화면으로 복귀")
                returnToApp(QUICK_RETURN_DELAY_MS)
                NaviWaitService.stop(appContext)
            }
            replanUntil = 0L
            _state.update {
                it.copy(
                    routeVersion = it.routeVersion + 1,
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

        override fun onRoutingCompleted() {
            onNaviAck()
        }

        override fun onNavigationStatus(navigationStatus: NavigationStatus) {
            Log.d(TAG, "navi status $navigationStatus")
            naviStatus = navigationStatus
            if (navigationStatus == NavigationStatus.FREE_DRIVE || navigationStatus == NavigationStatus.ROUTE_DRIVE) {
                markNaviReady()
            }
        }

        override fun onReRouteEnded() {
            onNaviAck()
            _state.update { it.copy(message = "경로 재탐색 완료", routeVersion = it.routeVersion + 1) }
            refreshRoute()
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            // 안내 중이 아닐 때도 빈 목적지(이름 없음, 0m)가 온다 → 안내 없음으로 본다.
            if (destinationInfo.name.isBlank() && destinationInfo.remainDistance <= 0) {
                // 경유지 추가·경로 요청 직후 내비는 기존 안내를 취소하고 미리보기를 거쳐 다시 안내한다(약 10초).
                // 그 사이 빈 목적지가 와도 안내가 끝난 것으로 보지 않는다 (화면 깜빡임·유류비 초기화 방지).
                if (SystemClock.elapsedRealtime() < replanUntil) return
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
            markNaviReady()
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
            onNaviAck()
            val added = waypointChangedInfo.cmdType == WaypointCmdType.ADD
            _state.update {
                it.copy(
                    routeVersion = it.routeVersion + 1,
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
            if (tbtInfoList.isNotEmpty()) markNaviReady()
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
            onNaviAck()
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
        NaviWaitService.stop(appContext)
        naviHelper.removeListener(listener)
        naviHelper.release()
    }

    /** 내장 내비로 목적지 길안내를 시작한다. */
    fun startRoute(destination: NaviPlace) {
        if (_state.value.guiding) replanUntil = SystemClock.elapsedRealtime() + REPLAN_GRACE_MS
        _state.update {
            it.copy(
                destinationName = destination.name,
                destinationPosition = destination.position,
                message = "${destination.name} 경로 요청 중…",
            )
        }
        sendToNavi("경로 요청") { naviHelper.requestRoute(destination.toRouteInfo()) }
    }

    /**
     * 내장 내비에 요청을 보낸다.
     * - 내비가 이미 준비돼 있으면(FREE_DRIVE·ROUTE_DRIVE) 우리가 내비를 띄우지 않고 바로 보낸다.
     *   단, 내비는 경로 요청·경유지 추가를 받으면 스스로 앞으로 나온다(에뮬레이터 확인, 요청 후 약 0.1초).
     *   그래서 우리 화면이 가려지는 순간([onAppHidden]) 바로 돌아오고, 혹시 그 신호를 못 받으면 2초 뒤에 돌아온다.
     * - 아직 준비 전(부팅 직후 "navi not ready")이면 내비를 먼저 띄우고 보낸 뒤 돌아온다.
     */
    private fun sendToNavi(action: String, expectStart: Boolean = true, request: () -> Unit) {
        if (expectStart) watchRouteStart(action)
        if (!naviReady) {
            Log.d(TAG, "$action: 내비 준비 전 → 내비 화면을 띄우고 요청 (status=$naviStatus)")
            withNaviOnScreen {
                safely(action, request)
                returnToApp()
            }
            return
        }
        Log.d(TAG, "$action: 내비를 띄우지 않고 바로 요청 (status=$naviStatus)")
        popWatchUntil = SystemClock.elapsedRealtime() + POP_WATCH_MS
        safely(action, request)
        returnToApp() // 안전장치: 가려짐 신호를 못 받았을 때. 이미 앞에 있으면 아무 일도 없다.
    }

    /**
     * 경로 요청 뒤 내비는 미리보기를 보여 주다가 몇 초 뒤 저절로 안내를 시작한다(에뮬레이터 5~8초).
     * 혹시 [START_TIMEOUT_MS]가 지나도 시작되지 않으면(미리보기에서 멈춤) 내비 화면을 앞으로 띄워
     * 카운트다운이 끝나거나 운전자가 [시작]을 누를 수 있게 하고, 안내가 시작되면 바로 돌아온다.
     */
    private fun watchRouteStart(action: String) {
        waitingForStart = true
        returnWhenStarted = false
        mainHandler.removeCallbacksAndMessages(START_WATCH_TOKEN)
        mainHandler.postAtTime({
            if (!waitingForStart) return@postAtTime
            Log.w(TAG, "$action: 안내가 시작되지 않음 → 내비 화면을 띄워 둔다")
            returnWhenStarted = true
            _state.update { it.copy(message = "내비 화면에서 경로를 확인해 주세요") }
            safely("내비 화면 열기") { naviHelper.requestNavigationScreen(NavigationScreenRequest.Foreground) }
        }, START_WATCH_TOKEN, SystemClock.uptimeMillis() + START_TIMEOUT_MS)
    }

    /**
     * 우리 화면이 가려졌을 때(Activity onPause) 부른다.
     * 방금 화면 뒤로 요청을 보낸 직후라면 내비가 튀어나온 것이므로 바로 돌아온다.
     */
    fun onAppHidden() {
        if (SystemClock.elapsedRealtime() > popWatchUntil) return
        popWatchUntil = 0L
        Log.d(TAG, "요청 직후 내비가 앞으로 나옴 → 바로 복귀")
        mainHandler.removeCallbacksAndMessages(RETURN_TOKEN)
        returnToApp(QUICK_RETURN_DELAY_MS)
    }

    /**
     * 내비 화면이 앞으로 나온 뒤 우리 화면으로 돌아온다.
     * 내비가 요청을 받아 경로를 잡을 시간을 조금 주고 돌아온다 (너무 빨리 돌아오면 요청이 묻힐 수 있다).
     */
    private fun returnToApp(delayMs: Long = RETURN_DELAY_MS) {
        val target = returnTo ?: return
        mainHandler.postAtTime({
            try {
                appContext.startActivity(
                    Intent(appContext, target).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                    ),
                )
            } catch (e: Exception) {
                Log.w(TAG, "앱 화면으로 복귀 실패", e)
            }
        }, RETURN_TOKEN, SystemClock.uptimeMillis() + delayMs)
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
     * 안내 중이면 "지금 목적지 + (합류 지점, 기존 경유지)"로 경로를 새로 요청하고, 안내 중이 아니면 그 주유소로 바로 안내한다.
     *
     * addWaypoint + requestReRoute를 쓰면 내장 내비가 기존 안내를 취소하고 "경로 목록" 화면에서 멈춰서
     * 운전자가 내비 화면에서 [시작]을 직접 눌러야 한다(에뮬레이터 로그: PreviewList). 반면 경로 요청은
     * 미리보기(PreviewDefaultState) 뒤 몇 초 만에 저절로 안내를 시작한다. 그래서 경로 요청으로 보낸다.
     */
    fun addMeetupStop(stop: NaviPlace) {
        val current = _state.value
        val destination = current.destinationPosition
        if (!current.guiding || destination == null) {
            startRoute(stop)
            return
        }
        addedStops[stop.name] = stop.position
        replanUntil = SystemClock.elapsedRealtime() + REPLAN_GRACE_MS
        _state.update { it.copy(message = "${stop.name} 경유지 추가 중…") }

        // 내장 내비 경유지는 최대 2개(FIRST·SECOND): 합류 지점을 첫 번째로, 기존 경유지를 그 뒤에
        val existing = current.waypointNames.zip(current.waypointPositions).filter { (name, _) -> name != stop.name }
        val stops = (listOf(stop.name to stop.position) + existing).take(MAX_WAYPOINTS)
        val waypoints = stops.mapIndexed { i, (name, position) ->
            RequestWaypointInfo(
                latitude = position.latitude,
                longitude = position.longitude,
                poiName = name,
                waypointIndex = if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
                poiId = "",
                address = if (name == stop.name) stop.address else "",
                poiSubId = "",
            )
        }
        val goal = NaviPlace(name = current.destinationName ?: "목적지", position = destination)
        sendToNavi("경유지 추가") {
            naviHelper.requestRouteWithWaypoints(RouteWithWaypointsRequest(goal.toRouteInfo(), waypoints))
        }
    }

    /**
     * 내장 내비의 장소 검색 화면을 키워드와 함께 연다. 사용자가 고른 곳으로 내비가 경로를 잡고,
     * 안내가 시작되면([onRouteStarted]) 우리 화면으로 돌아온다. 검색 화면은 운전자가 골라야 하므로 바로 돌아오지 않는다.
     */
    fun searchPlace(keyword: String) {
        if (keyword.isBlank()) return
        val query = keyword.trim()
        _state.update { it.copy(message = "내비에서 '$query' 검색 중…") }
        mainHandler.removeCallbacksAndMessages(START_WATCH_TOKEN)
        popWatchUntil = 0L
        waitingForStart = true
        returnWhenStarted = true
        // 내비 검색 화면에서 고르는 동안 앱이 얼면 "안내 시작" 이벤트를 못 받는다 → 잠깐 포그라운드 서비스로 붙잡는다
        NaviWaitService.start(appContext)
        val open = { safely("장소 검색") { naviHelper.requestNavigationScreen(NavigationScreenRequest.SearchPlace(query)) } }
        if (naviReady) open() else withNaviOnScreen(open)
    }

    /** 내장 내비 안내 화면을 앞으로 띄운다. */
    fun openNaviScreen() {
        withNaviOnScreen {
            safely("내비 화면 열기") { naviHelper.requestNavigationScreen(NavigationScreenRequest.Foreground) }
        }
    }

    fun cancelRoute() {
        replanUntil = 0L
        waitingForStart = false
        returnWhenStarted = false
        NaviWaitService.stop(appContext)
        mainHandler.removeCallbacksAndMessages(START_WATCH_TOKEN)
        // 안내 취소도 내비 화면을 앞으로 띄운다 → 경로 요청과 같이 바로 돌아온다
        popWatchUntil = SystemClock.elapsedRealtime() + POP_WATCH_MS
        safely("안내 취소") { naviHelper.cancelRoute() }
        returnToApp()
    }

    /**
     * 주행 중 화면 갱신용: 현재 위치, 다음 회전, 남은 거리·시간을 다시 받는다.
     * 주행 화면이 보이는 동안 1초마다 부른다 (경유지는 바뀔 때만 refreshRoute로).
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
        /** 경로 요청·경유지 추가 뒤 우리 화면으로 돌아오기까지 기다리는 시간 */
        const val RETURN_DELAY_MS = 2_000L
        /** 경로를 다시 잡는 동안 "안내 없음"을 무시하는 최대 시간 */
        const val REPLAN_GRACE_MS = 30_000L
        /** 복귀 예약을 한 번에 지우기 위한 표식 */
        val RETURN_TOKEN = Any()
        val START_WATCH_TOKEN = Any()
        /** 경로 요청 뒤 이 시간 안에 안내가 시작되지 않으면 내비 화면을 띄운다 (평소 5~8초) */
        const val START_TIMEOUT_MS = 15_000L
        const val KEY_READY_BOOT = "ready_boot_count"
        /** 내장 내비가 받는 경유지 최대 개수 (WaypointIndex FIRST·SECOND) */
        const val MAX_WAYPOINTS = 2
        /** 요청 직후 이 시간 안에 우리 화면이 가려지면 내비가 튀어나온 것으로 본다 */
        const val POP_WATCH_MS = 6_000L
        /** 내비가 튀어나왔을 때 돌아오기까지 (내비가 요청을 받을 틈) */
        const val QUICK_RETURN_DELAY_MS = 150L
    }
}
