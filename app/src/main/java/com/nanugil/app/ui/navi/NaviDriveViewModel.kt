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
import com.nanugil.app.data.mock.FakeGroupLocationRepository
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.navi.NaviController
import com.nanugil.app.data.navi.NaviPlace
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.route.NaverDirectionsClient
import com.nanugil.app.data.search.NaverPlaceSearchRepository
import com.nanugil.app.data.search.PlaceResult
import com.nanugil.app.data.search.PlaceSearchException
import com.nanugil.app.data.search.PlaceSearchRepository
import com.nanugil.app.data.voice.VoiceSearch
import com.nanugil.app.data.vehicle.VehicleStatusSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 나누길 내비 화면의 상태와 동작.
 *
 * - 길안내는 차량 내장 내비가 한다(ADR-002). [NaviController]로 경로 요청·경유지 추가를 보내고,
 *   다음 회전·남은 거리·시간·통행료·현재 위치를 받아 온다. 음성 안내·재탐색도 내장 내비 몫이다.
 * - 차량 상태(Pleos Vehicle SDK), 그룹 차량, 유류비 N빵 계산과 합쳐 [uiState] 하나로 내보낸다.
 * - 합류 주유소를 경유지로 넣으면 → 내장 내비가 경로를 다시 잡고 → 남은 거리·통행료·경유지 카드가 바뀐다.
 * - Activity가 다시 만들어져도(라이트/다크 전환 등) ViewModel은 살아 있어서 길안내가 끊기지 않는다.
 *
 * 화면(Activity)은 이벤트 함수만 부르고, 데이터는 Repository를 통해서만 얻는다.
 */
class NaviDriveViewModel(app: Application) : AndroidViewModel(app) {

    // ---- 데이터 계층 ----
    // TODO(서버): 그룹 차량 위치·연료를 서버(실시간 동기화)에서 받는 구현으로 교체
    private val groupRepository: GroupLocationRepository = FakeGroupLocationRepository()
    private val vehicleStatus = VehicleStatusSource(app)
    private val tripCost = TripCostTracker(app, members = MockGroupDriveData.SHARE_MEMBERS)
    /** 내장 내비. 경로 요청·경유지 추가로 내비 화면이 앞에 나오면 이 화면으로 돌아온다. */
    private val navi = NaviController(app, returnTo = NaviDriveActivity::class.java)
    /**
     * 내장 내비 SDK는 경로 좌표를 주지 않는다. 그래서 내비 경로가 바뀔 때만(안내 시작·경유지 추가·재탐색)
     * 같은 출발·경유·목적지로 네이버 Directions 5를 한 번 불러 선을 그린다. 실제 안내 경로와 조금 다를 수 있다.
     * TODO(보안): Client Secret이 APK에 들어간다 → 출시 전 서버 프록시로 옮긴다.
     */
    private val directions = NaverDirectionsClient(BuildConfig.NAVER_MAP_CLIENT_ID, BuildConfig.NAVER_MAP_CLIENT_SECRET)
    private val _routePath = MutableStateFlow<List<Wgs84Point>>(emptyList())
    /** 지도에 그릴 경로선 (현재 위치 → 경유지 → 목적지) */
    val routePath: StateFlow<List<Wgs84Point>> = _routePath.asStateFlow()
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

