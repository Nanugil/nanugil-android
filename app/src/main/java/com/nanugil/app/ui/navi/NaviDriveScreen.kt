package com.nanugil.app.ui.navi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nanugil.app.R
import com.nanugil.app.data.fare.TripCost
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.navi.NaviTurn
import ai.pleos.playground.navi.constants.TurnType
import com.nanugil.app.data.vehicle.VehicleStatus
import com.naver.maps.map.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 다른 차량이 보낸 합류 제안 (지금은 연료 부족 차량 기준 목업). */
data class MeetupProposal(
    val carLabel: String,
    val driverName: String,
    val placeName: String,
    val distanceMeters: Int?,
    val fuelPercent: Int,
)

// ---- 브랜드 색 (Figma "로고·브랜드 보드") ----
internal val BrandPrimary = Color(0xFF1E5EFF)
internal val BrandDanger = Color(0xFFE5484D)
internal val BrandAmber = Color(0xFFFFB020)

/**
 * Figma v5 시안의 두 테마. 차량 화면 설정(Display Mode)이 다크면 다크, 아니면 화이트톤(글라스).
 * 수치는 시안 프레임(1682×1210 = Pleos 앱 영역) 기준 그대로다.
 */
internal data class NaviPalette(
    val dark: Boolean,
    val background: Color,
    val panel: Color,
    val panelBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val navSelected: Color,
    val navIcon: Color,
    val secondaryButton: Color,
    val secondaryButtonText: Color,
    val stopBackground: Color,
    val stopIcon: Color,
    val timelineConnector: Color,
    val distanceWeight: FontWeight,
)

internal val DarkPalette = NaviPalette(
    dark = true,
    background = Color(0xFF0A0A0B),
    panel = Color(0xF21A1B1F),
    panelBorder = Color(0x14FFFFFF),
    textPrimary = Color.White,
    textSecondary = Color(0xFF9CA3AF),
    divider = Color.White.copy(alpha = 0.08f),
    navSelected = Color.White.copy(alpha = 0.08f),
    navIcon = Color(0xFF9CA3AF),
    secondaryButton = Color.White.copy(alpha = 0.10f),
    secondaryButtonText = Color(0xFFE5E7EB),
    stopBackground = Color(0x40E5484D),
    stopIcon = BrandDanger,
    timelineConnector = Color(0x4D9CA3AF),
    distanceWeight = FontWeight.Bold,
)

internal val LightPalette = NaviPalette(
    dark = false,
    background = Color(0xFFE9EDF2),
    panel = Color(0xE6FFFFFF),
    panelBorder = Color(0x1A0F172A),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    divider = Color(0x1A0F172A),
    navSelected = Color.White.copy(alpha = 0.45f),
    navIcon = Color(0xFF64748B),
    secondaryButton = Color.White.copy(alpha = 0.55f),
    secondaryButtonText = Color(0xFF334155),
    stopBackground = Color(0xFFFF3B3B),
    stopIcon = Color.White,
    timelineConnector = Color(0x4D64748B),
    distanceWeight = FontWeight.Medium,
)

internal val CardShape = GlassShape

/** 지도에서 카드에 가리지 않는 영역 (지도 가장자리에서의 여백) */
data class MapInsets(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp)

/**
 * 일행 전체 보기를 눌렀을 때 일행을 모을 빈 영역. 화면마다 카드 배치가 달라서 화면별로 정한다.
 * - 홈: 위 왼쪽 넓은 카드 아래 + 오른쪽 카드 열 왼쪽
 * - 주행: 왼쪽 목적지 카드와 오른쪽 일행/합류 카드 사이
 */
private fun groupViewInsets(screen: AppScreen): MapInsets = when (screen) {
    AppScreen.HOME -> MapInsets(left = 24.dp, top = 560.dp, right = OuterPad + NarrowRight + 24.dp, bottom = 48.dp)
    AppScreen.DRIVE -> MapInsets(left = OuterPad + 559.dp + 24.dp, top = CardTop + 24.dp, right = OuterPad + 478.dp + 24.dp, bottom = 48.dp)
    AppScreen.CAR -> MapInsets(left = OuterPad + 559.dp + 24.dp, top = CardTop + 24.dp, right = 160.dp, bottom = 48.dp)
    else -> MapInsets(left = 24.dp, top = CardTop + 24.dp, right = 160.dp, bottom = 48.dp)
}

