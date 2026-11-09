package com.nanugil.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.bearingTo
import com.nanugil.app.core.geo.distanceTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** GPS 한 번의 위치. */
data class GpsFix(
    val position: Wgs84Point,
    /** 진행 방향(도, 북=0). 멈춰 있으면 직전 방향을 유지한다 */
    val headingDegrees: Double,
    val speedMps: Float,
    val timeMillis: Long,
)

/**
 * 차량 GPS 위치를 1초마다 받는다 (AAOS LocationManager).
 *
 * 내장 내비가 알려 주는 위치는 내비가 멈추면 같이 멈춘다. 그래서 내 차 표시·남은 거리·이동 거리는
 * GPS를 우선으로 쓴다. 에뮬레이터에서는 Extended Controls → Location(경로 재생)이나
 * `adb emu geo fix 경도 위도`로 넣은 위치가 들어온다.
 *
 * 권한: ACCESS_FINE_LOCATION (실행 중 허용 필요)
 */
class GpsLocationSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val _fix = MutableStateFlow<GpsFix?>(null)
    val fix: StateFlow<GpsFix?> = _fix.asStateFlow()
    private var started = false

    private val listener = LocationListener { location -> onLocation(location) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasPermission()) return
        started = true
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        }.filter { locationManager.allProviders.contains(it) }
        providers.forEach { provider ->
            locationManager.getLastKnownLocation(provider)?.let(::onLocation)
            locationManager.requestLocationUpdates(provider, UPDATE_MS, 0f, listener, Looper.getMainLooper())
        }
        Log.i(TAG, "GPS 수신 시작: $providers")
    }

    fun stop() {
        if (!started) return
        started = false
        locationManager.removeUpdates(listener)
    }

    private fun onLocation(location: Location) {
        // 에뮬레이터 기본 위치(미국 구글 본사)처럼 국내가 아닌 값은 버린다
        if (location.latitude !in KOREA_LAT || location.longitude !in KOREA_LNG) return
        val position = Wgs84Point(location.latitude, location.longitude)
        val previous = _fix.value
        val heading = when {
            location.hasBearing() && location.hasSpeed() && location.speed > MOVING_MPS -> location.bearing.toDouble()
            previous != null && previous.position.distanceTo(position) > MIN_MOVE_FOR_HEADING_M ->
                previous.position.bearingTo(position)
            else -> previous?.headingDegrees ?: 0.0
        }
        _fix.value = GpsFix(
            position = position,
            headingDegrees = heading,
            speedMps = if (location.hasSpeed()) location.speed else 0f,
            timeMillis = location.time,
        )
    }

    private companion object {
        const val TAG = "GpsLocation"
        const val UPDATE_MS = 1_000L
        const val MOVING_MPS = 0.5f
        const val MIN_MOVE_FOR_HEADING_M = 3.0
        val KOREA_LAT = 33.0..39.0
        val KOREA_LNG = 124.0..132.0
    }
}
