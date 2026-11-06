package com.nanugil.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {

    // 서버가 정해지기 전까지는 가짜 그룹 데이터로 화면을 돌린다.
    private val groupRepository = FakeGroupLocationRepository()
    private val opinetClient = OpinetClient(BuildConfig.OPINET_API_KEY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GroupDriveScreen(
                        groupRepository = groupRepository,
                        opinetClient = opinetClient,
                        onProposeMeetup = { station ->
                            // TODO: 서버로 합류 이벤트 전송 → 각 차량에서 NaviHelper.addWaypoint + requestReRoute
                            Toast.makeText(
                                this,
                                "${station.name} 합류 제안 (NaviHelper 연동 예정)",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
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
