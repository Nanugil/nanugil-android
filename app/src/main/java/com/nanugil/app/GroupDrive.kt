package com.nanugil.app

// 그룹 드라이브 기능 전체: 네이버 지도 + 오피넷 주유소 (ADR-001)
// 순서: 앱 시작 → 좌표 변환 → 오피넷 → 그룹 상태 → 지도 → 화면

import android.app.Application
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.util.MarkerIcons
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ============================================================================
// 앱 시작: 네이버 지도 SDK 인증
// ============================================================================

class NanugilApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 키는 local.properties의 NAVER_MAP_CLIENT_ID → BuildConfig로 들어온다 (README "시작하기" 참고).
        NaverMapSdk.getInstance(this).apply {
            client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAP_CLIENT_ID)
            onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { e ->
                // 키 누락, 패키지명 미등록(com.nanugil.app) 등이면 지도가 회색으로 뜨고 여기로 온다.
                Log.e("NaverMap", "네이버 지도 인증 실패", e)
            }
        }
    }
}

// ============================================================================
// 좌표 변환: WGS84(위경도) ↔ KATEC(오피넷 좌표)
// ============================================================================

/** WGS84 위경도. 네이버 지도, GPS, Pleos Fused Location이 쓰는 좌표계. */
data class Wgs84Point(val latitude: Double, val longitude: Double)

/** KATEC 평면 좌표(미터). 오피넷 API가 요청·응답에 쓰는 좌표계. */
data class KatecPoint(val x: Double, val y: Double)

/**
 * WGS84 ↔ KATEC 좌표 변환.
 *
 * 오피넷은 위경도가 아니라 KATEC 좌표만 받기 때문에, 네이버 지도 위치를 넘기거나
 * 오피넷이 돌려준 주유소 위치를 지도에 찍으려면 이 변환이 필요하다.
 *
 * KATEC 정의 (proj4):
 * `+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000 +ellps=bessel
 *  +towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43`
 *
 * 순서: WGS84 위경도 → 지구중심좌표 → (7변수 Helmert) → Bessel 위경도 → 횡메르카토르 투영.
 * 오피넷 응답 좌표와는 약 10m 이내로 맞는다.
 */
object KatecConverter {

    // --- 타원체 ---
    private const val WGS84_A = 6378137.0
    private const val WGS84_F = 1 / 298.257223563
    private const val BESSEL_A = 6377397.155
    private const val BESSEL_F = 1 / 299.1528128

    // --- 투영 (KATEC) ---
    private val LAT0 = Math.toRadians(38.0)
    private val LON0 = Math.toRadians(128.0)
    private const val K0 = 0.9999
    private const val FALSE_EASTING = 400000.0
    private const val FALSE_NORTHING = 600000.0

    // --- 7변수 Helmert (Bessel → WGS84, position vector 방식) ---
    private const val DX = -115.80
    private const val DY = 474.99
    private const val DZ = 674.11
    private const val ARCSEC_TO_RAD = Math.PI / (180.0 * 3600.0)
    private const val RX = 1.16 * ARCSEC_TO_RAD
    private const val RY = -2.31 * ARCSEC_TO_RAD
    private const val RZ = -1.63 * ARCSEC_TO_RAD
    private const val SCALE = 1.0 + 6.43e-6

    private val besselE2 = BESSEL_F * (2 - BESSEL_F)
    private val besselEp2 = besselE2 / (1 - besselE2)
    private val wgs84E2 = WGS84_F * (2 - WGS84_F)
    private val m0 = meridianArc(LAT0)

    fun toKatec(point: Wgs84Point): KatecPoint {
        val ecefWgs = toEcef(Math.toRadians(point.latitude), Math.toRadians(point.longitude), WGS84_A, wgs84E2)
        val ecefBessel = wgs84ToBessel(ecefWgs)
        val (lat, lon) = fromEcef(ecefBessel, BESSEL_A, besselE2)
        return project(lat, lon)
    }

