package com.nanugil.app.core.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** WGS84 위경도. 네이버 지도, GPS, Pleos Fused Location이 쓰는 좌표계. */
data class Wgs84Point(val latitude: Double, val longitude: Double)

/** KATEC 평면 좌표(미터). 오피넷 API가 요청·응답에 쓰는 좌표계. */
data class KatecPoint(val x: Double, val y: Double)

/**
 * WGS84 ↔ KATEC 좌표 변환 공통 유틸리티. 상태가 없어서 어디서든 바로 호출하면 된다.
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
