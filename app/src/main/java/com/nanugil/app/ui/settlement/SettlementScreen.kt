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

private fun Long.wonText() = "%,d원".format(this)

/** 처음 들어왔을 때 바로 눌러 볼 수 있게 채워 두는 예시 값. */
internal fun exampleCars(type: VehicleType): List<CarInput> = listOf(
    CarInput(type, "지수", "민호, 서연", startFuelL = "40", endFuelL = "30", startBatteryKwh = "70", endBatteryKwh = "50", tollWon = "5000"),
    CarInput(type, "현우", "다은", startFuelL = "30", endFuelL = "25", startBatteryKwh = "60", endBatteryKwh = "48", tollWon = "3500"),
)



/** 유가 칸을 어디서 채웠는지. 요약 카드의 "유가 기준"에 보여 준다. 근처 주유소는 고른 주유소 이름을 따로 보여 준다. */
private enum class PriceSource(val text: String) {
    NONE("직접 입력"),
    LOADING("불러오는 중"),
    NEARBY("근처 주유소"),
    OPINET("오피넷 전국 평균"),
    EXAMPLE("예시 유가 · 실제 값으로 고쳐 주세요"),
}

/** 근처 주유소를 찾는 유종. 하이브리드는 휘발유를 쓴다. */
private val NEARBY_PRODUCTS = listOf(FuelProduct.GASOLINE, FuelProduct.DIESEL, FuelProduct.PREMIUM_GASOLINE)

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
private fun settleOrMessage(cars: List<CarInput>): Pair<Settlement?, String?> = try {
    SettlementCalculator.settle(toTrips(cars)) to null
} catch (e: IllegalArgumentException) {
    null to e.message
} catch (e: ArithmeticException) {
    null to "입력한 값이 너무 큽니다."
}

/** 차 이름. Figma 08처럼 A차, B차 … 로 부른다. 1번 차(A차)가 이 기기가 달린 내 차다. */
private fun carName(index: Int): String = "${'A' + index}차"

/**
 * 도착 후 정산 화면. 은식님 Figma "08 정산 요약"을 따른다.
 * 왼쪽 카드는 1인 금액·차량별 비용·유가 기준, 오른쪽 카드는 주고받을 돈과 버튼.
 * 값을 고칠 때는 "값 수정"으로 입력 패널을 연다. 고치면 결과가 바로 바뀐다.
 *
 * @param initialType 새 차량의 기본 차종. 차마다 입력 패널에서 바꿀 수 있다.
 * @param readMyFuelMl 내 차(A차) 현재 연료량(mL)을 읽는 함수.
 * @param readMyBatteryWh 내 차(A차) 현재 배터리 잔량(Wh)을 읽는 함수.
 *   두 함수 모두 있으면 A차의 출발·도착 칸에 "차에서 읽기"가 생기고, 실패하면 [IllegalStateException] 메시지를 보여 준다.
 * @param onRequestSettlement '정산 요청'을 눌렀을 때. 정산 결과와 이 차 운전자(A차) 이름을 넘긴다.
 * @param prefillMyCar 운행 중 기록한 값으로 A차(내 차) 칸을 채운다. 화면이 열릴 때 한 번만 적용한다.
 * @param readMyCarType 내 차 차종을 읽는 함수. 읽히면 A차 차종 버튼을 그 값으로 바꾼다.
 * @param loadNearbyStations 내 위치 근처 주유소(유종별)를 불러오는 함수. 있으면 근처 최저가가 유가 기본값이 되고,
 *   요약 카드의 "유가 기준"을 눌러 주유소를 직접 고를 수 있다. 실패하면 [loadFuelPrices](전국 평균)로 넘어간다.
 */
