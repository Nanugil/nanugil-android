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
import com.nanugil.app.data.location.GpsLocationSource
import com.nanugil.app.data.fare.TripCostTracker
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.vehicle.VehicleStatusSource
import com.nanugil.app.data.mock.FakeGroupLocationRepository
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.navi.OwnNavigator
import com.nanugil.app.data.navi.NaviPlace
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.route.NaverDirectionsClient
import com.naver.maps.map.MapView
import kotlinx.coroutines.launch

/**
 * 나누길 주행 화면 ("나누길 내비").
 *
 * 일반 Activity라서 지도를 손으로 끌기·핀치 확대·회전할 수 있다.
 * - 지도: 네이버 MapView (어두운 내비 지도, 경로선 = 네이버 Directions 5)
 * - 내 차 위치: GPS 우선 (내장 내비 위치는 내비가 멈추면 같이 멈추므로 GPS가 없을 때만 쓴다)
 * - 카드: 목적지·도착 예정·이동 경로(내장 내비), 합류 제안(그룹), 1인 정산(목업)
 * - 길안내: 우리 앱이 직접 한다 (OwnNavigator: 네이버 경로 + GPS + 음성). 내장 내비 화면이 끼어들지 않는다
 */
class NaviDriveActivity : ComponentActivity() {

    /** 자체 길안내 (내장 내비를 거치지 않는다) */
    private lateinit var navigator: OwnNavigator
    private val directionsClient = NaverDirectionsClient(BuildConfig.NAVER_MAP_CLIENT_ID, BuildConfig.NAVER_MAP_CLIENT_SECRET)
    // 서버가 정해지기 전까지는 가짜 그룹 데이터
    private val groupRepository = FakeGroupLocationRepository()
    private lateinit var vehicleStatus: VehicleStatusSource
    private lateinit var tripCost: TripCostTracker
    private lateinit var gps: GpsLocationSource

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) gps.start() else toast("위치 권한이 있어야 길안내를 할 수 있어요")
    }

    private lateinit var mapView: MapView
    private var drive by mutableStateOf<DriveMapController?>(null)
    /** 화면 테마 (자동/라이트/다크). 앱을 껐다 켜도 유지되게 저장한다 */
    private var themeMode by mutableStateOf(ThemeMode.AUTO)

    /** 지도가 내 차를 따라가는 중인지 (파란 빛 표시용) */
    private var following by mutableStateOf(true)

    private var cars by mutableStateOf<List<CarState>>(emptyList())
    private var routeError by mutableStateOf<String?>(null)
    /** "건너뛰기"·"합류하기"를 누른 합류 제안은 다시 띄우지 않는다 */
    private var meetupHandled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigator = OwnNavigator(this, directionsClient, lifecycleScope)
        // Pleos Vehicle SDK: 연료·배터리 잔량, 주행거리, 연비
        vehicleStatus = VehicleStatusSource(this).also { it.start(lifecycleScope) }
        tripCost = TripCostTracker(this, members = MockGroupDriveData.SHARE_MEMBERS)
        gps = GpsLocationSource(this)
        if (gps.hasPermission()) gps.start() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        mapView = MapView(this).also { it.onCreate(savedInstanceState) }
        themeMode = loadThemeMode()

        setContent {
            val navi by navigator.state.collectAsState()
            val vehicle by vehicleStatus.status.collectAsState()
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
                    navigator.start(NaviPlace(name = "가평 펜션", position = MockGroupDriveData.DESTINATION))
                },
                onStopRoute = navigator::stop,
                onAcceptMeetup = {
                    meetupHandled = true
                    // TODO(서버): 합류 수락을 그룹 전체에 푸시 → 각 차량이 같은 경유지를 추가
                    navigator.addWaypoint(NaviPlace(MockGroupDriveData.MEETUP_NAME, MockGroupDriveData.MEETUP_POSITION))
                },
                onSkipMeetup = { meetupHandled = true },
                onZoomIn = { drive?.zoomBy(1.0) },
                onZoomOut = { drive?.zoomBy(-1.0) },
                onRecenter = { drive?.recenter() },
                onHome = { drive?.recenter() },
                onCars = { startActivity(Intent(this, MainActivity::class.java)) },
                carGlow = following && navi.guiding,
                themeMode = themeMode,
                onThemeModeChange = ::changeThemeMode,
            )
            // 지도도 화면 테마에 맞춰 낮/밤으로
            val dark = resolveDarkTheme(themeMode)
            androidx.compose.runtime.LaunchedEffect(dark, drive) { drive?.setDarkTheme(dark) }
        }

        mapView.getMapAsync { map ->
            val controller = DriveMapController(this, map)
            controller.onFollowingChanged = { following = it }
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

    private fun observe(controller: DriveMapController) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1) GPS → 자체 길안내 엔진 + GPS 이동 거리(유류비 계산용)
                launch {
                    var last: Wgs84Point? = null
                    gps.fix.collect { fix ->
                        fix ?: return@collect
                        navigator.onGps(fix)
                        if (navigator.state.value.guiding) {
                            last?.let { prev ->
                                val step = prev.distanceTo(fix.position)
                                if (step < MAX_GPS_STEP_M) tripCost.addGpsDistance(step)
                            }
                            last = fix.position
                        } else {
                            last = null
                        }
                    }
                }
                // 2) 길안내 상태 → 지도
                launch {
                    navigator.state.collect { state ->
                        controller.updateNavigation(state)
                        controller.updateVehicle(state.currentPosition, state.heading)
                    }
                }
                // 3) 경로선 → 지도
                launch { navigator.path.collect { controller.updateRoute(it) } }
                // 4) 그룹 차량 (몇 대 주행 중, 연료 부족 → 합류 제안, 지도에 다른 차 표시)
                launch {
                    groupRepository.observeGroup("demo").collect {
                        cars = it
                        controller.updateGroupCars(it.filterNot { car -> car.isMe })
                    }
                }
            }
        }
    }

    private fun changeThemeMode(mode: ThemeMode) {
        themeMode = mode
        getSharedPreferences(PREFS_UI, MODE_PRIVATE).edit().putString(KEY_THEME, mode.name).apply()
    }

    private fun loadThemeMode(): ThemeMode {
        val saved = getSharedPreferences(PREFS_UI, MODE_PRIVATE).getString(KEY_THEME, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.AUTO
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
        navigator.release()
        super.onDestroy()
    }

    private companion object {
        const val PREFS_UI = "navi_ui"
        const val KEY_THEME = "theme_mode"
        /** 1초 사이 이보다 멀리 움직이면 위치 점프로 보고 이동 거리에서 뺀다 (≈ 360km/h) */
        const val MAX_GPS_STEP_M = 100.0
    }
}
