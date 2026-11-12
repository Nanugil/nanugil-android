package com.nanugil.app.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.naver.maps.map.MapView

/**
 * 네이버 [MapView]를 Activity 생명주기에 그대로 맞춘다.
 *
 * MapView는 onCreate~onDestroy를 빠짐없이 받아야 GL 자원·타일 캐시가 정리된다.
 * Activity마다 직접 부르면 하나만 빠져도 메모리 누수가 생기므로, 이 객체를 lifecycle 관찰자로 붙여 한 번에 처리한다.
 *
 * - ON_CREATE ~ ON_DESTROY → MapView 같은 이름 함수
 * - 메모리 부족 → onLowMemory (시스템 콜백을 등록했다가 destroy 때 해제)
 * - 상태 저장은 Lifecycle 이벤트가 없으므로 Activity.onSaveInstanceState에서 [onSaveInstanceState]를 부른다
 * - destroy 후에는 자기 자신을 관찰자에서 빼서 Activity를 붙잡지 않는다
 */
class MapViewLifecycleBinder(
    private val mapView: MapView,
    private val savedInstanceState: Bundle?,
) : DefaultLifecycleObserver, ComponentCallbacks2 {

    private var created = false

    override fun onCreate(owner: LifecycleOwner) {
        mapView.onCreate(savedInstanceState)
        mapView.context.applicationContext.registerComponentCallbacks(this)
        created = true
    }

    override fun onStart(owner: LifecycleOwner) = mapView.onStart()

    override fun onResume(owner: LifecycleOwner) = mapView.onResume()

    override fun onPause(owner: LifecycleOwner) = mapView.onPause()

    override fun onStop(owner: LifecycleOwner) = mapView.onStop()

    override fun onDestroy(owner: LifecycleOwner) {
        if (created) {
            mapView.context.applicationContext.unregisterComponentCallbacks(this)
            mapView.onDestroy()
            created = false
        }
        owner.lifecycle.removeObserver(this)
    }

    /** Activity.onSaveInstanceState에서 부른다 (카메라 위치 등을 저장) */
    fun onSaveInstanceState(outState: Bundle) {
        if (created) mapView.onSaveInstanceState(outState)
    }

    override fun onTrimMemory(level: Int) {
        if (created && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
    }

    @Deprecated("API 34에서 deprecated, onTrimMemory로 대체되지만 구형 기기를 위해 남긴다")
    override fun onLowMemory() {
        if (created) mapView.onLowMemory()
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    companion object {
        /** MapView를 [owner] 생명주기에 붙인다. 반환된 binder로 상태 저장만 넘겨주면 된다. */
        fun bind(owner: LifecycleOwner, mapView: MapView, savedInstanceState: Bundle?): MapViewLifecycleBinder =
            MapViewLifecycleBinder(mapView, savedInstanceState).also { owner.lifecycle.addObserver(it) }
    }
}