    /** 화면 전체 상태. 어느 값 하나만 바뀌어도 새 스냅샷이 나온다. */
    private val baseState = combine(
        navi.state,
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
        navi.connect()
        vehicleStatus.start(viewModelScope)
        // 내장 내비에서 다음 회전·남은 거리·현재 위치를 1초마다, 목적지·경유지는 몇 초마다 다시 받는다.
        // (내비 화면에서 직접 안내를 시작·취소했을 수도 있어서 안내 중이 아닐 때도 목적지를 확인한다)
        viewModelScope.launch {
            var tick = 0
            while (true) {
                navi.requestLiveInfo()
                if (tick++ % ROUTE_REFRESH_TICKS == 0) navi.refreshRoute()
                delay(LIVE_INFO_INTERVAL_MS)
            }
        }
        // 내장 내비가 알려 준 현재 위치로 안내 중 이동 거리를 쌓는다 (주행거리계를 못 읽는 차의 유류비 계산용).
        // 위치를 내비에서 받으므로 우리 앱은 위치 권한이 필요 없다.
        viewModelScope.launch {
            var last: Wgs84Point? = null
            navi.state.map { it.guiding to it.currentPosition }.distinctUntilChanged().collect { (guiding, pos) ->
                if (guiding && pos != null) {
                    last?.let { prev ->
                        val step = prev.distanceTo(pos)
                        // 순간이동(위치 점프)은 이동 거리로 치지 않는다
                        if (step < MAX_STEP_M) tripCost.addGpsDistance(step)
                    }
                    last = pos
                } else {
                    last = null
                }
            }
        }
        // 내비 경로가 바뀌면 경로선을 다시 받는다 (위치가 움직일 때마다 부르지 않는다 → 과금 최소화)
        viewModelScope.launch {
            navi.state
                .map { s -> RouteKey(s.guiding, s.routeVersion, s.destinationPosition, s.waypointPositions, s.currentPosition != null) }
                .distinctUntilChanged()
                .collectLatest { key ->
                    val start = navi.state.value.currentPosition
                    val goal = key.destination
                    if (!key.guiding || start == null || goal == null) {
                        _routePath.value = emptyList()
                        return@collectLatest
                    }
                    delay(ROUTE_DEBOUNCE_MS) // 경유지·목적지 정보가 연달아 들어오는 동안 기다렸다 한 번만 부른다
                    _routePath.value = try {
                        directions.drivingPath(start, goal, key.waypoints)
                    } catch (e: Exception) {
                        // 키가 없거나 실패하면 직선으로라도 이어서 보여 준다
                        listOf(start) + key.waypoints + goal
                    }
                }
        }
    }

    private data class RouteKey(
        val guiding: Boolean,
        val version: Int,
        val destination: Wgs84Point?,
        val waypoints: List<Wgs84Point>,
        val hasPosition: Boolean,
    )

    // ---- 화면 이벤트 ----

    fun startRoute() {
        // TODO(서버): 그룹 목적지를 서버에서 받는다. 지금은 데모용 고정 목적지.
        navi.startRoute(NaviPlace(name = "가평 펜션", position = MockGroupDriveData.DESTINATION))
    }

    fun stopRoute() = navi.cancelRoute()

    /** 화면이 다시 보일 때: 내비 화면에서 안내를 바꾸고 돌아왔을 수 있으니 상태를 다시 맞춘다 */
    fun onScreenVisible() = navi.refreshRoute()

    /** 합류 제안 수락 → 그 주유소(휴게소)를 내장 내비의 첫 번째 경유지로 넣고 재탐색 */
    fun acceptMeetup() {
        meetupHandled.value = true
        // TODO(서버): 합류 수락을 그룹 전체에 푸시 → 각 차량이 같은 경유지를 추가
        navi.addMeetupStop(NaviPlace(MockGroupDriveData.MEETUP_NAME, MockGroupDriveData.MEETUP_POSITION))
    }

    fun skipMeetup() {
        meetupHandled.value = true
    }

    /** 주유소 하나를 경유지로 추가 (주유소 목록 화면 등에서 부른다) */
    fun addFuelStop(name: String, position: Wgs84Point) = navi.addMeetupStop(NaviPlace(name, position))

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
                val results = placeSearch.search(q, navi.state.value.currentPosition)
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

    /** 검색 결과를 고르면 내장 내비로 그곳까지 길안내 시작 */
    fun selectPlace(place: PlaceResult) {
        closeSearch()
        navi.startRoute(NaviPlace(name = place.name, position = place.position, address = place.address))
    }

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    override fun onCleared() {
        vehicleStatus.stop()
        navi.release()
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
        /** 위치 한 번 사이(≈1초) 이보다 멀리 움직이면 위치 점프로 본다 (≈ 360km/h) */
        const val MAX_STEP_M = 100.0
        const val LIVE_INFO_INTERVAL_MS = 1_000L
        /** 목적지·경유지 재조회 주기 (LIVE_INFO_INTERVAL_MS 단위) */
        const val ROUTE_REFRESH_TICKS = 5
        const val ROUTE_DEBOUNCE_MS = 1_500L
    }
}
