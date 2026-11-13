package com.nanugil.app.data.group

import com.nanugil.app.core.geo.Wgs84Point
import kotlinx.coroutines.flow.Flow

/** 그룹 드라이브 중인 차량 한 대의 상태. */
data class CarState(
    val carId: String,
    val driverName: String,
    val position: Wgs84Point,
    val speedKmh: Int,
    val etaMinutes: Int,
    /** 연료(또는 배터리) 잔량 0~100 */
    val fuelPercent: Int,
    val isMe: Boolean = false,
    /** 화면에 쓰는 차 이름 글자 ("B" → "B차"). 목업은 carId 끝 글자("car-b" → "B"), 서버는 운전자 순서로 붙인다 */
    val label: String = carId.substringAfterLast('-').uppercase(),
) {
    val isLowFuel: Boolean get() = fuelPercent <= LOW_FUEL_PERCENT

    companion object {
        /** 이 값 이하로 떨어지면 주변 주유소를 찾는다 */
        const val LOW_FUEL_PERCENT = 15
    }
}

/**
 * 같은 그룹 차량들의 위치를 실시간으로 받는 창구.
 *
 * 실제 구현은 서버(ADR-001)가 정해지면 붙인다. 그 전까지는
 * [com.nanugil.app.data.mock.FakeGroupLocationRepository]를 쓴다.
 * 내 차 위치·연료는 Pleos Fused Location SDK와 Vehicle SDK에서 읽어 서버로 보낸다.
 */
interface GroupLocationRepository {
    fun observeGroup(groupId: String): Flow<List<CarState>>
}
