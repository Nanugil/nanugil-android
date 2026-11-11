package com.nanugil.app.ui.settlement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nanugil.app.BuildConfig
import com.nanugil.app.data.fuel.OpinetClient
import com.nanugil.app.data.vehicle.GearState
import com.nanugil.app.data.vehicle.settlementBlockReason
import com.nanugil.app.nanugilApp

/**
 * 정산 입력 화면.
 *
 * 글자를 입력하는 화면이라 주행 중에는 쓰면 안 된다. 그래서 지도([com.nanugil.app.MainActivity])와 달리
 * 매니페스트에 `distractionOptimized`를 붙이지 않는다. 주행 중이 되면 시스템이 이 화면을 가리고,
 * 우리도 기어가 P단에서 빠지면 바로 닫는다.
 */
class SettlementActivity : ComponentActivity() {

    private val opinetClient = OpinetClient(BuildConfig.OPINET_API_KEY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 차량 연결은 앱 전체가 함께 쓴다. 이 화면에서 release하면 지도 화면의 기어 알림까지 끊긴다.
        val fuelReader = nanugilApp.fuelReader
        val gearWatcher = nanugilApp.gearWatcher
        val recorder = nanugilApp.tripRecorder
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val gear by gearWatcher.gear.collectAsState()
                    val blockReason = settlementBlockReason(gear)
                    LaunchedEffect(blockReason) {
                        // 화면이 막 열렸을 때는 기어를 아직 못 읽어 UNKNOWN일 수 있다. 주행 중으로 바뀐 것만 보고 닫는다.
                        if (blockReason != null && gear != GearState.UNKNOWN) {
                            Toast.makeText(this@SettlementActivity, blockReason, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }

                    // 차종은 '값 수정'의 차량 카드마다 고른다(따로 차종 선택 단계를 두지 않는다).
                    SettlementScreen(
                        initialType = VehicleType.GASOLINE,
                        onBack = ::finish,
                        readMyFuelMl = fuelReader::readFuelMl,
                        readMyBatteryWh = fuelReader::readBatteryWh,
                        loadFuelPrices = opinetClient::averagePrices,
                        prefillMyCar = { car -> car.withRecorded(recorder.fuel, recorder.battery) },
                        // TODO(팀장 결정): 송금 안내 방식(QR·계좌, 송금 순서)이 정해지면 Figma 09 "정산 요청 보냄" 화면으로 연결
                        onRequestSettlement = {
                            // 이번 운행 정산이 끝났으니 다음 운행 기록을 새로 시작한다.
                            recorder.reset()
                            Toast.makeText(this, "송금 안내 방식이 정해지면 연결됩니다.", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, SettlementActivity::class.java)
    }
}
