package com.nanugil.app.ui.navi

import ai.pleos.playground.vehicle.Vehicle
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanugil.app.BuildConfig
import com.nanugil.app.NanugilApplication
import com.nanugil.app.data.pay.PayLinkStore
import com.nanugil.app.ui.settlement.PriceChoice
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
import com.nanugil.app.data.server.EndedCars
import com.nanugil.app.data.server.Group
import com.nanugil.app.data.server.GroupTripApi
import com.nanugil.app.data.server.MyCarSnapshot
import com.nanugil.app.data.server.Place
import com.nanugil.app.data.server.ServerException
import com.nanugil.app.data.server.ServerGroupCars
import com.nanugil.app.data.server.Supabase
import com.nanugil.app.data.server.Trip
import com.nanugil.app.data.server.TripMember
import com.nanugil.app.data.voice.VoiceSearch
import com.nanugil.app.data.vehicle.VehicleGearWatcher
import com.nanugil.app.data.vehicle.VehicleStatusSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
@OptIn(ExperimentalCoroutinesApi::class)
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
    /** 정산 값(차량 기록·근처 유가·송금 요청). 값 수정 화면(SettlementActivity)과 함께 본다. */
    private val settlement = (app as NanugilApplication).settlement
    private val toll = (app as NanugilApplication).tollRecorder.also { it.reconnect() }
    private val payLinks = PayLinkStore(app)
    private val payLink = MutableStateFlow(payLinks.link)

    /**
     * 지금 들어가 있는 서버 그룹 ID. null이면 서버 없이 목업 일행 차를 보여 준다.
     * 그룹 만들기·코드 참여·운행 시작을 서버에 연결하면(이슈 #18 3번) 그 화면에서 [useServerGroup]으로 넣는다.
     */
    private val serverGroupId = MutableStateFlow<String?>(null)
    /** 서버 그룹이 없을 때: B차가 내 실제 내비 경로 위를 앞서 달리는 목업 */
    private val mockGroup: GroupLocationRepository = RouteFollowingGroupRepository(
        myPosition = { navi.state.value.currentPosition },
        route = { _routePath.value },
        guiding = { navi.state.value.guiding },
    )
    /** 서버 그룹이 있을 때: 진행 중 운행의 차들을 받고, 내 차 상태를 1초마다 보낸다 (전송은 주행 3초·정차 30초) */
    private val serverGroup = ServerGroupCars()
    /** 일행 차 (나 포함, isMe) */
    private val groupCars: StateFlow<List<CarState>> = serverGroupId
        .flatMapLatest { id ->
            when {
                id != null -> serverGroup.cars(id, ::myCarSnapshot)
                // 서버가 있으면 운행 전(홈·대기실)엔 일행 차가 없다. 목업 차는 서버 없이 시연할 때만.
                // (serverOn 프로퍼티는 아직 초기화 전이라 BuildConfig를 직접 본다)
                BuildConfig.SUPABASE_URL.isNotBlank() -> flowOf(emptyList())
                else -> mockGroup.observeGroup(GROUP_ID)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 서버로 보낼 내 차 상태. 내장 내비가 아직 위치를 모르면 null (보내지 않는다) */
    private fun myCarSnapshot(): MyCarSnapshot? {
        val n = navi.state.value
        val position = n.currentPosition ?: return null
        return MyCarSnapshot(
            position = position,
            etaMinutes = if (n.guiding) (n.remainTimeSeconds ?: 0) / 60 else 0,
            // 연료를 못 읽으면 100으로 보낸다 (0이면 다른 차 화면에 연료 부족 합류 제안이 잘못 뜬다)
            fuelPercent = vehicleStatus.status.value.energyPercent ?: 100,
        )
    }

    /** 화면 전체 상태. 어느 값 하나만 바뀌어도 새 스냅샷이 나온다. */
    private val baseState = combine(
        navi.state,
        vehicleStatus.status,
        groupCars,
        meetupHandled,
    ) { navi, vehicle, cars, handled ->
        NaviDriveUiState(
            navi = navi,
            vehicle = vehicle,
            groupCars = cars,
            proposal = meetupProposal(navi, cars, handled),
            cost = tripCost.update(navi, vehicle),
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
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NaviDriveUiState())

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
        // 08·09 화면은 정산 세션 값을 보여 준다.
        viewModelScope.launch {
            combine(settlement.state, payLink) { s, link -> s.toUi(link) }.collect { ui ->
                groupUi.value = groupUi.value.copy(settlement = ui)
            }
        }
        // 나중에 참가한 차가 보이면 주행 화면 1인 금액의 인원(탄 사람 수)을 다시 센다
        viewModelScope.launch {
            combine(serverGroupId, groupCars.map { it.size }.distinctUntilChanged()) { id, _ -> id }.collectLatest { id ->
                if (id == null) {
                    // 서버 그룹 밖이면 나 혼자. 서버 키가 없는 오프라인 시연만 데모 인원. (serverOn은 init보다 아래에 있어 아직 초기화 전이다)
                    tripCost.members = if (BuildConfig.SUPABASE_URL.isNotBlank()) 1 else MockGroupDriveData.SHARE_MEMBERS
                    return@collectLatest
                }
                try {
                    GroupTripApi.activeTrip(id)?.let { GroupTripApi.tripMembers(it.id).size }?.takeIf { it > 0 }?.let { tripCost.members = it }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "탄 사람 수 읽기 실패", e)
                }
            }
        }
        // 내장 내비가 "목적지 도착"을 알리면 08 도착(정산) 화면을 연다.
        // TODO(정산): 시나리오 7단계대로 P단(주차)일 때만 연다 (Vehicle SDK 기어 상태)
        viewModelScope.launch {
            var wasGuiding = false
            navi.state.collect { s ->
                // 경로가 바뀔 때마다 지나온 만큼의 통행료를 모은다 (정산의 내 차 통행료)
                toll.update(s)
                if (wasGuiding && !s.guiding && s.message == "목적지 도착") {
                    toll.arrive()
                    // 그룹 운행 중 목적지에 도착하면 [운행 종료]를 누른 것과 같다 (모든 차가 끝나면 다 같이 08)
                    if (groupUi.value.tripStarted) endTrip() else go(AppScreen.ARRIVAL)
                }
                wasGuiding = s.guiding
            }
        }
    }

    // ---- 와이어프레임 v5 화면 흐름 ----

    private fun go(screen: AppScreen) {
        groupUi.value = groupUi.value.copy(screen = screen, note = null)
        // 홈에 올 때마다 내 그룹(인원·운행 중)을 서버에서 다시 읽는다
        if (screen == AppScreen.HOME) refreshMyGroups()
        // 08을 처음 열 때 운행 기록·차종·내 위치 근처 유가로 정산 값을 채운다(운행마다 한 번).
        if (screen == AppScreen.ARRIVAL) viewModelScope.launch {
            // 내비가 아직 위치를 안 줬으면 잠깐 기다린다. 그래도 없으면 세션이 데모 출발지로 찾는다.
            val here = navi.state.value.currentPosition
                ?: withTimeoutOrNull(POSITION_WAIT_MS) { navi.state.first { it.currentPosition != null }.currentPosition }
            settlement.load(here)
        }
        // 08을 보는 동안 다른 차가 나중에 저장한 비용을 받아 온다. 운행을 못 읽었으면 다시 읽는다. 08을 떠나면 멈춘다.
        if (screen == AppScreen.ARRIVAL && tripRefresh?.isActive != true) tripRefresh = viewModelScope.launch {
            while (groupUi.value.screen == AppScreen.ARRIVAL) {
                delay(TRIP_REFRESH_MS)
                if (groupUi.value.screen == AppScreen.ARRIVAL) {
                    settlement.load(navi.state.value.currentPosition)
                    settlement.refreshTrip()
                }
            }
        }
    }

    private var tripRefresh: Job? = null

    /** 위쪽 탭: 드라이브(안내 중이면 주행 화면, 아니면 홈) · 내 차 · 정산(보냈으면 보낸 화면) */
    fun selectTab(tab: NaviTab) {
        val g = groupUi.value
        go(
            when (tab) {
                NaviTab.DRIVE -> if (navi.state.value.guiding) AppScreen.DRIVE else AppScreen.HOME
                NaviTab.CAR -> AppScreen.CAR
                NaviTab.SETTLE -> if (settlement.state.value.request != null) AppScreen.SETTLE_SENT else AppScreen.ARRIVAL
            },
        )
    }

    fun back() {
        // 09 정산 요청 보냄 → 08 정산으로. 보낸 요청을 거두고 값을 고쳐 다시 요청할 수 있게 한다.
        if (groupUi.value.screen == AppScreen.SETTLE_SENT) {
            settlement.cancelRequest()
            go(AppScreen.ARRIVAL)
            return
        }
        go(
            when (groupUi.value.screen) {
                // 홈 그룹 목록에서 바로 05로 왔으면(코드 입력 없이) 홈으로
                AppScreen.JOIN_SETUP -> if (groupUi.value.join.code.isEmpty()) AppScreen.HOME else AppScreen.JOIN_CODE
                else -> AppScreen.HOME
            },
        )
    }

    fun openDrive() = go(AppScreen.DRIVE)

    fun openCreateGroup() = go(AppScreen.CREATE_GROUP)

    fun openJoin() {
        groupUi.value = groupUi.value.copy(screen = AppScreen.JOIN_CODE, join = JoinState(myName = savedName), note = null)
    }

    /** 홈 "내 그룹"에서 고르면: 방장은 03 대기실, 멤버는 05 운행 참가 (목업 그룹이면 MT 그룹 대기실) */
    fun openGroup(summary: GroupSummary) {
        val id = summary.id
        if (id == null) {
            val group = groupUi.value.group?.takeIf { it.name == summary.name }
                ?: MockGroups.mtGroup.copy(name = summary.name, logo = summary.logo)
            groupUi.value = groupUi.value.copy(group = group, screen = AppScreen.LOBBY)
            return
        }
        serverAction {
            val l = loadGroup(id)
            if (l.info.host?.id == l.myMemberId) {
                groupUi.update { it.copy(group = l.info, screen = AppScreen.LOBBY) }
            } else {
                showJoinSetup(l, JoinState(myName = savedName))
            }
        }
    }

    fun updateCreateForm(form: CreateGroupForm) {
        groupUi.value = groupUi.value.copy(createForm = form)
    }

    /** [만들고 코드 받기]: 나를 방장(운전)으로 넣고 초대 코드를 만든다 */
    fun createGroup() {
        val form = groupUi.value.createForm
        if (serverOn) {
            val n = navi.state.value
            // 목적지는 내비에서 고른 곳만 쓴다. 안 골랐으면 데모 좌표를 몰래 넣지 않고 고르라고 한다 (#32-5)
            val at = n.destinationPosition
            val name = n.destinationName
            if (at == null || name == null) {
                groupUi.update { it.copy(note = "목적지를 먼저 검색해서 고르세요") }
                return
            }
            val myName = form.myName.trim()
            saveName(myName)
            serverAction {
                val g = GroupTripApi.createGroup(form.name.trim(), Place(name, at.latitude, at.longitude), myName, logoUrl = form.logo.name)
                groupUi.update { it.copy(group = loadGroup(g.id, g).info, screen = AppScreen.LOBBY) }
                refreshMyGroups()
            }
            return
        }
        saveName(form.myName.trim())
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
        val id = g.id ?: run {
            groupUi.value = groupUi.value.copy(group = g.copy(code = MockGroups.newCode()))
            return
        }
        serverAction {
            val code = GroupTripApi.resetInviteCode(id).code
            groupUi.update { s -> s.copy(group = s.group?.copy(code = code)) }
        }
    }

    fun setPassengerName(name: String) {
        groupUi.update { it.copy(passengerName = name) }
    }

    /** [동승자 추가]: 03에 넣은 이름으로 앱 없는 동승자를 그룹에 넣는다 (#32-10). */
    fun addPassenger() {
        val g = groupUi.value.group ?: return
        val name = groupUi.value.passengerName.trim().ifEmpty { return }
        val id = g.id ?: run {
            groupUi.value = groupUi.value.copy(group = g.copy(members = g.members + GroupMember(name, driver = false)), passengerName = "")
            return
        }
        serverAction {
            GroupTripApi.addPassenger(id, name)
            groupUi.update { it.copy(group = loadGroup(id).info, passengerName = "") }
        }
    }

    /**
     * [운행 시작] (방장): 서버 운행을 시작(이미 있으면 그 운행)하고 내 차를 등록한다.
     * 03에서 추가한 앱 없는 동승자 중 아직 아무 차에도 안 탄 사람은 내 차에 태운다.
     * 그다음 그룹 목적지로 내장 내비 안내를 시작하고 주행 화면으로.
     */
    fun startTrip() {
        val id = groupUi.value.group?.id ?: run {
            groupUi.value = groupUi.value.copy(tripStarted = true, screen = AppScreen.DRIVE)
            startRoute()
            return
        }
        serverAction {
            val trip = GroupTripApi.activeTrip(id) ?: GroupTripApi.startTrip(id)
            GroupTripApi.joinTrip(trip.id, HOST_CAR_LABEL, fuelTypeOf(nanugil.readMyCarType()))
            val l = loadGroup(id)
            val mine = passengerChoices(l.info, l.riders, l.myMemberId)
                .filter { it.takenBy == null && (it.selected || !it.member.driver) }
                .mapNotNull { it.member.id }
            GroupTripApi.setTripPassengers(trip.id, mine)
            enterTrip(loadGroup(id))
        }
    }

    /** 서버 운행에 들어갔다: 일행 차를 서버에서 받고, 그룹 목적지로 안내를 시작한다. */
    private fun enterTrip(l: LoadedGroup) {
        val info = l.info
        l.trip?.let { settlement.startTrip(it.id) }
        groupUi.update { it.copy(group = info, tripStarted = true, endWait = null, screen = AppScreen.DRIVE) }
        useServerGroup(info.id)
        startRoute()
    }

    /**
     * [운행 종료] 버튼 (각 차). 아직 안내 중이면(목적지 전) 실수일 수 있어 "정산하시겠습니까?"를 먼저 묻는다.
     * 그룹 운행이 아니면(혼자 검색한 목적지) 묻지 않고 길안내만 끈다.
     */
    fun endTrip() {
        val g = groupUi.value
        when {
            !g.tripStarted -> navi.cancelRoute()
            navi.state.value.guiding -> groupUi.update { it.copy(confirmEnd = true) }
            else -> finishMyCar()
        }
    }

    /** 확인 창 [정산하기] */
    fun confirmEndTrip() {
        groupUi.update { it.copy(confirmEnd = false) }
        finishMyCar()
    }

    /** 확인 창 [계속 주행] */
    fun cancelEndTrip() = groupUi.update { it.copy(confirmEnd = false) }

    /** 기다림 팝업 오른쪽 위 X: 팝업을 닫고 아래 작은 상자로만 보인다 */
    fun minimizeEndWait() = groupUi.update { s -> s.copy(endWait = s.endWait?.copy(minimized = true)) }

    /**
     * 내 차 운행을 지금 끝낸다: 길안내를 끄고, 서버에 운행 종료를 남기고(T6 arrived_at), 내 차 정산 값(연료·통행료)을 이 순간 값으로 채운다.
     * 통행료는 [TollLedger]가 지나온 만큼만 센다 (목적지 전이면 남은 유료 구간은 빠진다).
     * 모든 차가 끝내면 [endedCars] 감시가 모두를 08로 보낸다. 서버 없는 시연이면 바로 08.
     */
    private fun finishMyCar() {
        navi.cancelRoute()
        val id = groupUi.value.group?.id ?: return onTripEnded()
        serverAction {
            val trip = GroupTripApi.activeTrip(id) ?: return@serverAction onTripEnded() // 이미 끝난 운행
            GroupTripApi.markArrived(trip.id)
            groupUi.update { it.copy(endWait = EndWait(0, 0)) }
            showEndWaiting(serverGroup.endedCars.value)
            // 08을 열 때 다시 부르면 이미 채워 둔 값을 그대로 쓴다 (운행마다 한 번)
            viewModelScope.launch { settlement.load(navi.state.value.currentPosition) }
        }
    }

    private fun showEndWaiting(e: EndedCars) {
        val w = groupUi.value.endWait ?: return
        if (!groupUi.value.tripStarted || e.allEnded) return
        // 방금 누른 내 차가 아직 서버 목록에 안 잡혔을 수 있어 최소 1대로 센다
        groupUi.update { it.copy(endWait = w.copy(ended = maxOf(e.ended, 1), total = maxOf(e.total, 1))) }
    }

    /** 운행이 끝났다 (모든 차가 [운행 종료]를 눌렀거나 서버 운행이 끝남): 길안내를 끄고 08 정산으로 */
    private fun onTripEnded() {
        if (!groupUi.value.tripStarted) return
        groupUi.update { it.copy(tripStarted = false, confirmEnd = false, endWait = null) }
        navi.cancelRoute()
        go(AppScreen.ARRIVAL)
    }

    fun typeCode(ch: Char) {
        val j = groupUi.value.join
        if (j.code.length >= MockGroups.CODE_LENGTH) return
        val code = j.code + ch
        groupUi.value = groupUi.value.copy(join = j.copy(code = code, found = null), note = null)
        if (code.length < MockGroups.CODE_LENGTH) return
        if (!serverOn) {
            // 서버 없이: 6자리가 되면 시연용 MT 그룹을 찾은 것으로 한다
            val found = MockGroups.mtGroup.copy(members = MockGroups.mtGroup.members.take(3))
            groupUi.update { it.copy(join = it.join.copy(found = found)) }
            return
        }
        // 참여 전에는 이름·인원만 볼 수 있다 (I3). 방장·목적지는 참여하면 보인다.
        serverAction {
            val p = GroupTripApi.previewInvite(code)
            val found = GroupInfo(
                name = p.groupName, destinationName = "참여하면 보여요", code = code, logo = GroupLogo.CAR,
                members = emptyList(), cars = 0, id = p.groupId, memberCount = p.memberCount,
            )
            groupUi.update { s -> if (s.join.code == code) s.copy(join = s.join.copy(found = found)) else s }
        }
    }

    fun deleteCode() {
        val j = groupUi.value.join
        groupUi.value = groupUi.value.copy(join = j.copy(code = j.code.dropLast(1), found = null), note = null)
    }

    fun setJoinName(name: String) {
        groupUi.update { it.copy(join = it.join.copy(myName = name)) }
    }

    /** [참여하기] → 05 운행 참가. 서버면 그룹에 들어가고(이미 멤버면 그대로) 멤버·운행을 읽는다. */
    fun joinGroup() {
        val j = groupUi.value.join
        val found = j.found ?: return
        val id = found.id ?: run {
            groupUi.value = groupUi.value.copy(screen = AppScreen.JOIN_SETUP, join = j.copy(passengers = MockGroups.joinPassengers))
            return
        }
        saveName(j.myName.trim())
        serverAction {
            try {
                GroupTripApi.joinGroup(j.code, j.myName.trim())
            } catch (e: ServerException) {
                if (e.code != "ALREADY_MEMBER") throw e
            }
            showJoinSetup(loadGroup(id), j)
            refreshMyGroups()
        }
    }

    /**
     * 05 운행 참가를 서버 그룹 값으로 채운다. 운행 전이면 [출발]을 잠그고, 서버 알림으로 다시 채운다(#32-3).
     * 다시 채울 때는 내가 이미 고른 동승자를 그대로 둔다(남의 차에 탔으면 풀린다).
     */
    private suspend fun showJoinSetup(l: LoadedGroup, j: JoinState) {
        val fuel = nanugil.readMyCarType()?.label ?: j.fuelType
        val picked = j.passengers.filter { it.selected }.mapNotNull { it.member.id }.toSet()
        val choices = passengerChoices(l.info, l.riders, l.myMemberId)
            .map { c -> if (c.takenBy == null && c.member.id in picked) c.copy(selected = true) else c }
        groupUi.update {
            it.copy(
                screen = AppScreen.JOIN_SETUP,
                join = j.copy(
                    found = l.info,
                    passengers = choices,
                    fuelType = fuel,
                    carName = freeCarName(j.carName, l.riders.filter { r -> r.isDriver && r.memberId != l.myMemberId }.mapNotNull { r -> r.vehicleLabel }),
                    tripStarted = l.trip != null,
                ),
                note = null,
            )
        }
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
        val id = j.found?.id ?: run {
            groupUi.value = groupUi.value.copy(group = j.found, tripStarted = true, screen = AppScreen.DRIVE)
            startRoute()
            return
        }
        serverAction {
            val trip = GroupTripApi.activeTrip(id) ?: throw ServerException("TRIP_NOT_ACTIVE")
            GroupTripApi.joinTrip(trip.id, j.carName.ifBlank { "B차" }.trim(), fuelTypeOf(nanugil.readMyCarType()))
            GroupTripApi.setTripPassengers(trip.id, j.passengers.filter { it.selected }.mapNotNull { it.member.id })
            enterTrip(loadGroup(id))
        }
    }

    fun setPriceBasis(basis: PriceBasis) =
        settlement.choose(if (basis == PriceBasis.AVERAGE) PriceChoice.NEARBY_AVERAGE else PriceChoice.NEARBY_LOWEST)

    /** [정산 요청]: 정산 결과를 확정해 09로 간다. 값이 틀려 정산할 수 없으면 그대로 둔다(08에 고칠 내용이 보인다). */
    fun requestSettlement() {
        if (settlement.request() == null) return
        go(AppScreen.SETTLE_SENT)
        // 다른 차 앱이 같은 정산을 계산하도록 내 차 비용을 서버에 올린다
        viewModelScope.launch { settlement.saveMyCost() }
    }

    /** 09에서 송금 한 줄을 누르면 받음 체크를 바꾼다 */
    fun togglePaid(index: Int) = settlement.togglePaid(index)

    /** 서버 그룹에 들어가면 그 ID를, 나가면 null(목업으로 돌아감)을 넣는다. 일행 차를 서버에서 받기 시작한다. */
    fun useServerGroup(groupId: String?) {
        serverGroupId.value = groupId
        nanugil.serverGroupId = groupId // 정산이 이 그룹의 운행을 읽는다
    }

    /**
     * 09 [홈으로]. 모두 받았으면 이번 정산을 끝내고 다음 운행을 준비한다.
     * 서버 그룹이면 방장이 운행을 끝내고(T4), 일행 차는 목업으로 돌아간다.
     */
    fun goHome() {
        if (settlement.state.value.request?.isComplete == true) {
            settlement.finish()
            val g = groupUi.value.group
            if (g?.id != null) {
                serverAction {
                    GroupTripApi.activeTrip(g.id)?.let { trip ->
                        try {
                            GroupTripApi.endTrip(trip.id)
                        } catch (e: ServerException) {
                            if (e.code != "NOT_OWNER") throw e // 방장이 아니면 끝낼 수 없다 (방장 기기가 끝낸다)
                        }
                    }
                    // 홈은 끝내기 전에 목록을 읽었다. 끝난 뒤 다시 읽어야 "운행 중"이 사라진다.
                    refreshMyGroups()
                }
                useServerGroup(null)
                groupUi.update { it.copy(group = null, tripStarted = false) }
            }
        }
        go(AppScreen.HOME)
    }

    fun startRoute() {
        val g = groupUi.value.group
        navi.startRoute(
            NaviPlace(
                name = g?.destinationName ?: MockGroups.DESTINATION,
                position = g?.destination ?: MockGroupDriveData.DESTINATION,
            ),
        )
    }

    // ---- 서버 그룹 (#18 3번) ----

    private val serverOn = BuildConfig.SUPABASE_URL.isNotBlank()
    private val nanugil get() = getApplication<NanugilApplication>()

    /** 한 번 넣은 내 이름 (02 그룹 만들기·04 코드 참여에 같이 쓴다, #32-4) */
    private val profile = app.getSharedPreferences("group_profile", Context.MODE_PRIVATE)
    private val savedName: String get() = profile.getString(KEY_MY_NAME, "").orEmpty()
    private fun saveName(name: String) {
        if (name.isNotBlank()) profile.edit().putString(KEY_MY_NAME, name).apply()
        groupUi.update { it.copy(createForm = it.createForm.copy(myName = name.ifBlank { it.createForm.myName })) }
    }

    // serverOn을 정한 뒤에 부른다 (프로퍼티는 적힌 순서대로 초기화된다)
    init {
        groupUi.update {
            it.copy(
                createForm = it.createForm.copy(myName = savedName),
                // 서버를 쓰면 목업 그룹·정산을 보여 주지 않는다. 못 읽으면 빈 목록 (#32-9)
                myGroups = if (serverOn) emptyList() else it.myGroups,
                recent = if (serverOn) emptyList() else it.recent,
            )
        }
        refreshMyGroups(restore = true)
        // 03 대기실·05 운행 참가를 보는 동안 서버 변화(운행 시작, 차 등록·동승자)를 받아 다시 채운다 (#32-3).
        // 그룹 참여는 알림이 없어 tripChanges의 15초 재확인으로 반영된다.
        viewModelScope.launch {
            groupUi.map { s ->
                when (s.screen) {
                    AppScreen.LOBBY -> s.group?.id
                    AppScreen.JOIN_SETUP -> s.join.found?.id
                    else -> null
                }
            }.distinctUntilChanged().collectLatest { id ->
                if (id == null || !serverOn) return@collectLatest
                try {
                    GroupTripApi.tripChanges(id).collect { reloadGroupScreen(id) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "그룹 알림 구독 실패", e)
                }
            }
        }
        // 모든 차가 [운행 종료]를 누르면 다 같이 08로. 그 전엔 누른 차에 기다림 안내(몇 대 끝났는지)를 갱신한다.
        viewModelScope.launch {
            serverGroup.endedCars.collect { e -> if (e.allEnded) onTripEnded() else showEndWaiting(e) }
        }
        // 서버 운행 자체가 끝나도(ended_at) 남은 차는 길안내를 끄고 08로 간다
        viewModelScope.launch {
            var last: String? = null
            serverGroup.activeTripId.collect { id ->
                if (last != null && id == null && serverGroupId.value != null) onTripEnded()
                last = id
            }
        }
    }

    /** 03·05를 서버 값으로 다시 채운다. 실패해도 다음 알림에 다시 해 본다. */
    private suspend fun reloadGroupScreen(id: String) {
        try {
            val l = loadGroup(id)
            val s = groupUi.value
            when {
                s.screen == AppScreen.LOBBY && s.group?.id == id -> groupUi.update { it.copy(group = l.info) }
                s.screen == AppScreen.JOIN_SETUP && s.join.found?.id == id -> showJoinSetup(l, s.join)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "그룹 다시 읽기 실패", e)
        }
    }

    private data class LoadedGroup(val info: GroupInfo, val trip: Trip?, val riders: List<TripMember>, val myMemberId: String?)

    /** 그룹·멤버·진행 중 운행을 한 번에 읽는다. [group]을 이미 갖고 있으면 다시 읽지 않는다. */
    private suspend fun loadGroup(groupId: String, group: Group? = null): LoadedGroup {
        val me = Supabase.ensureSignedIn()
        val g = group ?: GroupTripApi.myGroups().first { it.group.id == groupId }.group
        val members = GroupTripApi.members(groupId)
        val trip = GroupTripApi.activeTrip(groupId)
        val riders = trip?.let { GroupTripApi.tripMembers(it.id) }.orEmpty()
        val myMemberId = members.firstOrNull { it.userId == me }?.id
        return LoadedGroup(groupInfoOf(g, members, riders).copy(myId = myMemberId), trip, riders, myMemberId)
    }

    /**
     * 홈 "내 그룹"·"최근 정산"을 서버 값으로. 실패하면 지금 목록을 그대로 둔다.
     * 들어가 있던 운행이 (방장이) 끝났으면 진행 중 표시를 내린다 (#32-6).
     * 읽는 중에 다시 부르면 앞의 읽기를 취소한다. 늦게 끝난 앞의 읽기가 운행 종료 전 상태로 덮어쓰지 않게 하려는 것이다.
     *
     * @param restore 앱을 켤 때만 true. 내가 탄 진행 중 운행이 있으면 그 운행으로 돌아간다 (#32-11).
     *   홈에 올 때마다 하면 정산을 끝내고 [홈으로]를 누른 참여자가 (방장이 아직 안 끝낸) 그 운행으로 다시 끌려간다.
     */
    private var refreshJob: Job? = null
    /** 앱을 켤 때의 복귀를 아직 못 했는지. 그사이 다른 읽기가 앞의 읽기를 취소해도 복귀는 이어서 한다. */
    private var restorePending = false

    private fun refreshMyGroups(restore: Boolean = false) {
        if (!serverOn) return
        if (restore) restorePending = true
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val groups = GroupTripApi.myGroups()
                val names = groups.associate { mg -> mg.group.id to GroupTripApi.members(mg.group.id).associate { it.id to it.displayName } }
                val list = groups.map { mg ->
                    GroupSummary(
                        name = mg.group.name,
                        memberCount = names[mg.group.id].orEmpty().size,
                        logo = logoOf(mg.group.logoUrl),
                        driving = mg.activeTrip != null,
                        id = mg.group.id,
                        destination = mg.group.destinationName,
                    )
                }
                val groupNames = groups.associate { it.group.id to it.group.name }
                val recent = GroupTripApi.endedTrips(RECENT_COUNT).map { t ->
                    recentSettlementOf(groupNames[t.groupId].orEmpty(), t.endedAt.orEmpty(), GroupTripApi.tripMembers(t.id), names[t.groupId].orEmpty())
                }
                groupUi.update { it.copy(myGroups = list, recent = recent) }

                val current = groupUi.value.group?.id
                if (current != null && groupUi.value.tripStarted && groups.none { it.group.id == current && it.activeTrip != null }) {
                    useServerGroup(null)
                    groupUi.update { it.copy(group = null, tripStarted = false) }
                } else if (restorePending && current == null) {
                    // 앱을 다시 켰을 때: 내가 탄 진행 중 운행이 있으면 그 그룹으로 돌아간다 (길안내는 내비가 이어 간다)
                    groups.filter { it.activeTrip != null }.firstNotNullOfOrNull { mg ->
                        loadGroup(mg.group.id, mg.group).takeIf { l -> l.riders.any { it.memberId == l.myMemberId } }
                    }?.let { l ->
                        // enterTrip과 같이 정산도 이 운행으로 맞춘다 (#33). 화면 이동·길안내 시작은 하지 않는다.
                        l.trip?.let { settlement.startTrip(it.id) }
                        groupUi.update { it.copy(group = l.info, tripStarted = true) }
                        useServerGroup(l.info.id)
                    }
                }
                restorePending = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "내 그룹 읽기 실패", e)
            }
        }
    }

    /** 서버 요청을 보낸다. 실패하면 화면 아래에 안내를 띄우고 지금 화면에 머문다. */
    private fun serverAction(block: suspend () -> Unit) {
        groupUi.update { it.copy(note = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "그룹 서버 요청 실패", e)
                groupUi.update { it.copy(note = groupErrorText(e)) }
            }
        }
    }

    /** 화면이 다시 보일 때: 내비 화면에서 안내를 바꾸고 돌아왔을 수 있으니 상태를 다시 맞춘다 */
    fun onScreenVisible() {
        navi.refreshRoute()
        // 송금 링크 등록 화면에서 돌아왔을 수 있다
        payLink.value = payLinks.link
    }

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
            carLabel = car.label + "차",
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


    private companion object {
        const val TAG = "NaviDriveViewModel"
        /** 방장 차 이름 (03에는 차 이름 칸이 없다). 05에서 들어오는 차는 기본 "B차". */
        const val HOST_CAR_LABEL = "A차"
        const val KEY_MY_NAME = "my_name"
        /** 홈 "최근 정산"에 보여 줄 끝난 운행 수 */
        const val RECENT_COUNT = 3L
        const val GROUP_ID = "demo"
        /** 08을 열 때 내비 현재 위치를 기다리는 최대 시간 */
        const val POSITION_WAIT_MS = 5_000L
        /** 08에서 운행(다른 차 비용)을 다시 읽는 간격 (#18: 10~15초) */
        const val TRIP_REFRESH_MS = 10_000L
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
