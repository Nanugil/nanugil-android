package com.nanugil.app.ui.navi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.nanugil.app.BuildConfig
import com.nanugil.app.R
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.bearingTo
import com.nanugil.app.core.geo.distanceTo
import com.nanugil.app.core.geo.offset
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.navi.NaviState
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.InfoWindow
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import com.naver.maps.map.util.MarkerIcons
import kotlin.math.roundToInt

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
        // 두께는 줌에 맞춰 [fitRouteWidth]가 정한다 (축소하면 가늘게)
    }
    /** 손으로 지도를 움직이면 따라가기를 끈다 (init보다 먼저 선언해야 한다) */
    private val cameraListener = NaverMap.OnCameraChangeListener { reason, _ ->
        if (reason == CameraUpdate.REASON_GESTURE) setFollowing(false)
        fitRouteWidth()
    }

    /**
     * 경로선 두께를 줌에 맞춘다. 경로선은 줌과 상관없이 같은 픽셀 두께라, 그대로 두면 축소할수록 도로보다 굵어 보인다.
     * 줌 [ROUTE_ZOOM_FAR] 이하에서 가장 가늘고, [ROUTE_ZOOM_NEAR] 이상에서 가장 굵다.
     */
    private fun fitRouteWidth() {
        val t = ((map.cameraPosition.zoom - ROUTE_ZOOM_FAR) / (ROUTE_ZOOM_NEAR - ROUTE_ZOOM_FAR)).coerceIn(0.0, 1.0)
        val width = dp((ROUTE_WIDTH_FAR_DP + (ROUTE_WIDTH_NEAR_DP - ROUTE_WIDTH_FAR_DP) * t).roundToInt())
        val outline = dp((ROUTE_OUTLINE_FAR_DP + (ROUTE_OUTLINE_NEAR_DP - ROUTE_OUTLINE_FAR_DP) * t).roundToInt())
        if (routeOverlay.width != width) routeOverlay.width = width
        if (routeOverlay.outlineWidth != outline) routeOverlay.outlineWidth = outline
    }
    /** 경로선 각 꼭짓점까지의 누적 거리(m). 지나온 비율 계산용 */
    private var routeCumulative: DoubleArray = DoubleArray(0)
    private val destinationMarker = Marker().apply {
        icon = MarkerIcons.BLACK
        // 어두운 지도에서 잘 보이게 흰 핀
        iconTintColor = 0xFFFFFFFF.toInt()
        zIndex = 100
    }
    /** 목적지 이름표 (핀 바로 아래) */
    private val destinationLabel = labelMarker(zIndex = 99)
    private var destinationName: String = "목적지"

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
        fitRouteWidth()
        // 빈 지도를 누르면 일행 차 팝업을 닫는다 (closeCarInfo는 누를 때 불리므로 init 순서와 상관없다)
        map.onMapClickListener = NaverMap.OnMapClickListener { _, _ -> closeCarInfo() }
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
        groupInk = ink
        groupHalo = halo
        groupSubInk = if (dark) 0xFFD1D5DB.toInt() else 0xFF475569.toInt()
        // 이름표는 테마 색으로 다시 그린다
        destinationLabel.icon = labelImage(destinationName, topGapDp = 4)
        waypointLabels.forEachIndexed { i, m -> m.icon = labelImage(waypointNames.getOrElse(i) { "" }, topGapDp = 30) }
        groupCars.values.forEach(::styleGroupMarker)
        if (selectedCarId != null) carInfo.invalidate()
    }

    private val groupCars = mutableMapOf<String, GroupCar>()
    private var selectedCarId: String? = null
    private val iconCache = mutableMapOf<Pair<Int, Boolean>, OverlayImage>()
    private val labelCache = mutableMapOf<LabelKey, OverlayImage>()
    private val waypointMarkers = mutableListOf<Marker>()
    private val waypointLabels = mutableListOf<Marker>()
    private var waypointNames: List<String> = emptyList()
    private val fuelIcon by lazy {
        OverlayImage.fromBitmap(ContextCompat.getDrawable(appContext, R.drawable.ic_fuel_marker)!!.toBitmap())
    }

    /**
     * 경유지(합류 주유소)마다 주유소 마커를 띄운다. 경로선(현재 위치 → 주유소 → 목적지) 위에 함께 보인다.
     * 경유지를 지나치면 목록에서 빠지므로 마커도 함께 사라진다.
     */
    fun updateWaypoints(names: List<String>, positions: List<Wgs84Point>) {
        val count = minOf(names.size, positions.size)
        waypointNames = names.take(count)
        while (waypointMarkers.size > count) waypointMarkers.removeAt(waypointMarkers.lastIndex).map = null
        while (waypointLabels.size > count) waypointLabels.removeAt(waypointLabels.lastIndex).map = null
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
            val label = waypointLabels.getOrNull(i) ?: labelMarker(zIndex = 109).also { waypointLabels += it }
            label.position = positions[i].toLatLng()
            label.icon = labelImage(names[i], topGapDp = 30)
            label.map = map
        }
    }

    /** Activity가 끝날 때 지도에서 모든 오버레이와 리스너를 떼어 MapView·Activity를 붙잡지 않게 한다 */
    fun release() {
        map.removeOnCameraChangeListener(cameraListener)
        routeOverlay.map = null
        destinationMarker.map = null
        destinationLabel.map = null
        closeCarInfo()
        map.onMapClickListener = null
        groupCars.values.forEach {
            it.animator?.cancel()
            it.marker.map = null
            it.label.map = null
        }
        groupCars.clear()
        waypointMarkers.forEach { it.map = null }
        waypointMarkers.clear()
        waypointLabels.forEach { it.map = null }
        waypointLabels.clear()
        onFollowingChanged = null
    }
    private var groupInk = 0xFFFFFFFF.toInt()
    private var groupHalo = 0xFF111216.toInt()
    private var groupSubInk = 0xFFD1D5DB.toInt()

    /**
     * 같은 그룹의 다른 차를 지도에 표시한다.
     * - 내 차와 같은 화살표 아이콘에 색만 다르게(차마다), 진행 방향으로 돈다. 연료 부족이면 빨간 테두리
     * - 위치가 몇 초마다 와도 순간이동하지 않고 미끄러지듯 움직인다
     * - 누르면 그 차 옆에 정보 팝업(운전자·속도·남은 시간·나와의 거리·연료)이 붙어 다닌다
     * TODO(서버): 지금은 목업 위치. #15가 붙으면 각 차량이 보낸 실제 위치가 들어온다.
     */
    fun updateGroupCars(cars: List<CarState>) {
        val ids = cars.map { it.carId }.toSet()
        groupCars.keys.filterNot { it in ids }.forEach { id ->
            groupCars.remove(id)?.let {
                it.animator?.cancel()
                it.marker.map = null
                it.label.map = null
            }
            if (selectedCarId == id) closeCarInfo()
        }
        cars.forEach { car ->
            val g = groupCars.getOrPut(car.carId) { newGroupCar(car) }
            val target = car.position.toLatLng()
            // 움직인 방향 = 진행 방향. 서 있으면 마지막 방향을 유지한다
            val from = g.target
            if (from.distanceTo(target) > HEADING_MIN_MOVE_M) {
                g.heading = Wgs84Point(from.latitude, from.longitude).bearingTo(car.position)
            }
            g.target = target
            g.state = car
            styleGroupMarker(g)
            animateTo(g, target)
        }
        if (selectedCarId != null) carInfo.invalidate()
    }

    /** 일행 차를 누르면 그 차로 지도를 옮기고 옆에 정보 팝업을 띄운다. 다시 누르거나 지도를 누르면 닫는다. */
    private fun selectCar(id: String) {
        if (selectedCarId == id) {
            closeCarInfo()
            return
        }
        val g = groupCars[id] ?: return
        selectedCarId = id
        carInfo.position = g.marker.position
        carInfo.open(map)
        carInfo.invalidate()
        setFollowing(false)
        // 지도 가운데가 아니라 카드가 없는 빈 곳(가로 42%, 세로 68%)에 차를 두어 오른쪽 팝업이 카드에 가리지 않게
        map.moveCamera(CameraUpdate.scrollTo(g.target).pivot(PointF(SELECT_PIVOT_X, SELECT_PIVOT_Y)).animate(CameraAnimation.Easing))
    }

    private fun closeCarInfo() {
        selectedCarId = null
        carInfo.close()
    }

    /**
     * 일행 전체 보기: 내 차와 일행 차가 모두 보이게 지도를 맞춘다.
     * 여백(dp)은 화면이 정해 준다 — 카드에 가리지 않는 영역 안에 모이게.
     */
    fun showAllCars(leftDp: Int, topDp: Int, rightDp: Int, bottomDp: Int) {
        val points = listOfNotNull(current?.toLatLng()) +
            groupCars.values.filterNot { it.state.isMe && current != null }.map { it.target }
        if (points.isEmpty()) return
        setFollowing(false)
        var bounds = points.fold(LatLngBounds.Builder()) { b, p -> b.include(p) }.build()
        if (bounds.southWest.distanceTo(bounds.northEast) < GROUP_MIN_SPAN_M) {
            // 너무 붙어 있으면 최대로 확대되지 않게 가운데 기준으로 최소 범위를 넓힌다
            val c = Wgs84Point(bounds.center.latitude, bounds.center.longitude)
            val half = GROUP_MIN_SPAN_M / 2
            bounds = LatLngBounds.Builder()
                .include(c.offset(315.0, half).toLatLng())
                .include(c.offset(135.0, half).toLatLng())
                .include(bounds.southWest).include(bounds.northEast)
                .build()
        }
        // 마커 반지름·이름표만큼 더 띄워서 차가 빈 영역 가장자리에 걸리지 않게
        val m = GROUP_MARKER_MARGIN_DP
        val update = CameraUpdate.fitBounds(bounds, dp(leftDp + m), dp(topDp + m), dp(rightDp + m), dp(bottomDp + m))
        map.moveCamera(update.animate(CameraAnimation.Fly))
    }

    private fun newGroupCar(car: CarState): GroupCar {
        val color = GROUP_COLORS[groupCars.count { !it.value.state.isMe } % GROUP_COLORS.size]
        val marker = Marker().apply {
            // 위치 없이 지도에 올리면 NaN 좌표로 크래시 → 위치를 먼저 넣는다
            position = car.position.toLatLng()
            anchor = PointF(0.5f, 0.5f)
            zIndex = 90
            setOnClickListener {
                selectCar(car.carId)
                true
            }
            this.map = this@DriveMapController.map
        }
        val label = labelMarker(zIndex = 89).apply {
            position = car.position.toLatLng()
            setOnClickListener {
                selectCar(car.carId)
                true
            }
            this.map = this@DriveMapController.map
        }
        return GroupCar(marker, label, color, car, car.position.toLatLng())
    }

    private fun styleGroupMarker(g: GroupCar) {
        val car = g.state
        val stopped = car.speedKmh < STOPPED_KMH
        g.marker.icon = carIcon(g.color, car.isLowFuel)
        // 내 차 화살표처럼 지도 위에 눕혀 진행 방향으로 돌린다 (서 있으면 마지막 방향 그대로)
        g.marker.isFlat = true
        // 마커 angle은 북=0에서 시계 방향 (나침반 방향과 같다. 90을 넣으면 동쪽을 가리킴을 에뮬레이터에서 확인)
        g.marker.angle = g.heading.toFloat()
        g.label.icon = labelImage(
            title = "${carLabel(car)} · ${car.driverName}",
            sub = when {
                car.isLowFuel -> "연료 ${car.fuelPercent}%"
                stopped -> "정차 중"
                else -> null
            },
            subColor = if (car.isLowFuel) DANGER else groupSubInk,
            topGapDp = 26,
        )
    }

    /** 새 위치까지 [GROUP_ANIM_MS] 동안 미끄러지듯 옮긴다. 팝업도 같이 따라간다. */
    private fun animateTo(g: GroupCar, target: LatLng) {
        g.animator?.cancel()
        val start = g.marker.position
        if (start.distanceTo(target) > GROUP_JUMP_M) {
            // 너무 멀리 뛰면(앱 재시작·경로 변경) 그냥 옮긴다
            g.marker.position = target
            g.label.position = target
            if (selectedCarId == g.state.carId) carInfo.position = target
            return
        }
        g.animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GROUP_ANIM_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = (it.animatedValue as Float).toDouble()
                val p = LatLng(
                    start.latitude + (target.latitude - start.latitude) * t,
                    start.longitude + (target.longitude - start.longitude) * t,
                )
                g.marker.position = p
                g.label.position = p
                if (selectedCarId == g.state.carId) carInfo.position = p
            }
            start()
        }
    }

    /** 차 옆에 붙는 정보 팝업. 오른쪽에 띄워 차를 가리지 않는다. */
    private val carInfo = InfoWindow().apply {
        // 차 오른쪽 위로 붙인다 (아래쪽 이름표를 가리지 않게)
        anchor = PointF(0f, 0.8f)
        offsetX = dp(48)
        zIndex = 200
        adapter = object : InfoWindow.ViewAdapter() {
            override fun getView(infoWindow: InfoWindow): View = carInfoView()
        }
        setOnClickListener {
            closeCarInfo()
            true
        }
    }

    private fun carInfoView(): View {
        val dark = groupInk == 0xFFFFFFFF.toInt()
        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (dark) 0xF21A1C22.toInt() else 0xF2FFFFFF.toInt())
                setStroke(dp(1), if (dark) 0x1AFFFFFF else 0x14000000)
            }
        }
        val g = selectedCarId?.let { groupCars[it] } ?: return root
        val car = g.state
        val ink = if (dark) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt()
        val sub = if (dark) 0xFF9CA3AF.toInt() else 0xFF64748B.toInt()
        fun line(text: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false) {
            root.addView(TextView(appContext).apply {
                this.text = text
                textSize = sizeSp
                setTextColor(color)
                if (bold) typeface = Typeface.DEFAULT_BOLD
            })
        }
        // 제목 앞 색 점 = 지도 위 그 차 색
        val title = SpannableStringBuilder()
            .append("● ", ForegroundColorSpan(g.color), 0)
            .append("${carLabel(car)} · ${car.driverName}")
        line(title, 28f, ink, bold = true)
        val stopped = car.speedKmh < STOPPED_KMH
        val moving = "${car.speedKmh}km/h" + if (car.etaMinutes > 0) " · 도착까지 ${car.etaMinutes}분" else ""
        line(if (stopped) "정차 중" else moving, 22f, sub)
        current?.let { me -> line("나와 ${formatDistance(me.distanceTo(car.position))}", 22f, sub) }
        line(
            if (car.isLowFuel) "연료 ${car.fuelPercent}% · 부족" else "연료 ${car.fuelPercent}%",
            22f, if (car.isLowFuel) DANGER else sub, bold = car.isLowFuel,
        )
        return root
    }

    /**
     * 일행 차 아이콘: 내 차 아이콘(ic_nav_arrow: 흰 테두리 원 + 파란 원 + 흰 화살표)과 같은 모양에 색이 다르고 더 작다.
     * 색만으로 구분하면 색각이 다른 운전자에게 불리해서 크기도 다르게 둔다 (내 차 56dp, 일행 [GROUP_ICON_DP]dp).
     * 연료가 부족하면 흰 테두리 대신 빨간 테두리. 색·연료 상태별로 한 번만 그려 둔다.
     */
    private fun carIcon(color: Int, lowFuel: Boolean): OverlayImage =
        iconCache.getOrPut(color to lowFuel) {
            val size = dp(GROUP_ICON_DP)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            // ic_nav_arrow와 같은 56칸 좌표계를 크기에 맞춰 줄여 그린다
            val k = size / 56f
            c.scale(k, k)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            paint.color = if (lowFuel) DANGER else 0xFFFFFFFF.toInt()
            c.drawCircle(28f, 28f, 26f, paint)
            paint.color = color
            c.drawCircle(28f, 28f, 22f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            c.drawPath(
                Path().apply {
                    moveTo(28f, 12f)
                    lineTo(40f, 42f)
                    lineTo(28f, 35f)
                    lineTo(16f, 42f)
                    close()
                },
                paint,
            )
            OverlayImage.fromBitmap(bmp)
        }

    /**
     * 지도 위 이름표. 지도 글자를 숨기려고 symbolScale=0을 쓰는데 그러면 마커 캡션도 사라져서,
     * 글자를 그림으로 그려 위치 바로 아래에 붙는 마커로 띄운다.
     * [topGapDp]만큼 위를 비워 두어 아이콘(핀·화살표)과 겹치지 않게 한다.
     */
    private fun labelImage(title: String, sub: String? = null, subColor: Int = groupSubInk, topGapDp: Int): OverlayImage {
        val dark = groupInk == 0xFFFFFFFF.toInt()
        val key = LabelKey(title, sub, subColor, dark, topGapDp)
        return labelCache.getOrPut(key) {
            val metrics = appContext.resources.displayMetrics
            fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, metrics)
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = groupInk
                textSize = sp(24f)
                typeface = Typeface.DEFAULT_BOLD
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = sp(20f)
                typeface = if (subColor == DANGER) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val padH = dp(14).toFloat()
            val padV = dp(8).toFloat()
            val gap = dp(topGapDp).toFloat()
            val titleH = titlePaint.fontMetrics.let { it.descent - it.ascent }
            val subH = sub?.let { subPaint.fontMetrics.let { m -> m.descent - m.ascent } } ?: 0f
            val textW = maxOf(titlePaint.measureText(title), sub?.let(subPaint::measureText) ?: 0f)
            val w = (textW + padH * 2).toInt().coerceAtLeast(1)
            val h = (gap + padV * 2 + titleH + subH).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0xE61A1C22.toInt() else 0xF2FFFFFF.toInt() }
            c.drawRoundRect(RectF(0f, gap, w.toFloat(), h.toFloat()), dp(14).toFloat(), dp(14).toFloat(), bg)
            var y = gap + padV - titlePaint.fontMetrics.ascent
            c.drawText(title, (w - titlePaint.measureText(title)) / 2, y, titlePaint)
            if (sub != null) {
                y += titlePaint.fontMetrics.descent - subPaint.fontMetrics.ascent
                c.drawText(sub, (w - subPaint.measureText(sub)) / 2, y, subPaint)
            }
            OverlayImage.fromBitmap(bmp)
        }
    }

    /** 위치 바로 아래(가운데 위 꼭짓점 기준)에 붙는 이름표 마커 */
    private fun labelMarker(zIndex: Int) = Marker().apply {
        anchor = PointF(0.5f, 0f)
        this.zIndex = zIndex
    }

    private data class LabelKey(val title: String, val sub: String?, val subColor: Int, val dark: Boolean, val gap: Int)

    private fun carLabel(car: CarState) = car.carId.substringAfterLast('-').uppercase() + "차"

    private fun formatDistance(m: Double) = if (m < 1_000) "${m.roundToInt()}m" else "%.1fkm".format(m / 1_000)

    private fun dp(v: Int): Int = (v * appContext.resources.displayMetrics.density).roundToInt()

    /** 지도 위 일행 차 하나 */
    private class GroupCar(
        val marker: Marker,
        /** 차 아래 이름표 */
        val label: Marker,
        val color: Int,
        var state: CarState,
        /** 마지막으로 받은 위치 (애니메이션 도착점) */
        var target: LatLng,
        var heading: Double = 0.0,
        var animator: ValueAnimator? = null,
    )

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
            destinationLabel.map = null
        } else {
            destinationMarker.position = dest.toLatLng()
            destinationName = state.destinationName ?: "목적지"
            destinationLabel.position = dest.toLatLng()
            destinationLabel.icon = labelImage(destinationName, topGapDp = 4)
            destinationLabel.map = map
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

        /** 경로선 두께(dp): 축소(줌 11 이하)일 때 ↔ 확대(줌 17 이상)일 때. 예전엔 줌과 상관없이 선 14 + 테두리 14 */
        const val ROUTE_ZOOM_FAR = 11.0
        const val ROUTE_ZOOM_NEAR = 17.0
        const val ROUTE_WIDTH_FAR_DP = 4
        const val ROUTE_WIDTH_NEAR_DP = 10
        const val ROUTE_OUTLINE_FAR_DP = 1
        const val ROUTE_OUTLINE_NEAR_DP = 4

        /**
         * 일행 차 색 (지도 위 화살표·팝업 점). 첫 색(시연의 B차)은 내 차 파랑에서 가장 먼 주황.
         * 연료 부족 빨간 테두리와 헷갈리는 분홍은 뺐다.
         */
        val GROUP_COLORS = intArrayOf(0xFFF59E0B.toInt(), 0xFF14B8A6.toInt(), 0xFFA855F7.toInt())
        /** 일행 차 아이콘 크기 (내 차 ic_nav_arrow는 56dp) */
        const val GROUP_ICON_DP = 44
        const val DANGER = 0xFFE5484D.toInt()
        /** 이보다 느리면 서 있는 것으로 그린다 */
        const val STOPPED_KMH = 3
        /** 이보다 덜 움직이면 방향을 바꾸지 않는다 (GPS 흔들림) */
        const val HEADING_MIN_MOVE_M = 3.0
        /** 위치 사이를 미끄러지는 시간 (위치는 약 1초마다 온다) */
        const val GROUP_ANIM_MS = 950L
        /** 이보다 멀리 뛰면 애니메이션 없이 옮긴다 */
        const val GROUP_JUMP_M = 3_000.0
        /** 일행 전체 보기에서 보여 줄 최소 범위 (붙어 있어도 너무 확대되지 않게) */
        const val GROUP_MIN_SPAN_M = 400.0
        /** 일행 전체 보기에서 화면이 준 여백에 더할 마커·이름표 여유 */
        const val GROUP_MARKER_MARGIN_DP = 56
        /** 일행 차를 눌렀을 때 차가 놓일 화면 위치 (왼쪽·오른쪽 카드 사이, 위쪽 카드 아래) */
        const val SELECT_PIVOT_X = 0.42f
        const val SELECT_PIVOT_Y = 0.68f
    }
}
