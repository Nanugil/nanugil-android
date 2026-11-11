package com.nanugil.app.ui.navi

import ai.pleos.playground.vehicle.Vehicle
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
import com.nanugil.app.data.mock.RouteFollowingGroupRepository
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.navi.NaviController
import com.nanugil.app.data.navi.NaviPlace
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.route.NaverDirectionsClient
import com.nanugil.app.data.voice.VoiceSearch
import com.nanugil.app.data.vehicle.VehicleGearWatcher
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
    private val vehicleStatus = VehicleStatusSource(app)
    /** 기어(P단 여부) — 입력 화면을 정차 중에만 열기 위해 본다. 정산 화면(#14)과 같은 [VehicleGearWatcher] */
    private val gearVehicle = Vehicle(app)
    private val gearWatcher = VehicleGearWatcher(gearVehicle)
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
    private val voice = VoiceSearch(app)
    private val search = MutableStateFlow(SearchUiState())

    // ---- 화면 전용 상태 ----
    /** 합류 제안을 수락하거나 건너뛰면 다시 띄우지 않는다 */
    private val meetupHandled = MutableStateFlow(false)
    /** 내 차 연료 부족 → 휴게소 제안 카드 진행 단계 */
    private val restStopPhase = MutableStateFlow(RestStopPhase.NONE)
    /** 음성 인식이 합류 팝업 답변용인지 (아니면 목적지 검색용) */
    private val answering = MutableStateFlow(false)
    /** 와이어프레임 v5 화면 흐름. TODO(서버): 그룹·초대 코드·멤버·송금 상태를 서버와 동기화 */
    private val groupUi = MutableStateFlow(GroupUiState())
    private val themeMode = MutableStateFlow(loadThemeMode())

    /**
     * 일행 차 위치. 지금은 B차가 내 실제 내비 경로 위를 앞서 달리는 목업.
     * TODO(서버): #15 머지 후 SupabaseGroupLocationRepository로 바꾼다 (같은 GroupLocationRepository).
     */
    private val groupRepository: GroupLocationRepository = RouteFollowingGroupRepository(
        myPosition = { navi.state.value.currentPosition },
        route = { _routePath.value },
        guiding = { navi.state.value.guiding },
    )
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

    private val withGroup = combine(baseState, groupUi, gearWatcher.gear) { base, g, gear ->
        base.copy(group = g, gear = gear)
    }

    val uiState: StateFlow<NaviDriveUiState> =
        combine(withGroup, search, voice.state, restStopPhase, answering) { base, s, v, phase, answer ->
            base.copy(
                search = if (answer) s else s.copy(listening = v.listening, partial = v.partial, error = v.error ?: s.error),
                suggestion = restStopSuggestion(base, phase),
                answerListening = answer && v.listening,
                answerHeard = if (answer) v.partial else "",
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NaviDriveUiState(themeMode = themeMode.value))

    init {
        navi.connect()
        vehicleStatus.start(viewModelScope)
        gearVehicle.initialize()
        gearWatcher.start()
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

    init {
        // 내장 내비가 "목적지 도착"을 알리면 08 도착(정산) 화면을 연다.
        // TODO(정산): 시나리오 7단계대로 P단(주차)일 때만 연다 (Vehicle SDK 기어 상태)
        viewModelScope.launch {
            var wasGuiding = false
            navi.state.collect { s ->
                if (wasGuiding && !s.guiding && s.message == "목적지 도착") {
                    groupUi.value = groupUi.value.copy(screen = AppScreen.ARRIVAL)
                }
                wasGuiding = s.guiding
            }
        }
    }

    // ---- 와이어프레임 v5 화면 흐름 ----

    private fun go(screen: AppScreen) {
        groupUi.value = groupUi.value.copy(screen = screen)
    }

    /** 위쪽 탭: 드라이브(안내 중이면 주행 화면, 아니면 홈) · 내 차 · 정산(보냈으면 보낸 화면) */
    fun selectTab(tab: NaviTab) {
        val g = groupUi.value
        go(
            when (tab) {
                NaviTab.DRIVE -> if (navi.state.value.guiding) AppScreen.DRIVE else AppScreen.HOME
                NaviTab.CAR -> AppScreen.CAR
                NaviTab.SETTLE -> if (g.screen == AppScreen.SETTLE_SENT) AppScreen.SETTLE_SENT else AppScreen.ARRIVAL
            },
        )
    }

    fun back() {
        go(
            when (groupUi.value.screen) {
                AppScreen.JOIN_SETUP -> AppScreen.JOIN_CODE
                else -> AppScreen.HOME
            },
        )
    }

    fun openDrive() = go(AppScreen.DRIVE)

    fun openCreateGroup() = go(AppScreen.CREATE_GROUP)

    fun openJoin() {
        groupUi.value = groupUi.value.copy(screen = AppScreen.JOIN_CODE, join = JoinState())
    }

    /** 홈 "내 그룹"에서 고르면 그 그룹 대기실 (목업: MT 그룹) */
    fun openGroup(summary: GroupSummary) {
        val group = groupUi.value.group?.takeIf { it.name == summary.name }
            ?: MockGroups.mtGroup.copy(name = summary.name, logo = summary.logo)
        groupUi.value = groupUi.value.copy(group = group, screen = AppScreen.LOBBY)
    }

    fun updateCreateForm(form: CreateGroupForm) {
        groupUi.value = groupUi.value.copy(createForm = form)
    }

    /** [만들고 코드 받기]: 나를 방장(운전)으로 넣고 초대 코드를 만든다 */
    fun createGroup() {
        val form = groupUi.value.createForm
        val me = GroupMember(form.myName.ifBlank { "나" }, driver = true, host = true, car = "A차")
        val group = GroupInfo(
            name = form.name.trim(),
            destinationName = navi.state.value.destinationName ?: MockGroups.DESTINATION,
            code = MockGroups.newCode(),
            logo = form.logo,
            members = listOf(me) + MockGroups.mtGroup.members.drop(1),
            cars = 2,
        )
        groupUi.value = groupUi.value.copy(group = group, screen = AppScreen.LOBBY)
    }

    fun newInviteCode() {
        val g = groupUi.value.group ?: return
        groupUi.value = groupUi.value.copy(group = g.copy(code = MockGroups.newCode()))
    }

    /** [동승자 추가] (목업: 다음 사람이 들어온다) */
    fun addPassenger() {
        val g = groupUi.value.group ?: return
        val next = MockGroups.extraPassengers.firstOrNull { c -> g.members.none { it.name == c.name } } ?: return
        groupUi.value = groupUi.value.copy(group = g.copy(members = g.members + next))
    }

    /** [운행 시작]: 그룹 목적지로 내장 내비 안내를 시작하고 주행 화면으로 */
    fun startTrip() {
        groupUi.value = groupUi.value.copy(tripStarted = true, screen = AppScreen.DRIVE)
        startRoute()
    }

    fun typeCode(ch: Char) {
        val j = groupUi.value.join
        if (j.code.length >= MockGroups.CODE_LENGTH) return
        val code = j.code + ch
        // TODO(서버): 코드로 그룹을 찾는다. 지금은 6자리가 되면 시연용 MT 그룹을 찾은 것으로 한다.
        val found = if (code.length == MockGroups.CODE_LENGTH) MockGroups.mtGroup.copy(members = MockGroups.mtGroup.members.take(3)) else null
        groupUi.value = groupUi.value.copy(join = j.copy(code = code, found = found))
    }

    fun deleteCode() {
        val j = groupUi.value.join
        groupUi.value = groupUi.value.copy(join = j.copy(code = j.code.dropLast(1), found = null))
    }

    /** [참여하기] → 05 운행 참가 */
    fun joinGroup() {
        val j = groupUi.value.join
        if (j.found == null) return
        groupUi.value = groupUi.value.copy(
            screen = AppScreen.JOIN_SETUP,
            join = j.copy(passengers = MockGroups.joinPassengers),
        )
    }

    fun setCarName(name: String) {
        val j = groupUi.value.join
        groupUi.value = groupUi.value.copy(join = j.copy(carName = name))
    }

    fun togglePassenger(name: String) {
        val j = groupUi.value.join
        groupUi.value = groupUi.value.copy(
            join = j.copy(
                passengers = j.passengers.map { c ->
                    if (c.member.name == name && c.takenBy == null) c.copy(selected = !c.selected) else c
                },
            ),
        )
    }

    /** [동승자 n명과 출발]: 찾은 그룹으로 운행을 시작한다 */
    fun departJoin() {
        val j = groupUi.value.join
        groupUi.value = groupUi.value.copy(group = j.found, tripStarted = true, screen = AppScreen.DRIVE)
        startRoute()
    }

    fun setPriceBasis(basis: PriceBasis) {
        groupUi.value = groupUi.value.copy(settlement = MockGroups.settlement(basis))
    }

    /** [정산 요청] TODO(정산): 정산 담당 화면·송금 요청과 연결 */
    fun requestSettlement() = go(AppScreen.SETTLE_SENT)

    fun goHome() = go(AppScreen.HOME)

    fun startRoute() {
        // TODO(서버): 그룹 목적지를 서버에서 받는다. 지금은 데모용 고정 목적지.
        val name = groupUi.value.group?.destinationName ?: MockGroups.DESTINATION
        navi.startRoute(NaviPlace(name = name, position = MockGroupDriveData.DESTINATION))
    }

    fun stopRoute() = navi.cancelRoute()

    /** 화면이 다시 보일 때: 내비 화면에서 안내를 바꾸고 돌아왔을 수 있으니 상태를 다시 맞춘다 */
    fun onScreenVisible() = navi.refreshRoute()

    /** 화면이 가려질 때: 방금 보낸 요청 때문에 내비가 튀어나온 거라면 바로 돌아온다 */
    fun onScreenHidden() = navi.onAppHidden()

    // ---- 시나리오 4단계: 내 차 연료 부족 → 휴게소 정차 제안 ----

    /** [제안]: 그룹에 휴게소 정차를 제안한다. 다른 차가 [합류]하면 두 차 모두 경유지가 추가된다. */
    fun proposeRestStop() {
        if (restStopPhase.value != RestStopPhase.NONE) return
        restStopPhase.value = RestStopPhase.SENT
        // TODO(서버): 제안을 그룹 전체에 푸시하고, 다른 차의 [합류] 응답을 받으면 아래를 실행한다.
        // 서버가 붙기 전까지는 다른 차가 몇 초 뒤 [합류]했다고 치고 시연한다.
        viewModelScope.launch {
            delay(DEMO_REPLY_DELAY_MS)
            if (restStopPhase.value != RestStopPhase.SENT) return@launch
            restStopPhase.value = RestStopPhase.HANDLED
            navi.addMeetupStop(NaviPlace(MockGroupDriveData.MEETUP_NAME, MockGroupDriveData.MEETUP_POSITION))
        }
    }

    /** [괜찮아요]: 이번 여행에서는 다시 묻지 않는다 */
    fun dismissRestStop() {
        restStopPhase.value = RestStopPhase.HANDLED
    }

    // ---- 시나리오 5·6단계: 다른 차의 제안 팝업 → [합류] 하면 경유지 추가 ----

    /**
     * 합류 팝업에 말로 답한다 ("합류", "좋아" → 합류 / "괜찮아", "아니" → 거절).
     * 마이크 권한은 화면에서 먼저 확인한다.
     */
    fun listenForMeetupAnswer() {
        if (uiState.value.proposal == null || voice.state.value.listening) return
        answering.value = true
        voice.start { text ->
            answering.value = false
            when {
                DECLINE_WORDS.any { it in text } -> skipMeetup()
                ACCEPT_WORDS.any { it in text } -> acceptMeetup()
            }
        }
    }

    private fun stopAnswering() {
        if (answering.value) {
            voice.stop()
            answering.value = false
        }
    }

    /** [합류]: 그 휴게소를 내장 내비의 첫 번째 경유지로 넣고 재탐색 (내비 화면이 잠깐 떴다가 돌아온다) */
    fun acceptMeetup() {
        stopAnswering()
        meetupHandled.value = true
        // TODO(서버): 합류 수락을 그룹 전체에 푸시 → 각 차량이 같은 경유지를 추가
        navi.addMeetupStop(NaviPlace(MockGroupDriveData.MEETUP_NAME, MockGroupDriveData.MEETUP_POSITION))
    }

    fun skipMeetup() {
        stopAnswering()
        meetupHandled.value = true
    }

    /** 주유소 하나를 경유지로 추가 (주유소 목록 화면 등에서 부른다) */
    fun addFuelStop(name: String, position: Wgs84Point) = navi.addMeetupStop(NaviPlace(name, position))

    // ---- 목적지 검색 ----

    fun openSearch() {
        search.value = search.value.copy(open = true, error = null)
    }

    fun closeSearch() {
        voice.stop()
        search.value = SearchUiState()
    }

    fun onQueryChange(query: String) {
        voice.clearError()
        search.value = search.value.copy(query = query, error = null)
    }

    /**
     * 검색은 내장 내비가 한다: 검색어로 내비 검색 화면을 열고, 운전자가 결과를 고르면 내비가 안내를 시작하고
     * 그 순간 나누길 화면으로 돌아온다. (내비 SDK는 검색 결과를 앱에 돌려주지 않아서 결과 목록은 내비 화면에서 고른다)
     */
    fun submitSearch(query: String = search.value.query) {
        val q = query.trim()
        if (q.isEmpty()) return
        closeSearch()
        navi.searchPlace(q)
    }

    /** 마이크 버튼: 말이 끝나면 인식된 글자로 바로 내비 검색 (마이크 권한은 화면에서 먼저 받는다) */
    fun startVoiceSearch() {
        search.value = search.value.copy(open = true, error = null)
        voice.start { text ->
            search.value = search.value.copy(query = text)
            submitSearch(text)
        }
    }

    fun stopVoiceSearch() = voice.stop()

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    override fun onCleared() {
        vehicleStatus.stop()
        gearVehicle.release()
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

    /** 내 차 연료(배터리)가 부족하고 안내 중이면 휴게소 정차 제안 카드를 띄운다 */
    private fun restStopSuggestion(base: NaviDriveUiState, phase: RestStopPhase): RestStopSuggestion? {
        if (phase == RestStopPhase.HANDLED || !base.navi.guiding) return null
        if (MockGroupDriveData.MEETUP_NAME in base.navi.waypointNames) return null
        val percent = base.vehicle.energyPercent ?: return null
        if (percent > CarState.LOW_FUEL_PERCENT && phase == RestStopPhase.NONE) return null
        // TODO(경로): 경로 위에서 가장 가까운 휴게소·주유소를 찾는다. 지금은 시연용 고정 휴게소.
        return RestStopSuggestion(
            placeName = MockGroupDriveData.MEETUP_NAME,
            distanceMeters = base.navi.currentPosition?.distanceTo(MockGroupDriveData.MEETUP_POSITION)?.toInt(),
            energyPercent = percent,
            isEv = base.vehicle.isEv,
            sent = phase == RestStopPhase.SENT,
        )
    }

    private enum class RestStopPhase { NONE, SENT, HANDLED }

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
        /** 서버 없이 시연할 때 다른 차가 [합류]로 답하기까지 걸리는 시간 */
        const val DEMO_REPLY_DELAY_MS = 3_000L
        val ACCEPT_WORDS = listOf("합류", "좋아", "네", "응", "그래", "들르")
        val DECLINE_WORDS = listOf("괜찮", "아니", "싫", "안 ", "됐어")
    }
}
