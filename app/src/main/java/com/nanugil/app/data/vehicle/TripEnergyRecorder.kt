package com.nanugil.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlin.math.roundToLong

/**
 * 내 차의 연료(mL)·배터리(Wh) 잔량을 계속 받아 출발값, 도착값, 주유·충전량을 기록한다.
 * 정산 화면은 이 기록으로 A차(내 차) 칸을 채운다.
 *
 * - 연료: Pleos `registerFuelPercent`(%) × 탱크 용량. 전기차는 알림이 실패하니 기록하지 않는다.
 * - 배터리: Pleos `registerEvBatteryLevel`(Wh).
 * - 앱이 꺼졌다 켜져도 이어지도록 SharedPreferences에 저장한다. 정산 요청을 보내면 [reset]으로 새 운행을 시작한다.
 *
 * shortcut: 앱이 떠 있는 동안만 기록한다. 앱이 꺼진 사이의 주유는 놓친다. 서버의 운행 시작·종료가 생기면 포그라운드 서비스로 옮긴다.
 */
class TripEnergyRecorder(
    context: Context,
    private val vehicle: Vehicle,
    private val fuelReader: VehicleFuelReader,
) {
    private val prefs = context.getSharedPreferences("trip_energy", Context.MODE_PRIVATE)

    @Volatile var fuel: RefillDetector = load(FUEL); private set
    @Volatile var battery: RefillDetector = load(BATTERY); private set

    // 탱크 용량은 차가 바뀌지 않는 한 그대로다. 못 읽으면(전기차 등) 연료 기록을 건너뛴다.
    private val tankCapacityMl: Long? by lazy {
        runCatching { fuelReader.readTankCapacityMl() }.onFailure { Log.w(TAG, "탱크 용량 읽기 실패", it) }.getOrNull()
    }

    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(percent: Int?) {
            if (percent == null || percent !in 0..100) return
            val capacity = tankCapacityMl ?: return
            fuel = fuel.update(fuelMlFromPercent(percent, capacity), FUEL_THRESHOLD_ML).also { save(FUEL, it) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "연료 잔량 알림 실패 (전기차면 정상)", e)
        }
    }

    private val batteryListener = object : EvBatteryLevelListener {
        override fun onEvBatteryLevelUpdated(wh: Float?) {
            if (wh == null || wh < 0f) return
            battery = battery.update(wh.roundToLong(), BATTERY_THRESHOLD_WH).also { save(BATTERY, it) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "배터리 잔량 알림 실패 (연료차면 정상)", e)
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        vehicle.getFuel().registerFuelPercent(fuelListener)
        vehicle.getEvBattery().registerEvBatteryLevel(batteryListener)
    }

    /** 새 운행을 시작한다. 다음 알림 값이 출발값이 된다. */
    fun reset() {
        fuel = RefillDetector()
        battery = RefillDetector()
        prefs.edit().clear().apply()
    }

    private fun load(key: String) = RefillDetector(
        start = prefs.longOrNull("${key}_start"),
        latest = prefs.longOrNull("${key}_latest"),
        added = prefs.getLong("${key}_added", 0),
        lowest = prefs.longOrNull("${key}_lowest"),
        counted = prefs.getLong("${key}_counted", 0),
    )

    private fun save(key: String, d: RefillDetector) {
        prefs.edit()
            .putLong("${key}_start", d.start ?: return)
            .putLong("${key}_latest", d.latest ?: return)
            .putLong("${key}_added", d.added)
            .putLong("${key}_lowest", d.lowest ?: return)
            .putLong("${key}_counted", d.counted)
            .apply()
    }

    private fun SharedPreferences.longOrNull(key: String) = if (contains(key)) getLong(key, 0) else null

    private companion object {
        const val TAG = "TripEnergyRecorder"
        const val FUEL = "fuel"
        const val BATTERY = "battery"

        /** 연료는 1% 단위라 흔들림이 크다. 2 L 이상 올라야 주유로 본다 (vehicle-api-research.md의 임시값). */
        const val FUEL_THRESHOLD_ML = 2_000L

        /** 주행 중 회생 제동으로 조금씩 오르는 것은 빼고, 1 kWh 이상 올라야 충전으로 본다. */
        const val BATTERY_THRESHOLD_WH = 1_000L
    }
}
