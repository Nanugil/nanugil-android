package com.nanugil.app.ui.settlement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nanugil.app.BuildConfig
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.fuel.OpinetClient
import com.nanugil.app.data.mock.FakeGroupLocationRepository
import com.nanugil.app.data.pay.PayLinkStore
import com.nanugil.app.data.pay.SettlementRequestStore
import com.nanugil.app.data.vehicle.GearState
import com.nanugil.app.data.vehicle.settlementBlockReason
import com.nanugil.app.nanugilApp
import com.nanugil.app.share.SettlementRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 정산 입력 화면.
 *
 * 글자를 입력하는 화면이라 주행 중에는 쓰면 안 된다. 그래서 지도([com.nanugil.app.MainActivity])와 달리
 * 매니페스트에 `distractionOptimized`를 붙이지 않는다. 주행 중이 되면 시스템이 이 화면을 가리고,
 * 우리도 기어가 P단에서 빠지면 바로 닫는다.
 */
class SettlementActivity : ComponentActivity() {

    private val opinetClient = OpinetClient(BuildConfig.OPINET_API_KEY)
    private val payLinks by lazy { PayLinkStore(this) }
    private val requests by lazy { SettlementRequestStore(this) }

    // 송금 링크 등록 화면에서 돌아오면 QR을 바로 보여 주도록 onResume마다 다시 읽는다.
    private var payLink by mutableStateOf<String?>(null)

    override fun onResume() {
        super.onResume()
        payLink = payLinks.link
    }

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

                    // 보낸 정산 요청이 있으면 송금 화면부터 보여 준다(앱을 다시 켜도 이어진다).
                    var request by remember { mutableStateOf(requests.load()) }
                    val current = request
                    if (current != null) {
                        SettlementRequestScreen(
                            request = current,
                            payLink = payLink,
                            onToggle = { i -> request = current.toggled(i).also(requests::save) },
                            onRegisterLink = { startActivity(PayLinkSetupActivity.intent(this)) },
                            onFinish = {
                                requests.clear()
                                request = null
                            },
                            onBack = ::finish,
                        )
                        return@Surface
                    }

                    // 차종은 '값 수정'의 차량 카드마다 고른다(따로 차종 선택 단계를 두지 않는다).
                    SettlementScreen(
                        initialType = VehicleType.GASOLINE,
                        onBack = ::finish,
                        readMyFuelMl = fuelReader::readFuelMl,
                        readMyBatteryWh = fuelReader::readBatteryWh,
                        loadFuelPrices = opinetClient::averagePrices,
                        loadNearbyStations = { product -> opinetClient.findStationsAround(myPosition(), product = product) },
                        prefillMyCar = { car -> car.withRecorded(recorder.fuel, recorder.battery) },
                        readMyCarType = {
                            val reported = withContext(Dispatchers.IO) {
                                runCatching {
                                    val types = fuelReader.readFuelTypes()
                                    vehicleTypeFromFuelTypes(types).also { Log.i("SettlementActivity", "차 연료 종류 ${types.toList()} → $it") }
                                }
                                    .onFailure { Log.w("SettlementActivity", "연료 종류 읽기 실패", it) }
                                    .getOrNull()
                            }
                            myCarType(reported, recorder.fuel, recorder.battery)
                        },
                        onEditPayLink = { startActivity(PayLinkSetupActivity.intent(this)) },
                        onRequestSettlement = { result, myId ->
                            // 정산 결과를 확정해 저장한 뒤 운행 기록을 지운다(순서가 바뀌면 앱이 죽었을 때 둘 다 잃는다).
                            val sent = SettlementRequest.of(result, myId).also(requests::save)
                            recorder.reset()
                            request = sent
                            if (payLinks.link == null) startActivity(PayLinkSetupActivity.intent(this))
                        },
                    )
                }
            }
        }
    }

    // shortcut: 지도 화면과 같은 가짜 그룹 데이터의 내 차 위치(서울시청)를 쓴다. feat/emulator_map이 머지되면 NaviController 현재 위치로 바꾼다.
    private suspend fun myPosition(): Wgs84Point =
        FakeGroupLocationRepository().observeGroup("demo").first().first { it.isMe }.position

    companion object {
        fun intent(context: Context) = Intent(context, SettlementActivity::class.java)
    }
}
