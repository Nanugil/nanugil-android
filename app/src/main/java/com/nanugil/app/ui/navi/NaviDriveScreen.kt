package com.nanugil.app.ui.navi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.nanugil.app.data.search.PlaceResult
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
import com.nanugil.app.data.navi.NaviState
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

/** 화면 테마 선택. 자동 = 차량 화면 설정(Display Mode)을 따른다. */
enum class ThemeMode(val label: String) {
    AUTO("자동"),
    LIGHT("라이트"),
    DARK("다크"),
    ;

    /** 위쪽 바 버튼을 누를 때마다 자동 → 라이트 → 다크 → 자동 */
    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

// ---- 브랜드 색 (Figma "로고·브랜드 보드") ----
private val BrandPrimary = Color(0xFF1E5EFF)
private val BrandDanger = Color(0xFFE5484D)
private val BrandAmber = Color(0xFFFFB020)

/**
 * Figma v5 시안의 두 테마. 차량 화면 설정(Display Mode)이 다크면 다크, 아니면 화이트톤(글라스).
 * 수치는 시안 프레임(1682×1210 = Pleos 앱 영역) 기준 그대로다.
 */
private data class NaviPalette(
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

private val DarkPalette = NaviPalette(
    dark = true,
    background = Color(0xFF0A0A0B),
    panel = Color(0xF21A1B1F),
    panelBorder = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF5F6F8),
    textSecondary = Color(0xFF9CA3AF),
    divider = Color(0x1FFFFFFF),
    navSelected = Color(0xFF26282E),
    navIcon = Color(0xFF9CA3AF),
    secondaryButton = Color(0xFF2A2C32),
    secondaryButtonText = Color(0xFFE5E7EB),
    stopBackground = Color(0x40E5484D),
    stopIcon = BrandDanger,
    timelineConnector = Color(0x4D9CA3AF),
    distanceWeight = FontWeight.Bold,
)

private val LightPalette = NaviPalette(
    dark = false,
    background = Color(0xFFE9EDF2),
    panel = Color(0xE6FFFFFF),
    panelBorder = Color(0x1A0F172A),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    divider = Color(0x1A0F172A),
    navSelected = Color(0xFFF1F5F9),
    navIcon = Color(0xFF64748B),
    secondaryButton = Color(0xFFE2E8F0),
    secondaryButtonText = Color(0xFF334155),
    stopBackground = Color(0xFFFF3B3B),
    stopIcon = Color.White,
    timelineConnector = Color(0x4D64748B),
    distanceWeight = FontWeight.Medium,
)

private val CardShape = RoundedCornerShape(28.dp)

/**
 * 나누길 주행 화면 (Figma "그룹 드라이브 v5").
 *
 * 지도(MapView)는 맨 아래에 깔고 카드·버튼은 위에 겹친다. 카드가 아닌 곳의 터치는
 * 그대로 지도로 내려가서 끌기·확대·회전이 된다.
 */
@Composable
fun NaviDriveScreen(
    mapView: MapView,
    navi: NaviState,
    vehicle: VehicleStatus,
    carsDriving: Int,
    proposal: MeetupProposal?,
    cost: TripCost,
    routeError: String?,
    onStartRoute: () -> Unit,
    onStopRoute: () -> Unit,
    onAcceptMeetup: () -> Unit,
    onSkipMeetup: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    onHome: () -> Unit,
    onCars: () -> Unit,
    /** 내 차를 따라가는 중이면 차 아래에 파란 빛을 깐다 (시안의 도로 위 블루 글로우) */
    carGlow: Boolean = false,
    themeMode: ThemeMode = ThemeMode.AUTO,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    search: SearchUiState = SearchUiState(),
    searchActions: SearchActions = SearchActions(),
) {
    val darkTheme = resolveDarkTheme(themeMode)
    val p = if (darkTheme) DarkPalette else LightPalette
    // 위쪽 바의 정산 버튼으로 "지금까지 쓴 돈" 카드를 열고 닫는다 (시안에 자리가 없어 오른쪽 카드 아래에 띄운다)
    var showFare by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(p.background)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (carGlow) CarGlow(Modifier.fillMaxSize())

        TopBar(
            p = p,
            carsDriving = carsDriving,
            guiding = navi.guiding,
            fareSelected = showFare,
            themeMode = themeMode,
            onTheme = { onThemeModeChange(themeMode.next()) },
            onSearch = searchActions.onOpen,
            onHome = onHome,
            onCars = onCars,
            onFare = { showFare = !showFare },
            onStop = onStopRoute,
            modifier = Modifier.align(Alignment.TopCenter).padding(start = 32.dp, end = 32.dp, top = 26.dp).fillMaxWidth(),
        )

        val leftModifier = Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 146.dp).width(558.dp)
        if (search.open) {
            SearchPanel(p, search, searchActions, leftModifier)
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
            Modifier.align(Alignment.TopEnd).padding(end = 32.dp, top = 146.dp).width(518.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (proposal != null) {
                MeetupCard(p, proposal, onAcceptMeetup, onSkipMeetup)
            } else {
                VehicleCard(p, vehicle)
            }
            if (showFare) FareCard(p, cost)
        }

        MapControls(
            p = p,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onRecenter = onRecenter,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 48.dp),
        )
    }
}

