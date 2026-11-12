package com.nanugil.app.ui.navi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanugil.app.R
import com.nanugil.app.ui.settlement.qrBitmap
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.navi.NaviState

/*
 * 와이어프레임 v5 화면들 (Figma "나누길 와이어프레임 v5", 프레임 1682×1210 = Pleos 앱 영역).
 * 지도는 NaviDriveScreen이 맨 아래에 깔고, 여기 카드들은 그 위에 겹친다.
 * 수치는 시안 그대로(1px ≈ 1dp): 바깥 여백 32, 위쪽 바 높이 88, 카드 시작 143, 카드 사이 24.
 */

internal val OuterPad = 32.dp
internal val CardTop = 143.dp
internal val CardGap = 24.dp
internal val InnerPad = 30.dp
internal val SmallShape = RoundedCornerShape(14.dp)

// 카드 너비 (시안: 넓은 왼쪽 952 + 오른쪽 638, 반반 793 + 795, 주행 559 + 478, 코드 입력 1031 + 557)
internal val WideLeft = 952.dp
internal val NarrowRight = 638.dp
internal val HalfLeft = 793.dp
internal val HalfRight = 795.dp
internal val KeypadLeft = 1031.dp
internal val KeypadRight = 557.dp

/** 화면 흐름 이벤트 (ViewModel 함수에 연결) */
data class GroupActions(
    val onTab: (NaviTab) -> Unit = {},
    val onBack: () -> Unit = {},
    val onOpenDrive: () -> Unit = {},
    val onOpenCreate: () -> Unit = {},
    val onOpenJoin: () -> Unit = {},
    val onOpenGroup: (GroupSummary) -> Unit = {},
    val onCreateFormChange: (CreateGroupForm) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onSearchDestination: () -> Unit = {},
    val onNewCode: () -> Unit = {},
    val onAddPassenger: () -> Unit = {},
    val onStartTrip: () -> Unit = {},
    val onCodeKey: (Char) -> Unit = {},
    val onCodeDelete: () -> Unit = {},
    val onJoin: () -> Unit = {},
    val onCarNameChange: (String) -> Unit = {},
    val onTogglePassenger: (String) -> Unit = {},
    val onDepart: () -> Unit = {},
    val onPriceBasis: (PriceBasis) -> Unit = {},
    val onEditToll: () -> Unit = {},
    /** 09: 송금 한 줄을 누르면 받음 체크를 바꾼다 */
    val onTogglePaid: (Int) -> Unit = {},
    /** 09: 운전자 카카오페이 송금 링크 등록·변경 */
    val onEditPayLink: () -> Unit = {},
    val onRequestSettlement: () -> Unit = {},
    val onGoHome: () -> Unit = {},
)

// ================= 위쪽 바: 로고(또는 뒤로) · 제목 · [드라이브|내 차|정산] · 상태 칩 =================

/** 오른쪽 위 상태 칩 */
internal enum class TopChip { STOPPED, DRIVING, HOST, END_TRIP }

@Composable
internal fun AppTopBar(
    p: NaviPalette,
    title: String,
    showBack: Boolean,
    tab: NaviTab,
    chip: TopChip,
    onBack: () -> Unit,
    onTab: (NaviTab) -> Unit,
    onSearch: () -> Unit,
    onEndTrip: () -> Unit,
    modifier: Modifier,
) {
    Panel(p, modifier.height(88.dp), GlassShape) {
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            // 왼쪽: 로고 또는 뒤로 + 제목
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    Box(
                        Modifier.size(MinTouch).clip(RoundedCornerShape(16.dp)).background(p.navSelected).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) { Icon(R.drawable.ic_back, p.textPrimary, 28.dp) }
                } else {
                    Image(
                        painterResource(R.drawable.logo_nanugil),
                        contentDescription = "나누길",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(CircleShape),
                    )
                }
                Label(title, 31.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 18.dp).width(560.dp))
            }

            // 가운데: 탭
            Row(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp))
                    .background(if (p.dark) Color(0x66000000) else Color(0x140F172A)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NaviTab.entries.forEach { t ->
                    val selected = t == tab
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (selected) p.navSelected else Color.Transparent)
                            .clickable { onTab(t) }
                            .heightIn(min = MinTouch)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.iconRes, if (selected) BrandPrimary else p.navIcon, 24.dp)
                        Label(
                            t.label, 23.sp, if (selected) p.textPrimary else p.navIcon,
                            if (selected) FontWeight.Bold else FontWeight.Medium, Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }

            // 오른쪽: 검색 · 테마 · 상태 칩
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                SmallIconButton(p, R.drawable.ic_search, onSearch)
                StatusChip(p, chip, onEndTrip)
            }
        }
    }
}