/** 글자를 입력하는 화면 (정차 중에만) */
private val INPUT_SCREENS = setOf(AppScreen.CREATE_GROUP, AppScreen.JOIN_CODE, AppScreen.JOIN_SETUP)

/**
 * 나누길 화면 (Figma "나누길 와이어프레임 v5").
 *
 * 지도(MapView)는 맨 아래에 깔고, 위쪽 바([드라이브 | 내 차 | 정산])와 화면별 카드를 위에 겹친다.
 * 카드가 아닌 곳의 터치는 그대로 지도로 내려가서 끌기·확대·회전이 된다.
 */
@Composable
fun NaviDriveScreen(
    mapView: MapView,
    navi: NaviState,
    vehicle: VehicleStatus,
    carsDriving: Int,
    groupCars: List<CarState>,
    proposal: MeetupProposal?,
    cost: TripCost,
    routeError: String?,
    group: GroupUiState,
    groupActions: GroupActions,
    onStartRoute: () -> Unit,
    onStopRoute: () -> Unit,
    onAcceptMeetup: () -> Unit,
    onSkipMeetup: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    /** 일행 전체 보기. 지도는 이 여백 안(카드가 없는 곳)에 일행이 모두 들어오게 맞춘다 */
    onShowGroup: (MapInsets) -> Unit = {},
    /** 내 차를 따라가는 중이면 차 아래에 파란 빛을 깐다 (시안의 도로 위 블루 글로우) */
    carGlow: Boolean = false,
    search: SearchUiState = SearchUiState(),
    searchActions: SearchActions = SearchActions(),
    suggestion: RestStopSuggestion? = null,
    onProposeRestStop: () -> Unit = {},
    onDismissRestStop: () -> Unit = {},
    answerListening: Boolean = false,
    answerHeard: String = "",
    /** 합류 제안 음성 답변. auto=true면 제안이 뜰 때 자동으로 부른 것 */
    onVoiceAnswer: (auto: Boolean) -> Unit = {},
    /** P단이면 true. 아니면 글자 입력 화면(02·04·05)을 막고, 검색은 말로만 받는다 */
    canType: Boolean = true,
    /** 기어를 아직 모르면 true (막는 안내 문구만 다르다) */
    gearUnknown: Boolean = false,
) {
    val p = vehiclePalette()
    val screen = group.screen

    Box(Modifier.fillMaxSize().background(p.background)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (carGlow && screen == AppScreen.DRIVE) CarGlow(Modifier.fillMaxSize())

        // ---- 화면별 카드 ----
        // 02·04·05는 글자를 입력하는 화면이라 정차 중(P단)에만 연다. 주행 중엔 안내 카드로 바꾸고, 입력한 값은 그대로 둔다.
        val inputBlocked = !canType && screen in INPUT_SCREENS
        if (inputBlocked) ParkOnlyCard(p, gearUnknown, groupActions.onBack) else when (screen) {
            AppScreen.HOME -> HomeScreen(p, navi, group, carsDriving, groupActions)
            AppScreen.CREATE_GROUP -> CreateGroupScreen(
                p, group.createForm, navi.destinationName ?: MockGroups.DESTINATION, groupActions,
            )
            AppScreen.LOBBY -> group.group?.let { LobbyScreen(p, it, groupActions) }
            AppScreen.JOIN_CODE -> JoinCodeScreen(p, group.join, groupActions)
            AppScreen.JOIN_SETUP -> JoinSetupScreen(p, group.join, groupActions)
            AppScreen.DRIVE -> {
                val leftModifier = Modifier.align(Alignment.TopStart).padding(start = OuterPad, top = CardTop).width(559.dp)
                if (search.open) {
                    SearchPanel(p, search, searchActions, leftModifier, canType)
                } else {
                    DestinationCard(
                        p = p,
                        navi = navi,
                        routeError = routeError,
                        onStartRoute = onStartRoute,
                        onOpenSearch = searchActions.onOpen,
                        onVoiceSearch = searchActions.onVoice,
                        modifier = leftModifier,
                    )
                }
                Column(
                    Modifier.align(Alignment.TopEnd).padding(end = OuterPad, top = CardTop).width(478.dp),
                    verticalArrangement = Arrangement.spacedBy(CardGap),
                ) {
                    when {
                        proposal != null -> {
                            // 다른 차의 정차 제안: 주행 중이라 큰 버튼 한 번이나 음성으로만 답한다
                            LaunchedEffect(proposal.carLabel, proposal.placeName) { onVoiceAnswer(true) }
                            MeetupCard(
                                p, proposal, answerListening, answerHeard,
                                onAccept = onAcceptMeetup, onSkip = onSkipMeetup, onVoice = { onVoiceAnswer(false) },
                            )
                        }
                        suggestion != null -> RestStopCard(p, suggestion, onProposeRestStop, onDismissRestStop)
                        groupCars.isNotEmpty() -> GroupCarsCard(p, groupCars, navi)
                    }
                }
            }
            AppScreen.CAR -> Column(
                Modifier.padding(start = OuterPad, top = CardTop).width(559.dp),
                verticalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                VehicleCard(p, vehicle)
                FareCard(p, cost)
            }
            AppScreen.ARRIVAL -> ArrivalScreen(p, group.settlement, groupActions)
            AppScreen.SETTLE_SENT -> SettleSentScreen(p, group.settlement, groupActions)
        }

        // 지도 버튼(확대·축소·내 위치)은 운행 전 화면에서도 쓴다. 오른쪽 아래는 어느 화면에서든 비어 있다.
        MapControls(
            p = p,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onRecenter = onRecenter,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = OuterPad, bottom = 48.dp),
            // 일행 차가 있을 때만
            onShowGroup = { onShowGroup(groupViewInsets(screen)) }.takeIf { groupCars.any { !it.isMe } },
        )

        // 목적지 검색(내장 내비 검색으로 넘김)은 어느 화면에서든 왼쪽 위에 띄운다
        if (search.open && screen != AppScreen.DRIVE) {
            SearchPanel(
                p, search, searchActions,
                Modifier.align(Alignment.TopStart).padding(start = OuterPad, top = CardTop).width(559.dp),
                canType,
            )
        }

        // ---- 위쪽 바 ----
        val g = group.group
        val title = when (screen) {
            AppScreen.HOME -> "나누길"
            AppScreen.CREATE_GROUP -> "그룹 만들기"
            AppScreen.LOBBY -> g?.name ?: "초대 코드"
            AppScreen.JOIN_CODE -> "코드로 참여"
            AppScreen.JOIN_SETUP -> "운행 참가"
            AppScreen.DRIVE -> if (navi.guiding) "${carsDriving}대 주행 중" else "드라이브"
            AppScreen.CAR -> "내 차"
            AppScreen.ARRIVAL -> "도착"
            AppScreen.SETTLE_SENT -> "정산"
        }
        val chip = when {
            screen == AppScreen.DRIVE && navi.guiding -> TopChip.END_TRIP
            screen == AppScreen.LOBBY || screen == AppScreen.ARRIVAL -> TopChip.HOST
            // 안내 중이거나 기어가 P가 아니면(D·R·N) 주행 중
            navi.guiding || (!canType && !gearUnknown) -> TopChip.DRIVING
            else -> TopChip.STOPPED
        }
        AppTopBar(
            p = p,
            title = title,
            showBack = screen in setOf(AppScreen.CREATE_GROUP, AppScreen.LOBBY, AppScreen.JOIN_CODE, AppScreen.JOIN_SETUP, AppScreen.SETTLE_SENT),
            tab = screen.tab,
            chip = chip,
            onBack = groupActions.onBack,
            onTab = groupActions.onTab,
            onSearch = searchActions.onOpen,
            onEndTrip = onStopRoute,
            modifier = Modifier.align(Alignment.TopCenter).padding(start = OuterPad, end = OuterPad, top = 28.dp).fillMaxWidth(),
        )
    }
}

