package com.nanugil.app.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

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
