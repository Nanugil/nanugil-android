package com.nanugil.app.ui.settlement

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanugil.app.data.fuel.FuelProduct
import com.nanugil.app.data.fuel.GasStation
import com.nanugil.app.data.fuel.OpinetBrand
import com.nanugil.app.ui.navi.Avatar
import com.nanugil.app.ui.navi.BrandAmber
import com.nanugil.app.ui.navi.BrandDanger
import com.nanugil.app.ui.navi.BrandPrimary
import com.nanugil.app.ui.navi.CardShape
import com.nanugil.app.ui.navi.Caption
import com.nanugil.app.ui.navi.FieldBox
import com.nanugil.app.ui.navi.InnerPad
import com.nanugil.app.ui.navi.Label
import com.nanugil.app.ui.navi.MinTouch
import com.nanugil.app.ui.navi.NaviPalette
import com.nanugil.app.ui.navi.Panel
import com.nanugil.app.ui.navi.PrimaryButton
import com.nanugil.app.ui.navi.SecondaryButton
import com.nanugil.app.ui.navi.SmallShape
import com.nanugil.app.ui.navi.Tag
import kotlinx.coroutines.launch

/**
 * 값 수정 화면 (와이어프레임 v5 #13 디자인). 정산 요약·요청은 #13의 08·09 화면이 [SettlementSession] 값을 보여 주고,
 * 글자를 넣는 입력만 여기서 한다(P단 전용 [SettlementActivity]).
 *
 * @param p #13 위쪽 바에서 고른 테마(라이트·다크)의 색 묶음
 * @param readMyFuelMl 내 차(A차) 현재 연료량(mL)을 읽는 함수. 있으면 A차 출발·도착 칸에 "읽기"가 생긴다.
 * @param readMyBatteryWh 내 차(A차) 현재 배터리 잔량(Wh)을 읽는 함수.
 */
@Composable
internal fun SettlementEditScreen(
    session: SettlementSession,
    p: NaviPalette,
    onBack: () -> Unit,
    readMyFuelMl: (suspend () -> Long)? = null,
    readMyBatteryWh: (suspend () -> Long)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    // 입력 칸은 칸마다 바로 고치는 목록을 쓴다. 고칠 때마다 세션에 넘겨 08 화면도 같은 값을 본다.
    val cars = remember { mutableStateListOf(*state.cars.toTypedArray()) }
    LaunchedEffect(Unit) { snapshotFlow { cars.toList() }.collect(session::setCars) }
    // 주유소를 고르면 세션이 유가를 바꾸므로 그 값을 칸에 다시 받는다.
    LaunchedEffect(state.prices) {
        state.cars.forEachIndexed { i, c -> if (i < cars.size && cars[i].fuelPrice != c.fuelPrice) cars[i] = cars[i].copy(fuelPrice = c.fuelPrice) }
    }
    var readError by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    // 차량 값은 이 기기가 달린 차(A차)에서만 읽을 수 있다.
    fun readInto(read: suspend () -> Long, update: (CarInput, String) -> CarInput) {
        scope.launch {
            try {
                cars[0] = update(cars[0], milliToDecimalText(read()))
                readError = null
            } catch (e: IllegalStateException) {
                readError = e.message
            }
        }
    }

    Column(Modifier.fillMaxSize().background(p.background).padding(horizontal = 32.dp, vertical = 24.dp)) {
        TopBar(
            p,
            title = if (picking) "주유소 선택" else "값 수정",
            onBack = if (picking) ({ picking = false }) else onBack,
            action = if (!picking && state.stations.isNotEmpty()) "주유소 선택" to { picking = true } else null,
        )
        Box(Modifier.padding(top = 20.dp).weight(1f)) {
            if (picking) {
                // 차들이 쓰는 유종만, 내 차(A차) 유종부터 보여 준다.
                val products = cars.mapNotNull { it.type.opinetProduct }.distinct().filter { it in state.stations }
                StationPicker(p, products, state.stations, state.chosen, session::choose)
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    cars.forEachIndexed { i, car ->
                        CarCard(
                            p, i, car, state.prices,
                            onChange = { cars[i] = it },
                            onRemove = if (cars.size > 1) ({ cars.removeAt(i) }) else null,
                            onReadFuel = readMyFuelMl?.takeIf { i == 0 }?.let { read ->
                                { isStart: Boolean -> readInto(read) { c, t -> if (isStart) c.copy(startFuelL = t) else c.copy(endFuelL = t) } }
                            },
                            onReadBattery = readMyBatteryWh?.takeIf { i == 0 }?.let { read ->
                                { isStart: Boolean -> readInto(read) { c, t -> if (isStart) c.copy(startBatteryKwh = t) else c.copy(endBatteryKwh = t) } }
                            },
                        )
                    }
                    SecondaryButton(p, "+  차량 추가", { cars.add(CarInput().withType(VehicleType.GASOLINE, state.prices)) }, Modifier.fillMaxWidth())
                }
            }
        }
        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            val message = if (picking) null else readError ?: settleOrMessage(cars.toList()).second
            Label(message.orEmpty(), 21.sp, BrandAmber, FontWeight.Bold, Modifier.weight(1f))
            PrimaryButton("완료", true, if (picking) ({ picking = false }) else onBack, Modifier.width(240.dp))
        }
    }
}

