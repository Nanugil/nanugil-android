package com.nanugil.app.ui.settlement

import android.util.Log
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.fuel.FuelProduct
import com.nanugil.app.data.fuel.GasStation
import com.nanugil.app.data.fuel.OpinetException
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.pay.SettlementRequestStore
import com.nanugil.app.data.vehicle.TripEnergyRecorder
import com.nanugil.app.share.Settlement
import com.nanugil.app.share.SettlementRequest
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 유가를 어떻게 골랐는지. 08 도착 화면의 [근처 평균]·[근처 최저가] 버튼과, 값 수정에서 주유소를 직접 고른 경우. */
enum class PriceChoice { NEARBY_AVERAGE, NEARBY_LOWEST, PICKED }

/**
 * 한 번의 운행 정산에 쓰는 값 전부. 08 도착(#13 화면)과 값 수정 화면이 같은 상태를 본다.
 *
 * @property chosen 유종별로 유가를 가져온 주유소. 근처 평균이면 비어 있다.
 * @property request 보낸 정산 요청(09 화면). 보내기 전이면 null.
 * @property tripId 서버의 진행 중 운행. 없으면 데모 값으로 정산한다.
 * @property serverNote 서버 연결 상태 안내 (예: 데모 값 사용, 비용 저장 실패). 문제없으면 null.
 */
data class SettlementState(
    val cars: List<CarInput>,
    val stations: Map<FuelProduct, List<GasStation>> = emptyMap(),
    val chosen: Map<FuelProduct, GasStation> = emptyMap(),
    val prices: Map<FuelProduct, Int> = emptyMap(),
    val priceSource: PriceSource = PriceSource.LOADING,
    val choice: PriceChoice = PriceChoice.NEARBY_LOWEST,
    val request: SettlementRequest? = null,
    val tripId: String? = null,
    val serverNote: String? = null,
) {
    /** 이 차(A차) 운전자. 이 사람에게 송금한다. */
    val myDriver: String get() = cars.firstOrNull()?.driver?.trim().orEmpty()

    /** 입력값으로 바로 정산한다. 값이 틀리면 결과 대신 고칠 내용. */
    val settled: Pair<Settlement?, String?> by lazy { settleOrMessage(cars) }
}

/**
 * 정산 상태를 화면 밖에 둔다. 주행 중에도 보이는 #13 화면(08·09)은 이 값을 보여 주기만 하고,
 * 글자를 넣는 값 수정은 P단 전용 [SettlementActivity]에서 한다. 앱 전체에 하나([com.nanugil.app.NanugilApplication.settlement]).
 */
