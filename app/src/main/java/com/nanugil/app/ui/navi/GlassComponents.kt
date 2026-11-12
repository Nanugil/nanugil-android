package com.nanugil.app.ui.navi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/*
 * 나누길 Matte Dark Glass 디자인 토큰.
 * 수치는 Figma "나누길 와이어프레임 v5" 카드(Section) 실측값 + 차량용 다크 글래스 규칙을 합쳤다.
 *
 * - 바탕:   #1A1C22 (Figma 카드 색) 78% — 지도는 형체만 남도록 묵직하게
 * - 흐림:   카드 뒤 지도 blur 32dp (Figma 배경 흐림 64 ≈ 32dp). 지금은 성능 때문에 끔 → 매트 바탕만
 * - 결:     Figma 선형 그라데이션 그대로 — 흰 16% → 흰 3% → #8FAEFF 6.5% → #6E96FF 10%
 * - 테두리: 1dp White 10%, 위쪽 안쪽 1px 하이라이트 White 10% (Figma 안쪽 그림자 0,1 #FFF 10%)
 * - 그림자: Figma 바깥 그림자 (0, 8, 흐림 24, #000 32%)
 * - 모서리: 24dp
 * - 글자:   큰 글자·제목 White Bold, 보조 #9CA3AF Regular, 글자마다 옅은 그림자
 * - 터치:   모든 버튼 최소 높이 64dp
 */

/** 카드 모서리 24dp */
internal val GlassShape = RoundedCornerShape(24.dp)

/** 차량용 최소 터치 높이 */
internal val MinTouch = 64.dp

private val MatteBase = Color(0xFF1A1C22)
private const val MatteAlpha = 0.78f
private const val SolidAlpha = 1f
private val GlassBlur = 32.dp
private val GlassBorder = Color.White.copy(alpha = 0.1f)
private val GlassTopHighlight = Color.White.copy(alpha = 0.1f)

/** Figma 카드 결 (왼쪽 위 → 오른쪽 아래) */
private val DarkSheen = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.16f),
    0.35f to Color.White.copy(alpha = 0.03f),
    0.7f to Color(0xFF8FAEFF).copy(alpha = 0.065f),
    1f to Color(0xFF6E96FF).copy(alpha = 0.10f),
    start = Offset.Zero,
    end = Offset(1400f, 1400f),
)

/** 라이트 테마(Figma "화이트톤 글라스") */
private val LightBase = Color(0xFFF1F5F9)
private const val LightAlpha = 0.80f
private val LightSheen = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.55f),
    0.5f to Color.White.copy(alpha = 0.1f),
    1f to Color(0xFF8FAEFF).copy(alpha = 0.08f),
    start = Offset.Zero,
    end = Offset(1400f, 1400f),
)

/**
 * Matte Dark Glass 표면. 모든 카드·위쪽 바가 이걸 쓴다.
 *
 * 기본은 블러 없이 묵직한 매트 바탕(78%)과 Figma 결만 얹는다 (지도 성능 우선, [MapBackdrop] 참고).
 * [LocalMapBackdrop]이 있으면 그 줄인 지도 그림에서 카드 자리만큼 잘라 blur를 깐 뒤 그 위에 얹는다.
 */
@Composable
internal fun GlassSurface(
    dark: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    shadowElevation: Dp = 16.dp,
    /** 다른 카드 위에 겹쳐 뜨는 패널(검색 등)은 아래 글자가 비치지 않게 불투명하게 */
    solid: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalMapBackdrop.current
    var posInWindow by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            // Figma 바깥 그림자: 아래로 8, 넓게 24 퍼짐, 검정 32%
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.32f),
                spotColor = Color.Black.copy(alpha = 0.32f),
            )
            .clip(shape)
            .onGloballyPositioned { posInWindow = it.positionInWindow() },
    ) {
        // ① 카드 뒤 지도를 흐리게 (형체만 남도록)
        if (backdrop != null) {
            Box(
                Modifier.matchParentSize().blur(GlassBlur, BlurredEdgeTreatment.Rectangle).drawBehind {
                    backdrop.version // 지도 그림이 바뀌면 다시 그린다
                    val img = backdrop.image ?: return@drawBehind
                    val k = 1f / MapBackdrop.DOWNSCALE
                    val left = ((posInWindow.x - backdrop.mapOriginInWindow.x) * k).toInt().coerceIn(0, img.width - 1)
                    val top = ((posInWindow.y - backdrop.mapOriginInWindow.y) * k).toInt().coerceIn(0, img.height - 1)
                    val w = (size.width * k).toInt().coerceIn(1, img.width - left)
                    val h = (size.height * k).toInt().coerceIn(1, img.height - top)
                    drawImage(
                        img,
                        srcOffset = IntOffset(left, top),
                        srcSize = IntSize(w, h),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Low,
                    )
                },
            )
        }
        // ② 묵직한 매트 바탕 + Figma 결
        Box(
            Modifier.matchParentSize()
                .background(
                    if (dark) MatteBase.copy(alpha = if (solid) SolidAlpha else MatteAlpha)
                    else LightBase.copy(alpha = if (solid) SolidAlpha else LightAlpha),
                )
                .background(if (dark) DarkSheen else LightSheen),
        )
        // ③ 1dp 테두리 + 위쪽 안쪽 1px 하이라이트 (끄트머리에 빛이 살짝 맺히는 느낌)
        Box(
            Modifier.matchParentSize()
                .border(1.dp, if (dark) GlassBorder else Color.Black.copy(alpha = 0.06f), shape)
                .drawBehind {
                    drawLine(
                        if (dark) GlassTopHighlight else Color.White.copy(alpha = 0.8f),
                        start = Offset(24.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 24.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        )
        // ④ 내용. 글자 그림자는 빛 번짐처럼 보여서 깔지 않는다 (카드 바탕이 불투명에 가까워 없어도 잘 읽힌다)
        content()
    }
}
