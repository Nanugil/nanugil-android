package com.nanugil.app.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nanugil.app.data.fuel.GasStation
import com.nanugil.app.data.fuel.OpinetBrand
import com.nanugil.app.data.fuel.OpinetClient
import com.nanugil.app.data.fuel.OpinetConstants
import com.nanugil.app.data.fuel.OpinetException
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.group.GroupLocationRepository
import com.nanugil.app.ui.map.NaverMapView
import kotlinx.coroutines.launch

private val LowFuelRed = Color(0xFFE5484D)

/**
 * 그룹 드라이브 화면: 왼쪽은 네이버 지도, 오른쪽은 일행·주유소 패널.
 *
 * 연료가 [CarState.LOW_FUEL_PERCENT] 아래로 내려간 차가 생기면 그 차 위치 기준으로
 * 오피넷에서 반경 5km 주유소를 가격순으로 자동 조회한다.
 */
@Composable
fun GroupDriveScreen(
    groupRepository: GroupLocationRepository,
    opinetClient: OpinetClient,
    onProposeMeetup: (GasStation) -> Unit,
    groupId: String = "demo",
) {
    val cars by remember(groupId) { groupRepository.observeGroup(groupId) }
        .collectAsState(initial = emptyList())
    var stations by remember { mutableStateOf<List<GasStation>>(emptyList()) }
    var selected by remember { mutableStateOf<GasStation?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchedFor by remember { mutableStateOf<CarState?>(null) }
    val scope = rememberCoroutineScope()

    fun search(car: CarState) {
        scope.launch {
            searching = true
            error = null
            try {
                stations = opinetClient.findStationsAround(car.position)
                selected = stations.firstOrNull()
                searchedFor = car
            } catch (e: OpinetException) {
                error = e.message
            } finally {
                searching = false
            }
        }
    }

    // 연료 부족 차량이 새로 생기면 한 번 자동 조회
    val lowFuelCar = cars.firstOrNull { it.isLowFuel }
    LaunchedEffect(lowFuelCar?.carId) {
        lowFuelCar?.let { search(it) }
    }

    Row(Modifier.fillMaxSize()) {
        NaverMapView(
            cars = cars,
            stations = stations,
            selectedStation = selected,
            onStationClick = { selected = it },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SidePanel(
            cars = cars,
            stations = stations,
            selected = selected,
            searching = searching,
            error = error,
            searchedFor = searchedFor,
            onSearchNearMe = { cars.firstOrNull { it.isMe }?.let(::search) },
            onSelect = { selected = it },
            onProposeMeetup = onProposeMeetup,
            modifier = Modifier.width(400.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun SidePanel(
    cars: List<CarState>,
    stations: List<GasStation>,
    selected: GasStation?,
    searching: Boolean,
    error: String?,
    searchedFor: CarState?,
    onSearchNearMe: () -> Unit,
    onSelect: (GasStation) -> Unit,
    onProposeMeetup: (GasStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("그룹 드라이브", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        cars.forEach { CarRow(it) }

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = searchedFor?.let { "${it.driverName} 주변 주유소" } ?: "주변 주유소",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        searchedFor?.takeIf { it.isLowFuel }?.let {
            Text(
                "${it.driverName} 연료 ${it.fuelPercent}% — 반경 ${OpinetConstants.MAX_RADIUS_METERS / 1000}km 최저가 순",
                color = LowFuelRed,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stations, key = { it.id }) { station ->
                StationRow(station, isSelected = station.id == selected?.id, onClick = { onSelect(station) })
            }
        }

        // 주행 중에도 누르기 쉽도록 버튼은 크게
        Button(
            onClick = { selected?.let(onProposeMeetup) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("이 주유소로 합류 제안")
        }
        OutlinedButton(
            onClick = onSearchNearMe,
            enabled = !searching,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("내 차 주변 주유소 다시 찾기")
        }
    }
}

@Composable
private fun CarRow(car: CarState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                car.driverName + if (car.isMe) " (나)" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${car.speedKmh}km/h · 도착 ${car.etaMinutes}분",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "연료 ${car.fuelPercent}%",
            style = MaterialTheme.typography.titleMedium,
            color = if (car.isLowFuel) LowFuelRed else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StationRow(station: GasStation, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "%.1fkm · %s".format(station.distanceMeters / 1000.0, OpinetBrand.displayName(station.brandCode)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("%,d원".format(station.priceWon), style = MaterialTheme.typography.titleMedium)
        }
    }
}