/**
 * 내 차 아래 파란 빛. 주행 시점에서 내 차는 화면 가로 가운데, 세로 75% 지점에 놓인다
 * (DriveMapController.DRIVE_PIVOT_Y). 터치는 받지 않으므로 지도 조작을 막지 않는다.
 */
@Composable
internal fun CarGlow(modifier: Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width * 0.5f, size.height * 0.75f)
        val radius = size.minDimension * 0.32f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(BrandPrimary.copy(alpha = 0.45f), BrandPrimary.copy(alpha = 0.12f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            topLeft = Offset(center.x - radius, center.y - radius * 0.75f),
            size = Size(radius * 2, radius * 1.5f),
        )
    }
}

// ---- 왼쪽 카드: 목적지 · 도착 · 이동 경로 ----

@Composable
internal fun DestinationCard(
    p: NaviPalette,
    navi: NaviState,
    routeError: String?,
    onStartRoute: () -> Unit,
    onOpenSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    modifier: Modifier,
) {
    Panel(p, modifier, CardShape) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 30.dp)) {
            Label("목적지", 24.sp, p.textSecondary)
            if (!navi.guiding) {
                Label("어디로 갈까요?", 36.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(top = 4.dp))
                // 검색창 모양 버튼: 누르면 검색 패널, 마이크는 바로 음성 검색
                Row(
                    Modifier.padding(top = 24.dp).fillMaxWidth().height(76.dp).clip(RoundedCornerShape(18.dp))
                        .background(p.navSelected).clickable(onClick = onOpenSearch).padding(start = 22.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(R.drawable.ic_search, p.textSecondary, 28.dp)
                    Label("장소·주소 검색", 28.sp, p.textSecondary, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(BrandPrimary).clickable(onClick = onVoiceSearch),
                        contentAlignment = Alignment.Center,
                    ) { Icon(R.drawable.ic_mic, Color.White, 30.dp) }
                }
                SecondaryButton(
                    p = p,
                    text = "그룹 목적지(가평 펜션)로 안내",
                    onClick = onStartRoute,
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
                )
                navi.message?.let { Label(it, 24.sp, p.textSecondary, modifier = Modifier.padding(top = 16.dp)) }
                return@Column
            }

            Label(navi.destinationName ?: "목적지", 36.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(top = 2.dp))

            // 다음 회전: 내장 내비가 화면 뒤에서 안내하고, 우리는 그 정보(getTBTInfo)를 받아 보여 준다
            navi.nextTurn?.let { TurnBanner(p, it, navi.afterNextTurn, Modifier.padding(top = 18.dp)) }

            val remainSeconds = navi.remainTimeSeconds
            if (remainSeconds != null) {
                Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
                    Label(arrivalTime(remainSeconds), 80.sp, p.textPrimary, FontWeight.ExtraBold, letterSpacing = (-2).sp)
                    Label("도착", 28.sp, p.textSecondary, modifier = Modifier.padding(start = 12.dp, bottom = 12.dp))
                }
                Label("${formatMinutes(remainSeconds)} 남음", 28.sp, p.textPrimary, modifier = Modifier.padding(top = 2.dp))
            }

            Box(Modifier.padding(top = 26.dp).fillMaxWidth().height(1.dp).background(p.divider))

            Column(Modifier.padding(top = 14.dp)) {
                TimelineRow(p, Marker.Current, "현재 위치", "이동 중", BrandPrimary, FontWeight.Bold)
                navi.waypointNames.forEachIndexed { i, name ->
                    TimelineConnector(p, highlighted = i == 0)
                    TimelineRow(
                        p, Marker.Stop, name,
                        navi.waypointRemainMeters.getOrNull(i)?.let(::formatKm).orEmpty(),
                    )
                }
                TimelineConnector(p, highlighted = navi.waypointNames.isEmpty())
                TimelineRow(
                    p, Marker.Destination, navi.destinationName ?: "목적지",
                    navi.remainDistanceMeters?.let(::formatKm).orEmpty(),
                )
            }
            routeError?.let { Label(it, 24.sp, BrandDanger, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

internal enum class Marker { Current, Stop, Destination }

/** "300m 앞 좌회전 · 성남시청 방면" 안내 + 그다음 회전 한 줄 */
@Composable
internal fun TurnBanner(p: NaviPalette, turn: NaviTurn, after: NaviTurn?, modifier: Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BrandPrimary).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Label(turn.type.arrow(), 56.sp, Color.White, FontWeight.Bold) }
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Label(formatKm(turn.distanceMeters.toInt()), 48.sp, Color.White, FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Label(turn.description.ifBlank { turn.type.korean() }, 26.sp, Color.White.copy(alpha = 0.92f))
            }
        }
        if (after != null) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("그다음", 22.sp, Color.White.copy(alpha = 0.75f))
                Label(after.type.arrow(), 26.sp, Color.White, FontWeight.Bold, Modifier.padding(start = 10.dp))
                Label(
                    "${formatKm(after.distanceMeters.toInt())} ${after.type.korean()}",
                    22.sp, Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** 회전 종류 → 화살표 글자 (아이콘 리소스 없이 기본 글꼴로 그린다) */
internal fun TurnType.arrow(): String = when (this) {
    TurnType.TURN_LEFT, TurnType.TURN_SHARP_L -> "←"
    TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> "→"
    TurnType.TURN_KEEP_L, TurnType.TURN_EXIT_IC_L, TurnType.TURN_ENTER_IC_L,
    TurnType.TURN_ENTER_UNDER_L, TurnType.TURN_AVOID_UNDER_L, TurnType.TURN_ENTER_OVER_L, TurnType.TURN_AVOID_OVER_L -> "↖"
    TurnType.TURN_KEEP_R, TurnType.TURN_EXIT_IC_R, TurnType.TURN_ENTER_IC_R,
    TurnType.TURN_ENTER_UNDER_R, TurnType.TURN_AVOID_UNDER_R, TurnType.TURN_ENTER_OVER_R, TurnType.TURN_AVOID_OVER_R -> "↗"
    TurnType.TURN_UTURN -> "↶"
    TurnType.TURN_ENTER_ROUNDABOUT_02, TurnType.TURN_ENTER_ROUNDABOUT_03, TurnType.TURN_ENTER_ROUNDABOUT_04,
    TurnType.TURN_ENTER_ROUNDABOUT_06, TurnType.TURN_ENTER_ROUNDABOUT_08, TurnType.TURN_ENTER_ROUNDABOUT_09,
    TurnType.TURN_ENTER_ROUNDABOUT_10, TurnType.TURN_ENTER_ROUNDABOUT_12, TurnType.TURN_EXIT_ROUNDABOUT -> "↻"
    TurnType.TURN_ARRIVE, TurnType.TURN_WAY -> "●"
    else -> "↑"
}

internal fun TurnType.korean(): String = when (this) {
    TurnType.TURN_LEFT -> "좌회전"
    TurnType.TURN_SHARP_L -> "급좌회전"
    TurnType.TURN_RIGHT -> "우회전"
    TurnType.TURN_SHARP_R -> "급우회전"
    TurnType.TURN_KEEP_L -> "왼쪽 유지"
    TurnType.TURN_KEEP_R -> "오른쪽 유지"
    TurnType.TURN_UTURN -> "유턴"
    TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_C -> "고속도로 진입"
    TurnType.TURN_EXIT_IC_L, TurnType.TURN_EXIT_IC_R, TurnType.TURN_EXIT_IC_C -> "고속도로 출구"
    TurnType.TURN_ENTER_UNDER_L, TurnType.TURN_ENTER_UNDER_R, TurnType.TURN_ENTER_UNDER_C -> "지하차도 진입"
    TurnType.TURN_AVOID_UNDER_L, TurnType.TURN_AVOID_UNDER_R, TurnType.TURN_AVOID_UNDER_C -> "지하차도 옆길"
    TurnType.TURN_ENTER_OVER_L, TurnType.TURN_ENTER_OVER_R, TurnType.TURN_ENTER_OVER_C -> "고가도로 진입"
    TurnType.TURN_AVOID_OVER_L, TurnType.TURN_AVOID_OVER_R, TurnType.TURN_AVOID_OVER_C -> "고가도로 옆길"
    TurnType.TURN_EXIT_ROUNDABOUT -> "회전교차로 나가기"
    TurnType.TURN_WAY -> "경유지"
    TurnType.TURN_ARRIVE -> "목적지 도착"
    TurnType.TURN_TUNNEL -> "터널"
    TurnType.TURN_TOLL -> "요금소"
    TurnType.TURN_ENTER_SERVICEAREA, TurnType.TURN_RESTAREA -> "휴게소"
    TurnType.TURN_STRAIGHT, TurnType.TURN_DEVIDE_MERGE -> "직진"
    else -> if (name.startsWith("TURN_ENTER_ROUNDABOUT")) "회전교차로" else "직진"
}

@Composable
internal fun TimelineRow(
    p: NaviPalette,
    marker: Marker,
    title: String,
    trailing: String,
    trailingColor: Color = p.textPrimary,
    trailingWeight: FontWeight = p.distanceWeight,
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            when (marker) {
                Marker.Current -> Box(
                    Modifier.size(22.dp).clip(CircleShape).background(BrandPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(12.dp).clip(CircleShape).background(BrandPrimary)) }
                Marker.Stop -> Box(Modifier.size(14.dp).clip(CircleShape).border(2.5.dp, BrandPrimary, CircleShape))
                Marker.Destination -> Icon(R.drawable.ic_pin, p.textSecondary, 24.dp)
            }
        }
        Label(title, 28.sp, p.textPrimary, modifier = Modifier.weight(1f).padding(start = 18.dp))
        Label(trailing, 28.sp, trailingColor, trailingWeight)
    }
}

