package com.nanugil.app.ui.navi

import android.graphics.Bitmap
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 글래스모피즘 카드 뒤에 깔 "흐린 지도" 원본.
 *
 * 지금은 쓰지 않는다 (NaviDriveActivity가 LocalMapBackdrop을 주지 않음 → 블러 없는 반투명 카드).
 * 지도를 끌 때 CPU·끊김이 늘어서 껐다. 실제 차량 기기에서 여유가 있으면 지도를 useTextureView(true)로 띄우고
 * `CompositionLocalProvider(LocalMapBackdrop provides backdrop)` + `backdrop.run(mapView)`로 다시 켤 수 있다.
 *
 * 네이버 지도는 원래 별도 OpenGL 화면(SurfaceView)이라 앱이 그 위 카드 뒤를 흐리게 할 수 없다.
 * 그래서 지도를 TextureView 모드로 띄우고(NaverMapOptions.useTextureView), 지도 그림을 작게 줄여
 * 주기적으로 복사해 둔다. 각 카드([Panel])는 자기 뒤에 해당하는 부분을 잘라 흐리게 그린다.
 * 작게 줄인 그림을 다시 키우는 것만으로도 부드럽게 번지고, 그 위에 blur를 한 번 더 건다.
 */
class MapBackdrop {
    /** 줄인 지도 그림 (지도 화면 기준 1/[DOWNSCALE] 크기) */
    var image: ImageBitmap? = null
        private set
    /** 그림이 바뀔 때마다 오른다. 카드는 그리기 단계에서 이 값을 읽어 다시 그린다. */
    var version by mutableIntStateOf(0)
        private set
    /** 지도 화면 왼쪽 위의 창(window) 좌표 */
    var mapOriginInWindow: Offset = Offset.Zero
        private set

    private val buffers = arrayOfNulls<Bitmap>(2)
    private var next = 0

    /** 지도 뷰 안의 TextureView를 찾아 [intervalMs]마다 줄인 그림을 복사한다. 화면이 보이는 동안만 부른다. */
    suspend fun run(mapView: View, intervalMs: Long = 66L) {
        while (coroutineContext.isActive) {
            capture(mapView)
            delay(intervalMs)
        }
    }

    private fun capture(mapView: View) {
        val texture = findTextureView(mapView) ?: return
        if (!texture.isAvailable || texture.width <= 0 || texture.height <= 0) return
        val w = (texture.width / DOWNSCALE).coerceAtLeast(1)
        val h = (texture.height / DOWNSCALE).coerceAtLeast(1)
        // 같은 비트맵에 계속 덮어쓰면 화면이 이전 그림을 그리는 도중 바뀌므로 두 장을 번갈아 쓴다
        val bmp = buffers[next]?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { buffers[next] = it }
        texture.getBitmap(bmp)
        val loc = IntArray(2)
        texture.getLocationInWindow(loc)
        mapOriginInWindow = Offset(loc[0].toFloat(), loc[1].toFloat())
        image = bmp.asImageBitmap()
        next = 1 - next
        version++
    }

    private fun findTextureView(view: View): TextureView? {
        if (view is TextureView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findTextureView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    companion object {
        /** 지도 그림을 얼마나 줄여서 복사할지 (작을수록 가볍고 더 부드럽게 번진다) */
        const val DOWNSCALE = 6
    }
}

/** 화면 안의 카드들이 같은 흐린 지도 원본을 쓰도록 내려 준다. 없으면 반투명 유리만 그린다. */
val LocalMapBackdrop = staticCompositionLocalOf<MapBackdrop?> { null }