/**
 * 내 차 아래 파란 빛. 주행 시점에서 내 차는 화면 가로 가운데, 세로 75% 지점에 놓인다
 * (DriveMapController.DRIVE_PIVOT_Y). 터치는 받지 않으므로 지도 조작을 막지 않는다.
 */
@Composable
private fun CarGlow(modifier: Modifier) {
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

// ---- 위쪽 바: 메뉴 · 로고 · 주행 대수 · 안내 종료 ----

@Composable
private fun TopBar(
    p: NaviPalette,
    carsDriving: Int,
    guiding: Boolean,
    fareSelected: Boolean,
    themeMode: ThemeMode,
    onTheme: () -> Unit,
    onSearch: () -> Unit,
    onHome: () -> Unit,
    onCars: () -> Unit,
    onFare: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier,
) {
    Panel(p, modifier.height(92.dp), RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            NavButton(p, R.drawable.ic_home, selected = !fareSelected, onClick = onHome)
            NavButton(p, R.drawable.ic_car, selected = false, onClick = onCars)
            NavButton(p, R.drawable.ic_card, selected = fareSelected, onClick = onFare)
            Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(44.dp).background(p.divider))
            Image(
                painterResource(R.drawable.logo_nanugil),
                contentDescription = "나누길",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp).clip(CircleShape),
            )
            Label("${carsDriving}대 주행 중", 32.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 16.dp))
            Spacer(Modifier.weight(1f))
            SquareIconButton(p, R.drawable.ic_search, onSearch)
            ThemeButton(p, themeMode, onTheme)
            if (guiding) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(p.stopBackground).clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) { Icon(R.drawable.ic_stop, p.stopIcon, 30.dp) }
            }
        }
    }
}

/** 라이트·다크 전환 버튼: 지금 상태 아이콘만 (◑ 자동 / ☀ 라이트 / ☾ 다크) */
@Composable
private fun ThemeButton(p: NaviPalette, mode: ThemeMode, onClick: () -> Unit) {
    val icon = when (mode) {
        ThemeMode.AUTO -> R.drawable.ic_theme_auto
        ThemeMode.LIGHT -> R.drawable.ic_theme_light
        ThemeMode.DARK -> R.drawable.ic_theme_dark
    }
    Box(
        Modifier.padding(end = 14.dp).size(64.dp).clip(RoundedCornerShape(18.dp))
            .background(p.navSelected).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, p.textPrimary, 30.dp) }
}

@Composable
private fun SquareIconButton(p: NaviPalette, iconRes: Int, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 14.dp).size(64.dp).clip(RoundedCornerShape(18.dp))
            .background(p.navSelected).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(iconRes, p.textPrimary, 30.dp) }
}

@Composable
private fun NavButton(p: NaviPalette, iconRes: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) p.navSelected else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(iconRes, if (selected) BrandPrimary else p.navIcon, 30.dp) }
}

// ---- 왼쪽 카드: 목적지 · 도착 · 이동 경로 ----

@Composable
private fun DestinationCard(
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

private enum class Marker { Current, Stop, Destination }

@Composable
private fun TimelineRow(
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
private fun TimelineConnector(p: NaviPalette, highlighted: Boolean) {
    Box(
        Modifier.padding(start = 11.dp).width(2.dp).height(18.dp)
            .background(if (highlighted) BrandPrimary else p.timelineConnector),
    )
}

// ---- 오른쪽 카드 ① 합류 제안 ----

@Composable
private fun MeetupCard(p: NaviPalette, proposal: MeetupProposal, onAccept: () -> Unit, onSkip: () -> Unit) {
    Panel(p, Modifier.fillMaxWidth(), CardShape) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 30.dp)) {
            Label("${proposal.placeName}에서 합류", 36.sp, p.textPrimary, FontWeight.Bold)
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_pin, BrandPrimary, 26.dp)
                Label(
                    proposal.distanceMeters?.let { "${formatKm(it)} 전방" } ?: "거리 계산 중",
                    26.sp, p.textPrimary, modifier = Modifier.padding(start = 8.dp),
                )
                Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(24.dp).background(p.divider))
                Icon(R.drawable.ic_fuel, BrandPrimary, 26.dp)
                Label(
                    "${proposal.carLabel} 연료 ${proposal.fuelPercent}%",
                    26.sp, p.textPrimary, modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("합류하기", true, onAccept, Modifier.width(160.dp))
                SecondaryButton(p, "건너뛰기", onSkip, Modifier.width(160.dp))
            }
        }
    }
}

