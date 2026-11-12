package com.nanugil.app.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0
/** 위도 1도의 길이(m). [distanceTo]와 같은 지구 반지름을 써서 서로 맞는다 */
private const val METERS_PER_DEGREE = EARTH_RADIUS_M * Math.PI / 180

/** 두 지점 사이 직선거리(m) */
fun Wgs84Point.distanceTo(other: Wgs84Point): Double {
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLng = Math.toRadians(other.longitude - longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** 이 지점에서 다른 지점을 바라보는 방향(도, 북=0, 시계 방향) */
fun Wgs84Point.bearingTo(other: Wgs84Point): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLng = Math.toRadians(other.longitude - longitude)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

/**
 * 경로선 위에서 [position]부터 끝까지 남은 거리(m).
 * 가장 가까운 경로 꼭짓점을 찾아 그 뒤 구간 길이를 더한다. 경로에서 [maxOffRouteM]보다 멀면 null.
 */
fun remainingAlong(path: List<Wgs84Point>, position: Wgs84Point, maxOffRouteM: Double = 300.0): Double? {
    if (path.size < 2) return null
    var nearest = 0
    var nearestDist = Double.MAX_VALUE
    path.forEachIndexed { i, p ->
        val d = position.distanceTo(p)
        if (d < nearestDist) {
            nearestDist = d
            nearest = i
        }
    }
    if (nearestDist > maxOffRouteM) return null
    var remain = nearestDist
    for (i in nearest until path.lastIndex) remain += path[i].distanceTo(path[i + 1])
    return remain
}

/** 경로선 각 꼭짓점까지의 누적 거리(m). 첫 값은 0, 마지막 값이 전체 길이. */
fun cumulativeDistances(path: List<Wgs84Point>): DoubleArray =
    DoubleArray(path.size).also { acc ->
        for (i in 1 until path.size) acc[i] = acc[i - 1] + path[i - 1].distanceTo(path[i])
    }

/**
 * 경로선 시작점에서 [position]까지 경로를 따라 간 거리(m).
 * 가장 가까운 구간에 수선을 내려 구간 안에서의 위치까지 더한다 (꼭짓점만 보면 긴 구간에서 값이 튄다).
 */
fun distanceAlong(path: List<Wgs84Point>, cumulative: DoubleArray, position: Wgs84Point): Double {
    if (path.size < 2) return 0.0
    var best = 0.0
    var bestDist = Double.MAX_VALUE
    for (i in 0 until path.lastIndex) {
        val a = path[i]
        val b = path[i + 1]
        // 짧은 구간이라 위경도를 평면(m)으로 보고 투영한다
        val ky = METERS_PER_DEGREE
        val kx = METERS_PER_DEGREE * cos(Math.toRadians(a.latitude))
        val bx = (b.longitude - a.longitude) * kx
        val by = (b.latitude - a.latitude) * ky
        val px = (position.longitude - a.longitude) * kx
        val py = (position.latitude - a.latitude) * ky
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - bx * t
        val dy = py - by * t
        val d = sqrt(dx * dx + dy * dy)
        if (d < bestDist) {
            bestDist = d
            best = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t
        }
    }
    return best
}

/** 경로선 시작점에서 [meters]만큼 경로를 따라간 지점과 그 지점의 진행 방향(도). 경로 밖이면 양 끝에 붙인다. */
fun pointAlong(path: List<Wgs84Point>, cumulative: DoubleArray, meters: Double): Pair<Wgs84Point, Double> {
    require(path.size >= 2) { "경로는 점이 2개 이상이어야 한다" }
    val s = meters.coerceIn(0.0, cumulative.last())
    var i = 0
    while (i < path.lastIndex - 1 && cumulative[i + 1] < s) i++
    val a = path[i]
    val b = path[i + 1]
    val seg = cumulative[i + 1] - cumulative[i]
    val t = if (seg <= 0.0) 0.0 else (s - cumulative[i]) / seg
    val p = Wgs84Point(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    return p to a.bearingTo(b)
}

/** 이 지점에서 [bearingDegrees] 방향으로 [meters]만큼 떨어진 지점 (짧은 거리용 근사) */
fun Wgs84Point.offset(bearingDegrees: Double, meters: Double): Wgs84Point {
    val rad = Math.toRadians(bearingDegrees)
    val dLat = meters * cos(rad) / METERS_PER_DEGREE
    val dLng = meters * sin(rad) / (METERS_PER_DEGREE * cos(Math.toRadians(latitude)))
    return Wgs84Point(latitude + dLat, longitude + dLng)
}
