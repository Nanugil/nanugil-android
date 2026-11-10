package com.nanugil.app.ui.navi

import android.content.Context
import android.graphics.PointF
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.nanugil.app.BuildConfig
import com.nanugil.app.R
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.distanceTo
import com.nanugil.app.data.navi.NaviState
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import com.naver.maps.map.util.MarkerIcons

/**
 * 주행 화면 지도 제어: 내 차 따라가기(TMAP식 3D 진행 방향 시점), 경로선, 목적지.
 *
 * 사용자가 지도를 손으로 움직이면 따라가기를 끄고, [recenter]로 다시 켠다.
 */
class DriveMapController(context: Context, private val map: NaverMap) {

    private val appContext = context.applicationContext

    /** 내 차를 따라가는 중인지. 화면의 "현재 위치" 버튼 표시에 쓴다. */
    var following: Boolean = true
        private set
    var onFollowingChanged: ((Boolean) -> Unit)? = null

    private var guiding = false
    private var current: Wgs84Point? = null
    private var heading = 0.0
    private var route: List<Wgs84Point> = emptyList()

    private val routeOverlay = PathOverlay().apply {
        // 시안의 "도로 위 파란 빛": 반투명 브랜드 Primary(#1E5EFF) + 넓고 옅은 테두리로 번지는 느낌
        color = 0xCC1E5EFF.toInt()
        outlineColor = 0x331E5EFF
        // 지나온 구간은 회색 (progress 앞쪽)
        passedColor = 0x995C6370.toInt()
        passedOutlineColor = 0x1A5C6370
        width = 14
        outlineWidth = 14
    }
    /** 손으로 지도를 움직이면 따라가기를 끈다 (init보다 먼저 선언해야 한다) */
    private val cameraListener = NaverMap.OnCameraChangeListener { reason, _ ->
        if (reason == CameraUpdate.REASON_GESTURE) setFollowing(false)
    }
    /** 경로선 각 꼭짓점까지의 누적 거리(m). 지나온 비율 계산용 */
    private var routeCumulative: DoubleArray = DoubleArray(0)
    private val destinationMarker = Marker().apply {
        icon = MarkerIcons.BLACK
        // 어두운 지도에서 잘 보이게 흰 핀 + 흰 글씨
        iconTintColor = 0xFFFFFFFF.toInt()
        captionColor = 0xFFFFFFFF.toInt()
        captionHaloColor = 0xFF111216.toInt()
        zIndex = 100
    }

    init {
        map.uiSettings.apply {
            isZoomControlEnabled = false
            isCompassEnabled = false
            isScaleBarEnabled = false
            isLocationButtonEnabled = false
            // 끌기·핀치 확대·두 손가락 회전·기울이기 모두 허용
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
        }
        applyFigmaStyle()
        map.locationOverlay.apply {
            isVisible = true
            icon = OverlayImage.fromBitmap(ContextCompat.getDrawable(context, R.drawable.ic_nav_arrow)!!.toBitmap())
            circleRadius = 0
            subIcon = null
        }
        // 손으로 지도를 움직이면 따라가기를 멈춘다 (안 그러면 1초 뒤 내 차로 끌려간다)
        map.addOnCameraChangeListener(cameraListener)
    }