@Composable
internal fun TimelineConnector(p: NaviPalette, highlighted: Boolean) {
    Box(
        Modifier.padding(start = 11.dp).width(2.dp).height(18.dp)
            .background(if (highlighted) BrandPrimary else p.timelineConnector),
    )
}

// ---- 오른쪽 카드 ① 합류 제안 ----

/** 시나리오 4단계: 내 차 연료 부족 → "근처 ○○휴게소 들를까요?" [제안] [괜찮아요] */
@Composable
internal fun RestStopCard(p: NaviPalette, s: RestStopSuggestion, onPropose: () -> Unit, onDismiss: () -> Unit) {
    Panel(p, Modifier.fillMaxWidth(), CardShape) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 30.dp)) {
            Label(if (s.isEv) "배터리가 부족해요" else "연료가 부족해요", 24.sp, BrandAmber, FontWeight.Bold)
            Label("근처 ${s.placeName} 들를까요?", 36.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_pin, BrandPrimary, 26.dp)
                Label(
                    s.distanceMeters?.let { "${formatKm(it)} 전방" } ?: "거리 계산 중",
                    26.sp, p.textPrimary, modifier = Modifier.padding(start = 8.dp),
                )
                Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(24.dp).background(p.divider))
                Icon(if (s.isEv) R.drawable.ic_battery else R.drawable.ic_fuel, BrandAmber, 26.dp)
                Label(
                    "내 차 ${if (s.isEv) "배터리" else "연료"} ${s.energyPercent}%",
                    26.sp, p.textPrimary, modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (s.sent) {
                Label("일행에게 제안했어요 · 답을 기다리는 중…", 26.sp, BrandPrimary, FontWeight.Bold, Modifier.padding(top = 26.dp))
            } else {
                Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("제안", true, onPropose, Modifier.width(180.dp))
                    SecondaryButton(p, "괜찮아요", onDismiss, Modifier.width(180.dp))
                }
            }
        }
    }
}

