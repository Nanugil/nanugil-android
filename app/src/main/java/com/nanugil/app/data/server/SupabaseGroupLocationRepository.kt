package com.nanugil.app.data.server

import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.group.GroupLocationRepository
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * 같은 운행 차량들의 위치를 Realtime private 채널로 주고받는다 (R1~R3, ADR-001).
 * 위치는 DB에 저장하지 않는다. 채널은 진행 중 운행의 운전자만 쓸 수 있다 (realtime.messages RLS).
 * 화면의 FakeGroupLocationRepository 자리에 바꿔 끼우면 된다.
 */
class SupabaseGroupLocationRepository : GroupLocationRepository {

    private var sendChannel: RealtimeChannel? = null

    /** 진행 중 운행의 다른 차들 상태. 내 메시지는 돌아오지 않는다. 운행이 없으면 아무것도 내보내지 않고 끝난다. */
    override fun observeGroup(groupId: String): Flow<List<CarState>> = flow {
        val trip = GroupTripApi.activeTrip(groupId) ?: return@flow
        val names = GroupTripApi.members(groupId).associate { it.id to it.displayName }
        val channel = Supabase.client.channel(trip.channel) { isPrivate = true }
        val messages = channel.broadcastFlow<CarStateMessage>(CAR_STATE_EVENT)
        channel.subscribe(blockUntilSubscribed = true)
        sendChannel = channel

        val latest = linkedMapOf<String, CarStateMessage>()
        try {
            messages.collect { msg ->
                // 늦게 도착한 옛 메시지는 버린다 (sentAt)
                if ((latest[msg.carId]?.sentAt ?: Long.MIN_VALUE) > msg.sentAt) return@collect
                latest[msg.carId] = msg
                emit(latest.values.map { it.toCarState(names[it.carId] ?: "") })
            }
        } finally {
            sendChannel = null
            withContext(NonCancellable) { Supabase.client.realtime.removeChannel(channel) }
        }
    }

    /**
     * R2. 내 차 상태 보내기. [observeGroup]을 구독하는 동안만 보낼 수 있다.
     * 3초 간격으로 부르고, 정차 중에는 부르지 않고 정차로 바뀔 때 한 번만 부른다 (ADR-001).
     */
    suspend fun sendCarState(message: CarStateMessage) {
        sendChannel?.broadcast(CAR_STATE_EVENT, message)
    }

    companion object {
        const val CAR_STATE_EVENT = "car_state"
    }
}

/** R2 메시지 (docs/group-trip-api.md "실시간 위치 연결"). carId는 운전자 멤버 ID. */
@Serializable
data class CarStateMessage(
    val carId: String,
    val lat: Double,
    val lng: Double,
    val speedKmh: Int,
    val etaMinutes: Int,
    val fuelPercent: Int,
    val driveStatus: DriveStatus,
    /** epoch 밀리초 */
    val sentAt: Long,
) {
    fun toCarState(driverName: String) = CarState(
        carId = carId,
        driverName = driverName,
        position = Wgs84Point(lat, lng),
        speedKmh = speedKmh,
        etaMinutes = etaMinutes,
        fuelPercent = fuelPercent,
    )
}

/** 보내는 쪽이 판정한다: 5 km/h 이상 DRIVING, 3 km/h 미만 30초 이상 STOPPED, 그 사이는 이전 상태 유지. */
@Serializable
enum class DriveStatus { DRIVING, STOPPED, REST_AREA }
