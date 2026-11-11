package com.nanugil.app.ui.settlement

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nanugil.app.share.RequestedTransfer
import com.nanugil.app.share.SettlementRequest

/** [text]를 담은 QR 이미지. 흰 바탕 검은 점이라 다크 모드에서도 폰 카메라가 읽는다. */
private fun qrBitmap(text: String, sizePx: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val pixels = IntArray(sizePx * sizePx) { i -> if (matrix[i % sizePx, i / sizePx]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * '정산 요청 보냄' 화면 (Figma 09 자리).
 * 왼쪽: 이 차 운전자의 카카오페이 송금 QR. 동승자가 폰으로 찍어 보낸다.
 * 오른쪽: 송금 목록. 받으면 운전자가 체크한다(카카오페이가 송금 결과를 알려 주지 않는다). 모두 체크하면 정산 완료.
 *
 * @param payLink 이 차 운전자의 송금 링크. 없으면 QR 대신 등록 버튼을 보여 준다.
 */
@Composable
fun SettlementRequestScreen(
    request: SettlementRequest,
    payLink: String?,
    onToggle: (index: Int) -> Unit,
    onRegisterLink: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = settlementPalette()
    CompositionLocalProvider(LocalSettlementPalette provides palette) {
        val p = palette
        Column(Modifier.fillMaxSize().background(p.background).padding(24.dp)) {
            Header(title = if (request.isComplete) "정산 완료" else "정산 요청 보냄", onBack = onBack)
            Row(Modifier.padding(top = 20.dp).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Panel(Modifier.weight(1f).fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Label("${request.myId}에게 보내 주세요", TextSize.Heading, p.textPrimary, FontWeight.Bold)
                        Label("폰 카메라로 찍으면 카카오페이 송금이 열립니다", TextSize.Caption, p.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        if (payLink != null) {
                            val qr = remember(payLink) { qrBitmap(payLink) }
                            Image(
                                qr, contentDescription = "${request.myId} 카카오페이 송금 QR",
                                modifier = Modifier.padding(top = 28.dp).size(360.dp).clip(RoundedCornerShape(16.dp)),
                            )
                            Label(
                                "송금 링크 변경", TextSize.Caption, BrandPrimary, FontWeight.Bold,
                                Modifier.padding(top = 20.dp).clickable(onClick = onRegisterLink).padding(12.dp),
                            )
                        } else {
                            Label("송금 링크가 등록되지 않았습니다.", TextSize.Body, BrandAmber, FontWeight.Bold, modifier = Modifier.padding(top = 40.dp))
                            PrimaryButton("송금 링크 등록", onClick = onRegisterLink, modifier = Modifier.padding(top = 20.dp).width(300.dp))
                        }
                    }
                }
                Panel(Modifier.weight(1f).fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 30.dp)) {
                        Label("주고받을 돈 · 받으면 체크", TextSize.Caption, p.textSecondary)
                        Column(Modifier.weight(1f).padding(top = 12.dp).verticalScroll(rememberScrollState())) {
                            request.transfers.forEachIndexed { i, t ->
                                TransferRow(t, mine = t.toId == request.myId || t.fromId == request.myId, onToggle = { onToggle(i) })
                            }
                        }
                        val left = request.transfers.count { !it.done }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label(
                                if (left == 0) "모두 받았습니다" else "${left}건 남음",
                                TextSize.Caption, if (left == 0) BrandSuccess else p.textSecondary, FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            PrimaryButton(
                                if (request.isComplete) "정산 마치기" else "다음 정산 시작",
                                onClick = onFinish, modifier = Modifier.width(260.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 송금 한 줄. 이 차 운전자가 받거나 보내는 송금은 진하게, 다른 차끼리의 송금은 흐리게 보여 준다. */
@Composable
private fun TransferRow(t: RequestedTransfer, mine: Boolean, onToggle: () -> Unit) {
    val p = LocalSettlementPalette.current
    val textColor = if (mine) p.textPrimary else p.textSecondary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onToggle).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (t.done) BrandSuccess else p.field),
            contentAlignment = Alignment.Center,
        ) { if (t.done) Label("✓", TextSize.Body, Color.White, FontWeight.Bold) }
        Label("${t.fromId} → ${t.toId}", TextSize.Body, textColor, FontWeight.Bold, Modifier.padding(start = 18.dp).weight(1f))
        Label("%,d원".format(t.amountWon), TextSize.Body, textColor, FontWeight.Bold)
    }
}
