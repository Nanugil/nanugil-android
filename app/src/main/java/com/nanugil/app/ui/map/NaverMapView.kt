package com.nanugil.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.fuel.GasStation
import com.nanugil.app.data.group.CarState
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.PathOverlay
import android.graphics.Color
import com.naver.maps.map.util.MarkerIcons

private const val COLOR_ME = 0xFF5B2EFF.toInt() // 나누길 보라
private const val COLOR_GROUP = 0xFF3D7BFF.toInt()
private const val COLOR_LOW_FUEL = 0xFFE5484D.toInt()
private const val COLOR_STATION = 0xFF2E9E5B.toInt()
private const val COLOR_STATION_SELECTED = 0xFFFF9F1C.toInt()
private const val COLOR_DESTINATION = 0xFF111111.toInt()
private const val COLOR_ROUTE = 0xFF5B2EFF.toInt()

fun Wgs84Point.toLatLng() = LatLng(latitude, longitude)

/**
 * 네이버 지도를 Compose에 올리고, 일행 차량과 주유소를 마커로 그린다.
 *
 * MapView는 액티비티 생명주기를 그대로 전달받아야 하므로 LifecycleEventObserver로 연결한다.
 */
@Composable
fun NaverMapView(
    cars: List<CarState>,
    stations: List<GasStation>,
    selectedStation: GasStation?,
    onStationClick: (GasStation) -> Unit,
    modifier: Modifier = Modifier,
    destination: Wgs84Point? = null,
    destinationName: String? = null,
    onMapLongClick: (Wgs84Point) -> Unit = {},
    routePath: List<Wgs84Point> = emptyList(),
) {
    val context = LocalContext.current
    val currentOnLongClick by rememberUpdatedState(onMapLongClick)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }

    DisposableEffect(lifecycle, mapView) {
        var created = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> { mapView.onCreate(null); created = true }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { mapView.onDestroy(); created = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // 액티비티는 살아 있는데 이 화면만 빠지는 경우에도 지도 리소스를 정리한다.
            if (created) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            // 주행 중 조작을 줄이기 위해 확대/축소 버튼은 숨긴다.
            map.uiSettings.isZoomControlEnabled = false
            // 지도를 길게 누르면 그 지점을 목적지로 쓴다.
            map.setOnMapLongClickListener { _, latLng ->
                currentOnLongClick(Wgs84Point(latLng.latitude, latLng.longitude))
            }
            naverMap = map
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    val map = naverMap ?: return
    val density = context.resources.displayMetrics.density
    RouteLine(map, routePath, paddingPx = (96 * density).toInt())
    CarMarkers(map, cars)
    DestinationMarker(
        map,
        destination,
        destinationName,
        myPosition = cars.firstOrNull { it.isMe }?.position,
        paddingPx = (96 * density).toInt(),
    )
    StationMarkers(map, stations, selectedStation, onStationClick, paddingPx = (96 * density).toInt(), cars = cars)
}

@Composable
private fun CarMarkers(map: NaverMap, cars: List<CarState>) {
    val markers = remember(map) { mutableMapOf<String, Marker>() }
    var cameraPlaced by remember(map) { mutableStateOf(false) }

    LaunchedEffect(map, cars) {
        val ids = cars.map { it.carId }.toSet()
        markers.keys.filterNot { it in ids }.forEach { markers.remove(it)?.map = null }

        cars.forEach { car ->
            val marker = markers.getOrPut(car.carId) {
                // 위치 없이 지도에 올리면 InvalidCoordinateException(NaN)으로 크래시 → position을 먼저 넣는다.
                Marker().apply {
                    position = car.position.toLatLng()
                    icon = MarkerIcons.BLACK
                    zIndex = 100
                    this.map = map
                }
            }
            marker.position = car.position.toLatLng()
            marker.iconTintColor = when {
                car.isLowFuel -> COLOR_LOW_FUEL
                car.isMe -> COLOR_ME
                else -> COLOR_GROUP
            }
            marker.captionText = car.driverName
            marker.subCaptionText = "${car.speedKmh}km/h · 연료 ${car.fuelPercent}%"
        }

        if (!cameraPlaced) {
            cars.firstOrNull { it.isMe }?.let {
                map.moveCamera(CameraUpdate.scrollAndZoomTo(it.position.toLatLng(), 11.0))
                cameraPlaced = true
            }
        }
    }

    DisposableEffect(map) {
        onDispose {
            markers.values.forEach { it.map = null }
            markers.clear()
        }
    }
}

/** 출발 → 경유지 → 목적지 경로선. 새 경로가 오면 경로 전체가 보이게 카메라를 맞춘다. */
@Composable
private fun RouteLine(map: NaverMap, path: List<Wgs84Point>, paddingPx: Int) {
    val overlay = remember(map) {
        PathOverlay().apply {
            color = COLOR_ROUTE
            outlineColor = Color.WHITE
            width = 14
            outlineWidth = 3
            zIndex = -1 // 마커 아래
        }
    }

    LaunchedEffect(map, path) {
        // PathOverlay는 좌표가 2개 미만이면 예외가 나므로 지도에서 내린다.
        if (path.size < 2) {
            overlay.map = null
            return@LaunchedEffect
        }
        val coords = path.map { it.toLatLng() }
        overlay.coords = coords
        overlay.map = map
        val bounds = coords.fold(LatLngBounds.Builder()) { b, p -> b.include(p) }.build()
        map.moveCamera(CameraUpdate.fitBounds(bounds, paddingPx).animate(CameraAnimation.Easing))
    }

    DisposableEffect(map) {
        onDispose { overlay.map = null }
    }
}

@Composable
private fun DestinationMarker(
    map: NaverMap,
    destination: Wgs84Point?,
    name: String?,
    myPosition: Wgs84Point?,
    paddingPx: Int,
) {
    val marker = remember(map) { Marker().apply { icon = MarkerIcons.BLACK; iconTintColor = COLOR_DESTINATION; zIndex = 200 } }

    LaunchedEffect(map, destination, name) {
        if (destination == null) {
            marker.map = null
            return@LaunchedEffect
        }
        marker.position = destination.toLatLng()
        marker.captionText = name ?: "목적지"
        marker.map = map
    }

    // 목적지가 새로 정해지면 내 차와 목적지가 한 화면에 들어오게 맞춘다 (차가 움직일 때마다는 하지 않는다).
    LaunchedEffect(map, destination) {
        destination ?: return@LaunchedEffect
        val points = listOfNotNull(destination, myPosition).map { it.toLatLng() }
        val update = if (points.size == 1) {
            CameraUpdate.scrollTo(points.first())
        } else {
            CameraUpdate.fitBounds(points.fold(LatLngBounds.Builder()) { b, p -> b.include(p) }.build(), paddingPx)
        }
        map.moveCamera(update.animate(CameraAnimation.Easing))
    }

    DisposableEffect(map) {
        onDispose { marker.map = null }
    }
}

@Composable
private fun StationMarkers(
    map: NaverMap,
    stations: List<GasStation>,
    selectedStation: GasStation?,
    onStationClick: (GasStation) -> Unit,
    paddingPx: Int,
    cars: List<CarState>,
) {
    val markers = remember(map) { mutableListOf<Marker>() }

    // 주유소 목록이 바뀌면 마커를 새로 그리고, 연료 부족 차량과 주유소가 한 화면에 들어오게 맞춘다.
    LaunchedEffect(map, stations) {
        markers.forEach { it.map = null }
        markers.clear()
        stations.forEach { station ->
            markers += Marker().apply {
                position = station.position.toLatLng()
                icon = MarkerIcons.BLACK
                iconTintColor = COLOR_STATION
                captionText = station.name
                subCaptionText = "%,d원".format(station.priceWon)
                tag = station.id
                onClickListener = Overlay.OnClickListener {
                    onStationClick(station)
                    true
                }
                this.map = map
            }
        }
        if (stations.isNotEmpty()) {
            val points = stations.map { it.position.toLatLng() } +
                cars.filter { it.isLowFuel }.map { it.position.toLatLng() }
            val bounds = points.fold(LatLngBounds.Builder()) { builder, p -> builder.include(p) }.build()
            map.moveCamera(CameraUpdate.fitBounds(bounds, paddingPx).animate(CameraAnimation.Easing))
        }
    }

    LaunchedEffect(map, stations, selectedStation) {
        markers.forEach { marker ->
            val selected = marker.tag == selectedStation?.id
            marker.iconTintColor = if (selected) COLOR_STATION_SELECTED else COLOR_STATION
            marker.zIndex = if (selected) 50 else 0
        }
        selectedStation?.let {
            map.moveCamera(CameraUpdate.scrollTo(it.position.toLatLng()).animate(CameraAnimation.Easing))
        }
    }

    DisposableEffect(map) {
        onDispose {
            markers.forEach { it.map = null }
            markers.clear()
        }
    }
}
