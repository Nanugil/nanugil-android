package com.nanugil.app.ui.settlement

import android.car.FuelType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanugil.app.data.fuel.FuelProduct
import com.nanugil.app.data.fuel.GasStation
import com.nanugil.app.data.fuel.OpinetBrand
import com.nanugil.app.data.fuel.OpinetException
import com.nanugil.app.data.vehicle.RefillDetector
import com.nanugil.app.share.Settlement
import com.nanugil.app.share.SettlementCalculator
import com.nanugil.app.share.VehicleTrip
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 공공 충전요금(원/kWh). 기후에너지환경부 「전기차 공공충전요금 체계 개편안」(2026.8.1 시행, 정책브리핑 2026.7.9).
 * 공공 충전기와 환경부 회원카드로 결제하는 민간 충전기에 적용된다. 민간 자체 요금은 칸에서 직접 고친다.
 */
enum class ChargerTier(val label: String, val price: String) {
    SLOW("30kW 미만", "295.0"),
    KW30("30~50kW", "307.2"),
    KW50("50~100kW", "325.6"),
    KW100("100~200kW", "348.4"),
    KW200("200kW 이상", "393.1"),
}

/** 단체 이동 중에는 고속도로 급속충전이 많아 100~200kW 구간을 기본값으로 둔다. */
private val DEFAULT_CHARGER_TIER = ChargerTier.KW100

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

/**
 * 차가 알려 준 연료 종류(AAOS `INFO_FUEL_TYPE`, [FuelType] 상수)로 차종을 정한다. 모르는 조합이면 null.
 * 고급휘발유는 차가 따로 알려 주지 않아 휘발유로 본다. 충전구 없는 일반 하이브리드는 휘발유로만 나올 수 있다.
 */
internal fun vehicleTypeFromFuelTypes(types: IntArray): VehicleType? {
    val electric = FuelType.ELECTRIC in types
    val gasoline = FuelType.UNLEADED in types || FuelType.LEADED in types
    val diesel = FuelType.DIESEL_1 in types || FuelType.DIESEL_2 in types || FuelType.BIODIESEL in types
    return when {
        electric && (gasoline || diesel) -> VehicleType.HYBRID
        electric -> VehicleType.ELECTRIC
        gasoline -> VehicleType.GASOLINE
        diesel -> VehicleType.DIESEL
        else -> null
    }
}

private val RefillDetector.isRecorded get() = start != null && latest != null

/**
 * 내 차 차종. 차가 알려 준 차종([reported])을 운행 기록으로 보정한다.
 * 연료와 배터리가 둘 다 기록되면 하이브리드(일반 하이브리드가 휘발유로만 알려 주는 경우),
 * 배터리만 기록되면 전기차로 본다.
 */