// ---- 오른쪽 카드 ② 내 차 상태 (Pleos Vehicle SDK) — 합류 제안이 없을 때 ----

@Composable
private fun VehicleCard(p: NaviPalette, vehicle: VehicleStatus) {
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

private fun efficiencyText(v: VehicleStatus): String = if (v.isEv) {
    v.evEfficiencyKmPerKwh?.let { "전비 %.1fkm/kWh".format(it) } ?: "전비 정보 없음"
} else {
    v.fuelEconomyLPer100Km?.takeIf { it > 0 }?.let { "연비 %.1fkm/L".format(100f / it) } ?: "연비 정보 없음"
}

// ---- 오른쪽 카드 ③ 지금까지 쓴 돈 (위쪽 바 정산 버튼) ----

@Composable
private fun FareCard(p: NaviPalette, cost: TripCost) {
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
private fun MapControls(
    p: NaviPalette,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier,
) {
    Panel(p, modifier.width(86.dp), RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTap(p, R.drawable.ic_zoom_in, onZoomIn)
            IconTap(p, R.drawable.ic_zoom_out, onZoomOut)
            Box(Modifier.width(40.dp).height(1.dp).background(p.divider))
            IconTap(p, R.drawable.ic_recenter, onRecenter)
        }
    }
}

// ---- 공통 조각 ----

@Composable
private fun Panel(p: NaviPalette, modifier: Modifier, shape: Shape, content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(if (p.dark) 24.dp else 18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(p.panel)
            .border(1.dp, p.panelBorder, shape),
    ) { content() }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(72.dp).clip(shape)
            .background(if (enabled) BrandPrimary else BrandPrimary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, 28.sp, Color.White, FontWeight.Bold) }
}

@Composable
private fun SecondaryButton(p: NaviPalette, text: String, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(72.dp).clip(shape).background(p.secondaryButton).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, 28.sp, p.secondaryButtonText, FontWeight.Bold) }
}

@Composable
private fun IconTap(p: NaviPalette, iconRes: Int, onClick: () -> Unit) {
    Box(Modifier.size(80.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(iconRes, p.textPrimary, 32.dp)
    }
}

@Composable
private fun Icon(iconRes: Int, tint: Color, size: Dp) {
    Image(
        painterResource(iconRes),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size),
    )
}

@Composable
private fun Label(
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
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, letterSpacing = letterSpacing),
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
    val onSelect: (PlaceResult) -> Unit = {},
)

/** 목적지 검색: 검색창 + 마이크 + 결과 목록 (왼쪽 카드 자리에 뜬다) */
@Composable
private fun SearchPanel(p: NaviPalette, search: SearchUiState, actions: SearchActions, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (!search.listening) focus.requestFocus() }

    Panel(p, modifier, CardShape) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("목적지 검색", 24.sp, p.textSecondary, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(56.dp).clip(CircleShape).clickable(onClick = actions.onClose),
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
                        readOnly = search.listening,
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
                        Label(if (search.listening) "듣고 있어요… 말씀하세요" else "장소·주소를 입력하세요", 30.sp, p.textSecondary)
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
            when {
                search.searching -> Label("검색 중…", 26.sp, p.textSecondary, modifier = Modifier.padding(top = 22.dp))
                // 안내 문구가 길 수 있어 여러 줄로
                error != null -> Text(
                    error,
                    modifier = Modifier.padding(top = 22.dp),
                    maxLines = 3,
                    style = TextStyle(color = BrandDanger, fontSize = 24.sp),
                )
                search.searched && search.results.isEmpty() ->
                    Label("검색 결과가 없어요", 26.sp, p.textSecondary, modifier = Modifier.padding(top = 22.dp))
            }

            // 결과 (가까운 순)
            Column(Modifier.padding(top = 12.dp)) {
                search.results.forEachIndexed { i, place ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable {
                                keyboard?.hide()
                                actions.onSelect(place)
                            }
                            .padding(vertical = 16.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(R.drawable.ic_pin, BrandPrimary, 28.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Label(place.name, 28.sp, p.textPrimary, FontWeight.Bold)
                            Label(place.address, 22.sp, p.textSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        place.distanceMeters?.let {
                            Label(formatKm(it), 26.sp, p.textPrimary, p.distanceWeight, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 테마 선택을 실제 다크 여부로 바꾼다. 자동이면 차량 화면 설정(시스템 다크 모드)을 따른다. */
@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

// ---- 표시 형식 ----

private const val LOW_ENERGY_PERCENT = 15
private val CLOCK_FORMAT = SimpleDateFormat("HH:mm", Locale.KOREA)

private fun arrivalTime(remainSeconds: Int): String =
    CLOCK_FORMAT.format(Date(System.currentTimeMillis() + remainSeconds * 1000L))

private fun formatMinutes(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

private fun formatKm(meters: Int): String =
    if (meters >= 10_000) "${meters / 1000}km" else if (meters >= 1000) "%.1fkm".format(meters / 1000.0) else "${meters}m"