/** 위쪽 바: 돌아가기 · 제목 · 오른쪽 버튼 하나 (#13 위쪽 바와 같은 유리 패널) */
@Composable
internal fun TopBar(p: NaviPalette, title: String, onBack: () -> Unit, action: Pair<String, () -> Unit>? = null) {
    Panel(p, Modifier.fillMaxWidth().height(96.dp), CardShape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(p, "‹ 돌아가기", onBack, Modifier.width(200.dp))
            Label(title, 31.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 24.dp).weight(1f))
            action?.let { (text, onClick) -> SecondaryButton(p, text, onClick, Modifier.width(220.dp)) }
        }
    }
}

/** #13 08 화면의 [근처 평균 | 근처 최저가]와 같은 모양의 선택 버튼 줄 */
@Composable
private fun <T> Segmented(p: NaviPalette, items: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(SmallShape).background(p.secondaryButton).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            val on = item == selected
            Box(
                Modifier.height(MinTouch).clip(RoundedCornerShape(12.dp))
                    .background(if (on) (if (p.dark) Color(0xFF111215) else Color.White) else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Label(label(item), 22.sp, if (on) p.textPrimary else p.textSecondary, if (on) FontWeight.Bold else FontWeight.Medium) }
        }
    }
}

/** 이름표 + #13 입력 칸. [action]은 이름표 오른쪽 작은 글자 버튼(예: "읽기"). */
@Composable
private fun RowScope.Field(
    p: NaviPalette,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    weight: Float = 1f,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(Modifier.weight(weight)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption(p, label, Modifier.weight(1f))
            action?.let { (text, onClick) -> Label(text, 20.sp, BrandPrimary, FontWeight.Bold, Modifier.clickable(onClick = onClick)) }
        }
        FieldBox(p) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                textStyle = TextStyle(color = p.textPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(BrandPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CarCard(
    p: NaviPalette,
    index: Int,
    car: CarInput,
    prices: Map<FuelProduct, Int>,
    onChange: (CarInput) -> Unit,
    onRemove: (() -> Unit)?,
    onReadFuel: ((isStart: Boolean) -> Unit)?,
    onReadBattery: ((isStart: Boolean) -> Unit)?,
) {
    Panel(p, Modifier.fillMaxWidth(), CardShape) {
        Column(Modifier.padding(InnerPad), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // 머리: 차 이름 · 차종 고르기 · 삭제
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p, "${'A' + index}", accent = index == 0)
                Label(carName(index), 28.sp, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 16.dp))
                if (index == 0) Box(Modifier.padding(start = 12.dp)) { Tag(p, "내 차", accent = true) }
                Spacer(Modifier.weight(1f))
                Segmented(p, VehicleType.entries, car.type, { it.label }, { if (it != car.type) onChange(car.withType(it, prices)) })
                onRemove?.let { Label("삭제", 22.sp, BrandDanger, FontWeight.Bold, Modifier.padding(start = 20.dp).clickable(onClick = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Field(p, "운전자", car.driver, { onChange(car.copy(driver = it)) })
                Field(p, "동승자 (쉼표로 구분)", car.passengers, { onChange(car.copy(passengers = it)) }, weight = 1.6f)
                Field(p, "통행료 (원)", car.tollWon, { onChange(car.copy(tollWon = it)) }, KeyboardType.Number)
            }
            if (car.type.usesFuel) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field(p, "유가 (원/L)", car.fuelPrice, { onChange(car.copy(fuelPrice = it)) }, KeyboardType.Number)
                    Field(p, "출발 연료 (L)", car.startFuelL, { onChange(car.copy(startFuelL = it)) }, KeyboardType.Decimal,
                        action = onReadFuel?.let { read -> "읽기" to { read(true) } })
                    Field(p, "주유량 (L)", car.refueledL, { onChange(car.copy(refueledL = it)) }, KeyboardType.Decimal)
                    Field(p, "도착 연료 (L)", car.endFuelL, { onChange(car.copy(endFuelL = it)) }, KeyboardType.Decimal,
                        action = onReadFuel?.let { read -> "읽기" to { read(false) } })
                }
            }
            if (car.type.usesBattery) {
                // 충전기 출력을 고르면 공공 충전요금이 단가 칸에 채워진다
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption(p, "충전기 출력", Modifier.padding(end = 16.dp))
                    Segmented(
                        p, ChargerTier.entries, ChargerTier.entries.firstOrNull { it.price == car.electricityPrice },
                        { "${it.label} · ${it.price}원" }, { onChange(car.copy(electricityPrice = it.price)) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field(p, "충전 단가 (원/kWh)", car.electricityPrice, { onChange(car.copy(electricityPrice = it)) }, KeyboardType.Decimal)
                    Field(p, "출발 배터리 (kWh)", car.startBatteryKwh, { onChange(car.copy(startBatteryKwh = it)) }, KeyboardType.Decimal,
                        action = onReadBattery?.let { read -> "읽기" to { read(true) } })
                    Field(p, "충전량 (kWh)", car.chargedKwh, { onChange(car.copy(chargedKwh = it)) }, KeyboardType.Decimal)
                    Field(p, "도착 배터리 (kWh)", car.endBatteryKwh, { onChange(car.copy(endBatteryKwh = it)) }, KeyboardType.Decimal,
                        action = onReadBattery?.let { read -> "읽기" to { read(false) } })
                }
            }
        }
    }
}

/**
 * 주유소 선택: 내 위치 근처 주유소 목록. 가까운순·최저가순으로 정렬하고, 고르면 그 가격이 유가가 된다.
 * 차들이 여러 유종을 쓰면 유종 버튼으로 바꿔 가며 고른다.
 */
@Composable
private fun StationPicker(
    p: NaviPalette,
    products: List<FuelProduct>,
    stations: Map<FuelProduct, List<GasStation>>,
    chosen: Map<FuelProduct, GasStation>,
    onChoose: (FuelProduct, GasStation) -> Unit,
) {
    var product by remember { mutableStateOf(products.firstOrNull()) }
    var order by remember { mutableStateOf(StationOrder.CHEAPEST) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (products.size > 1) Segmented(p, products, product, { it.label }, { product = it })
            Spacer(Modifier.weight(1f))
            Segmented(p, StationOrder.entries, order, { it.label }, { order = it })
        }
        Panel(p, Modifier.fillMaxSize(), CardShape) {
            val current = product
            val list = current?.let { stations[it] }.orEmpty().sortedFor(order)
            if (current == null || list.isEmpty()) {
                Label("근처 주유소를 찾지 못했습니다.", 24.sp, p.textSecondary, modifier = Modifier.padding(InnerPad))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    list.forEach { station ->
                        val selected = chosen[current]?.id == station.id
                        Row(
                            Modifier.fillMaxWidth().clickable { onChoose(current, station) }
                                .background(if (selected) BrandPrimary.copy(alpha = 0.14f) else Color.Transparent)
                                .padding(horizontal = InnerPad, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Label(station.name, 24.sp, p.textPrimary, FontWeight.Bold)
                                Label(
                                    "%s · %.1fkm".format(OpinetBrand.displayName(station.brandCode), station.distanceMeters / 1000.0),
                                    19.sp, p.textSecondary,
                                )
                            }
                            if (selected) Box(Modifier.padding(end = 16.dp)) { Tag(p, "선택됨", accent = true) }
                            Label("%,d원/L".format(station.priceWon), 26.sp, p.textPrimary, FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