@Composable
fun SettlementScreen(
    initialType: VehicleType,
    onBack: () -> Unit,
    readMyFuelMl: (suspend () -> Long)? = null,
    readMyBatteryWh: (suspend () -> Long)? = null,
    loadFuelPrices: (suspend () -> Map<FuelProduct, Int>)? = null,
    onRequestSettlement: (Settlement, myDriverId: String) -> Unit = { _, _ -> },
    prefillMyCar: (CarInput) -> CarInput = { it },
    readMyCarType: (suspend () -> VehicleType?)? = null,
    loadNearbyStations: (suspend (FuelProduct) -> List<GasStation>)? = null,
) {
    val scope = rememberCoroutineScope()
    val cars = remember {
        val example = exampleCars(initialType)
        mutableStateListOf(prefillMyCar(example[0]), *example.drop(1).toTypedArray())
    }
    var readError by remember { mutableStateOf<String?>(null) }
    var prices by remember { mutableStateOf<Map<FuelProduct, Int>>(emptyMap()) }
    val loadsPrices = loadNearbyStations != null || loadFuelPrices != null
    var priceSource by remember { mutableStateOf(if (loadsPrices) PriceSource.LOADING else PriceSource.NONE) }
    var stations by remember { mutableStateOf<Map<FuelProduct, List<GasStation>>>(emptyMap()) }
    var chosen by remember { mutableStateOf<Map<FuelProduct, GasStation>>(emptyMap()) }
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    // 화면이 열리면 유가 칸을 채운다: 근처 최저가 → 전국 평균 → 예시값 순. 사용자가 이미 고친 칸은 건드리지 않는다.
    LaunchedEffect(Unit) {
        val nearby = loadNearbyStations?.let { load ->
            coroutineScope {
                NEARBY_PRODUCTS.map { product ->
                    async { try { product to load(product) } catch (e: OpinetException) { null } }
                }.awaitAll().filterNotNull().filter { it.second.isNotEmpty() }.toMap()
            }
        }.orEmpty()
        val loaded = if (nearby.isNotEmpty()) {
            stations = nearby
            chosen = cheapestByProduct(nearby)
            priceSource = PriceSource.NEARBY
            chosen.mapValues { it.value.priceWon }
        } else {
            val load = loadFuelPrices ?: return@LaunchedEffect
            try {
                load().also { priceSource = PriceSource.OPINET }
            } catch (e: OpinetException) {
                priceSource = PriceSource.EXAMPLE
                return@LaunchedEffect
            }
        }
        prices = loaded
        cars.indices.forEach { i ->
            val c = cars[i]
            if (c.fuelPrice == c.type.defaultFuelPrice) cars[i] = c.copy(fuelPrice = c.type.priceText(loaded))
        }
    }

    // 주유소를 직접 고르면 그 유종을 쓰는 차의 유가를 모두 그 가격으로 바꾼다.
    fun choose(product: FuelProduct, station: GasStation) {
        chosen = chosen + (product to station)
        prices = prices + (product to station.priceWon)
        cars.indices.forEach { i ->
            if (cars[i].type.opinetProduct == product) cars[i] = cars[i].copy(fuelPrice = station.priceWon.toString())
        }
    }

    // 내 차 차종을 읽어 A차 차종 버튼을 맞춘다. 못 읽으면 그대로 둔다(직접 고르면 된다).
    LaunchedEffect(Unit) {
        val type = readMyCarType?.invoke() ?: return@LaunchedEffect
        if (type != cars[0].type) cars[0] = cars[0].withType(type, prices)
    }

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

    val snapshot = cars.toList()
    val (result, inputMessage) = remember(snapshot) { settleOrMessage(snapshot) }

    val palette = settlementPalette()
    CompositionLocalProvider(LocalSettlementPalette provides palette) {
        Column(Modifier.fillMaxSize().background(palette.background).padding(24.dp)) {
            Header(
                title = when { picking -> "주유소 선택"; editing -> "값 수정"; else -> "정산" },
                onBack = when { picking -> ({ picking = false }); editing -> ({ editing = false }); else -> onBack },
            )
            Box(Modifier.padding(top = 20.dp).fillMaxSize()) {
                if (picking) {
                    // 차들이 쓰는 유종만, 내 차(A차) 유종부터 보여 준다.
                    val products = cars.mapNotNull { it.type.opinetProduct }.distinct().filter { it in stations }
                    StationPicker(
                        products = products,
                        stations = stations,
                        chosen = chosen,
                        onChoose = ::choose,
                        onDone = { picking = false },
                    )
                } else if (editing) {
                    EditPanel(
                        cars = cars,
                        prices = prices,
                        message = readError ?: inputMessage,
                        initialType = initialType,
                        onDone = { editing = false },
                        onReadFuel = readMyFuelMl?.let { read ->
                            { isStart: Boolean -> readInto(read) { c, t -> if (isStart) c.copy(startFuelL = t) else c.copy(endFuelL = t) } }
                        },
                        onReadBattery = readMyBatteryWh?.let { read ->
                            { isStart: Boolean -> readInto(read) { c, t -> if (isStart) c.copy(startBatteryKwh = t) else c.copy(endBatteryKwh = t) } }
                        },
                    )
                } else if (result == null) {
                    // 값이 비었거나 틀리면 요약을 못 만든다. 고칠 내용과 함께 입력 패널로 보낸다.
                    Panel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 32.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Label(inputMessage ?: "값을 넣어 주세요.", TextSize.Body, BrandAmber, FontWeight.Bold, maxLines = 3)
                            PrimaryButton("값 수정", onClick = { editing = true }, modifier = Modifier.width(220.dp))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        // 근처 주유소 기준이면 내 차(A차) 유종의 고른 주유소를 보여 주고, 눌러서 바꿀 수 있다.
                        val myStation = cars[0].type.opinetProduct?.let(chosen::get) ?: chosen.values.firstOrNull()
                        SummaryCard(
                            result, cars, priceSource,
                            priceDetail = myStation?.takeIf { priceSource == PriceSource.NEARBY }
                                ?.let { "${it.name} · %,d원/L · 바꾸기 ›".format(it.priceWon) },
                            onPickStation = if (stations.isNotEmpty()) ({ picking = true }) else null,
                            modifier = Modifier.weight(1f),
                        )
                        TransfersCard(
                            result = result,
                            cars = cars,
                            onEdit = { editing = true },
                            onRequest = { onRequestSettlement(result, cars[0].driver.trim()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 위쪽 바: 돌아가기 · 화면 제목 (Title 40). */
@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    val p = LocalSettlementPalette.current
    Panel(Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("← 돌아가기", onClick = onBack, modifier = Modifier.width(190.dp).height(68.dp))
            Label(title, TextSize.Title, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 24.dp))
        }
    }
}

/** Figma 08 왼쪽 카드: 1인 금액(Display 56), 총액·인원, 차량별 비용, 유가 기준. */
@Composable
private fun SummaryCard(
    result: Settlement,
    cars: List<CarInput>,
    priceSource: PriceSource,
    priceDetail: String?,
    onPickStation: (() -> Unit)?,
    modifier: Modifier,
) {
    val p = LocalSettlementPalette.current
    Panel(modifier) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 30.dp).verticalScroll(rememberScrollState())) {
            Label("1인", TextSize.Caption, p.textSecondary)
            Label(result.perPersonShareWon.wonText(), TextSize.Display, p.textPrimary, FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Label(
                "총 %,d원 | %d명".format(result.totalCostWon, result.balancesWon.size),
                TextSize.Caption, p.textSecondary,
            )
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                result.vehicleCostsWon.entries.forEachIndexed { i, (_, cost) ->
                    val car = cars.getOrNull(i)
                    val toll = car?.let { runCatching { won(it.tollWon, "", required = false) }.getOrNull() } ?: 0L
                    val energyLabel = when (car?.type) {
                        VehicleType.ELECTRIC -> "충전"
                        VehicleType.HYBRID -> "주유·충전"
                        else -> "주유"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar("${'A' + i}", highlighted = i == 0)
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Label(carName(i), TextSize.Body, p.textPrimary, FontWeight.Bold)
                            Label("$energyLabel %,d | 통행료 %,d".format(cost - toll, toll), TextSize.Caption, p.textSecondary)
                        }
                        Label(cost.wonText(), TextSize.Body, p.textPrimary, FontWeight.Bold)
                    }
                }
            }
            if (result.remainderWon > 0) {
                Label(
                    "나머지 ${result.remainderWon.wonText()}은 받을 사람이 덜 받습니다.",
                    TextSize.Caption, p.textSecondary, modifier = Modifier.padding(top = 16.dp),
                )
            }
            Label("유가 기준", TextSize.Caption, p.textSecondary, modifier = Modifier.padding(top = 28.dp))
            Box(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(68.dp)
                    .clip(RoundedCornerShape(14.dp)).background(p.field)
                    .then(if (onPickStation != null) Modifier.clickable(onClick = onPickStation) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Label(
                    priceDetail?.let { "${priceSource.text} · $it" } ?: priceSource.text,
                    TextSize.Caption,
                    if (priceSource == PriceSource.EXAMPLE) BrandAmber else p.textPrimary,
                    FontWeight.Bold,
                )
            }
        }
    }
}

/** Figma 08 오른쪽 카드: 주고받을 돈, 합계 일치, '값 수정'·'정산 요청' 버튼. */
@Composable
private fun TransfersCard(
    result: Settlement,
    cars: List<CarInput>,
    onEdit: () -> Unit,
    onRequest: () -> Unit,
    modifier: Modifier,
) {
    val p = LocalSettlementPalette.current
    // 사람 → "A차 운전" / "A차 동승"
    val roles = buildMap {
        cars.forEachIndexed { i, car ->
            car.passengers.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { put(it, "${carName(i)} 동승") }
        }
        cars.forEachIndexed { i, car -> put(car.driver.trim(), "${carName(i)} 운전") }
    }
    Panel(modifier) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 30.dp)) {
            Label("주고받을 돈", TextSize.Caption, p.textSecondary)
            Column(
                Modifier.padding(top = 16.dp).weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                result.balancesWon.entries
                    .sortedByDescending { it.value }
                    .forEach { (person, balance) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person, highlighted = balance > 0)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Label(person, TextSize.Body, p.textPrimary, FontWeight.Bold)
                                Label(roles[person].orEmpty(), TextSize.Caption, p.textSecondary)
                            }
                            val sign = if (balance > 0) "+" else if (balance < 0) "−" else ""
                            Label(
                                "$sign%,d원".format(kotlin.math.abs(balance)),
                                TextSize.Body,
                                if (balance > 0) BrandPrimary else p.textPrimary,
                                FontWeight.Bold,
                            )
                        }
                    }
                if (result.transfers.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))
                    result.transfers.forEach {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("${it.fromId} → ${it.toId}", TextSize.Caption, p.textSecondary, modifier = Modifier.weight(1f))
                            Label(it.amountWon.wonText(), TextSize.Caption, p.textPrimary, FontWeight.Bold)
                        }
                    }
                }
            }
            Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(p.divider))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("합계", TextSize.Caption, p.textSecondary, modifier = Modifier.weight(1f))
                val receive = result.balancesWon.values.filter { it > 0 }.sum()
                Label("%,d원 ".format(receive), TextSize.Caption, p.textPrimary)
                Label("일치", TextSize.Caption, BrandSuccess, FontWeight.Bold)
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("값 수정", onClick = onEdit, modifier = Modifier.weight(1f))
                PrimaryButton("정산 요청", onClick = onRequest, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * '주유소 선택' 패널: 내 위치 근처 주유소 목록. 가까운순·최저가순으로 정렬하고, 고르면 그 가격이 유가가 된다.
 * 차들이 여러 유종을 쓰면 유종 버튼으로 바꿔 가며 고른다.
 */
@Composable
private fun StationPicker(
    products: List<FuelProduct>,
    stations: Map<FuelProduct, List<GasStation>>,
    chosen: Map<FuelProduct, GasStation>,
    onChoose: (FuelProduct, GasStation) -> Unit,
    onDone: () -> Unit,
) {
    val p = LocalSettlementPalette.current
    var product by remember { mutableStateOf(products.firstOrNull()) }
    var order by remember { mutableStateOf(StationOrder.CHEAPEST) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (products.size > 1) {
                products.forEach { ChipButton(it.label, selected = it == product, onClick = { product = it }) }
            }
            Spacer(Modifier.weight(1f))
            StationOrder.entries.forEach { ChipButton(it.label, selected = it == order, onClick = { order = it }) }
        }
        Panel(Modifier.fillMaxWidth().weight(1f)) {
            val current = product
            val list = current?.let { stations[it] }.orEmpty().sortedFor(order)
            if (current == null || list.isEmpty()) {
                Label("근처 주유소를 찾지 못했습니다.", TextSize.Body, p.textSecondary, modifier = Modifier.padding(32.dp))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    list.forEach { station ->
                        val selected = chosen[current]?.id == station.id
                        Row(
                            Modifier.fillMaxWidth().clickable { onChoose(current, station) }
                                .background(if (selected) BrandPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                .padding(horizontal = 32.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Label(station.name, TextSize.Body, p.textPrimary, FontWeight.Bold)
                                Label(
                                    "%s · %.1fkm".format(OpinetBrand.displayName(station.brandCode), station.distanceMeters / 1000.0),
                                    TextSize.Caption, p.textSecondary,
                                )
                            }
                            if (selected) Label("선택됨", TextSize.Caption, BrandPrimary, FontWeight.Bold, Modifier.padding(end = 20.dp))
                            Label("%,d원/L".format(station.priceWon), TextSize.Body, p.textPrimary, FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row {
            Spacer(Modifier.weight(1f))
            PrimaryButton("완료", onClick = onDone, modifier = Modifier.width(220.dp))
        }
    }
}

/** '값 수정' 패널: 차량별 입력 카드와 차량 추가. 고치면 정산이 바로 다시 계산된다. */
@Composable
private fun EditPanel(
    cars: MutableList<CarInput>,
    prices: Map<FuelProduct, Int>,
    message: String?,
    initialType: VehicleType,
    onDone: () -> Unit,
    onReadFuel: ((isStart: Boolean) -> Unit)?,
    onReadBattery: ((isStart: Boolean) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            cars.forEachIndexed { i, car ->
                CarCard(
                    index = i,
                    car = car,
                    prices = prices,
                    onChange = { cars[i] = it },
                    onRemove = if (cars.size > 1) ({ cars.removeAt(i) }) else null,
                    onReadFuel = onReadFuel?.takeIf { i == 0 },
                    onReadBattery = onReadBattery?.takeIf { i == 0 },
                )
            }
            SecondaryButton(
                "+  차량 추가",
                onClick = { cars.add(CarInput(initialType).withType(initialType, prices)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(message.orEmpty(), TextSize.Caption, BrandAmber, FontWeight.Bold, Modifier.weight(1f), maxLines = 2)
            PrimaryButton("완료", onClick = onDone, modifier = Modifier.width(220.dp))
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
    val p = LocalSettlementPalette.current
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // 머리: 차 이름(Heading 32) · 차종 고르기 · 삭제
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar("${'A' + index}", highlighted = index == 0)
                Label(carName(index), TextSize.Heading, p.textPrimary, FontWeight.Bold, Modifier.padding(start = 16.dp))
                if (index == 0) Label("내 차", TextSize.Caption, BrandPrimary, FontWeight.Bold, Modifier.padding(start = 12.dp))
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VehicleType.entries.forEach { type ->
                        ChipButton(type.label, selected = car.type == type, onClick = {
                            if (car.type != type) onChange(car.withType(type, prices))
                        })
                    }
                }
                onRemove?.let {
                    Label("삭제", TextSize.Caption, BrandDanger, FontWeight.Bold, Modifier.padding(start = 20.dp).clickable(onClick = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InputField("운전자", car.driver, { onChange(car.copy(driver = it)) }, Modifier.weight(1f))
                InputField("동승자 (쉼표로 구분)", car.passengers, { onChange(car.copy(passengers = it)) }, Modifier.weight(1.6f))
                InputField("통행료 (원)", car.tollWon, { onChange(car.copy(tollWon = it)) }, Modifier.weight(1f), KeyboardType.Number)
            }
            if (car.type.usesFuel) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InputField("유가 (원/L)", car.fuelPrice, { onChange(car.copy(fuelPrice = it)) }, Modifier.weight(1f), KeyboardType.Number)
                    InputField(
                        "출발 연료 (L)", car.startFuelL, { onChange(car.copy(startFuelL = it)) }, Modifier.weight(1f), KeyboardType.Decimal,
                        action = onReadFuel?.let { read -> "읽기" to { read(true) } },
                    )
                    InputField("주유량 (L)", car.refueledL, { onChange(car.copy(refueledL = it)) }, Modifier.weight(1f), KeyboardType.Decimal)
                    InputField(
                        "도착 연료 (L)", car.endFuelL, { onChange(car.copy(endFuelL = it)) }, Modifier.weight(1f), KeyboardType.Decimal,
                        action = onReadFuel?.let { read -> "읽기" to { read(false) } },
                    )
                }
            }
            if (car.type.usesBattery) {
                // 충전기 출력을 고르면 공공 충전요금이 단가 칸에 채워진다
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("충전기 출력", TextSize.Caption, p.textSecondary, modifier = Modifier.padding(end = 8.dp))
                    ChargerTier.entries.forEach { tier ->
                        ChipButton("${tier.label} · ${tier.price}원", selected = car.electricityPrice == tier.price, onClick = {
                            onChange(car.copy(electricityPrice = tier.price))
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InputField("충전 단가 (원/kWh)", car.electricityPrice, { onChange(car.copy(electricityPrice = it)) }, Modifier.weight(1f), KeyboardType.Decimal)
                    InputField(
                        "출발 배터리 (kWh)", car.startBatteryKwh, { onChange(car.copy(startBatteryKwh = it)) }, Modifier.weight(1f), KeyboardType.Decimal,
                        action = onReadBattery?.let { read -> "읽기" to { read(true) } },
                    )
                    InputField("충전량 (kWh)", car.chargedKwh, { onChange(car.copy(chargedKwh = it)) }, Modifier.weight(1f), KeyboardType.Decimal)
                    InputField(
                        "도착 배터리 (kWh)", car.endBatteryKwh, { onChange(car.copy(endBatteryKwh = it)) }, Modifier.weight(1f), KeyboardType.Decimal,
                        action = onReadBattery?.let { read -> "읽기" to { read(false) } },
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 1682, heightDp = 1210)
@Composable
private fun SettlementScreenPreview() {
    SettlementScreen(initialType = VehicleType.HYBRID, onBack = {})
}
