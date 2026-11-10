package com.nanugil.app.data.route

import com.nanugil.app.core.geo.Wgs84Point

/**
 * 길안내 경로를 구하는 창구. 화면·길안내 엔진은 이 인터페이스만 알고, 실제 API는 모른다.
 *
 * 지금은 네이버 Directions 5를 앱에서 바로 부른다([NaverRouteRepository]).
 * TODO(서버): Client Secret을 숨기려면 서버 프록시를 부르는 구현으로 바꾼다 (ADR-002) — 이 파일만 바꾸면 된다.
 */
interface RouteRepository {
    /**
     * 출발 → 경유지(순서대로, 최대 5개) → 목적지 경로.
     * @throws DirectionsException 키가 없거나, 네트워크 오류이거나, 경로를 찾지 못했을 때
     */
    suspend fun planRoute(start: Wgs84Point, goal: Wgs84Point, waypoints: List<Wgs84Point>): PlannedRoute
}

class NaverRouteRepository(private val client: NaverDirectionsClient) : RouteRepository {
    override suspend fun planRoute(start: Wgs84Point, goal: Wgs84Point, waypoints: List<Wgs84Point>): PlannedRoute =
        client.drivingRoute(start, goal, waypoints)
}
