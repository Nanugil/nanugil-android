package com.nanugil.app.ui.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 색·글자 크기는 은식님 Figma "나누길" 파일의 로고·브랜드 보드를 따른다.
// 글자: Noto Sans KR, 차량 화면 기준 최소 24px. Display 56(정산 금액) / Title 40(화면 제목) / Heading 32(카드 제목) / Body 28(본문) / Caption 24(보조 설명)
// 패널·배경 같은 화면 색은 같은 파일의 "그룹 드라이브 v5" 시안 값이다(#11 NaviDriveScreen과 같음).
// TODO(#11 머지 후): 두 화면이 같은 값을 쓰도록 공통 테마 파일로 옮긴다.

internal val BrandPrimary = Color(0xFF1E5EFF) // 버튼·내 차·강조
internal val BrandNavy = Color(0xFF002C5F) // Pleos·헤더
internal val BrandAmber = Color(0xFFFFB020) // 목적지·합류점, 주의
internal val BrandDanger = Color(0xFFE5484D) // 경고·삭제
internal val BrandSuccess = Color(0xFF12A150) // 도착·정산 완료

internal object TextSize {
    val Display = 56.sp
    val Title = 40.sp
    val Heading = 32.sp
    val Body = 28.sp
    val Caption = 24.sp
}

/** Figma v5 두 테마(다크 / 화이트톤 글라스) 중 정산 화면에 쓰는 값. */
internal data class SettlementPalette(
    val dark: Boolean,
    val background: Color,
    val panel: Color,
    val panelBorder: Color,
    val field: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val secondaryButton: Color,
    val secondaryButtonText: Color,
    val avatar: Color,
)

internal val DarkSettlementPalette = SettlementPalette(
    dark = true,
    background = Color(0xFF0A0A0B),
    panel = Color(0xF21A1B1F),
    panelBorder = Color(0x14FFFFFF),
    field = Color(0xFF26282E),
    textPrimary = Color(0xFFF5F6F8),
    textSecondary = Color(0xFF9CA3AF),
    divider = Color(0x1FFFFFFF),
    secondaryButton = Color(0xFF2A2C32),
    secondaryButtonText = Color(0xFFE5E7EB),
    avatar = Color(0xFF2A2C32),
)

internal val LightSettlementPalette = SettlementPalette(
    dark = false,
    background = Color(0xFFE9EDF2),
    panel = Color(0xE6FFFFFF),
    panelBorder = Color(0x1A0F172A),
    field = Color(0xFFF1F5F9),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    divider = Color(0x1A0F172A),
    secondaryButton = Color(0xFFE2E8F0),
    secondaryButtonText = Color(0xFF334155),
    avatar = Color(0xFFE2E8F0),
)

internal val LocalSettlementPalette = staticCompositionLocalOf { DarkSettlementPalette }

@Composable
internal fun settlementPalette(): SettlementPalette =
    if (isSystemInDarkTheme()) DarkSettlementPalette else LightSettlementPalette

internal val CardShape = RoundedCornerShape(28.dp)
private val ButtonShape = RoundedCornerShape(14.dp)
private val FieldShape = RoundedCornerShape(14.dp)

@Composable
internal fun Panel(modifier: Modifier = Modifier, shape: Shape = CardShape, content: @Composable () -> Unit) {
    val p = LocalSettlementPalette.current
    Box(
        modifier
            .shadow(if (p.dark) 24.dp else 18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(p.panel)
            .border(1.dp, p.panelBorder, shape),
    ) { content() }
}

@Composable
internal fun Label(
    text: String,
    size: TextUnit,
    color: Color = LocalSettlementPalette.current.textPrimary,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    maxLines: Int = 1,
) {
    Text(
        text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, letterSpacing = letterSpacing),
    )
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.height(72.dp).clip(ButtonShape)
            .background(if (enabled) BrandPrimary else BrandPrimary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, TextSize.Body, Color.White, FontWeight.Bold) }
}

@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalSettlementPalette.current
    Box(
        modifier.height(72.dp).clip(ButtonShape).background(p.secondaryButton).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, TextSize.Body, p.secondaryButtonText, FontWeight.Bold) }
}

/** 작은 선택 버튼. 카드 머리의 차종 고르기에 쓴다. */
@Composable
internal fun ChipButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalSettlementPalette.current
    Box(
        modifier.height(56.dp).clip(ButtonShape)
            .background(if (selected) BrandPrimary else p.secondaryButton)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Label(text, TextSize.Caption, if (selected) Color.White else p.secondaryButtonText, FontWeight.Bold) }
}

/** 둥근 상태 표시. 유가 출처처럼 짧은 안내에 쓴다. */
@Composable
internal fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Label(text, TextSize.Caption, color, FontWeight.Bold) }
}

/**
 * 이름표가 위에 붙은 입력 칸.
 * @param action 이름표 오른쪽에 붙는 작은 글자 버튼 (예: "차에서 읽기").
 */
@Composable
internal fun InputField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    action: Pair<String, () -> Unit>? = null,
) {
    val p = LocalSettlementPalette.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(label, TextSize.Caption, p.textSecondary, modifier = Modifier.weight(1f))
            action?.let { (text, onClick) ->
                Label(text, TextSize.Caption, BrandPrimary, FontWeight.Bold, Modifier.clickable(onClick = onClick))
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = p.textPrimary, fontSize = TextSize.Body, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(BrandPrimary),
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(68.dp).clip(FieldShape).background(p.field),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { inner() }
            },
        )
    }
}

/** 사람 이름 첫 글자를 넣은 동그라미. Figma 08의 주고받을 돈 목록 모양. */
@Composable
internal fun Avatar(name: String, highlighted: Boolean = false) {
    val p = LocalSettlementPalette.current
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(if (highlighted) BrandPrimary else p.avatar),
        contentAlignment = Alignment.Center,
    ) { Label(name.take(1), TextSize.Caption, if (highlighted) Color.White else p.textPrimary, FontWeight.Bold) }
}
