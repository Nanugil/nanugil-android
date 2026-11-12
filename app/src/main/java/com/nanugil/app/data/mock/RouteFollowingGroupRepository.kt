package com.nanugil.app.data.mock

import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.cumulativeDistances
import com.nanugil.app.core.geo.distanceAlong
import com.nanugil.app.core.geo.distanceTo
import com.nanugil.app.core.geo.offset
import com.nanugil.app.core.geo.pointAlong
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.group.GroupLocationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 시연용 목업: 일행 차(B차)가 **내 실제 내비 경로 위**를 앞서 달린다.
 *
 * [FakeGroupLocationRepository]는 서울시청 → 가평 직선을 따라 움직여서, 에뮬레이터의 내 위치·경로와 상관없는 곳에 떴다.
 * 여기서는 내장 내비가 알려 준 내 위치와 경로선을 받아,
 * - 안내 중이면: B차가 내 앞 [AHEAD_M] 근처를 같은 경로로 달린다. 뒤로 가지 않고, 속도만 바꿔 거리를 맞춘다
 *   (내가 서 있으면 [MIN_KMH]로 천천히 앞서 나간다).
 * - 안내 전이면: B차가 내 근처 [PARKED_M]에 서 있다 (정차).
 * 연료는 [MockGroupDriveData.CAR_B]처럼 2초마다 1%씩 줄어 약 30초 뒤 연료 부족 → 합류 제안이 뜬다.
 *
 * 서버 그룹에 들어가면 NaviDriveViewModel이 [com.nanugil.app.data.server.ServerGroupCars]로 바꿔 쓴다.
 * 이 목업은 서버 그룹이 없을 때(서버 없이 시연·개발할 때)만 쓴다.
 */
class RouteFollowingGroupRepository(
    /** 내 차 현재 위치 (내장 내비). 모르면 null */
    private val myPosition: () -> Wgs84Point?,
    /** 지도에 그리는 경로선 (현재 위치 → 경유지 → 목적지). 없으면 빈 목록 */
    private val route: () -> List<Wgs84Point>,
    /** 길안내 중인지 */
    private val guiding: () -> Boolean,
    private val data: MockGroupDriveData = MockGroupDriveData,
) : GroupLocationRepository {

    override fun observeGroup(groupId: String): Flow<List<CarState>> = flow {
        var elapsedMs = 0L
        var lastPath: List<Wgs84Point> = emptyList()
        var cumulative = DoubleArray(0)
        /** B차가 경로를 따라 간 거리(m). 경로가 바뀌면 다시 내 앞에 놓는다 */
        var otherAlong: Double? = null
        var lastMine: Double? = null
        val lastPos = mutableMapOf<String, Wgs84Point>()
        while (true) {
            val me = myPosition() ?: data.START
            val path = route()
            if (path !== lastPath) {
                lastPath = path
                cumulative = if (path.size >= 2) cumulativeDistances(path) else DoubleArray(0)
                otherAlong = null
                lastMine = null
            }
            val onRoute = guiding() && path.size >= 2

            val other = if (onRoute) {
                // 내 위치를 경로에 투영하고, B차는 내 앞 AHEAD_M ± DRIFT_M(90초 주기)을 목표로 속도를 맞춰 달린다
                val mine = distanceAlong(path, cumulative, me)
                val myMps = lastMine?.let { (mine - it).coerceAtLeast(0.0) * 1000 / TICK_MS } ?: 0.0
                lastMine = mine
                val target = mine + AHEAD_M + DRIFT_M * sin(2 * PI * elapsedMs / DRIFT_PERIOD_MS)
                val now = otherAlong ?: target
                val mps = (myMps + (target - now) * CATCH_UP_PER_S).coerceIn(MIN_KMH / 3.6, MAX_KMH / 3.6)
                val next = (now + mps * TICK_MS / 1000).coerceAtMost(cumulative.last())
                otherAlong = next
                pointAlong(path, cumulative, next).first
            } else {
                otherAlong = null
                lastMine = null
                me.offset(PARKED_BEARING, PARKED_M)
            }

            val tick = (elapsedMs / data.TICK_MILLIS).toInt()
            val cars = listOf(
                car(data.CAR_A, me, tick, lastPos, path, cumulative, onRoute),
                car(data.CAR_B, other, tick, lastPos, path, cumulative, onRoute),
            )
            emit(cars)
            delay(TICK_MS)
            elapsedMs += TICK_MS
        }
    }

    private fun car(
        mock: MockCar,
        position: Wgs84Point,
        tick: Int,
        lastPos: MutableMap<String, Wgs84Point>,
        path: List<Wgs84Point>,
        cumulative: DoubleArray,
        onRoute: Boolean,
    ): CarState {
        // 1초 사이 움직인 거리로 속도를 낸다 (서 있으면 0 → 지도에 "정차")
        val moved = lastPos[mock.id]?.distanceTo(position) ?: 0.0
        lastPos[mock.id] = position
        val speed = if (moved < MIN_MOVE_M) 0 else (moved * 3.6 * 1000 / TICK_MS).roundToInt()
        val remaining = if (onRoute) (cumulative.last() - distanceAlong(path, cumulative, position)).coerceAtLeast(0.0) else null
        val eta = remaining?.let { (it / 1000 / ETA_KMH * 60).roundToInt() } ?: 0
        return CarState(
            carId = mock.id,
            driverName = mock.driverName,
            position = position,
            speedKmh = speed,
            etaMinutes = eta,
            fuelPercent = (mock.startFuelPercent - mock.fuelDropPerTick * tick).coerceAtLeast(5),
            isMe = mock.isMe,
        )
    }

    private companion object {
        const val TICK_MS = 1_000L
        /** 안내 중 B차가 내 앞에 있는 거리 */
        const val AHEAD_M = 1_200.0
        /** 앞서거니 뒤서거니 하는 폭과 주기 */
        const val DRIFT_M = 400.0
        const val DRIFT_PERIOD_MS = 90_000.0
        /** 목표와의 거리 차를 1초에 얼마나 메우는지 (2% → 부드럽게 따라잡는다) */
        const val CATCH_UP_PER_S = 0.02
        /** B차 속도 범위 (뒤로 가거나 순간이동하지 않게) */
        const val MIN_KMH = 20.0
        const val MAX_KMH = 120.0
        /** 안내 전 B차가 서 있는 곳 (내 위치에서 북동쪽 250m) */
        const val PARKED_M = 250.0
        const val PARKED_BEARING = 45.0
        /** 이보다 덜 움직이면 서 있는 것으로 본다 (1초 기준) */
        const val MIN_MOVE_M = 1.0
        /** 남은 시간 계산용 평균 속도 */
        const val ETA_KMH = 70.0
    }
}