@Composable
private fun SmallIconButton(p: NaviPalette, iconRes: Int, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).size(MinTouch).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(iconRes, p.navIcon, 28.dp) }
}

@Composable
private fun StatusChip(p: NaviPalette, chip: TopChip, onEndTrip: () -> Unit) {
    val (icon, text) = when (chip) {
        TopChip.STOPPED -> R.drawable.ic_pause to "정차 중"
        TopChip.DRIVING -> R.drawable.ic_nav_arrow to "주행 중"
        TopChip.HOST -> R.drawable.ic_person to "방장"
        TopChip.END_TRIP -> R.drawable.ic_stop to "운행 종료"
    }
    val danger = chip == TopChip.END_TRIP
    val accent = when {
        danger -> BrandDanger
        chip == TopChip.DRIVING -> BrandPrimary
        else -> p.textSecondary
    }
    Row(
        Modifier.height(MinTouch).clip(RoundedCornerShape(16.dp))
            .background(if (danger) BrandDanger.copy(alpha = 0.18f) else p.navSelected)
            .clickable(enabled = danger, onClick = onEndTrip)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, accent, 22.dp)
        Label(text, 22.sp, if (danger) BrandDanger else p.textPrimary, FontWeight.Bold, Modifier.padding(start = 10.dp))
    }
}

// ================= 01 홈 =================

@Composable
internal fun HomeScreen(
    p: NaviPalette,
    navi: NaviState,
    state: GroupUiState,
    carsDriving: Int,
    actions: GroupActions,
) {
    Box(Modifier.fillMaxSize().padding(start = OuterPad, end = OuterPad, top = CardTop)) {
        // 진행 중인 운행
        Panel(p, Modifier.width(WideLeft), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "진행 중인 운행")
                val group = state.group
                if (navi.guiding) {
                    Title(p, group?.name ?: navi.destinationName ?: "운행 중", Modifier.padding(top = 4.dp))
                    navi.remainTimeSeconds?.let { remain ->
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                            Label(arrivalTime(remain), 76.sp, p.textPrimary, FontWeight.ExtraBold, letterSpacing = (-2).sp)
                            Label("도착", 22.sp, p.textSecondary, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
                        }
                        Label("${formatMinutes(remain)} 남음", 22.sp, p.textSecondary)
                    }
                    MetaRow(
                        p,
                        listOf(
                            R.drawable.ic_pin to (group?.destinationName ?: navi.destinationName ?: "목적지"),
                            R.drawable.ic_car to "${carsDriving.coerceAtLeast(1)}대",
                            R.drawable.ic_people to "${group?.members?.size ?: MockGroups.members.size}명",
                        ),
                        Modifier.padding(top = 18.dp),
                    )
                    PrimaryButton("드라이브 열기", true, actions.onOpenDrive, Modifier.padding(top = 22.dp).width(220.dp))
                } else {
                    Title(p, "아직 출발 전이에요", Modifier.padding(top = 4.dp))
                    Label(
                        "그룹을 만들거나 초대 코드로 참여한 뒤 운행을 시작하세요",
                        22.sp, p.textSecondary, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        Column(Modifier.align(Alignment.TopEnd).width(NarrowRight), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            // 내 그룹
            Panel(p, Modifier.fillMaxWidth(), CardShape) {
                Column(Modifier.padding(InnerPad)) {
                    Caption(p, "내 그룹")
                    state.myGroups.forEachIndexed { i, g ->
                        if (i > 0) Divider(p)
                        Row(
                            Modifier.fillMaxWidth().clickable { actions.onOpenGroup(g) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LogoBadge(p, g.logo, 52.dp)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Label(g.name, 24.sp, p.textPrimary, FontWeight.Bold)
                                Label("멤버 ${g.memberCount}명", 19.sp, p.textSecondary)
                            }
                            if (g.driving) Tag(p, "운행 중", accent = true) else Icon(R.drawable.ic_chevron_right, p.textSecondary, 28.dp)
                        }
                    }
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconPrimaryButton("그룹 만들기", R.drawable.ic_plus, actions.onOpenCreate, Modifier.weight(1f))
                        SecondaryButton(p, "코드로 참여", actions.onOpenJoin, Modifier.weight(1f))
                    }
                }
            }
            // 최근 정산
            Panel(p, Modifier.fillMaxWidth(), CardShape) {
                Column(Modifier.padding(InnerPad)) {
                    Caption(p, "최근 정산")
                    state.recent.forEachIndexed { i, r ->
                        if (i > 0) Divider(p)
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label(r.name, 24.sp, p.textPrimary, FontWeight.Bold)
                                Label(r.date, 19.sp, p.textSecondary)
                            }
                            Label(won(r.perPersonWon.toLong()), 25.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(end = 14.dp))
                            Tag(p, r.status, accent = !r.done)
                        }
                    }
                }
            }
        }
    }
}