// ---- 오른쪽 카드 ② 내 차 상태 (Pleos Vehicle SDK) — 합류 제안이 없을 때 ----

@Composable
internal fun VehicleCard(p: NaviPalette, vehicle: VehicleStatus) {
    Panel(p, Modifier.fillMaxWidth(), CardShape) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 30.dp)) {
            val percent = vehicle.energyPercent
            val low = percent != null && percent <= LOW_ENERGY_PERCENT
            Label("내 차 상태", 24.sp, p.textSecondary)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Label(if (vehicle.isEv) "잔여 배터리" else "잔여 연료", 36.sp, p.textPrimary, FontWeight.Bold)
                Label(
                    percent?.let { "$it%" } ?: "--",
                    36.sp, if (low) BrandAmber else BrandPrimary, FontWeight.Bold, Modifier.padding(start = 14.dp),
                )
            }
            Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(p.divider)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(((percent ?: 0) / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50)).background(if (low) BrandAmber else BrandPrimary),
                )
            }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_road, BrandPrimary, 26.dp)
                Label(
                    vehicle.odometerKm?.let { "누적 %,dkm".format(it.toInt()) } ?: "주행거리 정보 없음",
                    26.sp, p.textPrimary, modifier = Modifier.padding(start = 8.dp),
                )
                Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(24.dp).background(p.divider))
                Icon(if (vehicle.isEv) R.drawable.ic_battery else R.drawable.ic_fuel, BrandPrimary, 26.dp)
                Label(efficiencyText(vehicle), 26.sp, p.textPrimary, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun efficiencyText(v: VehicleStatus): String = if (v.isEv) {
    v.evEfficiencyKmPerKwh?.let { "전비 %.1fkm/kWh".format(it) } ?: "전비 정보 없음"
} else {
    v.fuelEconomyLPer100Km?.takeIf { it > 0 }?.let { "연비 %.1fkm/L".format(100f / it) } ?: "연비 정보 없음"
}

// ---- 오른쪽 카드 ③ 지금까지 쓴 돈 (위쪽 바 정산 버튼) ----

@Composable
internal fun FareCard(p: NaviPalette, cost: TripCost) {
    Panel(p, Modifier.fillMaxWidth(), CardShape) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 30.dp)) {
            Label("1인 · 지금까지", 24.sp, p.textSecondary)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Label("%,d".format(cost.perPersonWon), 56.sp, p.textPrimary, FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Label("원", 28.sp, p.textSecondary, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            }
            Label(
                "총 %,d원 · %d명  (유류비 %,d원 · 통행료 %,d원)".format(
                    cost.totalWon, cost.members, cost.energyWon, cost.tollWon,
                ),
                24.sp, p.textSecondary, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ---- 오른쪽 아래: 확대 · 축소 · 현재 위치 ----

@Composable
internal fun MapControls(
    p: NaviPalette,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier,
    /** 일행 전체 보기 (내 차 + 일행 차가 모두 보이게). null이면 버튼을 숨긴다 */
    onShowGroup: (() -> Unit)? = null,
) {
    Panel(p, modifier.width(86.dp), RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (onShowGroup != null) {
                IconTap(p, R.drawable.ic_people, onShowGroup)
                Box(Modifier.width(40.dp).height(1.dp).background(p.divider))
            }
            IconTap(p, R.drawable.ic_zoom_in, onZoomIn)
            IconTap(p, R.drawable.ic_zoom_out, onZoomOut)
            Box(Modifier.width(40.dp).height(1.dp).background(p.divider))
            IconTap(p, R.drawable.ic_recenter, onRecenter)
        }
    }
}

// ---- 공통 조각 ----

/** 모든 카드·위쪽 바의 바탕. 글래스모피즘 규칙은 [GlassSurface]에 있다. */
@Composable
internal fun Panel(p: NaviPalette, modifier: Modifier, shape: Shape, solid: Boolean = false, content: @Composable () -> Unit) {
    GlassSurface(dark = p.dark, modifier = modifier, shape = shape, solid = solid) { content() }
}

@Composable
internal fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(72.dp).clip(shape)
            .background(if (enabled) BrandPrimary else BrandPrimary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, 28.sp, Color.White, FontWeight.Bold) }
}

