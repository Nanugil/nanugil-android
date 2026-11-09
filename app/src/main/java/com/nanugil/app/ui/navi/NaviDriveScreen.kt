package com.nanugil.app.ui.navi

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.delay
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

// ---- 디자인 토큰 (다크 글라스, 고급스럽게: 낮은 채도 + 얇은 하이라이트 + 깊은 그림자) ----
private val Ink = Color(0xFF0B0C0F)
private val PanelTop = Color(0xF2202329)
private val PanelBottom = Color(0xEB111317)
private val Hairline = Color(0x1FFFFFFF)
private val HairlineFaint = Color(0x0AFFFFFF)
private val TextPrimary = Color(0xFFF5F6F8)
private val TextSecondary = Color(0xFF8E96A3)
private val TextTertiary = Color(0xFF5E6672)
private val Accent = Color(0xFF3D8BFF)
private val AccentDeep = Color(0xFF1E5FE0)
private val AccentSoft = Color(0x293D8BFF)
private val ButtonGrey = Color(0xFF262A31)
private val Danger = Color(0xFFF0555B)
private val Warning = Color(0xFFFFB547)
private val CardShape = RoundedCornerShape(26.dp)
private val AccentBrush = Brush.verticalGradient(listOf(Color(0xFF5A9CFF), AccentDeep))

