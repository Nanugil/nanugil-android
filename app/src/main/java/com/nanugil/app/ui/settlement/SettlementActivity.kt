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
import com.nanugil.app.data.vehicle.GearState
import com.nanugil.app.data.vehicle.settlementBlockReason
import com.nanugil.app.nanugilApp

/**
 * 정산 값 수정 화면 (차량별 연료·배터리·통행료, 차종, 충전기 출력, 주유소 선택).
 *
 * 정산 요약(08)·요청(09)은 주행 중에도 보이는 #13 화면이 보여 주고, 글자를 넣는 입력만 여기서 한다.
 * 그래서 매니페스트에 `distractionOptimized`를 붙이지 않는다. 주행 중이 되면 시스템이 이 화면을 가리고,
 * 우리도 기어가 P단에서 빠지면 바로 닫는다. 고친 값은 [SettlementSession]에 바로 반영된다.
 */
class SettlementActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 차량 연결은 앱 전체가 함께 쓴다. 이 화면에서 release하면 지도 화면의 기어 알림까지 끊긴다.
        val fuelReader = nanugilApp.fuelReader
        val gearWatcher = nanugilApp.gearWatcher
        val session = nanugilApp.settlement
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
                    // 08을 거치지 않고 바로 열린 경우(예전 지도 화면)에도 값이 채워지도록. 이미 불러왔으면 아무것도 안 한다.
                    LaunchedEffect(Unit) { session.load(here = null) }

                    SettlementEditScreen(
                        session = session,
                        onBack = ::finish,
                        readMyFuelMl = fuelReader::readFuelMl,
                        readMyBatteryWh = fuelReader::readBatteryWh,
                    )
                }
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, SettlementActivity::class.java)
    }
}