// ================= 02 그룹 만들기 =================

@Composable
internal fun CreateGroupScreen(p: NaviPalette, form: CreateGroupForm, destinationName: String, actions: GroupActions) {
    Box(Modifier.fillMaxSize().padding(start = OuterPad, end = OuterPad, top = CardTop)) {
        Panel(p, Modifier.width(HalfLeft), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "모임 이름")
                InputField(p, form.name, { actions.onCreateFormChange(form.copy(name = it)) })
                Caption(p, "목적지", Modifier.padding(top = 24.dp))
                FieldBox(p, Modifier.clickable(onClick = actions.onSearchDestination)) {
                    Icon(R.drawable.ic_pin, p.textSecondary, 24.dp)
                    Label(destinationName, 25.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 12.dp).weight(1f))
                    Icon(R.drawable.ic_search, p.textSecondary, 24.dp)
                }
                Caption(p, "내 이름", Modifier.padding(top = 24.dp))
                InputField(p, form.myName, { actions.onCreateFormChange(form.copy(myName = it)) })
            }
        }
        Panel(p, Modifier.align(Alignment.TopEnd).width(HalfRight), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "모임 로고")
                Label("지도 위 일행 차량에 표시", 23.sp, p.textPrimary, modifier = Modifier.padding(top = 2.dp))
                GroupLogo.entries.chunked(4).forEachIndexed { row, logos ->
                    Row(
                        Modifier.padding(top = if (row == 0) 20.dp else 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        logos.forEach { logo ->
                            val selected = logo == form.logo
                            Box(
                                Modifier.weight(1f).height(140.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) BrandPrimary.copy(alpha = 0.22f) else p.secondaryButton)
                                    .border(2.dp, if (selected) BrandPrimary else Color.Transparent, RoundedCornerShape(16.dp))
                                    .clickable { actions.onCreateFormChange(form.copy(logo = logo)) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(logo.iconRes, if (selected) BrandPrimary else p.textPrimary, 48.dp) }
                        }
                    }
                }
                PrimaryButton("만들고 코드 받기", form.name.isNotBlank(), actions.onCreate, Modifier.padding(top = 24.dp).fillMaxWidth())
            }
        }
    }
}

// ================= 03 초대 코드 (대기실) =================

@Composable
internal fun LobbyScreen(p: NaviPalette, group: GroupInfo, actions: GroupActions) {
    Box(Modifier.fillMaxSize().padding(start = OuterPad, end = OuterPad, top = CardTop)) {
        Panel(p, Modifier.width(WideLeft), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "초대 코드")
                CodeBoxes(p, group.code, Modifier.padding(top = 14.dp))
                Row(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label("0/O, 1/I 제외", 21.sp, p.textSecondary, modifier = Modifier.weight(1f))
                    Row(
                        Modifier.height(64.dp).clip(SmallShape).background(p.secondaryButton)
                            .clickable(onClick = actions.onNewCode).padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(R.drawable.ic_refresh, p.textPrimary, 24.dp)
                        Label("새 코드", 23.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 8.dp))
                    }
                }
                Divider(p, Modifier.padding(top = 20.dp))
                MetaRow(
                    p,
                    listOf(
                        R.drawable.ic_pin to group.destinationName,
                        R.drawable.ic_car to "${group.cars}대",
                        R.drawable.ic_people to "${group.members.size}명",
                    ),
                    Modifier.padding(top = 18.dp),
                )
            }
        }
        Panel(p, Modifier.align(Alignment.TopEnd).width(NarrowRight), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "멤버 ${group.members.size}명")
                group.members.forEachIndexed { i, m ->
                    if (i > 0) Divider(p)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p, m.initial, accent = m.host)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Label(m.name, 24.sp, p.textPrimary, FontWeight.Bold)
                            Label(m.roleLabel, 19.sp, p.textSecondary)
                        }
                        if (m.host) Tag(p, "방장", accent = true)
                    }
                }
                Row(
                    Modifier.padding(top = 18.dp).fillMaxWidth().height(72.dp).clip(SmallShape)
                        .background(p.secondaryButton).clickable(onClick = actions.onAddPassenger),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(R.drawable.ic_plus, p.secondaryButtonText, 24.dp)
                    Label("동승자 추가", 25.sp, p.secondaryButtonText, FontWeight.Bold, Modifier.padding(start = 8.dp))
                }
                PrimaryButton("운행 시작", true, actions.onStartTrip, Modifier.padding(top = 14.dp).fillMaxWidth())
            }
        }
    }
}

