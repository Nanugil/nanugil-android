package com.nanugil.app.ui.settlement

import android.util.Log
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.fare.TollRecorder
import com.nanugil.app.data.fare.evTollDiscountPercent
import com.nanugil.app.data.fare.tollWon
import com.nanugil.app.data.fuel.FuelProduct
import com.nanugil.app.data.fuel.GasStation
import com.nanugil.app.data.fuel.OpinetException
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.pay.SettlementRequestStore
import com.nanugil.app.data.vehicle.TripEnergyRecorder
import com.nanugil.app.share.Settlement
import com.nanugil.app.share.SettlementRequest
import java.time.LocalDate
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
 * @property tripId 서버의 진행 중 운행. 서버가 없으면(키 없음) 데모 값으로 정산한다.
 * @property serverNote 서버 연결 상태 안내 (예: 데모 값 사용, 비용 저장 실패). 문제없으면 null.
 * @property iDrive 이 기기 사용자가 A차(맨 앞) 운전자인지. 서버 운행에서 동승자면 false라 내 차 기록으로 채우거나 저장하지 않는다.
 * @property estimatedTollWon 내 차 통행료의 내비 예상액(경로 변경·할인 반영). 안내 없이 달렸으면 null.
 * @property tollDiscountPercent [estimatedTollWon]에 뺀 전기차 통행료 감면율(%)
 * @property myName 이 기기 사용자 이름(서버 운행). 동승자면 [myDriver]와 다르다. 서버가 없으면 빈칸.
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
    val iDrive: Boolean = true,
    val estimatedTollWon: Long? = null,
    val tollDiscountPercent: Int = 0,
    val myName: String = "",
) {
    /** 이 차(A차) 운전자. 이 사람에게 송금한다. */
    val myDriver: String get() = cars.firstOrNull()?.driver?.trim().orEmpty()

    /** 이 기기 사용자. 09에서 받을 돈인지 낼 돈인지를 이 사람 기준으로 보여 준다. */
    val me: String get() = myName.trim().ifBlank { myDriver }

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
    private val toll: TollRecorder? = null,
) {
    private val _state = MutableStateFlow(SettlementState(cars = startCars(), request = requests.load()))
    val state: StateFlow<SettlementState> = _state.asStateFlow()

    private val loadLock = Mutex()
    private var loaded = false

    /**
     * 정산 화면이 처음 열릴 때 한 번: 운행 기록으로 내 차 칸을 채우고, 차종을 읽고, [here] 근처 유가를 불러온다.
     * 다음 운행은 [finish]나 [startTrip] 뒤에 다시 채운다.
     */
    suspend fun load(here: Wgs84Point?) = loadLock.withLock {
        if (loaded) return@withLock
        loaded = true
        loadTrip()
        // 동승자면 A차는 남의 차라 이 기기의 운행 기록·차종으로 채우지 않는다
        if (_state.value.iDrive) {
            _state.update { s -> s.copy(cars = s.cars.mapFirst { it.withRecorded(recorder.fuel, recorder.battery) }) }
            readMyCarType()?.let { type ->
                _state.update { s -> s.copy(cars = s.cars.mapFirst { if (it.type != type) it.withType(type, s.prices) else it }) }
            }
            // 내 차 통행료: 내비 예상액(경로 변경 반영). 전기차는 하이패스 친환경차 감면을 뺀다.
            toll?.ledger?.totalWon?.takeIf { it > 0 }?.let { raw ->
                val ev = _state.value.cars.firstOrNull()?.type == VehicleType.ELECTRIC
                val discount = if (ev) evTollDiscountPercent(LocalDate.now().year) else 0
                val est = tollWon(raw, discount)
                _state.update { s ->
                    s.copy(estimatedTollWon = est, tollDiscountPercent = discount, cars = s.cars.mapFirst { it.copy(tollWon = est.toString()) })
                }
            }
        }
        loadPrices(here)
    }

    // 서버에 진행 중 운행이 있으면 그 참가자·차량별 비용으로 바꾼다. 없거나 연결이 안 되면 빈 화면에 안내만 띄운다.
    private suspend fun loadTrip() {
        val server = server ?: return
        try {
            val trip = server.loadTrip()
            if (trip == null || trip.cars.isEmpty()) {
                _state.update { it.copy(serverNote = NO_TRIP) }
            } else {
                _state.update { it.copy(cars = trip.cars, tripId = trip.tripId, iDrive = trip.iDrive, myName = trip.myName, serverNote = null) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "운행 불러오기 실패", e)
            _state.update { it.copy(serverNote = "서버에 연결하지 못했습니다. 잠시 뒤 다시 열어 주세요") }
        }
    }

    /**
     * 08을 보는 동안 주기적으로 운행을 다시 읽어, 다른 차가 나중에 저장(T8)한 비용을 채운다.
     * 실시간 알림이 빠질 때가 있어 다시 읽기로 맞춘다(#18). 실패하면 다음 번에 다시 한다.
     */
    suspend fun refreshTrip() = loadLock.withLock {
        val s = _state.value
        val tripId = s.tripId ?: return@withLock
        val server = server ?: return@withLock
        if (s.request != null) return@withLock
        val trip = try {
            server.loadTrip()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "운행 다시 읽기 실패", e)
            return@withLock
        }
        if (trip?.tripId != tripId) return@withLock
        _state.update { it.copy(cars = withSavedCosts(it.cars, trip.cars, it.iDrive)) }
    }

    /**
     * 정산 요청 뒤 내 차(A차) 비용을 서버에 저장한다(T6 도착 → T8). 다른 차 앱이 이 값으로 같은 정산을 계산한다.
     * 운행이 없으면 아무것도 안 한다. 실패해도 정산 요청은 그대로 두고 안내만 남긴다.
     */
    suspend fun saveMyCost() {
        val s = _state.value
        val tripId = s.tripId ?: return
        val server = server ?: return
        if (!s.iDrive) return // 동승자는 저장할 내 차가 없다(서버가 NOT_DRIVER로 거부)
        val car = s.cars.firstOrNull() ?: return
        try {
            server.saveMyCost(tripId, car, s.choice, s.estimatedTollWon)
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
        val sent = SettlementRequest.of(result, s.me).also(requests::save)
        recorder.reset()
        toll?.reset()
        _state.update { it.copy(request = sent) }
        return sent
    }

    fun togglePaid(index: Int) {
        val sent = _state.value.request?.toggled(index)?.also(requests::save) ?: return
        _state.update { it.copy(request = sent) }
    }

    /**
     * 보낸 정산 요청을 거두고 08로 돌아간다. 입력값(차량·유가)은 그대로라 고쳐서 다시 요청할 수 있다.
     * 운행 기록은 요청 때 이미 지웠지만 그 값은 차량 칸에 남아 있다. 서버에 올린 내 차 비용은 다시 요청하면 덮어쓴다.
     */
    fun cancelRequest() {
        requests.clear()
        _state.update { it.copy(request = null) }
    }

    /** 이번 정산을 끝내고 다음 운행을 준비한다. 다음에 정산 화면을 열면 새 운행 기록으로 다시 채운다. */
    fun finish() {
        requests.clear()
        loaded = false
        _state.value = SettlementState(cars = startCars())
    }

    /** 서버 운행에 들어갔다. 이전 운행 정산을 끝내지 않았으면(요청 전·송금 확인 중) 버리고 이 운행으로 다시 채운다(#30). */
    fun startTrip(tripId: String) {
        if (_state.value.tripId != tripId) finish()
    }

    // 서버가 있으면 운행 값만 쓴다(예시 값을 실제 운행처럼 보이지 않게). 서버 없이 시연할 때만 데모 그룹.
    private fun startCars() = if (server != null) emptyList() else initialCars()

    private fun SettlementState.withPrices(prices: Map<FuelProduct, Int>) = copy(
        prices = prices,
        cars = cars.map { c -> c.type.opinetProduct?.let(prices::get)?.let { c.copy(fuelPrice = it.toString()) } ?: c },
    )

    private fun List<CarInput>.mapFirst(f: (CarInput) -> CarInput) = mapIndexed { i, c -> if (i == 0) f(c) else c }

    private companion object {
        const val TAG = "SettlementSession"
        const val NO_TRIP = "진행 중인 운행이 없습니다. 운행을 시작하면 도착 후 여기서 정산합니다"
    }
}

/**
 * 다시 읽은 운행([fresh], 내 차가 맨 앞)을 지금 값에 합친다. 내 차와 값이 있는 차는 그대로 두고(사용자가 고친 값 포함),
 * 아직 비용이 없던 다른 차만 서버 값으로 바꾼다. 새로 생긴 차는 뒤에 붙인다.
 */
internal fun withSavedCosts(local: List<CarInput>, fresh: List<CarInput>, iDrive: Boolean = true): List<CarInput> {
    // 멤버 ID로 맞춘다(같은 이름의 운전자가 있을 수 있다). ID가 없으면 이름으로.
    fun CarInput.key() = memberId.ifBlank { driver }
    // 운전자 기기는 A차를 이 기기 값으로 두고, 동승자 기기는 A차도 서버 값으로 채운다
    val mine = if (iDrive) 1 else 0
    val others = fresh.drop(mine)
    val byKey = others.associateBy { it.key() }
    val merged = local.mapIndexed { i, c ->
        if (i >= mine && c.costMissing) byKey[c.key()] ?: c else c
    }
    return merged + others.filter { f -> merged.none { it.key() == f.key() } }
}

/** 서버 운행에서 이 차 운전자가 아직 비용을 저장(T8)하지 않았는지. 출발 연료·배터리가 둘 다 비어 있다. */
internal val CarInput.costMissing: Boolean get() = startFuelL.isBlank() && startBatteryKwh.isBlank()

/**
 * 서버가 붙기 전 데모 그룹(#13 MockGroups와 같은 사람들). A차가 이 기기가 달린 내 차라 운행 기록으로 다시 채워진다.
 * TODO(서버): 운행 참가자·차량을 서버(trip_members)에서 받는다.
 */
internal fun demoCars(): List<CarInput> = listOf(
    CarInput(VehicleType.GASOLINE, "김민수", "정예린, 한도윤, 오지민", startFuelL = "40", endFuelL = "26.875", tollWon = "10000"),
    CarInput(VehicleType.GASOLINE, "이서준", "박지우, 최하은, 윤서아", startFuelL = "50", endFuelL = "20", tollWon = "15000"),
)
