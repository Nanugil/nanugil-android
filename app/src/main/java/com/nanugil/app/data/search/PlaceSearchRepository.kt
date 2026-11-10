package com.nanugil.app.data.search

import android.text.Html
import android.util.Log
import com.nanugil.app.core.geo.KatecConverter
import com.nanugil.app.core.geo.KatecPoint
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.distanceTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 검색 결과 한 곳. */
data class PlaceResult(
    val name: String,
    val address: String,
    val position: Wgs84Point,
    /** 예: "음식점>카페" (주소 검색 결과면 빈 문자열) */
    val category: String = "",
    /** 내 위치에서 직선거리(m). 위치를 모르면 null */
    val distanceMeters: Int? = null,
)

class PlaceSearchException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** 목적지 검색 창구. 화면·ViewModel은 이 인터페이스만 안다. */
interface PlaceSearchRepository {
    /**
     * [query]로 장소를 찾는다. [near]가 있으면 가까운 순으로 정렬한다.
     * @throws PlaceSearchException 검색 키가 없거나 네트워크 오류일 때
     */
    suspend fun search(query: String, near: Wgs84Point?): List<PlaceResult>
}

/**
 * 네이버 검색 두 가지를 묶는다.
 * 1) 장소 이름 검색: 네이버 개발자센터 "지역 검색" API (스타벅스 강남, 가평 펜션 …)
 *    키: local.properties의 NAVER_SEARCH_CLIENT_ID / NAVER_SEARCH_CLIENT_SECRET (developers.naver.com 애플리케이션)
 * 2) 주소 검색: NCP Maps Geocoding (불정로 6 …) — 1)에서 결과가 없을 때. NCP 콘솔에서 Geocoding을 켜야 한다.
 *
 * TODO(서버): Secret을 앱에 두지 않으려면 서버 프록시를 부르는 구현으로 바꾼다.
 */
class NaverPlaceSearchRepository(
    private val searchClientId: String,
    private val searchClientSecret: String,
    private val ncpClientId: String,
    private val ncpClientSecret: String,
) : PlaceSearchRepository {

    private val hasLocalSearch get() = searchClientId.isNotBlank() && searchClientSecret.isNotBlank()
    private val hasGeocode get() = ncpClientId.isNotBlank() && ncpClientSecret.isNotBlank()

    override suspend fun search(query: String, near: Wgs84Point?): List<PlaceResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        if (!hasLocalSearch && !hasGeocode) {
            throw PlaceSearchException("검색 키가 없습니다. local.properties에 NAVER_SEARCH_CLIENT_ID/SECRET을 넣어 주세요.")
        }

        var lastError: PlaceSearchException? = null
        val places = if (hasLocalSearch) {
            try { localSearch(q) } catch (e: PlaceSearchException) { lastError = e; emptyList() }
        } else {
            emptyList()
        }
        val results = places.ifEmpty {
            if (hasGeocode) {
                try { geocode(q, near) } catch (e: PlaceSearchException) { lastError = lastError ?: e; emptyList() }
            } else {
                emptyList()
            }
        }
        if (results.isEmpty() && lastError != null) throw lastError!!

        results
            .map { r -> r.copy(distanceMeters = near?.distanceTo(r.position)?.toInt()) }
            .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
    }

    /** 네이버 지역 검색 (최대 5건) */
    private fun localSearch(query: String): List<PlaceResult> {
        val body = get(
            "https://openapi.naver.com/v1/search/local.json?query=${enc(query)}&display=5&sort=random",
            mapOf("X-Naver-Client-Id" to searchClientId, "X-Naver-Client-Secret" to searchClientSecret),
            what = "장소 검색",
        )
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.getJSONObject(i)
            val position = parseMapXY(o.optString("mapx"), o.optString("mapy")) ?: return@mapNotNull null
            PlaceResult(
                name = o.optString("title").stripHtml(),
                address = o.optString("roadAddress").ifBlank { o.optString("address") },
                position = position,
                category = o.optString("category"),
            )
        }
    }

    /** NCP 주소 검색 (내 위치 주변 우선) */
    private fun geocode(query: String, near: Wgs84Point?): List<PlaceResult> {
        val coordinate = near?.let { "&coordinate=${it.longitude},${it.latitude}" }.orEmpty()
        val body = get(
            "https://maps.apigw.ntruss.com/map-geocode/v2/geocode?query=${enc(query)}$coordinate",
            mapOf("X-NCP-APIGW-API-KEY-ID" to ncpClientId, "X-NCP-APIGW-API-KEY" to ncpClientSecret),
            what = "주소 검색",
        )
        val addresses = JSONObject(body).optJSONArray("addresses") ?: return emptyList()
        return (0 until addresses.length()).mapNotNull { i ->
            val a = addresses.getJSONObject(i)
            val lat = a.optString("y").toDoubleOrNull() ?: return@mapNotNull null
            val lng = a.optString("x").toDoubleOrNull() ?: return@mapNotNull null
            val road = a.optString("roadAddress").ifBlank { a.optString("jibunAddress") }
            PlaceResult(name = road, address = a.optString("jibunAddress"), position = Wgs84Point(lat, lng))
        }
    }

    private fun get(url: String, headers: Map<String, String>, what: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.w(TAG, "$what HTTP $code $err")
                throw PlaceSearchException(
                    when (code) {
                        401, 403 -> "$what 권한이 없습니다 (키 확인 또는 API 사용 신청 필요)"
                        else -> "$what 실패 (HTTP $code)"
                    },
                )
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: PlaceSearchException) {
            throw e
        } catch (e: IOException) {
            throw PlaceSearchException("$what 연결 실패: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "PlaceSearch"

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        /** "<b>스타벅스</b> 강남점" → "스타벅스 강남점" (&amp; 같은 엔티티도 풀기) */
        fun String.stripHtml(): String = Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()

        /**
         * 지역 검색 좌표. 지금 API는 WGS84 × 1e7 정수(예: 1270276245)를 주고,
         * 예전 API는 KATEC 좌표(예: 314000)를 줬다. 둘 다 받는다.
         */
        fun parseMapXY(mapx: String, mapy: String): Wgs84Point? {
            val x = mapx.toDoubleOrNull() ?: return null
            val y = mapy.toDoubleOrNull() ?: return null
            return if (x > 1e8) Wgs84Point(y / 1e7, x / 1e7) else KatecConverter.toWgs84(KatecPoint(x, y))
        }
    }
}
