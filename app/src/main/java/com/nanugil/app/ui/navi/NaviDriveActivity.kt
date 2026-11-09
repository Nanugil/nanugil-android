package com.nanugil.app.ui.navi

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nanugil.app.BuildConfig
import com.nanugil.app.MainActivity
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.distanceTo
import com.nanugil.app.core.geo.remainingAlong
import com.nanugil.app.data.location.GpsFix
import com.nanugil.app.data.location.GpsLocationSource
import com.nanugil.app.data.fare.TripCostTracker
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.vehicle.VehicleStatusSource
import com.nanugil.app.data.mock.FakeGroupLocationRepository
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.navi.NaviController
import com.nanugil.app.data.navi.NaviPlace
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.route.DirectionsException
import com.nanugil.app.data.route.NaverDirectionsClient
import com.nanugil.app.data.route.RouteCache
import com.naver.maps.map.MapView
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 나누길 주행 화면 ("나누길 내비").
 *
 * 일반 Activity라서 지도를 손으로 끌기·핀치 확대·회전할 수 있다.
 * - 지도: 네이버 MapView (어두운 내비 지도, 경로선 = 네이버 Directions 5)
 * - 내 차 위치: GPS 우선 (내장 내비 위치는 내비가 멈추면 같이 멈추므로 GPS가 없을 때만 쓴다)
 * - 카드: 목적지·도착 예정·이동 경로(내장 내비), 합류 제안(그룹), 1인 정산(목업)
 * - 실제 경로 계산·음성 안내·재탐색은 차량 내장 내비가 한다 (ADR-001)
 */
class NaviDriveActivity : ComponentActivity() {

