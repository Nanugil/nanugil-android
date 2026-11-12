package com.nanugil.app.data.mock

import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.group.GroupLocationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 서버 없이 화면을 개발·시연하기 위한 목업 데이터.
 * README 사용 시나리오대로 서울시청 → 가평 펜션을 차량 2대가 달린다.
 * 서버가 붙으면 이 파일만 빼면 된다.
 */
object MockGroupDriveData {
    val START = Wgs84Point(37.5665, 126.9780) // 서울시청
    val DESTINATION = Wgs84Point(37.8315, 127.5105) // 가평
    const val TOTAL_MINUTES = 80

    /** 한 번에 진행하는 비율 (2초마다 1%) */
    const val PROGRESS_PER_TICK = 0.01
    const val TICK_MILLIS = 2_000L

    val CAR_A = MockCar(id = "car-a", driverName = "김민수", speedKmh = 92, startFuelPercent = 62, fuelDropPerTick = 0, isMe = true)

    /** B 차량은 연료가 매 틱 1%씩 줄어 15번째 틱쯤 연료 부족이 된다. 출발도 3틱 늦다. */
    val CAR_B = MockCar(id = "car-b", driverName = "이서준", speedKmh = 88, startFuelPercent = 30, fuelDropPerTick = 1, startDelayTicks = 3)

    val CARS = listOf(CAR_A, CAR_B)

    /** 연료 부족 차량이 생기면 제안하는 합류 지점 (서울양양고속도로 가평휴게소 부근, 좌표는 대략값) */
    val MEETUP_NAME = "가평휴게소"
    val MEETUP_POSITION = Wgs84Point(37.6766, 127.4813)

    /** TODO(서버): 그룹 인원은 서버에서 받는다. 지금은 2대 · 8명 시연값 */
    const val SHARE_MEMBERS = 8
}

data class MockCar(
    val id: String,
    val driverName: String,
    val speedKmh: Int,
    val startFuelPercent: Int,
    val fuelDropPerTick: Int,
    val startDelayTicks: Int = 0,
    val isMe: Boolean = false,
)

class FakeGroupLocationRepository(
    private val data: MockGroupDriveData = MockGroupDriveData,
) : GroupLocationRepository {

    override fun observeGroup(groupId: String): Flow<List<CarState>> = flow {
        var tick = 0
        while (true) {
            emit(data.CARS.map { it.stateAt(tick) })
            tick++
            delay(data.TICK_MILLIS)
        }
    }

    private fun MockCar.stateAt(tick: Int): CarState {
        val progress = ((tick - startDelayTicks) * data.PROGRESS_PER_TICK).coerceIn(0.0, 1.0)
        return CarState(
            carId = id,
            driverName = driverName,
            position = lerp(data.START, data.DESTINATION, progress),
            speedKmh = speedKmh,
            etaMinutes = ((1 - progress) * data.TOTAL_MINUTES).toInt(),
            fuelPercent = (startFuelPercent - fuelDropPerTick * tick).coerceAtLeast(5),
            isMe = isMe,
        )
    }

    private fun lerp(a: Wgs84Point, b: Wgs84Point, t: Double) = Wgs84Point(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t,
    )
}
