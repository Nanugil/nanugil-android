package com.nanugil.app.data.fare

import android.content.Context
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.vehicle.VehicleStatus
import kotlin.math.roundToInt

/** 출발부터 지금까지 쓴 돈. */
data class TripCost(
    val traveledKm: Double,
    /** 유류비(전기차면 충전비) 추정 */
    val energyWon: Int,
    /** 지나온 거리만큼의 통행료 추정 */
    val tollWon: Int,
    val members: Int,
) {
    val totalWon: Int get() = energyWon + tollWon
    val perPersonWon: Int get() = if (members > 0) totalWon / members else totalWon
}

/**
 * 길안내를 시작한 순간을 기준으로 "출발 → 현재 위치"까지 쓴 비용을 계산한다.
 *
 * - 주행거리: Pleos Odometer(누적 km)의 출발 시점 값과 현재 값의 차이.
 *   GPS로 쌓은 이동 거리, 경로 위 진행 거리(출발 때 남은 거리 − 지금 남은 거리)와 비교해 가장 큰 값을 쓴다.
 * - 유류비: 주행거리 × 연비(Pleos Fuel, L/100km) × 유가. 전기차는 전비(km/kWh)와 충전 단가.
 * - 통행료: 내장 내비가 알려 준 경로 통행료 × 지나온 비율 (요금소를 지날 때 실제로 내는 시점과는 다를 수 있다).
 *
 * 출발 기준값은 앱을 껐다 켜도 이어지도록 SharedPreferences에 둔다.
 * TODO(정산): 그룹 전체 합산은 서버에서 받는다.
 */
class TripCostTracker(context: Context, private val members: Int) {

    private val prefs = context.getSharedPreferences("trip_cost", Context.MODE_PRIVATE)

    /**
     * 휘발유 1L 가격(원). 기본값은 고정값이고, 정산 화면과 금액을 맞추려면 오피넷 평균가로 바꿔 넣는다.
     * TODO(정산): 정산 쪽 OpinetClient.averagePrices()가 develop에 들어오면 그 값을 여기에 넣는다.
     */
    var fuelWonPerLiter: Double = DEFAULT_FUEL_WON_PER_L

    fun update(navi: NaviState, vehicle: VehicleStatus): TripCost {
        if (!navi.guiding) {
            // 안내가 "끝나는 것"을 직접 봤을 때만 기준값을 지운다. 앱을 막 켰을 때(내비 연결 전·목적지 조회 전)도
            // guiding=false라서, 그때 지우면 진행 중인 여행 금액이 0원으로 돌아간다.
            if (sawGuiding && navi.connected && prefs.contains(KEY_STARTED)) prefs.edit().clear().apply()
            sawGuiding = false
            return lastCost ?: TripCost(0.0, 0, 0, members)
        }
        sawGuiding = true
        val remainM = navi.remainDistanceMeters ?: 0
        if (!prefs.contains(KEY_STARTED)) {
            prefs.edit()
                .putBoolean(KEY_STARTED, true)
                .putFloat(KEY_START_ODO, vehicle.odometerKm ?: -1f)
                .putInt(KEY_START_REMAIN, remainM)
                .apply()
        }
        // 출발할 때 주행거리를 못 읽었다가 나중에 읽히면 그때를 기준으로 삼는다
        val startOdo = prefs.getFloat(KEY_START_ODO, -1f)
        val odo = vehicle.odometerKm
        if (startOdo < 0 && odo != null) prefs.edit().putFloat(KEY_START_ODO, odo).apply()

        val odoKm = if (odo != null && startOdo >= 0) (odo - startOdo).toDouble() else 0.0
        val gpsKm = prefs.getFloat(KEY_GPS_M, 0f) / 1000.0
        // 경로 위에서 줄어든 남은 거리 (GPS 위치 기준). 경유지를 넣어 경로가 길어지면 작아질 수 있다.
        val progressKm = (prefs.getInt(KEY_START_REMAIN, remainM) - remainM) / 1000.0
        // 세 값 중 가장 큰 값을 쓴다: 주행거리계가 멈춰 있거나(에뮬레이터), GPS가 띄엄띄엄 와서
        // 이동 거리가 덜 쌓여도 실제로 온 거리보다 적게 계산되지 않게.
        val traveledKm = maxOf(
            odoKm.takeIf { it > MIN_ODO_KM } ?: 0.0,
            gpsKm,
            progressKm,
        ).coerceAtLeast(0.0)

        val energyWon = if (vehicle.isEv) {
            val kmPerKwh = vehicle.evEfficiencyKmPerKwh?.takeIf { it > 0 } ?: DEFAULT_EV_KM_PER_KWH
            traveledKm / kmPerKwh * EV_WON_PER_KWH
        } else {
            val lPer100 = vehicle.fuelEconomyLPer100Km?.takeIf { it > 0 } ?: DEFAULT_L_PER_100KM
            traveledKm * lPer100 / 100.0 * fuelWonPerLiter
        }

        val toll = navi.tollFareWon ?: 0
        val progress = if (traveledKm + remainM / 1000.0 > 0) traveledKm / (traveledKm + remainM / 1000.0) else 0.0
        val tollWon = toll * progress

        return TripCost(traveledKm, energyWon.roundToInt(), tollWon.roundToInt(), members).also { lastCost = it }
    }

    private var lastCost: TripCost? = null
    /** 이번 실행에서 안내 중인 상태를 한 번이라도 봤는지 */
    private var sawGuiding = false

    /** 안내 중 GPS로 움직인 거리(m)를 더한다. 안내 시작 전에는 무시한다. */
    fun addGpsDistance(meters: Double) {
        if (!prefs.contains(KEY_STARTED)) return
        prefs.edit().putFloat(KEY_GPS_M, prefs.getFloat(KEY_GPS_M, 0f) + meters.toFloat()).apply()
    }

    private companion object {
        const val KEY_STARTED = "started"
        const val KEY_START_ODO = "start_odo_km"
        const val KEY_START_REMAIN = "start_remain_m"
        const val KEY_GPS_M = "gps_traveled_m"
        /** 주행거리계가 이만큼은 늘어야 실제로 달린 것으로 본다 */
        const val MIN_ODO_KM = 0.05

        // 차가 연비·전비를 주지 않을 때 쓰는 기본값 (중형 세단·전기차 평균 수준)
        const val DEFAULT_L_PER_100KM = 8.0f
        const val DEFAULT_EV_KM_PER_KWH = 5.0f
        // 오피넷 값을 받기 전에 쓰는 휘발유 가격. 충전 단가는 환경부 급속충전 기준.
        const val DEFAULT_FUEL_WON_PER_L = 1_700.0
        const val EV_WON_PER_KWH = 347.2
    }
}
