package com.nanugil.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanugil.app.data.fuel.OpinetClient
import com.nanugil.app.data.mock.FakeGroupLocationRepository
import com.nanugil.app.data.vehicle.settlementBlockReason
import com.nanugil.app.ui.drive.GroupDriveScreen
import com.nanugil.app.data.pay.PayLinkStore
import com.nanugil.app.ui.settlement.PayLinkSetupActivity
import com.nanugil.app.ui.settlement.SettlementActivity

/**
 * 그룹 드라이브 지도. 주행 중에 보는 화면이라 매니페스트에 `distractionOptimized`를 붙인다 (#11 NaviDriveActivity와 같은 방식).
 * 글자를 입력하는 정산은 주행 중 사용 표시가 없는 [SettlementActivity]로 따로 연다.
 */
class MainActivity : ComponentActivity() {

    // 서버가 정해지기 전까지는 가짜 그룹 데이터로 화면을 돌린다.
    private val groupRepository = FakeGroupLocationRepository()
    private val opinetClient = OpinetClient(BuildConfig.OPINET_API_KEY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 처음 켰을 때 한 번 운전자 송금 링크를 묻는다. 건너뛰면 정산 요청할 때 다시 묻는다.
        if (savedInstanceState == null && !PayLinkStore(this).asked) startActivity(PayLinkSetupActivity.intent(this))
        val gearWatcher = nanugilApp.gearWatcher
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val gear by gearWatcher.gear.collectAsState()
                    Box(Modifier.fillMaxSize()) {
                        GroupDriveScreen(
                            groupRepository = groupRepository,
                            opinetClient = opinetClient,
                            onProposeMeetup = { station ->
                                // TODO: 서버로 합류 이벤트 전송 → 각 차량에서 NaviHelper.addWaypoint + requestReRoute
                                Toast.makeText(
                                    this@MainActivity,
                                    "${station.name} 합류 제안 (NaviHelper 연동 예정)",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        // 주행 중 텍스트 입력 금지: P단에서만 연다. TODO: 운행 종료(서버의 trip 상태)도 열어 주는 조건에 넣기
                        SettlementEntryButton(
                            blockReason = settlementBlockReason(gear),
                            onClick = { startActivity(SettlementActivity.intent(this@MainActivity)) },
                            modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
                        )
                    }
                }
            }
        }
    }

}

/** 지도 위 '정산하기' 버튼. [blockReason]이 있으면 누를 수 없고, 그 이유를 버튼 아래에 보여 준다. */
@Composable
private fun SettlementEntryButton(blockReason: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onClick, enabled = blockReason == null) { Text("정산하기") }
        if (blockReason != null) {
            Text(
                blockReason,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
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
