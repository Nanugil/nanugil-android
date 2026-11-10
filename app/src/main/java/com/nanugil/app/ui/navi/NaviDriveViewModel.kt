package com.nanugil.app.ui.navi

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanugil.app.BuildConfig
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.distanceTo
import com.nanugil.app.data.fare.TripCostTracker
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.group.GroupLocationRepository
import com.nanugil.app.data.location.GpsLocationSource
import com.nanugil.app.data.mock.FakeGroupLocationRepository
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.navi.NaviPlace
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.navi.OwnNavigator
import com.nanugil.app.data.route.NaverDirectionsClient
import com.nanugil.app.data.route.NaverRouteRepository
import com.nanugil.app.data.route.RouteRepository
import com.nanugil.app.data.search.NaverPlaceSearchRepository
import com.nanugil.app.data.search.PlaceResult
import com.nanugil.app.data.search.PlaceSearchException
import com.nanugil.app.data.search.PlaceSearchRepository
import com.nanugil.app.data.voice.VoiceSearch
import kotlinx.coroutines.Job
import com.nanugil.app.data.vehicle.VehicleStatusSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 나누길 내비 화면의 상태와 동작.
 *
 * - 길안내([OwnNavigator]), GPS, 차량 상태(Pleos Vehicle SDK), 그룹 차량, 유류비 N빵 계산을
 *   한 곳에서 들고 있다가 [uiState] 하나로 합쳐 내보낸다.
 * - 경유지(주유소)가 추가되면 → 길안내가 경로를 다시 받고 → 경로선·남은 거리·경유지 카드가,
 *   GPS로 차가 움직이면 → 남은 거리와 1인 유류비가 [uiState]를 통해 화면에 바로 반영된다.
 * - Activity가 다시 만들어져도(라이트/다크 전환 등) ViewModel은 살아 있어서 길안내가 끊기지 않는다.
 *
 * 화면(Activity)은 이벤트 함수만 부르고, 데이터는 Repository를 통해서만 얻는다.
 */
class NaviDriveViewModel(app: Application) : AndroidViewModel(app) {

    // ---- 데이터 계층 ----
    private val routeRepository: RouteRepository =
        NaverRouteRepository(NaverDirectionsClient(BuildConfig.NAVER_MAP_CLIENT_ID, BuildConfig.NAVER_MAP_CLIENT_SECRET))
    // TODO(서버): 그룹 차량 위치·연료를 서버(실시간 동기화)에서 받는 구현으로 교체
    private val groupRepository: GroupLocationRepository = FakeGroupLocationRepository()
    private val gps = GpsLocationSource(app)
    private val vehicleStatus = VehicleStatusSource(app)
    private val tripCost = TripCostTracker(app, members = MockGroupDriveData.SHARE_MEMBERS)
    private val navigator = OwnNavigator(app, routeRepository, viewModelScope)
    private val prefs = app.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
    private val placeSearch: PlaceSearchRepository = NaverPlaceSearchRepository(
        searchClientId = BuildConfig.NAVER_SEARCH_CLIENT_ID,
        searchClientSecret = BuildConfig.NAVER_SEARCH_CLIENT_SECRET,
        ncpClientId = BuildConfig.NAVER_MAP_CLIENT_ID,
        ncpClientSecret = BuildConfig.NAVER_MAP_CLIENT_SECRET,
    )
    private val voice = VoiceSearch(app)
    private val search = MutableStateFlow(SearchUiState())
    private var searchJob: Job? = null

    // ---- 화면 전용 상태 ----
    /** 합류 제안을 수락하거나 건너뛰면 다시 띄우지 않는다 */
    private val meetupHandled = MutableStateFlow(false)
    private val themeMode = MutableStateFlow(loadThemeMode())

    private val groupCars: StateFlow<List<CarState>> = groupRepository.observeGroup(GROUP_ID)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 지도에 그릴 경로선 (현재 위치 → 경유지(주유소) → 목적지) */
    val routePath: StateFlow<List<Wgs84Point>> = navigator.path

    /** 화면 전체 상태. 어느 값 하나만 바뀌어도 새 스냅샷이 나온다. */
    private val baseState = combine(
        navigator.state,
        vehicleStatus.status,
        groupCars,
        meetupHandled,
        themeMode,
    ) { navi, vehicle, cars, handled, theme ->
        NaviDriveUiState(
            navi = navi,
            vehicle = vehicle,
            groupCars = cars,
            proposal = meetupProposal(navi, cars, handled),
            cost = tripCost.update(navi, vehicle),
            themeMode = theme,
        )
    }