@Composable
internal fun SecondaryButton(p: NaviPalette, text: String, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(72.dp).clip(shape).background(p.secondaryButton).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, 28.sp, p.secondaryButtonText, FontWeight.Bold) }
}

@Composable
internal fun IconTap(p: NaviPalette, iconRes: Int, onClick: () -> Unit) {
    Box(Modifier.size(80.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(iconRes, p.textPrimary, 32.dp)
    }
}

@Composable
internal fun Icon(iconRes: Int, tint: Color, size: Dp) {
    Image(
        painterResource(iconRes),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun Label(
    text: String,
    size: TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, letterSpacing = letterSpacing,
        ),
    )
}

/** 검색 패널 이벤트 묶음 (화면 함수 인자가 너무 길어지지 않게) */
data class SearchActions(
    val onOpen: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onVoice: () -> Unit = {},
    val onStopVoice: () -> Unit = {},
)

/**
 * 목적지 검색: 검색창 + 마이크 (왼쪽 카드 자리에 뜬다).
 * 검색은 내장 내비가 한다. 검색어를 넘기면 내비 검색 화면이 열리고, 거기서 고르면 안내가 시작되면서 이 화면으로 돌아온다.
 */
@Composable
internal fun SearchPanel(
    p: NaviPalette,
    search: SearchUiState,
    actions: SearchActions,
    modifier: Modifier,
    /** 주행 중(P단 아님)이면 자판을 띄우지 않고 마이크로만 검색한다 */
    canType: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(canType) {
        if (canType && !search.listening) focus.requestFocus() else keyboard?.hide()
    }

    // 홈·그룹 만들기 카드 위에 겹쳐 뜨므로 불투명하게
    Panel(p, modifier, CardShape, solid = true) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("목적지 검색", 24.sp, p.textSecondary, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(MinTouch).clip(CircleShape).clickable(onClick = actions.onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(R.drawable.ic_close, p.textSecondary, 28.dp) }
            }

            // 검색창 + 마이크
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(80.dp).clip(RoundedCornerShape(18.dp))
                    .background(p.navSelected)
                    .border(2.dp, if (search.listening) BrandPrimary else Color.Transparent, RoundedCornerShape(18.dp))
                    .padding(start = 22.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(R.drawable.ic_search, p.textSecondary, 28.dp)
                Box(Modifier.weight(1f).padding(start = 14.dp)) {
                    val shown = if (search.listening) search.partial else search.query
                    BasicTextField(
                        value = shown,
                        onValueChange = actions.onQueryChange,
                        singleLine = true,
                        readOnly = search.listening || !canType,
                        enabled = canType || search.listening,
                        textStyle = TextStyle(color = p.textPrimary, fontSize = 30.sp),
                        cursorBrush = SolidColor(BrandPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            actions.onSubmit()
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    if (shown.isEmpty()) {
                        val hint = when {
                            search.listening -> "듣고 있어요… 말씀하세요"
                            !canType -> "주행 중엔 마이크로 말해 주세요"
                            else -> "장소·주소를 입력하세요"
                        }
                        Label(hint, 30.sp, p.textSecondary)
                    }
                }
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .background(if (search.listening) BrandDanger else BrandPrimary)
                        .clickable {
                            keyboard?.hide()
                            if (search.listening) actions.onStopVoice() else actions.onVoice()
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (search.listening) R.drawable.ic_stop else R.drawable.ic_mic, Color.White, 30.dp) }
            }

            val error = search.error
            if (error != null) {
                // 안내 문구가 길 수 있어 여러 줄로
                Text(
                    error,
                    modifier = Modifier.padding(top = 22.dp),
                    maxLines = 3,
                    style = TextStyle(color = BrandDanger, fontSize = 24.sp),
                )
            } else {
                Text(
                    "검색하면 내장 내비에서 결과를 골라요. 안내가 시작되면 이 화면으로 돌아와요.",
                    modifier = Modifier.padding(top = 18.dp),
                    maxLines = 2,
                    style = TextStyle(color = p.textSecondary, fontSize = 22.sp),
                )
            }
            PrimaryButton(
                text = "내비에서 검색",
                enabled = search.query.isNotBlank() && !search.listening,
                onClick = {
                    keyboard?.hide()
                    actions.onSubmit()
                },
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth(),
            )
        }
    }
}

/**
 * 화면 테마는 차량(시스템) 주간·야간 모드를 그대로 따른다. 앱 안에 따로 바꾸는 버튼을 두지 않는다.
 * 앱은 차량 화면 테마를 바꿀 수 없어서(MODIFY_DAY_NIGHT_MODE는 시스템 앱 전용) 버튼을 두면 앱만 바뀌어 차량 화면과 어긋났다.
 * 실제 차는 조도 센서·전조등으로 야간 모드를 바꾸고, 그러면 이 값도 바로 바뀐다.
 */
@Composable
internal fun vehiclePalette(): NaviPalette = if (isSystemInDarkTheme()) DarkPalette else LightPalette

// ---- 표시 형식 ----

internal const val LOW_ENERGY_PERCENT = 15
internal val CLOCK_FORMAT = SimpleDateFormat("HH:mm", Locale.KOREA)

internal fun arrivalTime(remainSeconds: Int): String =
    CLOCK_FORMAT.format(Date(System.currentTimeMillis() + remainSeconds * 1000L))

internal fun formatMinutes(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

internal fun formatKm(meters: Int): String =
    if (meters >= 10_000) "${meters / 1000}km" else if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters}m"
