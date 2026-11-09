package com.nanugil.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Pleos Vehicle SDK에서 읽은 내 차 현재 상태. 차가 지원하지 않는 값은 null. */
data class VehicleStatus(
    /** 연료 잔량(%) — 내연기관·하이브리드 */
    val fuelPercent: Int? = null,
    /** 고전압 배터리 잔량(%) — 전기차 */
    val batteryPercent: Int? = null,
    /** 누적 주행거리(km) */
    val odometerKm: Float? = null,
    /** 연비(L/100km). 화면에는 km/L로 바꿔 보여 준다 */
    val fuelEconomyLPer100Km: Float? = null,
    /** 전비(km/kWh) */
    val evEfficiencyKmPerKwh: Float? = null,
) {
    /** 연료 %가 없고 배터리 %만 있으면 전기차로 본다 */
    val isEv: Boolean get() = fuelPercent == null && batteryPercent != null
    val energyPercent: Int? get() = fuelPercent ?: batteryPercent
}

/**
 * Pleos Vehicle SDK로 연료·배터리 잔량, 주행거리, 연비를 몇 초마다 읽는다.
 * (vehicle-api-research.md: getFuelPercent, getEvBatteryStateOfCharge, getCurrentDistanceDriven, 연비/전비)
 *
 * 권한: CAR_ENERGY(연료·배터리·연비), CAR_DRIVE(주행거리) — 매니페스트에 선언되어 있다.
 */
class VehicleStatusSource(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    private val _status = MutableStateFlow(VehicleStatus())
    val status: StateFlow<VehicleStatus> = _status.asStateFlow()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        vehicle.initialize()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                _status.value = readOnce()
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        vehicle.release()
    }

    private suspend fun readOnce(): VehicleStatus {
        val fuel = vehicle.getFuel()
        val battery = vehicle.getEvBattery()
        val odometer = vehicle.getOdometer()
        return VehicleStatus(
            fuelPercent = read("fuelPercent") { ok, fail -> fuel.getFuelPercent(ok, fail) },
            batteryPercent = read("batterySoc") { ok, fail -> battery.getEvBatteryStateOfCharge(ok, fail) },
            odometerKm = read("odometer") { ok, fail -> odometer.getCurrentDistanceDriven(ok, fail) },
            fuelEconomyLPer100Km = read("fuelEconomy") { ok, fail -> fuel.getCurrentFuelEconomy(ok, fail) }
                ?: read("fuelEconomyCum") { ok, fail -> fuel.getCumulativeFuelEconomy(ok, fail) },
            evEfficiencyKmPerKwh = read("evEfficiency") { ok, fail -> battery.getCurrentEvEfficiency(ok, fail) },
        )
    }

    /** 차가 지원하지 않는다고 답한 항목. 다시 묻지 않는다 (예: 전기차의 연료 %). */
    private val unsupported = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** 콜백 API를 suspend로 감싼다. 차가 지원하지 않거나 응답이 없으면 null. */
    private suspend fun <T> read(name: String, call: (onComplete: (T?) -> Unit, onFailed: (Exception) -> Unit) -> Unit): T? {
        if (name in unsupported) return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    call(
                        { value -> if (cont.isActive) cont.resume(value) },
                        { e ->
                            val message = e.message.orEmpty()
                            if ("NOT_SUPPORTED" in message || "not supported" in message) {
                                unsupported += name
                                Log.i(TAG, "$name: 이 차량은 지원하지 않음")
                            } else {
                                Log.d(TAG, "$name 읽기 실패: $message")
                            }
                            if (cont.isActive) cont.resume(null)
                        },
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "$name 호출 실패: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private companion object {
        const val TAG = "VehicleStatus"
        const val POLL_MS = 3_000L
        const val TIMEOUT_MS = 2_000L
    }
}