// ================= 04 코드로 참여 =================

@Composable
internal fun JoinCodeScreen(p: NaviPalette, join: JoinState, actions: GroupActions) {
    Box(Modifier.fillMaxSize().padding(start = OuterPad, end = OuterPad, top = CardTop)) {
        Panel(p, Modifier.width(KeypadLeft), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption(p, "초대 코드", Modifier.weight(1f))
                    if (join.code.isNotEmpty()) {
                        Label("지우기", 21.sp, p.textSecondary, FontWeight.Bold, Modifier.clip(SmallShape).clickable(onClick = actions.onCodeDelete).heightIn(min = MinTouch).padding(horizontal = 16.dp, vertical = 18.dp))
                    }
                }
                CodeBoxes(p, join.code, Modifier.padding(top = 14.dp).clickable(onClick = actions.onCodeDelete))
                // 0/O, 1/I를 뺀 32글자 키패드
                MockGroups.CODE_CHARS.chunked(8).forEachIndexed { row, keys ->
                    Row(Modifier.padding(top = if (row == 0) 22.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        keys.forEach { k ->
                            Box(
                                Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(12.dp))
                                    .background(p.secondaryButton).clickable { actions.onCodeKey(k) },
                                contentAlignment = Alignment.Center,
                            ) { Label(k.toString(), 28.sp, p.textPrimary, FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        Panel(p, Modifier.align(Alignment.TopEnd).width(KeypadRight), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                val found = join.found
                if (found == null) {
                    Caption(p, "찾은 그룹")
                    Label("코드 6자리를 입력하세요", 26.sp, p.textSecondary, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoBadge(p, found.logo, 60.dp)
                        Column(Modifier.padding(start = 16.dp)) {
                            Caption(p, "찾은 그룹")
                            Label(found.name, 30.sp, p.textPrimary, FontWeight.Bold)
                        }
                    }
                    InfoRow(p, "멤버", "${found.members.size}명", Modifier.padding(top = 18.dp))
                    Divider(p)
                    InfoRow(p, "방장", found.host?.name ?: "-")
                    Divider(p)
                    InfoRow(p, "목적지", found.destinationName)
                    PrimaryButton("참여하기", true, actions.onJoin, Modifier.padding(top = 18.dp).fillMaxWidth())
                }
            }
        }
    }
}

// ================= 05 운행 참가 =================

@Composable
internal fun JoinSetupScreen(p: NaviPalette, join: JoinState, actions: GroupActions) {
    val group = join.found ?: return
    Box(Modifier.fillMaxSize().padding(start = OuterPad, end = OuterPad, top = CardTop)) {
        Column(Modifier.width(HalfLeft), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            Panel(p, Modifier.fillMaxWidth(), CardShape) {
                Column(Modifier.padding(InnerPad)) {
                    Caption(p, "운행 시작됨")
                    Title(p, group.destinationName, Modifier.padding(top = 4.dp))
                    MetaRow(
                        p,
                        listOf(R.drawable.ic_person to (group.host?.name ?: "-"), R.drawable.ic_clock to "09:40 출발"),
                        Modifier.padding(top = 12.dp),
                    )
                }
            }
            Panel(p, Modifier.fillMaxWidth(), CardShape) {
                Column(Modifier.padding(InnerPad)) {
                    Caption(p, "내 차 이름")
                    InputField(p, join.carName, actions.onCarNameChange)
                    Caption(p, "연료", Modifier.padding(top = 24.dp))
                    FieldBox(p) {
                        Icon(R.drawable.ic_fuel, p.textSecondary, 24.dp)
                        Label(join.fuelType, 25.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 12.dp).weight(1f))
                        // TODO(차량): Pleos Vehicle SDK 연료 종류로 자동 확인
                        Label("자동 확인", 21.sp, p.textSecondary)
                    }
                }
            }
        }
        Panel(p, Modifier.align(Alignment.TopEnd).width(HalfRight), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "내 차 동승자")
                join.passengers.forEachIndexed { i, c ->
                    if (i > 0) Divider(p)
                    val enabled = c.takenBy == null
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = enabled) { actions.onTogglePassenger(c.member.name) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBox(p, checked = c.selected, enabled = enabled)
                        Avatar(p, c.member.initial, accent = false, modifier = Modifier.padding(start = 16.dp))
                        Label(
                            c.member.name, 24.sp, if (enabled) p.textPrimary else p.textSecondary,
                            FontWeight.Bold, Modifier.padding(start = 16.dp).weight(1f),
                        )
                        c.takenBy?.let { Tag(p, it, accent = false) }
                    }
                }
                PrimaryButton(
                    if (join.selectedCount > 0) "동승자 ${join.selectedCount}명과 출발" else "혼자 출발",
                    true, actions.onDepart, Modifier.padding(top = 18.dp).fillMaxWidth(),
                )
            }
        }
    }
}

