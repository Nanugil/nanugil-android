package com.nanugil.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 정산 화면을 열 수 있는지 가르는 기어 상태. */
enum class GearState { PARK, MOVING, UNKNOWN }

/** P단만 [GearState.PARK]. N단도 차가 굴러갈 수 있어 주행 중으로 본다. */
fun VehicleGear?.toGearState(): GearState = when (this) {
    VehicleGear.GEAR_PARK -> GearState.PARK
    null, VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
    else -> GearState.MOVING
}

/**
 * 정산 화면을 열면 안 되는 이유. 열어도 되면 null.
 * 차량 앱은 주행 중 텍스트 입력이 금지라(#9 기본 규칙, App Market 심사) P단에서만 연다.
 * 기어를 모를 때도 막는다. 주행 중일 수 있어서다.
 */
fun settlementBlockReason(gear: GearState): String? = when (gear) {
    GearState.PARK -> null
    GearState.MOVING -> "주행 중에는 정산할 수 없습니다. 차를 세우고 P단에 놓아 주세요."
    GearState.UNKNOWN -> "기어 상태를 확인하는 중입니다. P단에서만 정산할 수 있습니다."
}

/**
 * 내 차의 기어 상태를 지켜본다. Pleos Vehicle SDK `Gear` (`pleos.car.permission.CAR_DRIVE`).
 * 앱 전체에 하나만 두고([com.nanugil.app.NanugilApplication.gearWatcher]) 화면들은 [gear]만 본다.
 * [vehicle]은 앱 전체가 함께 쓰는 연결이라 여기서 release하지 않는다.
 */
class VehicleGearWatcher(private val vehicle: Vehicle) {
    private val _gear = MutableStateFlow(GearState.UNKNOWN)
    val gear: StateFlow<GearState> = _gear.asStateFlow()

    private val listener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _gear.value = gear.toGearState()
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 변경 알림 실패", e)
            _gear.value = GearState.UNKNOWN
        }
    }

    fun start() {
        val gearApi = vehicle.getGear()
        gearApi.getCurrentGearSelectionState(
            { _gear.value = it.toGearState() },
            { e ->
                Log.w(TAG, "현재 기어 읽기 실패", e)
                _gear.value = GearState.UNKNOWN
            },
        )
        gearApi.registerGearSelection(listener)
    }


    private companion object {
        const val TAG = "VehicleGearWatcher"
    }
}
