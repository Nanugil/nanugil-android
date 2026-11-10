package com.nanugil.app.ui.navi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nanugil.app.MainActivity
import com.nanugil.app.ui.map.MapViewLifecycleBinder
import com.naver.maps.map.MapView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 나누길 주행 화면 ("나누길 내비") — UI 계층.
 *
 * 이 Activity는 그리기와 이벤트 전달만 한다.
 * - 상태·로직: [NaviDriveViewModel] (길안내, GPS, 차량 상태, 그룹, 유류비 N빵)
 * - 지도 생명주기: [MapViewLifecycleBinder]
 * - 지도 그리기(경로선·주유소·목적지·그룹 차량 마커, 카메라): [DriveMapController]
 *
 * 라이트/다크 전환 등으로 Activity가 다시 만들어져도 ViewModel이 살아 있어 길안내가 끊기지 않는다.
 */
class NaviDriveActivity : ComponentActivity() {

    private val viewModel: NaviDriveViewModel by viewModels()

    private lateinit var mapView: MapView
    private lateinit var mapBinder: MapViewLifecycleBinder
    private var drive by mutableStateOf<DriveMapController?>(null)
    /** 지도가 내 차를 따라가는 중인지 (차 아래 파란 빛 표시용) */
    private var following by mutableStateOf(true)

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onLocationPermissionGranted()
        else Toast.makeText(this, "위치 권한이 있어야 길안내를 할 수 있어요", Toast.LENGTH_SHORT).show()
    }

    /** 마이크 권한을 받은 뒤 음성 검색을 시작한다 */
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceSearch()
        else Toast.makeText(this, "마이크 권한이 있어야 음성으로 검색할 수 있어요", Toast.LENGTH_SHORT).show()
    }

    private val searchActions by lazy {
        SearchActions(
            onOpen = viewModel::openSearch,
            onClose = viewModel::closeSearch,
            onQueryChange = viewModel::onQueryChange,
            onSubmit = { viewModel.submitSearch() },
            onVoice = ::startVoiceSearch,
            onStopVoice = viewModel::stopVoiceSearch,
            onSelect = viewModel::selectPlace,
        )
    }

    private fun startVoiceSearch() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startVoiceSearch() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapView = MapView(this)
        mapBinder = MapViewLifecycleBinder.bind(this, mapView, savedInstanceState)
        requestLocationIfNeeded()

        setContent {
            val ui by viewModel.uiState.collectAsState()
            NaviDriveScreen(
                mapView = mapView,
                navi = ui.navi,
                vehicle = ui.vehicle,
                carsDriving = ui.carsDriving,
                proposal = ui.proposal,
                cost = ui.cost,
                routeError = ui.navi.message?.takeIf { it.startsWith("경로 탐색 실패") },
                onStartRoute = viewModel::startRoute,
                onStopRoute = viewModel::stopRoute,
                onAcceptMeetup = viewModel::acceptMeetup,
                onSkipMeetup = viewModel::skipMeetup,
                onZoomIn = { drive?.zoomBy(1.0) },
                onZoomOut = { drive?.zoomBy(-1.0) },
                onRecenter = { drive?.recenter() },
                onHome = { drive?.recenter() },
                onCars = { startActivity(Intent(this, MainActivity::class.java)) },
                carGlow = following && ui.navi.guiding,
                themeMode = ui.themeMode,
                onThemeModeChange = viewModel::setThemeMode,
                search = ui.search,
                searchActions = searchActions,
            )
            // 지도도 화면 테마에 맞춰 낮/밤으로
            val dark = resolveDarkTheme(ui.themeMode)
            LaunchedEffect(dark, drive) { drive?.setDarkTheme(dark) }
        }

        mapView.getMapAsync { map ->
            // getMapAsync 콜백은 Activity가 이미 끝났을 때 올 수도 있다
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) return@getMapAsync
            val controller = DriveMapController(this, map)
            controller.onFollowingChanged = { following = it }
            drive = controller
            render(controller)
        }
    }

    /** ViewModel 상태를 지도에 반영한다. 화면이 보이는 동안만 구독한다. */
    private fun render(controller: DriveMapController) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.map { it.navi }.distinctUntilChanged().collect { navi ->
                        controller.updateNavigation(navi)
                        controller.updateVehicle(navi.currentPosition, navi.heading)
                        controller.updateWaypoints(navi.waypointNames, navi.waypointPositions)
                    }
                }
                launch { viewModel.routePath.collect(controller::updateRoute) }
                launch {
                    viewModel.uiState.map { it.groupCars }.distinctUntilChanged().collect { cars ->
                        controller.updateGroupCars(cars.filterNot { it.isMe })
                    }
                }
            }
        }
    }

    private fun requestLocationIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapBinder.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        // 오버레이·리스너를 먼저 떼고, MapView는 binder가 ON_DESTROY에서 정리한다
        drive?.release()
        drive = null
        super.onDestroy()
    }
}
