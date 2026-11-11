package com.nanugil.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToLong
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 연료 잔량(%)과 탱크 용량(mL)으로 연료량(mL)을 구한다. 소수 mL는 반올림한다.
 * 잔량이 1% 단위라 60 L 탱크면 최대 약 0.6 L 오차가 있다 (vehicle-api-research.md 대체 순서 2).
 */
fun fuelMlFromPercent(percent: Int, tankCapacityMl: Long): Long {
    require(percent in 0..100) { "연료 잔량은 0~100%여야 합니다: $percent" }
    require(tankCapacityMl > 0) { "탱크 용량은 0보다 커야 합니다: $tankCapacityMl" }
    return (tankCapacityMl * percent + 50) / 100
}

/**
 * 내 차의 현재 연료량을 읽는다.
 * - 잔량(%): Pleos Vehicle SDK `getFuelPercent` (`pleos.car.permission.CAR_ENERGY`)
 * - 탱크 용량(mL): Pleos SDK에 없어 AAOS `INFO_FUEL_CAPACITY` (`android.car.permission.CAR_INFO`)
 *
 * [vehicle]은 앱 전체가 함께 쓰는 연결이다([com.nanugil.app.NanugilApplication.vehicle]). 여기서 release하지 않는다.
 * 리터를 바로 주는 AAOS `FUEL_LEVEL`은 기술 지원팀 검토가 끝나면 이 클래스 안에서 바꾼다.
 */
class VehicleFuelReader(private val context: Context, private val vehicle: Vehicle) {

    /** @throws IllegalStateException 값을 읽지 못했을 때. 메시지는 화면에 그대로 보여 줄 문장이다. */
    suspend fun readFuelMl(): Long {
        val percent = readFuelPercent()
        val capacityMl = readTankCapacityMl()
        return try {
            fuelMlFromPercent(percent, capacityMl)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("차량 연료 값이 올바르지 않습니다. ${e.message}", e)
        }
    }

    /**
     * 전기차·하이브리드의 현재 배터리 잔량(Wh). Pleos `getEvBatteryLevel`.
     * Pleos는 AAOS `EV_BATTERY_LEVEL`로 옮기라고 하지만, 그쪽은 권한이 하나 더 필요해 시연까지는 이 API를 쓴다.
     *
     * @throws IllegalStateException 값을 읽지 못했을 때. 메시지는 화면에 그대로 보여 줄 문장이다.
     */
    @Suppress("DEPRECATION")
    suspend fun readBatteryWh(): Long = suspendCancellableCoroutine { cont ->
        vehicle.getEvBattery().getEvBatteryLevel(
            onComplete = { value ->
                if (!cont.isActive) return@getEvBatteryLevel
                if (value == null || value < 0f) {
                    cont.resumeWithException(IllegalStateException("차량이 배터리 잔량을 주지 않았습니다."))
                } else {
                    cont.resume(value.roundToLong())
                }
            },
            onFailed = { e ->
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("이 차량에서는 배터리 잔량을 읽을 수 없습니다. 배터리 값을 직접 입력하세요.", e),
                    )
                }
            },
        )
    }

    private suspend fun readFuelPercent(): Int = suspendCancellableCoroutine { cont ->
        vehicle.getFuel().getFuelPercent(
            onComplete = { value ->
                if (!cont.isActive) return@getFuelPercent
                if (value == null) {
                    cont.resumeWithException(IllegalStateException("차량이 연료 잔량을 주지 않았습니다."))
                } else {
                    cont.resume(value)
                }
            },
            onFailed = { e ->
                // 전기차(Pleos Connect 에뮬레이터 포함)는 FUEL_PERCENT를 지원하지 않아 여기로 온다.
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("이 차량에서는 연료 잔량을 읽을 수 없습니다. 전기차라면 연료 값을 직접 입력하세요.", e),
                    )
                }
            },
        )
    }

    internal fun readTankCapacityMl(): Long = withCarProperty("탱크 용량") { manager ->
        val ml = manager.getFloatProperty(VehiclePropertyIds.INFO_FUEL_CAPACITY, 0)
        check(ml > 0f) { "차량이 탱크 용량을 주지 않았습니다." }
        ml.roundToLong()
    }

    /**
     * 차가 쓰는 연료 종류 목록. AAOS `INFO_FUEL_TYPE` (`android.car.permission.CAR_INFO`).
     * Pleos SDK에는 FuelType 상수만 있고 내 차 값을 주는 API가 없다. 값은 [android.car.FuelType] 상수다.
     */
    fun readFuelTypes(): IntArray = withCarProperty("연료 종류") { manager ->
        manager.getIntArrayProperty(VehiclePropertyIds.INFO_FUEL_TYPE, 0)
    }

    /** 차량 속성 서비스에 잠깐 붙어 [block]을 실행하고 끊는다. 연결을 기다리므로 메인 스레드에서 부르지 않는다. */
    private fun <T> withCarProperty(what: String, block: (CarPropertyManager) -> T): T {
        val car = try {
            Car.createCar(context)
        } catch (e: LinkageError) {
            null // android.car 라이브러리가 없는 기기 (일반 휴대폰 등)
        } ?: throw IllegalStateException("차량 서비스에 연결하지 못해 ${what}을(를) 읽지 못했습니다.")
        try {
            return block(car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager)
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("${what}을(를) 읽지 못했습니다: ${e.message}", e)
        } finally {
            car.disconnect()
        }
    }
}