// ================= 06·07 주행 중: 오른쪽 일행 카드 · 합류 제안 카드 =================

/** 06 일행: A차(나) 38분, B차 정차 46분 */
@Composable
internal fun GroupCarsCard(p: NaviPalette, cars: List<CarState>, navi: NaviState) {
    Panel(p, Modifier.fillMaxWidth(), CardShape) {
        Column(Modifier.padding(InnerPad)) {
            Caption(p, "일행")
            cars.forEachIndexed { i, car ->
                if (i > 0) Divider(p)
                val label = car.label
                val eta = if (car.isMe) navi.remainTimeSeconds?.let { it / 60 } ?: car.etaMinutes else car.etaMinutes
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, label, accent = car.isMe)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Label("${label}차", 24.sp, p.textPrimary, FontWeight.Bold)
                        Label(
                            when {
                                car.isMe -> "나"
                                car.speedKmh == 0 -> "정차"
                                else -> "주행"
                            },
                            19.sp, p.textSecondary,
                        )
                    }
                    Label("${eta}분", 25.sp, p.textPrimary, FontWeight.Bold)
                }
            }
        }
    }
}

/** 07 합류 제안: "가평휴게소에서 합류" · 6.2km 전방 · B차 연료 12% · [합류하기] [건너뛰기] (말로도 답할 수 있다) */
@Composable
internal fun MeetupCard(
    p: NaviPalette,
    proposal: MeetupProposal,
    listening: Boolean,
    heard: String,
    onAccept: () -> Unit,
    onSkip: () -> Unit,
    onVoice: () -> Unit,
) {
    Panel(p, Modifier.fillMaxWidth(), CardShape) {
        Column(Modifier.padding(InnerPad)) {
            Label("${proposal.placeName}에서 합류", 31.sp, p.textPrimary, FontWeight.Bold)
            MetaRow(
                p,
                listOfNotNull(
                    proposal.distanceMeters?.let { R.drawable.ic_pin to "${formatKm(it)} 전방" },
                    R.drawable.ic_fuel to "${proposal.carLabel} 연료 ${proposal.fuelPercent}%",
                ),
                Modifier.padding(top = 10.dp),
                accentIcons = true,
            )
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("합류하기", true, onAccept, Modifier.weight(1f))
                SecondaryButton(p, "건너뛰기", onSkip, Modifier.weight(1f))
            }
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().heightIn(min = MinTouch).clip(SmallShape)
                    .clickable(enabled = !listening, onClick = onVoice).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (listening) BrandDanger else p.navSelected),
                    contentAlignment = Alignment.Center,
                ) { Icon(R.drawable.ic_mic, if (listening) Color.White else p.textPrimary, 22.dp) }
                Label(
                    when {
                        listening && heard.isNotBlank() -> "“$heard”"
                        listening -> "\"합류\" 또는 \"건너뛰기\"라고 말하세요"
                        else -> "말로 답하기"
                    },
                    21.sp, p.textSecondary, modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

// ================= 08 도착 (정산 내역) =================
// 화면 틀은 와이어프레임 v5, 값은 정산 세션(SettlementSession: 차량 기록·근처 유가·전기차 충전)에서 온다.
// 글자를 넣는 값 수정은 P단 전용 SettlementActivity에서 한다.

@Composable
internal fun ArrivalScreen(p: NaviPalette, s: SettlementUi, actions: GroupActions) {
    Box(Modifier.fillMaxSize().padding(start = OuterPad, end = OuterPad, top = CardTop)) {
        Panel(p, Modifier.width(HalfLeft), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "1인")
                BigAmount(p, s.perPersonWon)
                Label("총 ${won(s.totalWon)} | ${s.people}명", 22.sp, p.textSecondary)
                s.cars.forEachIndexed { i, c ->
                    Divider(p, Modifier.padding(top = if (i == 0) 20.dp else 0.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p, c.label.take(1), accent = i == 0)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Label(c.label, 24.sp, p.textPrimary, FontWeight.Bold)
                            Label("${c.energyLabel} ${num(c.fuelWon)} | 통행료 ${num(c.tollWon)}", 19.sp, p.textSecondary)
                        }
                        Label(won(c.totalWon), 26.sp, p.textPrimary, FontWeight.Bold)
                    }
                }
                Caption(p, "유가 기준", Modifier.padding(top = 18.dp))
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(SmallShape).background(p.secondaryButton).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PriceBasis.entries.forEach { b ->
                        val selected = b == s.basis
                        Box(
                            Modifier.weight(1f).height(MinTouch).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) (if (p.dark) Color(0xFF111215) else Color.White) else Color.Transparent)
                                .clickable { actions.onPriceBasis(b) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Label(b.label, 23.sp, if (selected) p.textPrimary else p.textSecondary, if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                // 유가를 가져온 주유소(최저가·직접 고름) 또는 근처를 못 불러온 이유
                s.basisDetail?.let { Label(it, 19.sp, p.textSecondary, modifier = Modifier.padding(top = 10.dp)) }
                s.problem?.let { Label(it, 19.sp, BrandAmber, FontWeight.Bold, Modifier.padding(top = 10.dp)) }
            }
        }
        Panel(p, Modifier.align(Alignment.TopEnd).width(HalfRight), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "주고받을 돈")
                s.lines.forEachIndexed { i, line ->
                    if (i > 0) Divider(p)
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p, line.initial, accent = line.highlight)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Label(line.name, 24.sp, p.textPrimary, FontWeight.Bold)
                            Label(line.detail, 19.sp, p.textSecondary)
                        }
                        Label(
                            (if (line.amountWon > 0) "+" else "−") + won(kotlin.math.abs(line.amountWon)),
                            26.sp, if (line.amountWon > 0) BrandPrimary else p.textPrimary, FontWeight.Bold,
                        )
                    }
                }
                Divider(p)
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("합계", 22.sp, p.textSecondary, modifier = Modifier.weight(1f))
                    Label("${won(s.sumCheckWon)} 일치", 22.sp, p.textSecondary)
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(p, "값 수정", actions.onEditToll, Modifier.weight(1f))
                    PrimaryButton("정산 요청", true, actions.onRequestSettlement, Modifier.weight(1f))
                }
            }
        }
    }
}

