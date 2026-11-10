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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanugil.app.share.Settlement
import com.nanugil.app.share.SettlementCalculator
import com.nanugil.app.share.VehicleTrip
import java.math.RoundingMode

/** 화면에서 입력받는 차량 한 대의 값. 사용자가 친 글자 그대로 들고 있다가 계산할 때 [toTrips]로 바꾼다. */
data class CarInput(
    val driver: String = "",
    val passengers: String = "",
    val startFuelL: String = "",
    val refueledL: String = "0",
    val endFuelL: String = "",
    val tollWon: String = "0",
)

/**
 * 입력값을 [VehicleTrip] 목록으로 바꾼다. 연료는 L(소수 가능)로 받아 mL로, 금액은 원 단위 정수로 받는다.
 * 출발·도착 연료는 비워 두면 거부하고, 주유량·통행료는 비우면 0으로 본다.
 *
 * @throws IllegalArgumentException 입력이 비었거나 숫자가 아닐 때. 메시지는 화면에 그대로 보여 줄 문장이다.
 */
fun toTrips(cars: List<CarInput>, fuelPriceWonPerLiter: String): List<VehicleTrip> {
    val price = won(fuelPriceWonPerLiter, "유가", required = true)
    return cars.mapIndexed { i, car ->
        val label = "${i + 1}번 차"
        require(car.driver.isNotBlank()) { "$label 운전자 이름을 입력하세요." }
        VehicleTrip(
            vehicleId = "car${i + 1}",
            driverId = car.driver.trim(),
            passengerIds = car.passengers.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            startFuelMl = litersToMl(car.startFuelL, "$label 출발 연료", required = true),
            refueledMl = litersToMl(car.refueledL, "$label 주유량", required = false),
            endFuelMl = litersToMl(car.endFuelL, "$label 도착 연료", required = true),
            fuelPriceWonPerLiter = price,
            tollWon = won(car.tollWon, "$label 통행료", required = false),
        )
    }
}

internal fun litersToMl(text: String, field: String, required: Boolean): Long {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        require(!required) { "$field 값을 입력하세요." }
        return 0
    }
    val liters = requireNotNull(trimmed.toBigDecimalOrNull()) { "$field 값이 숫자가 아닙니다: $text" }
    return liters.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
}

internal fun won(text: String, field: String, required: Boolean): Long {
    val trimmed = text.replace(",", "").trim()
    if (trimmed.isEmpty()) {
        require(!required) { "$field 값을 입력하세요." }
        return 0
    }
    return requireNotNull(trimmed.toLongOrNull()) { "$field 값은 원 단위 정수로 입력하세요: $text" }
}

private fun Long.wonText() = "%,d원".format(this)

/** 운전자가 주행 값을 직접 넣고 정산 결과를 보는 화면. 차량 데이터 연동 전까지 수동 입력으로 쓴다. */
@Composable
fun SettlementScreen(onBack: () -> Unit) {
    // 처음 들어왔을 때 바로 눌러 볼 수 있게 예시 값을 채워 둔다.
    val cars = remember {
        mutableStateListOf(
            CarInput("지수", "민호, 서연", "40", "0", "30", "5000"),
            CarInput("현우", "다은", "30", "0", "25", "3500"),
        )
    }
    var price by remember { mutableStateOf("1700") }
    var result by remember { mutableStateOf<Settlement?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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
            Row { Field("유가 (원/L)", price, KeyboardType.Number) { price = it } }

            cars.forEachIndexed { i, car ->
                CarCard(
                    index = i,
                    car = car,
                    onChange = { cars[i] = it },
                    onRemove = if (cars.size > 1) ({ cars.removeAt(i) }) else null,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { cars.add(CarInput()) }) { Text("차량 추가") }
                Button(onClick = {
                    try {
                        result = SettlementCalculator.settle(toTrips(cars, price))
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
private fun CarCard(index: Int, car: CarInput, onChange: (CarInput) -> Unit, onRemove: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}번 차", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                onRemove?.let { TextButton(onClick = it) { Text("삭제") } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("운전자", car.driver) { onChange(car.copy(driver = it)) }
                Field("동승자 (쉼표로 구분)", car.passengers) { onChange(car.copy(passengers = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("출발 연료 (L)", car.startFuelL, KeyboardType.Decimal) { onChange(car.copy(startFuelL = it)) }
                Field("주유량 (L)", car.refueledL, KeyboardType.Decimal) { onChange(car.copy(refueledL = it)) }
                Field("도착 연료 (L)", car.endFuelL, KeyboardType.Decimal) { onChange(car.copy(endFuelL = it)) }
                Field("통행료 (원)", car.tollWon, KeyboardType.Number) { onChange(car.copy(tollWon = it)) }
            }
        }
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
        Text("${i + 1}번 차 (${cars.getOrNull(i)?.driver?.trim().orEmpty()}): ${cost.wonText()}")
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
    MaterialTheme { SettlementScreen(onBack = {}) }
}