    fun toWgs84(point: KatecPoint): Wgs84Point {
        val (lat, lon) = unproject(point)
        val ecefBessel = toEcef(lat, lon, BESSEL_A, besselE2)
        val ecefWgs = besselToWgs84(ecefBessel)
        val (wLat, wLon) = fromEcef(ecefWgs, WGS84_A, wgs84E2)
        return Wgs84Point(Math.toDegrees(wLat), Math.toDegrees(wLon))
    }

    // ---------------------------------------------------------------- 지구중심좌표

    private fun toEcef(lat: Double, lon: Double, a: Double, e2: Double): DoubleArray {
        val n = a / sqrt(1 - e2 * sin(lat).pow(2))
        return doubleArrayOf(
            n * cos(lat) * cos(lon),
            n * cos(lat) * sin(lon),
            n * (1 - e2) * sin(lat),
        )
    }

    /** 지구중심좌표 → 위경도(라디안). 높이는 버린다. */
    private fun fromEcef(p: DoubleArray, a: Double, e2: Double): Pair<Double, Double> {
        val (x, y, z) = Triple(p[0], p[1], p[2])
        val lon = atan2(y, x)
        val r = hypot(x, y)
        var lat = atan2(z, r * (1 - e2))
        repeat(10) {
            val n = a / sqrt(1 - e2 * sin(lat).pow(2))
            val h = r / cos(lat) - n
            lat = atan2(z, r * (1 - e2 * n / (n + h)))
        }
        return lat to lon
    }

    private fun besselToWgs84(p: DoubleArray): DoubleArray {
        val (x, y, z) = Triple(p[0], p[1], p[2])
        return doubleArrayOf(
            SCALE * (x - RZ * y + RY * z) + DX,
            SCALE * (RZ * x + y - RX * z) + DY,
            SCALE * (-RY * x + RX * y + z) + DZ,
        )
    }

    private fun wgs84ToBessel(p: DoubleArray): DoubleArray {
        val x = (p[0] - DX) / SCALE
        val y = (p[1] - DY) / SCALE
        val z = (p[2] - DZ) / SCALE
        return doubleArrayOf(
            x + RZ * y - RY * z,
            -RZ * x + y + RX * z,
            RY * x - RX * y + z,
        )
    }

    // ---------------------------------------------------------------- 횡메르카토르 (Snyder)

    private fun meridianArc(lat: Double): Double {
        val e2 = besselE2
        val e4 = e2 * e2
        val e6 = e4 * e2
        return BESSEL_A * (
            (1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * lat -
                (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * sin(2 * lat) +
                (15 * e4 / 256 + 45 * e6 / 1024) * sin(4 * lat) -
                (35 * e6 / 3072) * sin(6 * lat)
            )
    }

    private fun project(lat: Double, lon: Double): KatecPoint {
        val n = BESSEL_A / sqrt(1 - besselE2 * sin(lat).pow(2))
        val t = tan(lat).pow(2)
        val c = besselEp2 * cos(lat).pow(2)
        val a = (lon - LON0) * cos(lat)
        val m = meridianArc(lat)

        val x = K0 * n * (
            a + (1 - t + c) * a.pow(3) / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * besselEp2) * a.pow(5) / 120
            )
        val y = K0 * (
            m - m0 + n * tan(lat) * (
                a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24 +
                    (61 - 58 * t + t * t + 600 * c - 330 * besselEp2) * a.pow(6) / 720
                )
            )
        return KatecPoint(x + FALSE_EASTING, y + FALSE_NORTHING)
    }

    private fun unproject(p: KatecPoint): Pair<Double, Double> {
        val e2 = besselE2
        val m = m0 + (p.y - FALSE_NORTHING) / K0
        val mu = m / (BESSEL_A * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256))
        val e1 = (1 - sqrt(1 - e2)) / (1 + sqrt(1 - e2))
        val phi1 = mu +
            (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) +
            (1097 * e1.pow(4) / 512) * sin(8 * mu)

        val c1 = besselEp2 * cos(phi1).pow(2)
        val t1 = tan(phi1).pow(2)
        val n1 = BESSEL_A / sqrt(1 - e2 * sin(phi1).pow(2))
        val r1 = BESSEL_A * (1 - e2) / (1 - e2 * sin(phi1).pow(2)).pow(1.5)
        val d = (p.x - FALSE_EASTING) / (n1 * K0)

        val lat = phi1 - (n1 * tan(phi1) / r1) * (
            d * d / 2 -
                (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * besselEp2) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * besselEp2 - 3 * c1 * c1) * d.pow(6) / 720
            )
        val lon = LON0 + (
            d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * besselEp2 + 24 * t1 * t1) * d.pow(5) / 120
            ) / cos(phi1)
        return lat to lon
    }
}

