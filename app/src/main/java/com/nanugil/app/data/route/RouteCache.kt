package com.nanugil.app.data.route

import android.content.Context
import android.util.Log
import com.nanugil.app.core.geo.Wgs84Point
import java.io.File

/**
 * 안내 중인 경로선을 앱 저장소에 둔다. 앱을 껐다 켜도 같은 경로선(출발점부터)을 다시 그려
 * 지나온 길(회색)이 이어지게 하기 위해서다.
 *
 * 키 = 목적지 + 경유지. 목적지·경유지가 바뀌면 다른 경로로 본다.
 * 파일 형식: 첫 줄 키, 둘째 줄 "위도,경도;위도,경도;…"
 */
class RouteCache(context: Context) {

    private val file = File(context.filesDir, "route_cache.txt")

    fun load(key: String): List<Wgs84Point>? = try {
        if (!file.exists()) {
            null
        } else {
            val lines = file.readLines()
            if (lines.size < 2 || lines[0] != key) {
                Log.i(TAG, "저장된 경로선 없음 (다른 목적지·경유지)")
                null
            } else {
                parse(lines[1]).also { Log.i(TAG, "저장된 경로선 사용: ${it?.size}개 점") }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "경로선 캐시 읽기 실패", e)
        null
    }

    fun save(key: String, path: List<Wgs84Point>) {
        if (path.size < 2) return
        try {
            file.writeText(key + "\n" + path.joinToString(";") { "${it.latitude},${it.longitude}" })
        } catch (e: Exception) {
            Log.w(TAG, "경로선 캐시 저장 실패", e)
        }
    }

    fun clear() {
        if (file.delete()) Log.i(TAG, "경로선 저장 삭제 (안내 종료)")
    }

    private fun parse(line: String): List<Wgs84Point>? = line.split(';').mapNotNull { pair ->
        val (lat, lng) = pair.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
        Wgs84Point(lat.toDoubleOrNull() ?: return@mapNotNull null, lng.toDoubleOrNull() ?: return@mapNotNull null)
    }.takeIf { it.size >= 2 }

    companion object {
        private const val TAG = "RouteCache"

        /** 목적지·경유지로 만든 키 (소수 5자리 ≈ 1m 단위로 맞춰 미세한 좌표 차이는 같은 경로로 본다) */
        fun keyOf(destination: Wgs84Point, waypoints: List<Wgs84Point>): String =
            (listOf(destination) + waypoints).joinToString("|") { "%.5f,%.5f".format(it.latitude, it.longitude) }
    }
}