// ================= 09 정산 요청 보냄 =================

@Composable
internal fun SettleSentScreen(p: NaviPalette, s: SettlementUi, actions: GroupActions) {
    Box(Modifier.fillMaxSize().padding(start = OuterPad, end = OuterPad, top = CardTop)) {
        Panel(p, Modifier.width(HalfLeft), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Box(
                    Modifier.size(92.dp).clip(CircleShape).background(BrandPrimary)
                        .border(6.dp, BrandPrimary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(R.drawable.ic_check, Color.White, 46.dp) }
                Title(p, "정산 요청 보냄", Modifier.padding(top = 22.dp))
                Label("${s.myName}에게 카카오페이로 | 1인 ${won(s.perPersonWon)}", 22.sp, p.textSecondary, modifier = Modifier.padding(top = 4.dp))
                // 운전자 송금 링크 QR. 동승자가 폰 카메라로 찍으면 카카오페이 송금이 열린다(금액은 직접 입력).
                val link = s.payLink
                if (link != null) {
                    val qr = remember(link) { qrBitmap(link) }
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                        Image(qr, "${s.myName} 카카오페이 송금 QR", Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)))
                        Label("링크 변경", 19.sp, BrandPrimary, FontWeight.Bold, Modifier.padding(start = 16.dp).clickable(onClick = actions.onEditPayLink))
                    }
                } else {
                    PrimaryButton("송금 링크 등록", true, actions.onEditPayLink, Modifier.padding(top = 16.dp).width(260.dp))
                }
                SecondaryButton(p, "홈으로", actions.onGoHome, Modifier.padding(top = 18.dp).width(150.dp))
            }
        }
        Panel(p, Modifier.align(Alignment.TopEnd).width(HalfRight), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Caption(p, "받은 사람 ${s.paidCount}/${s.payments.size} · 받으면 눌러서 체크")
                // 한 화면에 4명씩. 넘치면 아래 [이전]·[다음]으로 넘겨 모든 사람을 확인한다.
                val pages = paymentPages(s.payments.size)
                var page by remember(pages) { mutableIntStateOf(0) }
                val from = page * PAYMENTS_PER_PAGE
                s.payments.drop(from).take(PAYMENTS_PER_PAGE).forEachIndexed { i, pay ->
                    if (i > 0) Divider(p)
                    Row(
                        Modifier.fillMaxWidth().clickable { actions.onTogglePaid(from + i) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p, pay.initial, accent = false)
                        Label(pay.name, 24.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 16.dp).weight(1f))
                        Label(won(pay.amountWon), 22.sp, p.textSecondary, modifier = Modifier.padding(end = 12.dp))
                        Tag(p, if (pay.paid) "송금 완료" else "확인 전", accent = pay.paid)
                    }
                }
                if (pages > 1) {
                    Divider(p)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton(p, "‹ 이전", { if (page > 0) page-- }, Modifier.width(140.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Label("${page + 1} / $pages", 22.sp, p.textSecondary, FontWeight.Bold)
                        }
                        SecondaryButton(p, "다음 ›", { if (page < pages - 1) page++ }, Modifier.width(140.dp))
                    }
                }
            }
        }
    }
}

