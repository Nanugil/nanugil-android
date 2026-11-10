package com.nanugil.app.ui.settlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanugil.app.data.fuel.FuelProduct
import com.nanugil.app.data.fuel.OpinetException
import com.nanugil.app.share.Settlement
import com.nanugil.app.share.SettlementCalculator
import com.nanugil.app.share.VehicleTrip
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.launch

/** 충전 단가 예시값(원/kWh). 환경부 급속충전 요금 수준. 화면에서 고칠 수 있다. */
private const val DEFAULT_ELECTRICITY_WON_PER_KWH = "347"

/**
 * 차종. 차종에 따라 정산에 쓰는 값이 다르다.
 * - 연료차(경유·휘발유·고급휘발유): 사용 연료(L) × 유가
 * - 전기차: 사용 전력(kWh) × 충전 단가
 * - 하이브리드: 연료비 + 전력비. 일반 하이브리드는 배터리 칸을 비워 두면 0으로 본다.
 *
 * @property defaultFuelPrice 유가 예시값(원/L). 오피넷 평균가를 못 불러왔을 때 쓴다.
 * @property opinetProduct 오피넷 평균가를 가져올 유종. 하이브리드는 휘발유.
 */
enum class VehicleType(
    val label: String,
    val usesFuel: Boolean,
    val usesBattery: Boolean,
    val defaultFuelPrice: String = "",
    val opinetProduct: FuelProduct? = null,
) {
    DIESEL("경유", usesFuel = true, usesBattery = false, defaultFuelPrice = "1600", opinetProduct = FuelProduct.DIESEL),
    GASOLINE("휘발유", usesFuel = true, usesBattery = false, defaultFuelPrice = "1700", opinetProduct = FuelProduct.GASOLINE),
    PREMIUM_GASOLINE("고급휘발유", usesFuel = true, usesBattery = false, defaultFuelPrice = "1950", opinetProduct = FuelProduct.PREMIUM_GASOLINE),
    ELECTRIC("전기차", usesFuel = false, usesBattery = true),
    HYBRID("하이브리드", usesFuel = true, usesBattery = true, defaultFuelPrice = "1700", opinetProduct = FuelProduct.GASOLINE),
}

internal fun VehicleType.priceText(prices: Map<FuelProduct, Int>): String =
    opinetProduct?.let(prices::get)?.toString() ?: defaultFuelPrice

/** 화면에서 입력받는 차량 한 대의 값. 사용자가 친 글자 그대로 들고 있다가 계산할 때 [toTrips]로 바꾼다. */
data class CarInput(
    val type: VehicleType = VehicleType.GASOLINE,
    val driver: String = "",
    val passengers: String = "",
    val fuelPrice: String = type.defaultFuelPrice,
    val startFuelL: String = "",
    val refueledL: String = "0",
    val endFuelL: String = "",
    val electricityPrice: String = DEFAULT_ELECTRICITY_WON_PER_KWH,
    val startBatteryKwh: String = "",
    val chargedKwh: String = "0",
    val endBatteryKwh: String = "",
    val tollWon: String = "0",
) {
    /** 차종을 바꾸고, 유가는 오피넷 평균가([prices])가 있으면 그 값, 없으면 예시값으로 바꾼다. */
    fun withType(newType: VehicleType, prices: Map<FuelProduct, Int> = emptyMap()) =
        copy(type = newType, fuelPrice = newType.priceText(prices))

}

/**
 * 입력값을 [VehicleTrip] 목록으로 바꾼다. 연료는 L, 배터리는 kWh(소수 가능)로 받아 mL·Wh로, 금액은 원 단위 정수로 받는다.
 * 차종이 쓰지 않는 값은 0으로 둔다. 출발·도착 값과 단가는 비우면 거부하고, 주유량·충전량·통행료는 비우면 0으로 본다.
 * 하이브리드의 배터리 값은 비워도 된다(일반 하이브리드는 충전하지 않으므로).
 *
 * @throws IllegalArgumentException 입력이 비었거나 숫자가 아닐 때. 메시지는 화면에 그대로 보여 줄 문장이다.
 */
