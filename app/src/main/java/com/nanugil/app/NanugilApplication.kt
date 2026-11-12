package com.nanugil.app

import ai.pleos.playground.vehicle.Vehicle
import android.app.Application
import android.content.Context
import android.util.Log
import com.nanugil.app.data.vehicle.VehicleFuelReader
import com.nanugil.app.data.fuel.OpinetClient
import com.nanugil.app.data.pay.SettlementRequestStore
import com.nanugil.app.data.vehicle.TripEnergyRecorder
import com.nanugil.app.ui.settlement.SettlementActivity
import com.nanugil.app.ui.settlement.SettlementSession
import com.nanugil.app.ui.settlement.myCarType
import com.nanugil.app.ui.settlement.vehicleTypeFromFuelTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** 운행 중 연료·배터리 기록. 앱이 켜지면 바로 기록을 시작한다. */
    val tripRecorder: TripEnergyRecorder by lazy { TripEnergyRecorder(this, vehicle, fuelReader).also { it.start() } }

    /** 이번 운행 정산 값. #13의 08·09 화면과 값 수정 화면([SettlementActivity])이 함께 본다. */
    val settlement: SettlementSession by lazy {
        val opinet = OpinetClient(BuildConfig.OPINET_API_KEY)
        SettlementSession(
            recorder = tripRecorder,
            requests = SettlementRequestStore(this),
            readMyCarType = {
                val reported = withContext(Dispatchers.IO) {
                    runCatching { vehicleTypeFromFuelTypes(fuelReader.readFuelTypes()) }
                        .onFailure { Log.w("NanugilApplication", "연료 종류 읽기 실패", it) }
                        .getOrNull()
                }
                myCarType(reported, tripRecorder.fuel, tripRecorder.battery)
            },
            loadNearby = { product, at -> opinet.findStationsAround(at, product = product) },
            loadAverage = opinet::averagePrices,
        )
    }

    override fun onCreate() {
        super.onCreate()
        tripRecorder
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