// ============================================================================
// 오피넷: 반경 내 주유소·유가 조회
// ============================================================================

/** 오피넷 제품 코드. */
enum class FuelProduct(val code: String, val label: String) {
    GASOLINE("B027", "휘발유"),
    DIESEL("D047", "경유"),
    PREMIUM_GASOLINE("B034", "고급휘발유"),
    LPG("K015", "LPG"),
}

enum class StationSort(val code: String) {
    PRICE("1"),
    DISTANCE("2"),
}

data class GasStation(
    val id: String,
    val name: String,
    /** 상표 코드. SKE=SK에너지, GSC=GS칼텍스, HDO=HD현대오일뱅크, SOL=S-OIL, RTO/RTX=알뜰주유소 등 */
    val brandCode: String,
    val priceWon: Int,
    val distanceMeters: Int,
    val position: Wgs84Point,
)

class OpinetException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * 오피넷 "반경 내 주유소" API 클라이언트.
 *
 * - 문서: https://www.opinet.co.kr/user/custapi/openApiInfoDtl.do?apiId=3
 * - 무료 키 기준 하루 1,500건. 반경은 최대 5km.
 * - 좌표는 KATEC으로 주고받으므로 [KatecConverter]로 변환한다.
 *
 * 공모전 데모라서 키를 앱에 넣지만(BuildConfig), 실서비스에서는 서버를 거쳐 호출해 키를 숨긴다.
 */