fun toTrips(cars: List<CarInput>): List<VehicleTrip> = cars.mapIndexed { i, car ->
    val label = "${i + 1}번 차"
    require(car.driver.isNotBlank()) { "$label 운전자 이름을 입력하세요." }
    val fuel = car.type.usesFuel
    val battery = car.type.usesBattery
    val batteryRequired = car.type == VehicleType.ELECTRIC
    VehicleTrip(
        vehicleId = "car${i + 1}",
        driverId = car.driver.trim(),
        passengerIds = car.passengers.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        startFuelMl = if (fuel) decimalToMilli(car.startFuelL, "$label 출발 연료", required = true) else 0,
        refueledMl = if (fuel) decimalToMilli(car.refueledL, "$label 주유량", required = false) else 0,
        endFuelMl = if (fuel) decimalToMilli(car.endFuelL, "$label 도착 연료", required = true) else 0,
        fuelPriceWonPerLiter = if (fuel) won(car.fuelPrice, "$label 유가", required = true) else 0,
        startBatteryWh = if (battery) decimalToMilli(car.startBatteryKwh, "$label 출발 배터리", batteryRequired) else 0,
        chargedWh = if (battery) decimalToMilli(car.chargedKwh, "$label 충전량", required = false) else 0,
        endBatteryWh = if (battery) decimalToMilli(car.endBatteryKwh, "$label 도착 배터리", batteryRequired) else 0,
        electricityWonPerKwh = if (battery) won(car.electricityPrice, "$label 충전 단가", required = true) else 0,
        tollWon = won(car.tollWon, "$label 통행료", required = false),
    )
}

/** "40.5"(L 또는 kWh) → 40500(mL 또는 Wh). 소수 넷째 자리부터는 반올림한다. */
internal fun decimalToMilli(text: String, field: String, required: Boolean): Long {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        require(!required) { "$field 값을 입력하세요." }
        return 0
    }
    val value = requireNotNull(trimmed.toBigDecimalOrNull()) { "$field 값이 숫자가 아닙니다: $text" }
    return value.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
}

/** [decimalToMilli]의 반대. 17160 → "17.16". 화면 칸에 넣으면 그대로 다시 읽힌다. */
internal fun milliToDecimalText(milli: Long): String =
    BigDecimal.valueOf(milli).movePointLeft(3).stripTrailingZeros().toPlainString()

internal fun won(text: String, field: String, required: Boolean): Long {
    val trimmed = text.replace(",", "").trim()
    if (trimmed.isEmpty()) {
        require(!required) { "$field 값을 입력하세요." }
        return 0
    }
    return requireNotNull(trimmed.toLongOrNull()) { "$field 값은 원 단위 정수로 입력하세요: $text" }
}

private fun Long.wonText() = "%,d원".format(this)

/** 처음 들어왔을 때 바로 눌러 볼 수 있게 채워 두는 예시 값. */
internal fun exampleCars(type: VehicleType): List<CarInput> = listOf(
    CarInput(type, "지수", "민호, 서연", startFuelL = "40", endFuelL = "30", startBatteryKwh = "70", endBatteryKwh = "50", tollWon = "5000"),
    CarInput(type, "현우", "다은", startFuelL = "30", endFuelL = "25", startBatteryKwh = "60", endBatteryKwh = "48", tollWon = "3500"),
)

/**
 * 운전자가 주행 값을 넣고 정산 결과를 보는 화면.
 *
 * @param initialType 차종 선택 화면에서 고른 차종. 모든 차의 기본값이 되고, 차마다 바꿀 수 있다.
 * @param readMyFuelMl 내 차(1번 차) 현재 연료량(mL)을 읽는 함수.
 * @param readMyBatteryWh 내 차(1번 차) 현재 배터리 잔량(Wh)을 읽는 함수.
 *   두 함수 모두 있으면 해당 칸 옆에 "차에서 읽기" 버튼이 생기고, 실패하면 [IllegalStateException] 메시지를 보여 준다.
 */