    /**
     * Figma v5 지도: 글자·아이콘 없이 도로와 입체 건물 블록만 보이는 도시 풍경.
     * - 커스텀 스타일 ID가 있으면(NCP 스타일 에디터에서 만든 회색 블록 스타일) 그대로 쓴다.
     * - 없으면 기본 내비 지도에서 심벌(POI 아이콘·글자)을 지우고, 산·대중교통·자전거 레이어를 끄고,
     *   건물 높이를 최대로 올려 비슷하게 만든다.
     */
    private fun applyFigmaStyle() {
        map.mapType = NaverMap.MapType.Navi
        if (BuildConfig.NAVER_MAP_STYLE_ID.isNotBlank()) {
            map.customStyleId = BuildConfig.NAVER_MAP_STYLE_ID
        }
        map.symbolScale = 0f
        map.buildingHeight = 1f
        map.setLayerGroupEnabled(NaverMap.LAYER_GROUP_BUILDING, true)
        map.setLayerGroupEnabled(NaverMap.LAYER_GROUP_MOUNTAIN, false)
        map.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRANSIT, false)
        map.setLayerGroupEnabled(NaverMap.LAYER_GROUP_BICYCLE, false)
        map.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRAFFIC, false)
    }

    fun recenter() {
        setFollowing(true)
        followCurrent()
    }

    /** 화면 테마에 맞춰 지도도 낮(화이트톤)/밤(다크)으로 바꾼다 (Figma v5 두 시안) */
    fun setDarkTheme(dark: Boolean) {
        map.isNightModeEnabled = dark
        // 화이트톤 시안은 밝고 채도 낮게, 다크 시안은 더 어둡게
        map.lightness = if (dark) -0.15f else 0.25f
        val ink = if (dark) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()
        val halo = if (dark) 0xFF111216.toInt() else 0xFFFFFFFF.toInt()
        destinationMarker.iconTintColor = ink
        destinationMarker.captionColor = ink
        destinationMarker.captionHaloColor = halo
        groupMarkers.values.forEach { it.iconTintColor = ink }
        groupInk = ink
    }

    private val groupMarkers = mutableMapOf<String, Marker>()
    private val waypointMarkers = mutableListOf<Marker>()
    private val fuelIcon by lazy {
        OverlayImage.fromBitmap(ContextCompat.getDrawable(appContext, R.drawable.ic_fuel_marker)!!.toBitmap())
    }

    /**
     * 경유지(합류 주유소)마다 주유소 마커를 띄운다. 경로선(현재 위치 → 주유소 → 목적지) 위에 함께 보인다.
     * 경유지를 지나치면 목록에서 빠지므로 마커도 함께 사라진다.
     */
    fun updateWaypoints(names: List<String>, positions: List<Wgs84Point>) {
        val count = minOf(names.size, positions.size)
        while (waypointMarkers.size > count) waypointMarkers.removeAt(waypointMarkers.lastIndex).map = null
        for (i in 0 until count) {
            val marker = waypointMarkers.getOrNull(i) ?: Marker().apply {
                // 위치 없이 지도에 올리면 NaN 좌표로 크래시 → 위치부터 넣는다
                position = positions[i].toLatLng()
                icon = fuelIcon
                anchor = PointF(0.5f, 0.5f)
                zIndex = 110
                this.map = this@DriveMapController.map
            }.also { waypointMarkers += it }
            marker.position = positions[i].toLatLng()
            marker.captionText = names[i]
            marker.captionColor = groupInk
            marker.captionHaloColor = if (groupInk == 0xFFFFFFFF.toInt()) 0xFF111216.toInt() else 0xFFFFFFFF.toInt()
        }
    }

    /** Activity가 끝날 때 지도에서 모든 오버레이와 리스너를 떼어 MapView·Activity를 붙잡지 않게 한다 */
    fun release() {
        map.removeOnCameraChangeListener(cameraListener)
        routeOverlay.map = null
        destinationMarker.map = null
        groupMarkers.values.forEach { it.map = null }
        groupMarkers.clear()
        waypointMarkers.forEach { it.map = null }
        waypointMarkers.clear()
        onFollowingChanged = null
    }
    private var groupInk = 0xFFFFFFFF.toInt()

    /**
     * 같은 그룹의 다른 차를 지도에 표시한다 ("B차 정차", "B차"처럼 시안의 말풍선 라벨).
     * TODO(서버): 지금은 목업 위치. 서버가 붙으면 각 차량이 보낸 실제 GPS 위치가 들어온다.
     */
    fun updateGroupCars(cars: List<com.nanugil.app.data.group.CarState>) {
        val ids = cars.map { it.carId }.toSet()
        groupMarkers.keys.filterNot { it in ids }.forEach { groupMarkers.remove(it)?.map = null }
        cars.forEach { car ->
            val marker = groupMarkers.getOrPut(car.carId) {
                Marker().apply {
                    // 위치 없이 지도에 올리면 NaN 좌표로 크래시 → 위치를 먼저 넣는다
                    position = car.position.toLatLng()
                    icon = OverlayImage.fromBitmap(ContextCompat.getDrawable(appContext, R.drawable.ic_group_car)!!.toBitmap())
                    iconTintColor = groupInk
                    anchor = PointF(0.5f, 0.5f)
                    captionRequestedWidth = 0
                    captionTextSize = 18f
                    zIndex = 90
                    this.map = this@DriveMapController.map
                }
            }
            marker.position = car.position.toLatLng()
            val label = car.carId.substringAfterLast('-').uppercase() + "차"
            marker.captionText = if (car.speedKmh == 0) "$label 정차" else label
            marker.captionColor = groupInk
            marker.captionHaloColor = if (groupInk == 0xFFFFFFFF.toInt()) 0xFF111216.toInt() else 0xFFFFFFFF.toInt()
        }
    }

    fun zoomBy(delta: Double) {
        // 따라가기 중에도 사용자가 고른 줌을 유지한다 (안 그러면 1초 뒤 기본 줌으로 되돌아간다)
        followZoom = (map.cameraPosition.zoom + delta).coerceIn(MIN_FOLLOW_ZOOM, MAX_FOLLOW_ZOOM)
        map.moveCamera(CameraUpdate.zoomTo(followZoom!!).animate(CameraAnimation.Easing))
    }

    /** 따라가기 시점의 줌. 확대·축소 버튼을 누르면 그 값으로 바뀐다 (null이면 기본값) */
    private var followZoom: Double? = null

    fun updateNavigation(state: NaviState) {
        val wasGuiding = guiding
        guiding = state.guiding

        val dest = state.destinationPosition.takeIf { state.guiding }
        if (dest == null) {
            destinationMarker.map = null
        } else {
            destinationMarker.position = dest.toLatLng()
            destinationMarker.captionText = state.destinationName ?: "목적지"
            destinationMarker.map = map
        }

        // 안내가 시작되면 바로 주행 시점으로
        if (guiding && !wasGuiding) recenter()
        // 안내가 끝나면 평면 지도로
        if (!guiding && wasGuiding && following) followCurrent()
    }

    fun updateVehicle(position: Wgs84Point?, headingDegrees: Double) {
        val pos = position ?: return
        val first = current == null
        current = pos
        heading = headingDegrees
        map.locationOverlay.position = pos.toLatLng()
        map.locationOverlay.bearing = headingDegrees.toFloat()
        updatePassed(pos)
        if (following || first) followCurrent()
    }

    /** 내 차가 경로선 위 어디쯤인지 찾아 그 앞까지를 회색(지나온 길)으로 칠한다. */
    private fun updatePassed(pos: Wgs84Point) {
        val cumulative = routeCumulative
        if (route.size < 2 || cumulative.isEmpty()) return
        val total = cumulative.last().takeIf { it > 0 } ?: return
        var nearest = 0
        var nearestDist = Double.MAX_VALUE
        route.forEachIndexed { i, p ->
            val d = pos.distanceTo(p)
            if (d < nearestDist) {
                nearestDist = d
                nearest = i
            }
        }
        // 경로에서 많이 벗어나면(재탐색 전) 표시를 바꾸지 않는다
        if (nearestDist > OFF_ROUTE_M) return
        routeOverlay.progress = (cumulative[nearest] / total).coerceIn(0.0, 1.0)
    }

    fun updateRoute(path: List<Wgs84Point>) {
        if (path == route) return
        route = path
        // PathOverlay는 좌표가 2개 미만이면 예외가 난다
        if (path.size < 2) {
            routeOverlay.map = null
            return
        }
        routeOverlay.coords = path.map { it.toLatLng() }
        routeCumulative = DoubleArray(path.size).also { acc ->
            for (i in 1 until path.size) acc[i] = acc[i - 1] + path[i - 1].distanceTo(path[i])
        }
        routeOverlay.progress = 0.0
        current?.let(::updatePassed)
        routeOverlay.map = map
        // 안내 전(경로 미리보기)일 때만 전체 경로를 보여 준다
        if (!guiding) showWholeRoute()
    }

    private fun setFollowing(value: Boolean) {
        if (following == value) return
        following = value
        onFollowingChanged?.invoke(value)
    }

    private fun followCurrent() {
        val pos = current ?: return
        val update = if (guiding) {
            // TMAP식: 진행 방향이 위, 지도를 기울여 건물이 입체로, 내 차는 화면 아래쪽
            CameraUpdate.toCameraPosition(CameraPosition(pos.toLatLng(), followZoom ?: DRIVE_ZOOM, DRIVE_TILT, heading))
                .pivot(PointF(0.5f, DRIVE_PIVOT_Y))
        } else {
            CameraUpdate.toCameraPosition(CameraPosition(pos.toLatLng(), followZoom ?: IDLE_ZOOM, 0.0, 0.0))
        }
        map.moveCamera(update.animate(CameraAnimation.Easing))
    }

    private fun showWholeRoute() {
        setFollowing(false)
        val points = route.map { it.toLatLng() } + listOfNotNull(current?.toLatLng())
        val bounds = points.fold(LatLngBounds.Builder()) { b, p -> b.include(p) }.build()
        map.moveCamera(CameraUpdate.fitBounds(bounds, ROUTE_PADDING_PX).animate(CameraAnimation.Easing))
    }

    private fun Wgs84Point.toLatLng() = LatLng(latitude, longitude)

    private companion object {
        const val IDLE_ZOOM = 16.0
        const val DRIVE_ZOOM = 17.0
        const val MIN_FOLLOW_ZOOM = 8.0
        const val MAX_FOLLOW_ZOOM = 19.0
        /** 시안처럼 도시를 낮게 내려다보는 각도 (네이버 지도 최대 60°) */
        const val DRIVE_TILT = 60.0
        /** 주행 시점에서 내 차가 놓일 세로 위치 (0=위, 1=아래) */
        const val DRIVE_PIVOT_Y = 0.75f
        const val ROUTE_PADDING_PX = 160
        /** 경로선에서 이보다 멀면 경로를 벗어난 것으로 본다 */
        const val OFF_ROUTE_M = 300.0
    }
}