internal fun myCarType(reported: VehicleType?, fuel: RefillDetector, battery: RefillDetector): VehicleType? = when {
    fuel.isRecorded && battery.isRecorded -> VehicleType.HYBRID
    battery.isRecorded && reported != VehicleType.HYBRID -> VehicleType.ELECTRIC
    else -> reported
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
    val electricityPrice: String = DEFAULT_CHARGER_TIER.price,
    val startBatteryKwh: String = "",
    val chargedKwh: String = "0",
    val endBatteryKwh: String = "",
    val tollWon: String = "0",
    /** 서버 운행의 운전자 멤버 ID. 다시 읽은 운행과 차를 맞출 때 쓴다(이름은 겹칠 수 있다). 서버 운행이 아니면 빈칸. */
    val memberId: String = "",
    /** 서버 trip_members.vehicle_label (방장=A차, 참여자=B차…). 모든 기기에서 같은 이름이 보이게 순서 대신 이 값을 쓴다. 없으면 빈칸. */
    val label: String = "",
) {
    /** 차종을 바꾸고, 유가는 오피넷 평균가([prices])가 있으면 그 값, 없으면 예시값으로 바꾼다. */
    fun withType(newType: VehicleType, prices: Map<FuelProduct, Int> = emptyMap()) =
        copy(type = newType, fuelPrice = newType.priceText(prices))

    /** 운행 중 기록한 값(출발, 주유·충전량, 현재)을 칸에 넣는다. 기록이 없는 쪽은 그대로 둔다. 차종은 [myCarType]이 정한다. */
    fun withRecorded(fuel: RefillDetector, battery: RefillDetector): CarInput {
        var car = this
        if (fuel.isRecorded) car = car.copy(
            startFuelL = milliToDecimalText(fuel.start!!),
            refueledL = milliToDecimalText(fuel.added),
            endFuelL = milliToDecimalText(fuel.latest!!),
        )
        if (battery.isRecorded) car = car.copy(
            startBatteryKwh = milliToDecimalText(battery.start!!),
            chargedKwh = milliToDecimalText(battery.added),
            endBatteryKwh = milliToDecimalText(battery.latest!!),
        )
        return car
    }

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
        // 원/kWh(소수 가능) → 원/MWh
        electricityWonPerMwh = if (battery) decimalToMilli(car.electricityPrice, "$label 충전 단가", required = true) else 0,
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


/** 처음 들어왔을 때 바로 눌러 볼 수 있게 채워 두는 예시 값. */
internal fun exampleCars(type: VehicleType): List<CarInput> = listOf(
    CarInput(type, "지수", "민호, 서연", startFuelL = "40", endFuelL = "30", startBatteryKwh = "70", endBatteryKwh = "50", tollWon = "5000"),
    CarInput(type, "현우", "다은", startFuelL = "30", endFuelL = "25", startBatteryKwh = "60", endBatteryKwh = "48", tollWon = "3500"),
)



/** 유가 칸을 어디서 채웠는지. 요약 카드의 "유가 기준"에 보여 준다. 근처 주유소는 고른 주유소 이름을 따로 보여 준다. */
enum class PriceSource(val text: String) {
    NONE("직접 입력"),
    LOADING("불러오는 중"),
    NEARBY("근처 주유소"),
    OPINET("오피넷 전국 평균"),
    EXAMPLE("예시 유가 · 실제 값으로 고쳐 주세요"),
}

/** 근처 주유소를 찾는 유종. 하이브리드는 휘발유를 쓴다. */
internal val NEARBY_PRODUCTS = listOf(FuelProduct.GASOLINE, FuelProduct.DIESEL, FuelProduct.PREMIUM_GASOLINE)

/** 주유소 목록 정렬. */
internal enum class StationOrder(val label: String) {
    NEAREST("가까운순"),
    CHEAPEST("최저가순"),
}

/** 정렬한 주유소 목록. 같으면 다른 기준으로 한 번 더 정렬해 순서가 흔들리지 않게 한다. */
internal fun List<GasStation>.sortedFor(order: StationOrder): List<GasStation> = when (order) {
    StationOrder.NEAREST -> sortedWith(compareBy({ it.distanceMeters }, { it.priceWon }))
    StationOrder.CHEAPEST -> sortedWith(compareBy({ it.priceWon }, { it.distanceMeters }))
}

/** 유종마다 기본으로 고르는 주유소: 근처 최저가(같으면 가까운 곳). */
internal fun cheapestByProduct(stations: Map<FuelProduct, List<GasStation>>): Map<FuelProduct, GasStation> =
    stations.mapNotNull { (product, list) -> list.sortedFor(StationOrder.CHEAPEST).firstOrNull()?.let { product to it } }.toMap()

/** 입력값으로 바로 정산한다. 값이 비었거나 틀리면 결과 대신 고칠 내용을 돌려준다. */
internal fun settleOrMessage(cars: List<CarInput>): Pair<Settlement?, String?> = try {
    SettlementCalculator.settle(toTrips(cars)) to null
} catch (e: IllegalArgumentException) {
    null to e.message
} catch (e: ArithmeticException) {
    null to "입력한 값이 너무 큽니다."
}

/** 차 이름. Figma 08처럼 A차, B차 … 로 부른다. 1번 차(A차)가 이 기기가 달린 내 차다. */
internal fun carName(index: Int): String = "${'A' + index}차"

/** 서버 차 이름이 있으면 그 이름, 없으면 순서대로 A차, B차 … */
internal fun CarInput.name(index: Int): String = label.ifBlank { carName(index) }