@Composable
fun SettlementScreen(
    initialType: VehicleType,
    onBack: () -> Unit,
    readMyFuelMl: (suspend () -> Long)? = null,
    readMyBatteryWh: (suspend () -> Long)? = null,
    loadFuelPrices: (suspend () -> Map<FuelProduct, Int>)? = null,
) {
    val scope = rememberCoroutineScope()
    val cars = remember { mutableStateListOf(*exampleCars(initialType).toTypedArray()) }
    var result by remember { mutableStateOf<Settlement?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var prices by remember { mutableStateOf<Map<FuelProduct, Int>>(emptyMap()) }
    var priceNote by remember { mutableStateOf(if (loadFuelPrices == null) "" else "오피넷 평균 유가를 불러오는 중입니다.") }

    // 화면이 열리면 오피넷 전국 평균가로 유가 칸을 채운다. 사용자가 이미 고친 칸은 건드리지 않는다.
    LaunchedEffect(Unit) {
        val load = loadFuelPrices ?: return@LaunchedEffect
        try {
            val loaded = load()
            prices = loaded
            cars.indices.forEach { i ->
                val c = cars[i]
                if (c.fuelPrice == c.type.defaultFuelPrice) cars[i] = c.copy(fuelPrice = c.type.priceText(loaded))
            }
            priceNote = "유가는 오피넷 오늘 전국 평균입니다. 실제 넣은 값으로 고칠 수 있습니다."
        } catch (e: OpinetException) {
            priceNote = "오피넷 유가를 불러오지 못해 예시 유가를 넣었습니다. 실제 값으로 고쳐 주세요."
        }
    }

    // 차량 값은 이 기기가 달린 차(1번 차)에서만 읽을 수 있다.
    fun readInto(read: suspend () -> Long, update: (CarInput, String) -> CarInput) {
        scope.launch {
            try {
                val text = milliToDecimalText(read())
                cars[0] = update(cars[0], text)
                error = null
            } catch (e: IllegalStateException) {
                error = e.message
            }
        }
    }

    Row(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            Modifier.weight(1.4f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← 돌아가기") }
                Text("정산하기", style = MaterialTheme.typography.headlineSmall)
            }
            if (priceNote.isNotEmpty()) {
                Text(priceNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            cars.forEachIndexed { i, car ->
                val isMine = i == 0
                CarCard(
                    index = i,
                    car = car,
                    prices = prices,
                    onChange = { cars[i] = it },
                    onRemove = if (cars.size > 1) ({ cars.removeAt(i) }) else null,
                    onReadFuel = readMyFuelMl?.takeIf { isMine }?.let { read ->
                        { isStart: Boolean ->
                            readInto(read) { c, t -> if (isStart) c.copy(startFuelL = t) else c.copy(endFuelL = t) }
                        }
                    },
                    onReadBattery = readMyBatteryWh?.takeIf { isMine }?.let { read ->
                        { isStart: Boolean ->
                            readInto(read) { c, t -> if (isStart) c.copy(startBatteryKwh = t) else c.copy(endBatteryKwh = t) }
                        }
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { cars.add(CarInput(initialType).withType(initialType, prices)) }) { Text("차량 추가") }
                Button(onClick = {
                    try {
                        result = SettlementCalculator.settle(toTrips(cars))
                        error = null
                    } catch (e: IllegalArgumentException) {
                        result = null
                        error = e.message
                    } catch (e: ArithmeticException) {
                        result = null
                        error = "입력한 값이 너무 큽니다."
                    }
                }) { Text("계산하기") }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("정산 결과", style = MaterialTheme.typography.titleLarge)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { SettlementResult(it, cars) }
            if (result == null && error == null) Text("값을 넣고 계산하기를 누르세요.")
        }
    }
}

@Composable
private fun CarCard(
    index: Int,
    car: CarInput,
    prices: Map<FuelProduct, Int>,
    onChange: (CarInput) -> Unit,
    onRemove: (() -> Unit)?,
    onReadFuel: ((isStart: Boolean) -> Unit)?,
    onReadBattery: ((isStart: Boolean) -> Unit)?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}번 차", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                onRemove?.let { TextButton(onClick = it) { Text("삭제") } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleType.entries.forEach { type ->
                    FilterChip(
                        selected = car.type == type,
                        onClick = { if (car.type != type) onChange(car.withType(type, prices)) },
                        label = { Text(type.label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("운전자", car.driver) { onChange(car.copy(driver = it)) }
                Field("동승자 (쉼표로 구분)", car.passengers) { onChange(car.copy(passengers = it)) }
                Field("통행료 (원)", car.tollWon, KeyboardType.Number) { onChange(car.copy(tollWon = it)) }
            }
            if (car.type.usesFuel) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("유가 (원/L)", car.fuelPrice, KeyboardType.Number) { onChange(car.copy(fuelPrice = it)) }
                    Field("출발 연료 (L)", car.startFuelL, KeyboardType.Decimal) { onChange(car.copy(startFuelL = it)) }
                    Field("주유량 (L)", car.refueledL, KeyboardType.Decimal) { onChange(car.copy(refueledL = it)) }
                    Field("도착 연료 (L)", car.endFuelL, KeyboardType.Decimal) { onChange(car.copy(endFuelL = it)) }
                }
                onReadFuel?.let { ReadButtons("연료", it) }
            }
            if (car.type.usesBattery) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("충전 단가 (원/kWh)", car.electricityPrice, KeyboardType.Number) { onChange(car.copy(electricityPrice = it)) }
                    Field("출발 배터리 (kWh)", car.startBatteryKwh, KeyboardType.Decimal) { onChange(car.copy(startBatteryKwh = it)) }
                    Field("충전량 (kWh)", car.chargedKwh, KeyboardType.Decimal) { onChange(car.copy(chargedKwh = it)) }
                    Field("도착 배터리 (kWh)", car.endBatteryKwh, KeyboardType.Decimal) { onChange(car.copy(endBatteryKwh = it)) }
                }
                onReadBattery?.let { ReadButtons("배터리", it) }
            }
        }
    }
}