/** 09 송금 목록 한 페이지에 보이는 줄 수 */
internal const val PAYMENTS_PER_PAGE = 4

/** 송금 [count]줄을 몇 페이지로 나누는지 (0줄도 1페이지) */
internal fun paymentPages(count: Int): Int = maxOf(1, (count + PAYMENTS_PER_PAGE - 1) / PAYMENTS_PER_PAGE)

// ================= 주행 중 입력 막기 =================

/**
 * 주행 중(P단 아님)에 02 그룹 만들기·04 코드로 참여·05 운행 참가 대신 보여 주는 카드.
 * NaviDriveActivity가 distractionOptimized라서 주행 중엔 글자 입력 화면을 열면 안 된다 (#9 기본 규칙, App Market 심사).
 * 입력하던 값은 ViewModel에 남아 있어서 P단에 놓으면 그대로 이어서 쓴다.
 */
@Composable
internal fun ParkOnlyCard(p: NaviPalette, gearUnknown: Boolean, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = OuterPad, top = CardTop)) {
        Panel(p, Modifier.width(HalfLeft), CardShape) {
            Column(Modifier.padding(InnerPad)) {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(BrandAmber.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Label("P", 40.sp, BrandAmber, FontWeight.Bold) }
                Title(
                    p,
                    if (gearUnknown) "기어 상태를 확인하고 있어요" else "정차 중에만 입력할 수 있어요",
                    Modifier.padding(top = 22.dp),
                )
                Label(
                    "차를 세우고 P단에 놓으면 이어서 입력할 수 있어요. 입력하던 내용은 그대로 있어요.",
                    22.sp, p.textSecondary, modifier = Modifier.padding(top = 8.dp),
                )
                SecondaryButton(p, "돌아가기", onBack, Modifier.padding(top = 22.dp).width(200.dp))
            }
        }
    }
}

// ================= 작은 부품 =================

@Composable
internal fun Caption(p: NaviPalette, text: String, modifier: Modifier = Modifier) =
    Label(text, 20.sp, p.textSecondary, modifier = modifier)

@Composable
private fun Title(p: NaviPalette, text: String, modifier: Modifier = Modifier) =
    Label(text, 31.sp, p.textPrimary, FontWeight.Bold, modifier)