class SettlementSession(
    private val recorder: TripEnergyRecorder,
    private val requests: SettlementRequestStore,
    private val readMyCarType: suspend () -> VehicleType?,
    private val loadNearby: suspend (FuelProduct, Wgs84Point) -> List<GasStation>,
    private val loadAverage: suspend () -> Map<FuelProduct, Int>,
    private val initialCars: () -> List<CarInput> = ::demoCars,
    private val server: SettlementServer? = null,
) {
    private val _state = MutableStateFlow(SettlementState(cars = initialCars(), request = requests.load()))
    val state: StateFlow<SettlementState> = _state.asStateFlow()

    private val loadLock = Mutex()
    private var loaded = false

    /**
     * 정산 화면이 처음 열릴 때 한 번: 운행 기록으로 내 차 칸을 채우고, 차종을 읽고, [here] 근처 유가를 불러온다.
     * 다음 운행은 [finish] 뒤에 다시 채운다.
     */
    suspend fun load(here: Wgs84Point?) = loadLock.withLock {
        if (loaded) return@withLock
        loaded = true
        loadTrip()
        _state.update { s -> s.copy(cars = s.cars.mapFirst { it.withRecorded(recorder.fuel, recorder.battery) }) }
        readMyCarType()?.let { type ->
            _state.update { s -> s.copy(cars = s.cars.mapFirst { if (it.type != type) it.withType(type, s.prices) else it }) }
        }
        loadPrices(here)
    }

    // 서버에 진행 중 운행이 있으면 그 참가자·차량별 비용으로 바꾼다. 없거나 연결이 안 되면 데모 값 그대로.
    private suspend fun loadTrip() {
        val server = server ?: return
        try {
            val trip = server.loadTrip()
            if (trip == null || trip.cars.isEmpty()) {
                _state.update { it.copy(serverNote = "진행 중인 운행이 없어 예시 값으로 정산합니다") }
            } else {
                _state.update { it.copy(cars = trip.cars, tripId = trip.tripId, serverNote = null) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "운행 불러오기 실패", e)
            _state.update { it.copy(serverNote = "서버에 연결하지 못해 예시 값으로 정산합니다") }
        }
    }

    /**
     * 정산 요청 뒤 내 차(A차) 비용을 서버에 저장한다(T6 도착 → T8). 다른 차 앱이 이 값으로 같은 정산을 계산한다.
     * 운행이 없으면 아무것도 안 한다. 실패해도 정산 요청은 그대로 두고 안내만 남긴다.
     */
    suspend fun saveMyCost() {
        val s = _state.value
        val tripId = s.tripId ?: return
        val server = server ?: return
        val car = s.cars.firstOrNull() ?: return
        try {
            server.saveMyCost(tripId, car, s.choice)
            _state.update { it.copy(serverNote = null) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "내 차 비용 저장 실패", e)
            _state.update { it.copy(serverNote = "내 차 비용을 서버에 저장하지 못했습니다") }
        }
    }

    // 근처 최저가 → 전국 평균 → 예시값 순. 사용자가 이미 고친 유가 칸은 건드리지 않는다.
    private suspend fun loadPrices(here: Wgs84Point?) {
        // shortcut: 내비 현재 위치를 아직 못 받았으면 데모 출발지(서울시청)를 쓴다. 실제 차에서는 위치가 늘 있다.
        val at = here ?: MockGroupDriveData.START
        val nearby = coroutineScope {
            NEARBY_PRODUCTS.map { p -> async { try { p to loadNearby(p, at) } catch (e: OpinetException) { null } } }
                .awaitAll().filterNotNull().filter { it.second.isNotEmpty() }.toMap()
        }
        val (prices, source) = if (nearby.isNotEmpty()) {
            val chosen = cheapestByProduct(nearby)
            _state.update { it.copy(stations = nearby, chosen = chosen, choice = PriceChoice.NEARBY_LOWEST) }
            chosen.mapValues { it.value.priceWon } to PriceSource.NEARBY
        } else {
            try {
                loadAverage() to PriceSource.OPINET
            } catch (e: OpinetException) {
                _state.update { it.copy(priceSource = PriceSource.EXAMPLE) }
                return
            }
        }
        _state.update { s ->
            s.copy(
                prices = prices,
                priceSource = source,
                cars = s.cars.map { c -> if (c.fuelPrice == c.type.defaultFuelPrice) c.copy(fuelPrice = c.type.priceText(prices)) else c },
            )
        }
    }

    /** 08의 [근처 평균]·[근처 최저가]. 그 유종을 쓰는 차 유가를 모두 바꾼다. */
    fun choose(choice: PriceChoice) {
        _state.update { s ->
            if (s.stations.isEmpty()) return@update s
            val chosen = if (choice == PriceChoice.NEARBY_AVERAGE) emptyMap() else cheapestByProduct(s.stations)
            val prices = if (choice == PriceChoice.NEARBY_AVERAGE) {
                s.stations.mapValues { (_, list) -> list.map { it.priceWon }.average().roundToInt() }
            } else {
                chosen.mapValues { it.value.priceWon }
            }
            s.withPrices(prices).copy(chosen = chosen, choice = choice)
        }
    }

    /** 값 수정의 주유소 목록에서 직접 고른 주유소. */
    fun choose(product: FuelProduct, station: GasStation) {
        _state.update { s ->
            s.withPrices(s.prices + (product to station.priceWon)).copy(chosen = s.chosen + (product to station), choice = PriceChoice.PICKED)
        }
    }

    fun setCars(cars: List<CarInput>) = _state.update { it.copy(cars = cars) }

    /**
     * 정산 요청. 결과를 확정해 저장한 뒤 운행 기록을 지운다(순서가 바뀌면 앱이 죽었을 때 둘 다 잃는다).
     * @return 보낸 요청. 값이 틀려 정산할 수 없으면 null.
     */
    fun request(): SettlementRequest? {
        val s = _state.value
        val result = s.settled.first ?: return null
        val sent = SettlementRequest.of(result, s.myDriver).also(requests::save)
        recorder.reset()
        _state.update { it.copy(request = sent) }
        return sent
    }

    fun togglePaid(index: Int) {
        val sent = _state.value.request?.toggled(index)?.also(requests::save) ?: return
        _state.update { it.copy(request = sent) }
    }

    /** 이번 정산을 끝내고 다음 운행을 준비한다. 다음에 정산 화면을 열면 새 운행 기록으로 다시 채운다. */
    fun finish() {
        requests.clear()
        loaded = false
        _state.value = SettlementState(cars = initialCars())
    }

    private fun SettlementState.withPrices(prices: Map<FuelProduct, Int>) = copy(
        prices = prices,
        cars = cars.map { c -> c.type.opinetProduct?.let(prices::get)?.let { c.copy(fuelPrice = it.toString()) } ?: c },
    )

    private fun List<CarInput>.mapFirst(f: (CarInput) -> CarInput) = mapIndexed { i, c -> if (i == 0) f(c) else c }

    private companion object {
        const val TAG = "SettlementSession"
    }
}

/**
 * 서버가 붙기 전 데모 그룹(#13 MockGroups와 같은 사람들). A차가 이 기기가 달린 내 차라 운행 기록으로 다시 채워진다.
 * TODO(서버): 운행 참가자·차량을 서버(trip_members)에서 받는다.
 */
internal fun demoCars(): List<CarInput> = listOf(
    CarInput(VehicleType.GASOLINE, "김민수", "정예린, 한도윤, 오지민", startFuelL = "40", endFuelL = "26.875", tollWon = "10000"),
    CarInput(VehicleType.GASOLINE, "이서준", "박지우, 최하은, 윤서아", startFuelL = "50", endFuelL = "20", tollWon = "15000"),
)
