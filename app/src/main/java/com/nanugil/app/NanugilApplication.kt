package com.nanugil.app

import ai.pleos.playground.vehicle.Vehicle
import android.app.Application
import android.content.Context
import android.util.Log
import com.nanugil.app.data.vehicle.VehicleFuelReader
import com.nanugil.app.data.vehicle.VehicleGearWatcher
import com.naver.maps.map.NaverMapSdk

/** 화면에서 앱 전체 객체([NanugilApplication])를 꺼낼 때 쓴다. */
val Context.nanugilApp: NanugilApplication get() = applicationContext as NanugilApplication

class NanugilApplication : Application() {

    /**
     * Pleos Vehicle SDK 연결은 앱 전체에서 하나만 쓴다.
     * SDK의 `release()`는 그 객체 것만이 아니라 앱 안의 모든 리스너를 지운다(`clearAllListeners`).
     * 화면마다 만들고 지우면, 정산 화면을 닫을 때 지도 화면의 기어 알림까지 끊긴다. 그래서 프로세스가 살아 있는 동안 유지한다.
     */
    val vehicle: Vehicle by lazy { Vehicle(this).also { it.initialize() } }

    /** 앱 전체가 함께 보는 기어 상태. 처음 쓸 때 알림 등록을 시작한다. */
    val gearWatcher: VehicleGearWatcher by lazy { VehicleGearWatcher(vehicle).also { it.start() } }

    val fuelReader: VehicleFuelReader by lazy { VehicleFuelReader(this, vehicle) }

    override fun onCreate() {
        super.onCreate()
        // 키는 local.properties의 NAVER_MAP_CLIENT_ID → BuildConfig로 들어온다 (README "시작하기" 참고).
        NaverMapSdk.getInstance(this).apply {
            client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAP_CLIENT_ID)
            onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { e ->
                // 키 누락, 패키지명 미등록(com.nanugil.app) 등이면 지도가 회색으로 뜨고 여기로 온다.
                Log.e("NaverMap", "네이버 지도 인증 실패", e)
            }
        }
    }
}
