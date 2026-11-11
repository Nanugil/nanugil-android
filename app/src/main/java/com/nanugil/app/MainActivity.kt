package com.nanugil.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanugil.app.data.fuel.OpinetClient
import com.nanugil.app.data.mock.FakeGroupLocationRepository
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.navi.NaviController
import com.nanugil.app.data.navi.NaviPlace
import com.nanugil.app.data.route.NaverDirectionsClient
import com.nanugil.app.ui.drive.GroupDriveScreen
import com.nanugil.app.ui.settlement.SettlementScreen

class MainActivity : ComponentActivity() {

    // 서버가 정해지기 전까지는 가짜 그룹 데이터로 화면을 돌린다.
    private val groupRepository = FakeGroupLocationRepository()
    private val opinetClient = OpinetClient(BuildConfig.OPINET_API_KEY)
    private val directionsClient = NaverDirectionsClient(BuildConfig.NAVER_MAP_CLIENT_ID, BuildConfig.NAVER_MAP_CLIENT_SECRET)
    private lateinit var naviController: NaviController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        naviController = NaviController(this).also { it.connect() }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 화면이 두 개뿐이라 상태 하나로 전환. 화면이 늘면 Navigation Compose로 바꾼다.
                    var showSettlement by rememberSaveable { mutableStateOf(false) }
                    BackHandler(enabled = showSettlement) { showSettlement = false }

                    if (showSettlement) {
                        SettlementScreen(onBack = { showSettlement = false })
                    } else {
                        val naviState by naviController.state.collectAsState()
                        Box(Modifier.fillMaxSize()) {
                            GroupDriveScreen(
                                groupRepository = groupRepository,
                                opinetClient = opinetClient,
                                directionsClient = directionsClient,
                                naviState = naviState,
                                onStartRoute = {
                                    naviController.startRoute(
                                        NaviPlace(name = GROUP_DESTINATION_NAME, position = MockGroupDriveData.DESTINATION),
                                    )
                                },
                                onCancelRoute = naviController::cancelRoute,
                                onSearchPlace = naviController::searchPlace,
                                onOpenNavi = naviController::openNaviScreen,
                                onPickDestination = { point ->
                                    naviController.startRoute(NaviPlace(name = "지도에서 선택한 곳", position = point))
                                },
                                onProposeMeetup = { station ->
                                    // TODO(서버): 합류 이벤트를 그룹 전체에 푸시 → 각 차량이 같은 addMeetupStop을 실행
                                    naviController.addMeetupStop(NaviPlace(name = station.name, position = station.position))
                                    Toast.makeText(this@MainActivity, "${station.name} 내비 경유지로 추가", Toast.LENGTH_SHORT).show()
                                },
                            )
                            // TODO: 주행 중 텍스트 입력 금지. 정차 중(P단) 또는 운행 종료 후에만 정산 화면을 연다.
                            ExtendedFloatingActionButton(
                                onClick = { showSettlement = true },
                                modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
                            ) { Text("정산하기") }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 내장 내비 화면에서 안내를 시작·종료하고 돌아왔을 수 있으니 상태를 다시 맞춘다.
        naviController.refreshRoute()
    }

    override fun onDestroy() {
        naviController.release()
        super.onDestroy()
    }

    private companion object {
        /** 서버가 붙기 전까지 쓰는 그룹 목적지 이름 (좌표는 MockGroupDriveData.DESTINATION) */
        const val GROUP_DESTINATION_NAME = "가평 펜션"
    }
}

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("나누길", style = MaterialTheme.typography.displayMedium)
        Text("같이 달리고, 똑같이 나누는 그룹 주행 앱", style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme { HomeScreen() }
}
