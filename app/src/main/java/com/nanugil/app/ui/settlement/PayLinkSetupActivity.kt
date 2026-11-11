package com.nanugil.app.ui.settlement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nanugil.app.data.pay.PayLinkStore
import com.nanugil.app.data.vehicle.GearState
import com.nanugil.app.nanugilApp

/**
 * 운전자 카카오페이 송금 링크 등록. 앱을 처음 켤 때 한 번 띄우고, 정산 요청 화면에서도 열 수 있다.
 * 글자를 입력하는 화면이라 정산 화면처럼 `distractionOptimized`를 붙이지 않고, P단에서만 입력을 받는다.
 */
class PayLinkSetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PayLinkStore(this)
        val gearWatcher = nanugilApp.gearWatcher
        setContent {
            val gear by gearWatcher.gear.collectAsState()
            var text by remember { mutableStateOf(store.link.orEmpty()) }
            var error by remember { mutableStateOf<String?>(null) }
            val parked = gear == GearState.PARK
            val palette = settlementPalette()
            CompositionLocalProvider(LocalSettlementPalette provides palette) {
                Column(Modifier.fillMaxSize().background(palette.background).padding(24.dp)) {
                    Header(title = "송금 링크 등록", onBack = ::finish)
                    Panel(Modifier.padding(top = 20.dp).fillMaxWidth()) {
                        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Label("정산할 때 동승자가 이 링크로 운전자에게 송금합니다.", TextSize.Body, palette.textPrimary, FontWeight.Bold)
                            Label(
                                "카카오페이 앱 → 송금 → QR/송금코드에서 '링크 복사' 후 붙여 넣어 주세요. 처음 한 번만 등록하면 됩니다.",
                                TextSize.Caption, palette.textSecondary, maxLines = 2,
                            )
                            InputField(
                                "카카오페이 송금 링크", text, { text = it; error = null }, Modifier.fillMaxWidth(), KeyboardType.Uri,
                            )
                            val message = if (!parked) "차를 세우고 P단에서 등록해 주세요." else error
                            Label(message.orEmpty(), TextSize.Caption, BrandAmber, FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SecondaryButton("나중에", onClick = { store.skip(); finish() }, modifier = Modifier.width(220.dp))
                                PrimaryButton(
                                    "저장",
                                    enabled = parked,
                                    onClick = {
                                        try {
                                            store.save(text)
                                            setResult(RESULT_OK)
                                            finish()
                                        } catch (e: IllegalArgumentException) {
                                            error = e.message
                                        }
                                    },
                                    modifier = Modifier.width(220.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, PayLinkSetupActivity::class.java)
    }
}