/**
 * 나누길 주행 화면 (다크 테마 시안 기준).
 *
 * 지도(MapView)는 맨 아래에 깔고, 카드·버튼은 위에 겹친다. 카드 영역이 아닌 곳의 터치는
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
    onToggleLayer: () -> Unit,
    onRecenter: () -> Unit,
    onHome: () -> Unit,
    onCars: () -> Unit,
    onFare: () -> Unit,
    onMic: () -> Unit,
    onProfile: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // 위·아래 비네트: 카드 글씨가 지도에 묻히지 않게 (터치는 받지 않음)
        Box(
            Modifier.fillMaxWidth().height(220.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0xE60B0C0F), Color(0x660B0C0F), Color.Transparent))),
        )
        Box(
            Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x990B0C0F), Color(0xF20B0C0F)))),
        )

        Header(carsDriving, Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 22.dp))

        DestinationCard(
            navi = navi,
            routeError = routeError,
            onStartRoute = onStartRoute,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 118.dp).width(400.dp),
        )

        val rightCardModifier = Modifier.align(Alignment.TopEnd).padding(end = 120.dp, top = 118.dp).width(390.dp)
        if (proposal != null) {
            MeetupCard(proposal, onAcceptMeetup, onSkipMeetup, rightCardModifier)
        } else {
            VehicleCard(vehicle, rightCardModifier)
        }

        MapControls(
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onToggleLayer = onToggleLayer,
            onRecenter = onRecenter,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = 118.dp),
        )

        BottomBar(
            guiding = navi.guiding,
            cost = cost,
            onStopRoute = onStopRoute,
            onHome = onHome,
            onCars = onCars,
            onFare = onFare,
            onMic = onMic,
            onProfile = onProfile,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 20.dp),
        )
    }
}

// ---- 왼쪽 위: 로고 + 주행 상태 ----

@Composable
private fun Header(carsDriving: Int, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.logo_nanugil),
            contentDescription = "나누길",
            modifier = Modifier.size(64.dp).shadow(18.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black)
                .clip(RoundedCornerShape(16.dp)).border(1.dp, Hairline, RoundedCornerShape(16.dp)),
        )
        Column(Modifier.padding(start = 16.dp)) {
            Label("${carsDriving}대 주행 중", 25.sp, TextPrimary, FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Row(
                Modifier.padding(top = 7.dp).clip(RoundedCornerShape(50))
                    .background(AccentSoft).border(1.dp, Color(0x553D8BFF), RoundedCornerShape(50))
                    .padding(horizontal = 11.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot()
                Label("위치 공유 중", 12.sp, Color(0xFFBFD6FF), FontWeight.Medium, modifier = Modifier.padding(start = 7.dp))
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val alpha by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveAlpha",
    )
    Box(Modifier.size(7.dp).alpha(alpha).clip(CircleShape).background(Accent))
}

// ---- 왼쪽 카드: 목적지 · 도착 예정 · 이동 경로 ----

@Composable
private fun DestinationCard(navi: NaviState, routeError: String?, onStartRoute: () -> Unit, modifier: Modifier) {
    GlassPanel(modifier) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 26.dp)) {
            Eyebrow("목적지")
            if (!navi.guiding) {
                Label("아직 정하지 않았어요", 25.sp, TextPrimary, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
                PrimaryButton(
                    text = if (navi.connected) "가평 펜션으로 안내 시작" else "내장 내비 연결 중…",
                    enabled = navi.connected,
                    onClick = onStartRoute,
                    modifier = Modifier.padding(top = 22.dp).fillMaxWidth(),
                )
                navi.message?.let { Label(it, 13.sp, TextSecondary, modifier = Modifier.padding(top = 12.dp)) }
                return@Column
            }

            Label(navi.destinationName ?: "목적지", 25.sp, TextPrimary, FontWeight.SemiBold, Modifier.padding(top = 6.dp), letterSpacing = (-0.3).sp)

            val remainSeconds = navi.remainTimeSeconds
            if (remainSeconds != null) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Label(arrivalTime(remainSeconds), 56.sp, TextPrimary, FontWeight.Bold, letterSpacing = (-1.5).sp)
                    Label("도착 예정", 17.sp, TextSecondary, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
                }
                Label("${formatMinutes(remainSeconds)} 남음", 18.sp, Color(0xFFD5D9DF), modifier = Modifier.padding(top = 2.dp))
            }

            HairlineDivider(Modifier.padding(top = 22.dp))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Eyebrow("이동 경로", Modifier.weight(1f))
                navi.remainDistanceMeters?.let { Eyebrow("총 ${formatKm(it)} 남음") }
            }

            Column(Modifier.padding(top = 12.dp)) {
                TimelineRow(marker = Marker.Current, title = "현재 위치", trailing = "이동 중", trailingColor = Accent)
                navi.waypointNames.forEachIndexed { i, name ->
                    TimelineConnector()
                    TimelineRow(
                        marker = Marker.Stop,
                        title = name,
                        trailing = navi.waypointRemainMeters.getOrNull(i)?.let(::formatKm).orEmpty(),
                    )
                }
                TimelineConnector()
                TimelineRow(
                    marker = Marker.Destination,
                    title = navi.destinationName ?: "목적지",
                    trailing = navi.remainDistanceMeters?.let(::formatKm).orEmpty(),
                )
            }
            routeError?.let { Label(it, 12.sp, Danger, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

private enum class Marker { Current, Stop, Destination }

@Composable
private fun TimelineRow(marker: Marker, title: String, trailing: String, trailingColor: Color = TextPrimary) {
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            when (marker) {
                Marker.Current -> Box(
                    Modifier.size(18.dp).clip(CircleShape).background(AccentSoft),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(9.dp).clip(CircleShape).background(Accent)) }
                Marker.Stop -> Box(Modifier.size(9.dp).clip(CircleShape).border(2.dp, TextSecondary, CircleShape))
                Marker.Destination -> Icon(R.drawable.ic_pin, TextSecondary, 19.dp)
            }
        }
        Label(title, 19.sp, TextPrimary, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Label(trailing, 18.sp, trailingColor, FontWeight.Medium)
    }
}

@Composable
private fun TimelineConnector() {
    Box(
        Modifier.padding(start = 9.dp).width(2.dp).height(12.dp)
            .background(Brush.verticalGradient(listOf(Color(0x663D8BFF), Color(0x223D8BFF)))),
    )
}

// ---- 오른쪽 카드 ① 합류 제안 ----

@Composable
private fun MeetupCard(proposal: MeetupProposal, onAccept: () -> Unit, onSkip: () -> Unit, modifier: Modifier) {
    GlassPanel(modifier) {
        Column(Modifier.padding(26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("차량 합류 제안이 도착했어요", Modifier.weight(1f))
                CarChip(proposal.carLabel, proposal.driverName)
            }
            Label("${proposal.placeName}에서 합류", 25.sp, TextPrimary, FontWeight.SemiBold, Modifier.padding(top = 12.dp), letterSpacing = (-0.3).sp)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_pin, Accent, 17.dp)
                Label(proposal.distanceMeters?.let { "${formatKm(it)} 전방" } ?: "거리 계산 중", 16.sp, Color(0xFFD5D9DF), modifier = Modifier.padding(start = 6.dp))
                Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(13.dp).background(Hairline))
                Icon(R.drawable.ic_fuel, Warning, 17.dp)
                Label("잔여 연료 ${proposal.fuelPercent}%", 16.sp, Color(0xFFD5D9DF), modifier = Modifier.padding(start = 6.dp))
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("합류하기", true, onAccept, Modifier.width(124.dp))
                SecondaryButton("건너뛰기", onSkip, Modifier.width(124.dp))
            }
        }
    }
}

// ---- 오른쪽 카드 ② 내 차 상태 (Pleos Vehicle SDK) ----

@Composable
private fun VehicleCard(vehicle: VehicleStatus, modifier: Modifier) {
    GlassPanel(modifier) {
        Column(Modifier.padding(26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("내 차 상태", Modifier.weight(1f))
                CarChip("A차", "나")
            }
            val percent = vehicle.energyPercent
            val low = percent != null && percent <= LOW_ENERGY_PERCENT
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Label(if (vehicle.isEv) "잔여 배터리" else "잔여 연료", 19.sp, TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                Label(percent?.let { "$it" } ?: "--", 44.sp, if (low) Warning else TextPrimary, FontWeight.Bold, Modifier.padding(start = 12.dp), letterSpacing = (-1).sp)
                Label("%", 20.sp, TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
            }
            EnergyGauge(percent, low, Modifier.padding(top = 12.dp).fillMaxWidth())
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_road, TextSecondary, 17.dp)
                Label(
                    vehicle.odometerKm?.let { "누적 %,dkm".format(it.toInt()) } ?: "주행거리 정보 없음",
                    15.sp, Color(0xFFD5D9DF), modifier = Modifier.padding(start = 6.dp),
                )
                Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(13.dp).background(Hairline))
                Icon(if (vehicle.isEv) R.drawable.ic_battery else R.drawable.ic_fuel, TextSecondary, 17.dp)
                Label(efficiencyText(vehicle), 15.sp, Color(0xFFD5D9DF), modifier = Modifier.padding(start = 6.dp))
            }
            Label("Pleos 차량 정보 기준", 11.sp, TextTertiary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun EnergyGauge(percent: Int?, low: Boolean, modifier: Modifier) {
    val fraction = ((percent ?: 0) / 100f).coerceIn(0f, 1f)
    Box(modifier.height(8.dp).clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(50))
                .background(
                    if (low) Brush.horizontalGradient(listOf(Color(0xFFFF7A45), Warning))
                    else Brush.horizontalGradient(listOf(AccentDeep, Color(0xFF6FB0FF))),
                ),
        )
    }
}

private fun efficiencyText(v: VehicleStatus): String = if (v.isEv) {
    v.evEfficiencyKmPerKwh?.let { "전비 %.1fkm/kWh".format(it) } ?: "전비 정보 없음"
} else {
    v.fuelEconomyLPer100Km?.takeIf { it > 0 }?.let { "연비 %.1fkm/L".format(100f / it) } ?: "연비 정보 없음"
}

@Composable
private fun CarChip(car: String, driver: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x0FFFFFFF)).border(1.dp, Hairline, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(car, 13.sp, TextPrimary, FontWeight.SemiBold)
        Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(11.dp).background(Hairline))
        Label(driver, 13.sp, TextSecondary)
    }
}

// ---- 오른쪽 세로 버튼: 확대 · 축소 · 지도 종류 · 현재 위치 ----

@Composable
private fun MapControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleLayer: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier,
) {
    GlassPanel(modifier.width(72.dp), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTap(R.drawable.ic_zoom_in, onZoomIn)
            IconTap(R.drawable.ic_zoom_out, onZoomOut)
            Box(Modifier.width(32.dp).height(1.dp).background(Hairline))
            IconTap(R.drawable.ic_layers, onToggleLayer)
            IconTap(R.drawable.ic_recenter, onRecenter)
        }
    }
}

// ---- 하단 바: 메뉴 · 지금까지 쓴 돈 · 종료 · 음성 · 프로필 · 시계 ----

@Composable
private fun BottomBar(
    guiding: Boolean,
    cost: TripCost,
    onStopRoute: () -> Unit,
    onHome: () -> Unit,
    onCars: () -> Unit,
    onFare: () -> Unit,
    onMic: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier,
) {
    GlassPanel(modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(30.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2C3442), Color(0xFF1E242F))))
                    .border(1.dp, Color(0x333D8BFF), CircleShape)
                    .clickable(onClick = onHome),
                contentAlignment = Alignment.Center,
            ) { Icon(R.drawable.ic_home, Accent, 27.dp) }
            IconTap(R.drawable.ic_car, onCars, size = 64.dp, tint = TextSecondary)
            IconTap(R.drawable.ic_card, onFare, size = 64.dp, tint = TextSecondary)

            VerticalHairline()
            Column(Modifier.clickable(onClick = onFare).padding(horizontal = 4.dp)) {
                Label("1인 · 지금까지", 12.sp, TextSecondary, FontWeight.Medium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Label("%,d".format(cost.perPersonWon), 32.sp, TextPrimary, FontWeight.Bold, letterSpacing = (-0.8).sp)
                    Label("원", 16.sp, TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
                }
                Label("총 %,d원 · %d명".format(cost.totalWon, cost.members), 12.sp, TextTertiary)
            }
            VerticalHairline()

            Spacer(Modifier.weight(1f))

            if (guiding) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Color(0x26F0555B))
                        .border(1.dp, Color(0x55F0555B), CircleShape).clickable(onClick = onStopRoute),
                    contentAlignment = Alignment.Center,
                ) { Icon(R.drawable.ic_stop, Danger, 26.dp) }
            }
            IconTap(R.drawable.ic_mic, onMic, size = 64.dp, tint = TextSecondary)
            Box(
                Modifier.padding(horizontal = 6.dp).size(48.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF30343C), Color(0xFF1C1F25))))
                    .border(1.dp, Hairline, CircleShape).clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) { Icon(R.drawable.ic_person, TextPrimary, 24.dp) }
            Clock(Modifier.padding(start = 12.dp, end = 10.dp))
        }
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    Label(CLOCK_FORMAT.format(now), 24.sp, TextPrimary, FontWeight.Medium, modifier, letterSpacing = (-0.3).sp)
}

// ---- 공통 조각 ----

/** 위가 살짝 밝은 유리 패널 + 얇은 하이라이트 테두리 + 깊은 그림자 */
@Composable
private fun GlassPanel(modifier: Modifier, shape: Shape = CardShape, content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PanelTop, PanelBottom)))
            .border(1.dp, Brush.verticalGradient(listOf(Hairline, HairlineFaint)), shape),
    ) { content() }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(52.dp)
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = Accent, spotColor = Accent)
            .clip(shape)
            .background(if (enabled) AccentBrush else Brush.verticalGradient(listOf(ButtonGrey, ButtonGrey)))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, 18.sp, Color.White, FontWeight.SemiBold) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(52.dp).clip(shape).background(Color(0x14FFFFFF)).border(1.dp, Hairline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, 18.sp, Color(0xFFD5D9DF), FontWeight.Medium) }
}

@Composable
private fun IconTap(iconRes: Int, onClick: () -> Unit, size: Dp = 64.dp, tint: Color = TextPrimary) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(iconRes, tint, 26.dp)
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
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Label(text, 14.sp, TextSecondary, FontWeight.Medium, modifier, letterSpacing = 0.2.sp)
}

@Composable
private fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(1.dp)
            .background(Brush.horizontalGradient(listOf(Hairline, HairlineFaint))),
    )
}

@Composable
private fun VerticalHairline() {
    Box(Modifier.padding(horizontal = 20.dp).width(1.dp).fillMaxHeight(0.55f).background(Hairline))
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