    private lateinit var naviController: NaviController
    private val directionsClient = NaverDirectionsClient(BuildConfig.NAVER_MAP_CLIENT_ID, BuildConfig.NAVER_MAP_CLIENT_SECRET)
    // 서버가 정해지기 전까지는 가짜 그룹 데이터
    private val groupRepository = FakeGroupLocationRepository()
    private lateinit var vehicleStatus: VehicleStatusSource
    private lateinit var tripCost: TripCostTracker
    private lateinit var gps: GpsLocationSource
    private lateinit var routeCache: RouteCache
    /** 지금 그려진 경로선. GPS 위치로 남은 거리를 다시 계산할 때 쓴다 */
    private var routePath by mutableStateOf<List<Wgs84Point>>(emptyList())

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) gps.start() else toast("위치 권한이 없어 내장 내비 위치로 표시해요")
    }

    private lateinit var mapView: MapView
    private var drive: DriveMapController? = null

    private var cars by mutableStateOf<List<CarState>>(emptyList())
    private var routeError by mutableStateOf<String?>(null)
    /** "건너뛰기"·"합류하기"를 누른 합류 제안은 다시 띄우지 않는다 */
    private var meetupHandled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        naviController = NaviController(this).also { it.connect() }
        // Pleos Vehicle SDK: 연료·배터리 잔량, 주행거리, 연비
        vehicleStatus = VehicleStatusSource(this).also { it.start(lifecycleScope) }
        tripCost = TripCostTracker(this, members = MockGroupDriveData.SHARE_MEMBERS)
        gps = GpsLocationSource(this)
        routeCache = RouteCache(this)
        if (gps.hasPermission()) gps.start() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        mapView = MapView(this).also { it.onCreate(savedInstanceState) }

        setContent {
            val naviRaw by naviController.state.collectAsState()
            val gpsFix by gps.fix.collectAsState()
            val vehicle by vehicleStatus.status.collectAsState()
            val navi = withGps(naviRaw, gpsFix, routePath)
            NaviDriveScreen(
                mapView = mapView,
                navi = navi,
                vehicle = vehicle,
                carsDriving = cars.size,
                proposal = meetupProposal(navi),
                // 출발(길안내 시작)부터 지금까지 쓴 유류비·통행료
                cost = tripCost.update(navi, vehicle),
                routeError = routeError,
                onStartRoute = {
                    // TODO(서버): 그룹 목적지를 서버에서 받는다. 지금은 데모용 고정 목적지.
                    naviController.startRoute(NaviPlace(name = "가평 펜션", position = MockGroupDriveData.DESTINATION))
                },
                onStopRoute = naviController::cancelRoute,
                onAcceptMeetup = {
                    meetupHandled = true
                    // TODO(서버): 합류 수락을 그룹 전체에 푸시 → 각 차량이 같은 경유지를 추가
                    naviController.addMeetupStop(NaviPlace(MockGroupDriveData.MEETUP_NAME, MockGroupDriveData.MEETUP_POSITION))
                },
                onSkipMeetup = { meetupHandled = true },
                onZoomIn = { drive?.zoomBy(1.0) },
                onZoomOut = { drive?.zoomBy(-1.0) },
                onToggleLayer = { drive?.toggleLayer() },
                onRecenter = { drive?.recenter() },
                onHome = { startActivity(Intent(this, MainActivity::class.java)) },
                onCars = { startActivity(Intent(this, MainActivity::class.java)) },
                onFare = { toast("정산 화면은 준비 중이에요") },
                onMic = { toast("음성 명령은 준비 중이에요") },
                onProfile = { toast("프로필은 준비 중이에요") },
            )
        }

        mapView.getMapAsync { map ->
            val controller = DriveMapController(this, map)
            drive = controller
            observe(controller)
        }
    }

    /** 연료가 부족한 다른 차가 있으면 합류 제안 카드를 띄운다 (이미 경유지로 넣었거나 건너뛰면 숨김). */
    private fun meetupProposal(navi: NaviState): MeetupProposal? {
        if (!navi.guiding || meetupHandled) return null
        if (MockGroupDriveData.MEETUP_NAME in navi.waypointNames) return null
        val car = cars.firstOrNull { it.isLowFuel && !it.isMe } ?: return null
        return MeetupProposal(
            carLabel = car.carId.substringAfterLast('-').uppercase() + "차",
            driverName = car.driverName,
            placeName = MockGroupDriveData.MEETUP_NAME,
            distanceMeters = navi.currentPosition?.distanceTo(MockGroupDriveData.MEETUP_POSITION)?.toInt(),
            fuelPercent = car.fuelPercent,
        )
    }

    @OptIn(FlowPreview::class)
    private fun observe(controller: DriveMapController) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1) 내장 내비 상태 + GPS → 지도 (내 차 위치·방향은 GPS 우선)
                launch {
                    combine(naviController.state, gps.fix) { navi, fix -> navi to fix }.collect { (navi, fix) ->
                        val shown = withGps(navi, fix, routePath)
                        controller.updateNavigation(shown)
                        controller.updateVehicle(shown.currentPosition, shown.heading)
                    }
                }
                // 1-1) 안내 중 GPS로 이동한 거리를 쌓는다 (주행거리를 못 읽는 차의 유류비 계산용)
                launch {
                    var last: Wgs84Point? = null
                    gps.fix.collect { fix ->
                        val pos = fix?.position ?: return@collect
                        if (!naviController.state.value.guiding) {
                            last = null
                            return@collect
                        }
                        last?.let { prev ->
                            val step = prev.distanceTo(pos)
                            // 순간이동(에뮬레이터 위치 점프)은 이동 거리로 치지 않는다
                            if (step < MAX_GPS_STEP_M) tripCost.addGpsDistance(step)
                        }
                        last = pos
                    }
                }
                // 2) 목적지·경유지가 바뀔 때만 경로선을 다시 정한다 (저장해 둔 경로선이 있으면 그대로 쓴다)
                launch {
                    var prevGuiding: Boolean? = null
                    naviController.state
                        .map { RouteKey(it.guiding, it.destinationPosition, it.waypointPositions, it.currentPosition != null) }
                        .distinctUntilChanged()
                        // 목적지와 경유지 정보가 따로 도착하므로 잠깐 모아서 한 번만 처리한다
                        .debounce(ROUTE_SETTLE_MS)
                        .collect { key ->
                            if (!key.guiding) {
                                // 안내가 "끝나는 것"을 봤을 때만 저장한 경로선을 지운다 (앱을 막 켰을 때는 지우지 않음)
                                if (prevGuiding == true && naviController.state.value.connected) routeCache.clear()
                                prevGuiding = false
                                routePath = emptyList()
                                controller.updateRoute(emptyList())
                                return@collect
                            }
                            prevGuiding = true
                            val path = loadRoute(key)
                            routePath = path
                            controller.updateRoute(path)
                        }
                }
                // 3) 화면이 보이는 동안 1초마다 현재 위치·남은 거리 갱신
                launch {
                    naviController.refreshRoute()
                    while (true) {
                        naviController.requestLiveInfo()
                        delay(LIVE_INTERVAL_MS)
                    }
                }
                // 4) 그룹 차량 (몇 대 주행 중, 연료 부족 → 합류 제안)
                launch {
                    groupRepository.observeGroup("demo").collect { cars = it }
                }
            }
        }
    }

    /**
     * 안내 중인 경로선을 정한다.
     * 1) 같은 목적지·경유지로 저장해 둔 경로선이 있고 지금 위치가 그 위에 있으면 그대로 쓴다
     *    → 앱을 껐다 켜도 출발점부터의 경로선과 지나온 길(회색)이 이어진다.
     * 2) 아니면 지금 위치에서 새로 받되, 이전 경로선에서 이미 지나온 구간은 앞에 붙여 둔다
     *    → 경유지를 넣어 경로가 바뀌어도 회색 구간이 끊기지 않는다.
     */
    private suspend fun loadRoute(key: RouteKey): List<Wgs84Point> {
        val goal = key.destination ?: return emptyList()
        // 경로선 출발점도 GPS 우선
        val start = gps.fix.value?.position ?: naviController.state.value.currentPosition ?: return emptyList()
        val cacheKey = RouteCache.keyOf(goal, key.waypoints)

        routeCache.load(cacheKey)?.let { cached ->
            // 같은 목적지로 예전에 다른 곳에서 출발했던 경로면 쓰지 않는다
            if (remainingAlong(cached, start) != null) return cached
        }

        val fresh = try {
            directionsClient.drivingPath(start, goal, key.waypoints).also { routeError = null }
        } catch (e: DirectionsException) {
            routeError = "경로선: ${e.message}"
            return routePath
        }
        val merged = passedPart(routePath, start) + fresh
        routeCache.save(cacheKey, merged)
        return merged
    }

    /** 이전 경로선에서 [position]까지 이미 지나온 구간. 경로에서 벗어나 있으면 빈 목록. */
    private fun passedPart(path: List<Wgs84Point>, position: Wgs84Point): List<Wgs84Point> {
        if (path.size < 2) return emptyList()
        val nearest = path.indices.minByOrNull { path[it].distanceTo(position) } ?: return emptyList()
        if (path[nearest].distanceTo(position) > OFF_ROUTE_M) return emptyList()
        return path.subList(0, nearest + 1)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // ---- MapView 생명주기 ----

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // 내장 내비 화면에서 안내를 시작하고 돌아왔을 수 있다
        naviController.refreshRoute()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        vehicleStatus.stop()
        gps.stop()
        naviController.release()
        super.onDestroy()
    }

    private data class RouteKey(
        val guiding: Boolean,
        val destination: Wgs84Point?,
        val waypoints: List<Wgs84Point>,
        val hasStart: Boolean,
    )

    private companion object {
        const val LIVE_INTERVAL_MS = 1_000L
        /** 1초 사이 이보다 멀리 움직이면 위치 점프로 보고 이동 거리에서 뺀다 (≈ 360km/h) */
        const val MAX_GPS_STEP_M = 100.0
        /** 목적지·경유지 정보가 다 도착할 때까지 기다리는 시간 */
        const val ROUTE_SETTLE_MS = 1_500L
        /** 경로선에서 이보다 멀면 그 경로 위에 있지 않은 것으로 본다 */
        const val OFF_ROUTE_M = 300.0

        /**
         * 내장 내비 상태에 GPS를 덮어쓴다.
         * - 현재 위치·방향 = GPS
         * - 남은 거리 = 경로선 위에서 GPS 위치부터 목적지까지 거리
         *   (에뮬레이터처럼 내장 내비 위치가 멈춰 있어도 GPS로 달리면 줄어든다)
         * - 남은 시간 = 내비 예상 시간 × (남은 거리 비율), 경유지 거리 = 줄어든 만큼 빼기
         */
        fun withGps(navi: NaviState, fix: GpsFix?, path: List<Wgs84Point>): NaviState {
            if (fix == null) return navi
            val shown = navi.copy(currentPosition = fix.position, heading = fix.headingDegrees)
            val naviRemain = navi.remainDistanceMeters ?: return shown
            if (!navi.guiding || naviRemain <= 0 || path.size < 2) return shown
            val along = remainingAlong(path, fix.position)?.roundToInt() ?: return shown
            val ratio = (along.toDouble() / naviRemain).coerceIn(0.0, 1.0)
            val traveled = (naviRemain - along).coerceAtLeast(0)
            return shown.copy(
                remainDistanceMeters = along,
                remainTimeSeconds = navi.remainTimeSeconds?.let { (it * ratio).roundToInt() },
                waypointRemainMeters = navi.waypointRemainMeters.map { (it - traveled).coerceAtLeast(0) },
            )
        }
    }
}
