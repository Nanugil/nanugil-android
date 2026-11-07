package com.nanugil.app.data.fuel

import com.nanugil.app.core.geo.KatecConverter
import com.nanugil.app.core.geo.KatecPoint
import com.nanugil.app.core.geo.Wgs84Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GasStation(
    val id: String,
    val name: String,
    /** 오피넷 상표 코드. 화면 표시는 [OpinetBrand.displayName] */
    val brandCode: String,
    val priceWon: Int,
    val distanceMeters: Int,
    val position: Wgs84Point,
)

class OpinetException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * 오피넷 "반경 내 주유소" API 클라이언트.
 *
 * 좌표는 KATEC으로 주고받으므로 [KatecConverter]로 변환한다.
 * 공모전 데모라서 키를 앱에 넣지만(BuildConfig), 실서비스에서는 서버를 거쳐 호출해 키를 숨긴다.
 */
class OpinetClient(
    private val apiKey: String,
    private val baseUrl: String = OpinetConstants.BASE_URL,
) {

    suspend fun findStationsAround(
        center: Wgs84Point,
        radiusMeters: Int = OpinetConstants.MAX_RADIUS_METERS,
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
            "radius" to radiusMeters.coerceIn(1, OpinetConstants.MAX_RADIUS_METERS).toString(),
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
            // 에뮬레이터 기본 DNS로는 www.opinet.co.kr를 못 찾는 경우가 있다.
            // emulator -avd <AVD 이름> -dns-server 8.8.8.8,1.1.1.1 로 띄우면 된다.
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
        return (0 until oil.length()).mapNotNull { i ->
            val o = oil.getJSONObject(i)
            val x = o.optDouble("GIS_X_COOR")
            val y = o.optDouble("GIS_Y_COOR")
            // 좌표가 빠진 주유소는 지도에 찍을 수 없으니 건너뛴다 (NaN 좌표로 마커를 만들면 크래시).
            if (x.isNaN() || y.isNaN()) return@mapNotNull null
            GasStation(
                id = o.optString("UNI_ID"),
                name = o.optString("OS_NM"),
                // 문서 표는 POLL_DIV_CD, 실제 응답은 POLL_DIV_CO
                brandCode = o.optString("POLL_DIV_CO").ifEmpty { o.optString("POLL_DIV_CD") },
                priceWon = o.optDouble("PRICE", 0.0).toInt(),
                distanceMeters = o.optDouble("DISTANCE", 0.0).toInt(),
                position = KatecConverter.toWgs84(KatecPoint(x, y)),
            )
        }
    }
}