    val uiState: StateFlow<NaviDriveUiState> = combine(baseState, search, voice.state) { base, s, v ->
        base.copy(search = s.copy(listening = v.listening, partial = v.partial, error = v.error ?: s.error))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NaviDriveUiState(themeMode = themeMode.value))

    init {
        vehicleStatus.start(viewModelScope)
        if (gps.hasPermission()) gps.start()
        // GPS → 길안내 엔진, 그리고 안내 중 이동 거리를 쌓는다 (주행거리계를 못 읽는 차의 유류비 계산용)
        viewModelScope.launch {
            var last: Wgs84Point? = null
            gps.fix.filterNotNull().collect { fix ->
                navigator.onGps(fix)
                if (navigator.state.value.guiding) {
                    last?.let { prev ->
                        val step = prev.distanceTo(fix.position)
                        // 순간이동(위치 점프)은 이동 거리로 치지 않는다
                        if (step < MAX_GPS_STEP_M) tripCost.addGpsDistance(step)
                    }
                    last = fix.position
                } else {
                    last = null
                }
            }
        }
    }

    // ---- 화면 이벤트 ----

    /** 위치 권한을 막 허용받았을 때 */
    fun onLocationPermissionGranted() = gps.start()

    fun startRoute() {
        // TODO(서버): 그룹 목적지를 서버에서 받는다. 지금은 데모용 고정 목적지.
        navigator.start(NaviPlace(name = "가평 펜션", position = MockGroupDriveData.DESTINATION))
    }

    fun stopRoute() = navigator.stop()

    /** 합류 제안 수락 → 그 주유소(휴게소)를 첫 번째 경유지로 넣고 경로를 다시 받는다 */
    fun acceptMeetup() {
        meetupHandled.value = true
        // TODO(서버): 합류 수락을 그룹 전체에 푸시 → 각 차량이 같은 경유지를 추가
        navigator.addWaypoint(NaviPlace(MockGroupDriveData.MEETUP_NAME, MockGroupDriveData.MEETUP_POSITION))
    }

    fun skipMeetup() {
        meetupHandled.value = true
    }

    /** 주유소 하나를 경유지로 추가 (주유소 목록 화면 등에서 부른다) */
    fun addFuelStop(name: String, position: Wgs84Point) = navigator.addWaypoint(NaviPlace(name, position))

    // ---- 목적지 검색 ----

    fun openSearch() {
        search.value = search.value.copy(open = true, error = null)
    }

    fun closeSearch() {
        searchJob?.cancel()
        voice.stop()
        search.value = SearchUiState()
    }

    fun onQueryChange(query: String) {
        voice.clearError()
        search.value = search.value.copy(query = query, error = null)
    }

    /** 검색창 글자로 장소 검색 (내 위치에서 가까운 순) */
    fun submitSearch(query: String = search.value.query) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        search.value = search.value.copy(query = q, searching = true, error = null)
        searchJob = viewModelScope.launch {
            search.value = try {
                val results = placeSearch.search(q, navigator.state.value.currentPosition)
                search.value.copy(results = results, searching = false, searched = true)
            } catch (e: PlaceSearchException) {
                search.value.copy(results = emptyList(), searching = false, searched = true, error = e.message)
            }
        }
    }

    /** 마이크 버튼: 말이 끝나면 인식된 글자로 바로 검색 (마이크 권한은 화면에서 먼저 받는다) */
    fun startVoiceSearch() {
        search.value = search.value.copy(open = true, error = null)
        voice.start { text ->
            search.value = search.value.copy(query = text)
            submitSearch(text)
        }
    }

    fun stopVoiceSearch() = voice.stop()

    /** 검색 결과를 고르면 그곳으로 길안내 시작 */
    fun selectPlace(place: PlaceResult) {
        closeSearch()
        navigator.start(NaviPlace(name = place.name, position = place.position, address = place.address))
    }

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    override fun onCleared() {
        gps.stop()
        vehicleStatus.stop()
        navigator.release()
        voice.release()
    }

    // ---- 내부 ----

    /** 연료가 부족한 다른 차가 있으면 합류 제안 (이미 경유지로 넣었거나 건너뛰면 숨김) */
    private fun meetupProposal(navi: NaviState, cars: List<CarState>, handled: Boolean): MeetupProposal? {
        if (!navi.guiding || handled) return null
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

    private fun loadThemeMode(): ThemeMode {
        val saved = prefs.getString(KEY_THEME, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.AUTO
    }

    private companion object {
        const val GROUP_ID = "demo"
        const val PREFS_UI = "navi_ui"
        const val KEY_THEME = "theme_mode"
        /** 1초 사이 이보다 멀리 움직이면 위치 점프로 본다 (≈ 360km/h) */
        const val MAX_GPS_STEP_M = 100.0
    }
}
