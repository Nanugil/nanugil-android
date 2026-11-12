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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanugil.app.data.pay.PayLinkStore
import com.nanugil.app.data.vehicle.GearState
import com.nanugil.app.nanugilApp
import com.nanugil.app.ui.navi.BrandAmber
import com.nanugil.app.ui.navi.BrandPrimary
import com.nanugil.app.ui.navi.CardShape
import com.nanugil.app.ui.navi.Caption
import com.nanugil.app.ui.navi.FieldBox
import com.nanugil.app.ui.navi.InnerPad
import com.nanugil.app.ui.navi.Label
import com.nanugil.app.ui.navi.Panel
import com.nanugil.app.ui.navi.PrimaryButton
import com.nanugil.app.ui.navi.SecondaryButton
import com.nanugil.app.ui.navi.vehiclePalette

/**
 * 운전자 카카오페이 송금 링크 등록. 앱을 처음 켤 때 한 번 띄우고, 09 정산 요청 화면에서도 열 수 있다.
 * 글자를 입력하는 화면이라 값 수정 화면처럼 `distractionOptimized`를 붙이지 않고, P단에서만 저장한다.
 */
class PayLinkSetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PayLinkStore(this)
        val gearWatcher = nanugilApp.gearWatcher
        setContent {
            // 차량 주간·야간 모드를 따른다 (#13 화면과 같다)
            val p = vehiclePalette()
            val gear by gearWatcher.gear.collectAsState()
            var text by remember { mutableStateOf(store.link.orEmpty()) }
            var error by remember { mutableStateOf<String?>(null) }
            val parked = gear == GearState.PARK
            Column(Modifier.fillMaxSize().background(p.background).padding(horizontal = 32.dp, vertical = 24.dp)) {
                TopBar(p, title = "송금 링크 등록", onBack = ::finish)
                Panel(p, Modifier.padding(top = 20.dp).fillMaxWidth(), CardShape) {
                    Column(Modifier.padding(InnerPad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Label("정산할 때 동승자가 이 링크로 운전자에게 송금합니다.", 26.sp, p.textPrimary, FontWeight.Bold)
                        Label(
                            "카카오페이 앱 → 송금 → QR/송금코드에서 '링크 복사' 후 붙여 넣어 주세요. 처음 한 번만 등록하면 됩니다.",
                            21.sp, p.textSecondary,
                        )
                        Caption(p, "카카오페이 송금 링크", Modifier.padding(top = 10.dp))
                        FieldBox(p) {
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it; error = null },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                textStyle = TextStyle(color = p.textPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold),
                                cursorBrush = SolidColor(BrandPrimary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val message = if (!parked) "차를 세우고 P단에서 등록해 주세요." else error
                        Label(message.orEmpty(), 21.sp, BrandAmber, FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            SecondaryButton(p, "나중에", { store.skip(); finish() }, Modifier.width(220.dp))
                            PrimaryButton(
                                "저장",
                                parked,
                                {
                                    try {
                                        store.save(text)
                                        setResult(RESULT_OK)
                                        finish()
                                    } catch (e: IllegalArgumentException) {
                                        error = e.message
                                    }
                                },
                                Modifier.width(220.dp),
                            )
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