@Composable
private fun BigAmount(p: NaviPalette, amount: Long) =
    Label(won(amount), 70.sp, p.textPrimary, FontWeight.ExtraBold, Modifier.padding(top = 2.dp), letterSpacing = (-2).sp)

@Composable
private fun Divider(p: NaviPalette, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(p.divider))
}

@Composable
private fun MetaRow(
    p: NaviPalette,
    items: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
    accentIcons: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (icon, text) ->
            if (i > 0) Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(22.dp).background(p.divider))
            Icon(icon, if (accentIcons) BrandPrimary else p.textSecondary, 22.dp)
            Label(text, 22.sp, p.textPrimary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
internal fun Tag(p: NaviPalette, text: String, accent: Boolean) {
    Label(
        text, 19.sp, if (accent) Color(0xFF6E9BFF) else p.textSecondary, FontWeight.Bold,
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (accent) BrandPrimary.copy(alpha = 0.18f) else p.secondaryButton)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
internal fun Avatar(p: NaviPalette, text: String, accent: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(if (accent) BrandPrimary else p.secondaryButton),
        contentAlignment = Alignment.Center,
    ) { Label(text, 19.sp, if (accent) Color.White else p.textPrimary, FontWeight.Bold) }
}

@Composable
private fun LogoBadge(p: NaviPalette, logo: GroupLogo, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (p.dark) Color(0xFF0F1012) else Color(0xFF0F172A)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo == GroupLogo.CAR) {
            Image(painterResource(R.drawable.logo_nanugil), null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        } else {
            Icon(logo.iconRes, Color.White, size * 0.5f)
        }
    }
}

@Composable
private fun CodeBoxes(p: NaviPalette, code: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(MockGroups.CODE_LENGTH) { i ->
            val ch = code.getOrNull(i)
            Box(
                Modifier.weight(1f).height(132.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (ch != null) p.secondaryButton else p.navSelected)
                    .border(2.dp, if (ch == null && i == code.length) BrandPrimary.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { if (ch != null) Label(ch.toString(), 62.sp, p.textPrimary, FontWeight.ExtraBold) }
        }
    }
}

@Composable
internal fun FieldBox(p: NaviPalette, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.padding(top = 10.dp).fillMaxWidth().height(76.dp).clip(SmallShape)
            .background(p.navSelected).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun InputField(p: NaviPalette, value: String, onChange: (String) -> Unit) {
    FieldBox(p) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = p.textPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(BrandPrimary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoRow(p: NaviPalette, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 22.sp, p.textSecondary, modifier = Modifier.weight(1f))
        Label(value, 24.sp, p.textPrimary, FontWeight.Bold)
    }
}

@Composable
private fun CheckBox(p: NaviPalette, checked: Boolean, enabled: Boolean) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
            .background(if (checked) BrandPrimary else p.secondaryButton),
        contentAlignment = Alignment.Center,
    ) { if (checked) Icon(R.drawable.ic_check, Color.White, 24.dp) else if (!enabled) Spacer(Modifier) }
}

@Composable
private fun IconPrimaryButton(text: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.height(72.dp).clip(SmallShape).background(BrandPrimary).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconRes, Color.White, 24.dp)
        Label(text, 25.sp, Color.White, FontWeight.Bold, Modifier.padding(start = 8.dp))
    }
}

private fun num(v: Long): String = "%,d".format(v)
private fun won(v: Long): String = "%,d원".format(v)

// ================= 미리보기 (Android Studio) =================

@androidx.compose.ui.tooling.preview.Preview(name = "N빵 정산 · 다크", widthDp = 1682, heightDp = 1210, backgroundColor = 0xFF0A0A0B, showBackground = true)
@Composable
private fun ArrivalScreenPreview() {
    ArrivalScreen(DarkPalette, MockGroups.settlement(PriceBasis.AVERAGE), GroupActions())
}

@androidx.compose.ui.tooling.preview.Preview(name = "주행 중 · 일행/합류 카드", widthDp = 560, heightDp = 420, backgroundColor = 0xFF0A0A0B, showBackground = true)
@Composable
private fun MeetupCardPreview() {
    Box(Modifier.padding(24.dp)) {
        MeetupCard(
            DarkPalette,
            MeetupProposal(carLabel = "B차", driverName = "운전자 B", placeName = "가평휴게소", distanceMeters = 6_200, fuelPercent = 12),
            listening = false, heard = "", onAccept = {}, onSkip = {}, onVoice = {},
        )
    }
}