@Composable
private fun ReadButtons(what: String, read: (isStart: Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { read(true) }) { Text("출발 $what 차에서 읽기") }
        OutlinedButton(onClick = { read(false) }) { Text("도착 $what 차에서 읽기") }
    }
}

@Composable
private fun RowScope.Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun SettlementResult(result: Settlement, cars: List<CarInput>) {
    result.vehicleCostsWon.entries.forEachIndexed { i, (_, cost) ->
        val car = cars.getOrNull(i)
        Text("${i + 1}번 차 (${car?.driver?.trim().orEmpty()}, ${car?.type?.label.orEmpty()}): ${cost.wonText()}")
    }
    HorizontalDivider()
    Text("전체 비용: ${result.totalCostWon.wonText()}")
    Text("1인 부담: ${result.perPersonShareWon.wonText()}", style = MaterialTheme.typography.titleMedium)
    if (result.remainderWon > 0) Text("나머지 ${result.remainderWon.wonText()}은 받을 사람이 덜 받습니다.")
    HorizontalDivider()
    if (result.transfers.isEmpty()) {
        Text("보낼 돈이 없습니다.")
    } else {
        result.transfers.forEach {
            Text("${it.fromId} → ${it.toId}   ${it.amountWon.wonText()}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun SettlementScreenPreview() {
    MaterialTheme { SettlementScreen(initialType = VehicleType.HYBRID, onBack = {}) }
}