class OpinetClient(
    private val apiKey: String,
    private val baseUrl: String = "https://www.opinet.co.kr/api",
) {

    suspend fun findStationsAround(
        center: Wgs84Point,
        radiusMeters: Int = 5_000,
        product: FuelProduct = FuelProduct.GASOLINE,
        sort: StationSort = StationSort.PRICE,
    ): List<GasStation> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw OpinetException("오피넷 API 키가 없습니다. local.properties에 OPINET_API_KEY를 넣어 주세요.")
        }
        val katec = KatecConverter.toKatec(center)
        val params = mapOf(
            // 현재 문서의 파라미터 이름은 certkey, 예전 예제들은 code를 쓴다. 둘 다 보내 두면 안전하다.
            "certkey" to apiKey,
            "code" to apiKey,
            "out" to "json",
            "x" to "%.1f".format(katec.x),
            "y" to "%.1f".format(katec.y),
            "radius" to radiusMeters.coerceIn(1, 5_000).toString(),
            "prodcd" to product.code,
            "sort" to sort.code,
        )
        val body = get("$baseUrl/aroundAll.do", params)
        parseStations(body)
    }

    private fun get(url: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val connection = (URL("$url?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw OpinetException("오피넷 응답 오류 HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: OpinetException) {
            throw e
        } catch (e: IOException) {
            throw OpinetException("오피넷 연결 실패: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseStations(body: String): List<GasStation> {
        val result = try {
            JSONObject(body).getJSONObject("RESULT")
        } catch (e: Exception) {
            // 키가 틀리면 JSON 대신 안내 문구가 오는 경우가 있다.
            throw OpinetException("오피넷 응답을 읽을 수 없습니다: ${body.take(120)}", e)
        }
        // 결과가 한 건이면 배열이 아니라 객체로 오는 경우가 있어 둘 다 받는다.
        val oil = when (val raw = result.opt("OIL")) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            else -> JSONArray()
        }
        return (0 until oil.length()).map { i ->
            val o = oil.getJSONObject(i)
            GasStation(
                id = o.optString("UNI_ID"),
                name = o.optString("OS_NM"),
                // 문서 표는 POLL_DIV_CD, 실제 응답은 POLL_DIV_CO
                brandCode = o.optString("POLL_DIV_CO").ifEmpty { o.optString("POLL_DIV_CD") },
                priceWon = o.optDouble("PRICE", 0.0).toInt(),
                distanceMeters = o.optDouble("DISTANCE", 0.0).toInt(),
                position = KatecConverter.toWgs84(
                    KatecPoint(o.optDouble("GIS_X_COOR"), o.optDouble("GIS_Y_COOR")),
                ),
            )
        }
    }
}

// ============================================================================
// 그룹 차량 상태 (서버 전까지는 시연용 가짜 데이터)
// ============================================================================

/** 그룹 드라이브 중인 차량 한 대의 상태. */
data class CarState(
    val carId: String,
    val driverName: String,
    val position: Wgs84Point,
    val speedKmh: Int,
    val etaMinutes: Int,
    /** 연료(또는 배터리) 잔량 0~100 */
    val fuelPercent: Int,
    val isMe: Boolean = false,
) {
    val isLowFuel: Boolean get() = fuelPercent <= LOW_FUEL_PERCENT

    companion object {
        const val LOW_FUEL_PERCENT = 15
    }
}

/**
 * 같은 그룹 차량들의 위치를 실시간으로 받는 창구.
 *
 * 실제 구현은 서버(WebSocket 또는 Firebase)가 정해지면 붙인다.
 * 내 차 위치·연료는 Pleos Fused Location SDK와 Vehicle SDK에서 읽어 서버로 보낸다.
 */
interface GroupLocationRepository {
    fun observeGroup(groupId: String): Flow<List<CarState>>
}

/**
 * 서버 없이 화면을 개발·시연하기 위한 가짜 데이터.
 * README 시나리오대로 서울시청 → 가평 펜션을 차량 2대가 달리고, B 차량의 연료가 점점 줄어든다.
 */
class FakeGroupLocationRepository(
    private val tickMillis: Long = 2_000,
) : GroupLocationRepository {

    private val start = Wgs84Point(37.5665, 126.9780)
    private val destination = Wgs84Point(37.8315, 127.5105)

    override fun observeGroup(groupId: String): Flow<List<CarState>> = flow {
        var step = 0
        while (true) {
            val progressA = (step * 0.01).coerceAtMost(1.0)
            val progressB = ((step - 3) * 0.01).coerceIn(0.0, 1.0)
            emit(
                listOf(
                    CarState(
                        carId = "car-a",
                        driverName = "운전자 A",
                        position = lerp(start, destination, progressA),
                        speedKmh = 92,
                        etaMinutes = ((1 - progressA) * 80).toInt(),
                        fuelPercent = 62,
                        isMe = true,
                    ),
                    CarState(
                        carId = "car-b",
                        driverName = "운전자 B",
                        position = lerp(start, destination, progressB),
                        speedKmh = 88,
                        etaMinutes = ((1 - progressB) * 80).toInt(),
                        fuelPercent = (30 - step).coerceAtLeast(5),
                    ),
                ),
            )
            step++
            delay(tickMillis)
        }
    }

    private fun lerp(a: Wgs84Point, b: Wgs84Point, t: Double) = Wgs84Point(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t,
    )
}

// ============================================================================
// 네이버 지도 Compose 래퍼: 차량·주유소 마커
// ============================================================================

private const val COLOR_ME = 0xFF5B2EFF.toInt() // 나누길 보라
private const val COLOR_GROUP = 0xFF3D7BFF.toInt()
private const val COLOR_LOW_FUEL = 0xFFE5484D.toInt()
private const val COLOR_STATION = 0xFF2E9E5B.toInt()
private const val COLOR_STATION_SELECTED = 0xFFFF9F1C.toInt()

fun Wgs84Point.toLatLng() = LatLng(latitude, longitude)

/**
 * 네이버 지도를 Compose에 올리고, 일행 차량과 주유소를 마커로 그린다.
 *
 * MapView는 액티비티 생명주기를 그대로 전달받아야 하므로 LifecycleEventObserver로 연결한다.
 */
@Composable
fun NaverMapView(
    cars: List<CarState>,
    stations: List<GasStation>,
    selectedStation: GasStation?,
    onStationClick: (GasStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }

    DisposableEffect(lifecycle, mapView) {
        var created = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> { mapView.onCreate(null); created = true }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { mapView.onDestroy(); created = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // 액티비티는 살아 있는데 이 화면만 빠지는 경우에도 지도 리소스를 정리한다.
            if (created) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            // 주행 중 조작을 줄이기 위해 확대/축소 버튼은 숨긴다.
            map.uiSettings.isZoomControlEnabled = false
            naverMap = map
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    val map = naverMap ?: return
    val density = context.resources.displayMetrics.density
    CarMarkers(map, cars)
    StationMarkers(map, stations, selectedStation, onStationClick, paddingPx = (96 * density).toInt(), cars = cars)
}

@Composable
private fun CarMarkers(map: NaverMap, cars: List<CarState>) {
    val markers = remember(map) { mutableMapOf<String, Marker>() }
    var cameraPlaced by remember(map) { mutableStateOf(false) }

    LaunchedEffect(map, cars) {
        val ids = cars.map { it.carId }.toSet()
        markers.keys.filterNot { it in ids }.forEach { markers.remove(it)?.map = null }

        cars.forEach { car ->
            val marker = markers.getOrPut(car.carId) {
                Marker().apply {
                    icon = MarkerIcons.BLACK
                    zIndex = 100
                    this.map = map
                }
            }
            marker.position = car.position.toLatLng()
            marker.iconTintColor = when {
                car.isLowFuel -> COLOR_LOW_FUEL
                car.isMe -> COLOR_ME
                else -> COLOR_GROUP
            }
            marker.captionText = car.driverName
            marker.subCaptionText = "${car.speedKmh}km/h · 연료 ${car.fuelPercent}%"
        }

        if (!cameraPlaced) {
            cars.firstOrNull { it.isMe }?.let {
                map.moveCamera(CameraUpdate.scrollAndZoomTo(it.position.toLatLng(), 11.0))
                cameraPlaced = true
            }
        }
    }

    DisposableEffect(map) {
        onDispose {
            markers.values.forEach { it.map = null }
            markers.clear()
        }
    }
}

@Composable
private fun StationMarkers(
    map: NaverMap,
    stations: List<GasStation>,
    selectedStation: GasStation?,
    onStationClick: (GasStation) -> Unit,
    paddingPx: Int,
    cars: List<CarState>,
) {
    val markers = remember(map) { mutableListOf<Marker>() }

    // 주유소 목록이 바뀌면 마커를 새로 그리고, 연료 부족 차량과 주유소가 한 화면에 들어오게 맞춘다.
    LaunchedEffect(map, stations) {
        markers.forEach { it.map = null }
        markers.clear()
        stations.forEach { station ->
            markers += Marker().apply {
                position = station.position.toLatLng()
                icon = MarkerIcons.BLACK
                iconTintColor = COLOR_STATION
                captionText = station.name
                subCaptionText = "%,d원".format(station.priceWon)
                tag = station.id
                onClickListener = Overlay.OnClickListener {
                    onStationClick(station)
                    true
                }
                this.map = map
            }
        }
        if (stations.isNotEmpty()) {
            val points = stations.map { it.position.toLatLng() } +
                cars.filter { it.isLowFuel }.map { it.position.toLatLng() }
            val bounds = points.fold(LatLngBounds.Builder()) { builder, p -> builder.include(p) }.build()
            map.moveCamera(CameraUpdate.fitBounds(bounds, paddingPx).animate(CameraAnimation.Easing))
        }
    }

    LaunchedEffect(map, stations, selectedStation) {
        markers.forEach { marker ->
            val selected = marker.tag == selectedStation?.id
            marker.iconTintColor = if (selected) COLOR_STATION_SELECTED else COLOR_STATION
            marker.zIndex = if (selected) 50 else 0
        }
        selectedStation?.let {
            map.moveCamera(CameraUpdate.scrollTo(it.position.toLatLng()).animate(CameraAnimation.Easing))
        }
    }

    DisposableEffect(map) {
        onDispose {
            markers.forEach { it.map = null }
            markers.clear()
        }
    }
}

// ============================================================================
// 그룹 드라이브 화면
// ============================================================================

private val LowFuelRed = Color(0xFFE5484D)

/**
 * 그룹 드라이브 화면: 왼쪽은 네이버 지도, 오른쪽은 일행·주유소 패널.
 *
 * 연료가 [CarState.LOW_FUEL_PERCENT] 아래로 내려간 차가 생기면 그 차 위치 기준으로
 * 오피넷에서 반경 5km 주유소를 가격순으로 자동 조회한다.
 */
@Composable
fun GroupDriveScreen(
    groupRepository: GroupLocationRepository,
    opinetClient: OpinetClient,
    onProposeMeetup: (GasStation) -> Unit,
    groupId: String = "demo",
) {
    val cars by remember(groupId) { groupRepository.observeGroup(groupId) }
        .collectAsState(initial = emptyList())
    var stations by remember { mutableStateOf<List<GasStation>>(emptyList()) }
    var selected by remember { mutableStateOf<GasStation?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchedFor by remember { mutableStateOf<CarState?>(null) }
    val scope = rememberCoroutineScope()

    fun search(car: CarState) {
        scope.launch {
            searching = true
            error = null
            try {
                stations = opinetClient.findStationsAround(car.position)
                selected = stations.firstOrNull()
                searchedFor = car
            } catch (e: OpinetException) {
                error = e.message
            } finally {
                searching = false
            }
        }
    }

    // 연료 부족 차량이 새로 생기면 한 번 자동 조회
    val lowFuelCar = cars.firstOrNull { it.isLowFuel }
    LaunchedEffect(lowFuelCar?.carId) {
        lowFuelCar?.let { search(it) }
    }

    Row(Modifier.fillMaxSize()) {
        NaverMapView(
            cars = cars,
            stations = stations,
            selectedStation = selected,
            onStationClick = { selected = it },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SidePanel(
            cars = cars,
            stations = stations,
            selected = selected,
            searching = searching,
            error = error,
            searchedFor = searchedFor,
            onSearchNearMe = { cars.firstOrNull { it.isMe }?.let(::search) },
            onSelect = { selected = it },
            onProposeMeetup = onProposeMeetup,
            modifier = Modifier.width(400.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun SidePanel(
    cars: List<CarState>,
    stations: List<GasStation>,
    selected: GasStation?,
    searching: Boolean,
    error: String?,
    searchedFor: CarState?,
    onSearchNearMe: () -> Unit,
    onSelect: (GasStation) -> Unit,
    onProposeMeetup: (GasStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("그룹 드라이브", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        cars.forEach { CarRow(it) }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = searchedFor?.let { "${it.driverName} 주변 주유소" } ?: "주변 주유소",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        searchedFor?.takeIf { it.isLowFuel }?.let {
            Text(
                "${it.driverName} 연료 ${it.fuelPercent}% — 반경 5km 최저가 순",
                color = LowFuelRed,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stations, key = { it.id }) { station ->
                StationRow(station, isSelected = station.id == selected?.id, onClick = { onSelect(station) })
            }
        }

        // 주행 중에도 누르기 쉽도록 버튼은 크게
        Button(
            onClick = { selected?.let(onProposeMeetup) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("이 주유소로 합류 제안")
        }
        OutlinedButton(
            onClick = onSearchNearMe,
            enabled = !searching,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("내 차 주변 주유소 다시 찾기")
        }
    }
}

@Composable
private fun CarRow(car: CarState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                car.driverName + if (car.isMe) " (나)" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${car.speedKmh}km/h · 도착 ${car.etaMinutes}분",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "연료 ${car.fuelPercent}%",
            style = MaterialTheme.typography.titleMedium,
            color = if (car.isLowFuel) LowFuelRed else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StationRow(station: GasStation, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "%.1fkm · %s".format(station.distanceMeters / 1000.0, station.brandCode),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("%,d원".format(station.priceWon), style = MaterialTheme.typography.titleMedium)
        }
    }
}
