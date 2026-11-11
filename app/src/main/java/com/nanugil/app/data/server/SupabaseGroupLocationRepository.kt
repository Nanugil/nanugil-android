package com.nanugil.app.data.server

import android.util.Log
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.group.GroupLocationRepository
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * 같은 운행 차량들의 위치를 Realtime private 채널로 주고받는다 (R1~R3, ADR-001).
 * 위치는 DB에 저장하지 않는다. 채널은 진행 중 운행의 운전자만 쓸 수 있다 (realtime.messages RLS).
 * 화면의 FakeGroupLocationRepository 자리에 바꿔 끼우면 된다.
 */
class SupabaseGroupLocationRepository : GroupLocationRepository {

    private val sendLock = Mutex()
    private var sendChannel: RealtimeChannel? = null
    private var lastSent: CarStateMessage? = null
    private var lastSentAt = 0L // 마지막으로 보낸 시각 (이 기기 시계)

    /**
     * 진행 중 운행의 다른 차들 상태. 내 메시지는 돌아오지 않는다. 운행이 없으면 아무것도 내보내지 않고 끝난다.
     * 1분 넘게 소식이 없는 차(앱을 끈 차)는 목록에서 뺀다. 모르는 차가 오면 멤버 목록을 다시 읽는다.
     * 구독하는 동안 내 차가 정차 중이면 마지막 상태를 30초마다 저절로 다시 보낸다 (화면이 [sendCarState]를 안 불러도).
     */
    override fun observeGroup(groupId: String): Flow<List<CarState>> = flow {
        val trip = GroupTripApi.activeTrip(groupId) ?: return@flow
        var names = GroupTripApi.members(groupId).associate { it.id to it.displayName }
        val reloadedFor = mutableSetOf<String>() // 같은 carId로 멤버 목록을 계속 다시 읽지 않게
        val channel = Supabase.client.channel(trip.channel) { isPrivate = true }
        val messages = channel.broadcastFlow<CarStateMessage>(CAR_STATE_EVENT)
        channel.subscribe(blockUntilSubscribed = true)
        sendChannel = channel

        val board = CarBoard()
        val ticks = flow { while (true) { delay(STALE_CHECK_MS); emit(null) } }
        try {
            merge(messages, ticks).collect { msg ->
                val now = System.currentTimeMillis()
                if (msg == null) resendIfStopped(now)
                var changed = board.dropStale(now)
                if (msg != null && board.put(msg, now)) {
                    changed = true
                    if (msg.carId !in names && reloadedFor.add(msg.carId)) {
                        // 이름 다시 읽기가 실패해도 위치는 계속 받는다. 이름은 빈칸으로 두고 넘어간다.
                        ignoringFailure { names = GroupTripApi.members(groupId).associate { it.id to it.displayName } }
                    }
                }
                if (changed) emit(board.messages.map { it.toCarState(names[it.carId] ?: "") })
            }
        } finally {
            sendLock.withLock { sendChannel = null; lastSent = null }
            withContext(NonCancellable) { Supabase.client.realtime.removeChannel(channel) }
        }
    }

    /**
     * R2. 내 차 상태 보내기. [observeGroup]을 구독하는 동안만 보낼 수 있다.
     * 위치가 바뀌지 않아도 3초 이내 간격으로 계속 부른다 (지도 화면은 1초 내비 갱신 루프에서 매번 호출).
     * 보낼 때가 아니면 보내지 않고 false: 실제 전송은 주행 중 3초, 정차 중 30초 ([shouldSendCarState]).
     */
    suspend fun sendCarState(message: CarStateMessage): Boolean = sendLock.withLock {
        val channel = sendChannel ?: return@withLock false
        if (!shouldSendCarState(lastSent, message)) return@withLock false
        channel.broadcast(CAR_STATE_EVENT, message)
        lastSent = message
        lastSentAt = System.currentTimeMillis()
        true
    }

    /** 정차 중인데 30초 넘게 보낸 게 없으면 마지막 상태를 시각만 바꿔 다시 보낸다. 실패하면 다음 확인 때 다시. */
    private suspend fun resendIfStopped(now: Long) = sendLock.withLock {
        val channel = sendChannel ?: return@withLock
        val last = lastSent ?: return@withLock
        if (!stoppedResendDue(last, lastSentAt, now)) return@withLock
        val again = last.copy(sentAt = now)
        ignoringFailure {
            channel.broadcast(CAR_STATE_EVENT, again)
            lastSent = again
            lastSentAt = now
        }
    }

    /** 네트워크 실패는 무시하고 넘어간다. 취소는 그대로 전한다. */
    private inline fun ignoringFailure(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "무시하고 계속함", e)
        }
    }

    companion object {
        const val CAR_STATE_EVENT = "car_state"

        /** 주행 중 보내는 간격 (ADR-001) */
        const val DRIVING_INTERVAL_MS = 3_000L

        /** 정차 중에도 이 간격으로 한 번씩 보낸다. 나중에 들어온 차도 서 있는 차를 볼 수 있게. */
        const val STOPPED_INTERVAL_MS = 30_000L

        /** 이 시간 넘게 소식이 없으면 끊긴 차로 보고 뺀다. 정차 간격(30초)보다 길어야 한다. */
        const val STALE_AFTER_MS = 60_000L

        /** 끊긴 차 확인과 정차 중 재전송을 이 간격으로 한다. 재전송은 30~40초마다라 1분 기준에 안 걸린다. */
        private const val STALE_CHECK_MS = 10_000L
        private const val TAG = "GroupLocation"
    }
}

/** 정차·휴게소 중이고 마지막 전송에서 30초가 지났으면 다시 보낼 때다. [lastSentAt]·[now]는 이 기기 시계. */
fun stoppedResendDue(last: CarStateMessage?, lastSentAt: Long, now: Long): Boolean =
    last != null && last.driveStatus != DriveStatus.DRIVING &&
        now - lastSentAt >= SupabaseGroupLocationRepository.STOPPED_INTERVAL_MS

/**
 * R2를 지금 보낼지. 주행 중 3초마다, 상태가 바뀌면(정차로 바뀌는 순간 등) 바로,
 * 정차·휴게소 중에는 30초마다. 전송 횟수를 무료 한도 안에 두려는 규칙이다 (ADR-001).
 */
fun shouldSendCarState(last: CarStateMessage?, next: CarStateMessage): Boolean {
    if (last == null || last.driveStatus != next.driveStatus) return true
    val interval = if (next.driveStatus == DriveStatus.DRIVING) {
        SupabaseGroupLocationRepository.DRIVING_INTERVAL_MS
    } else {
        SupabaseGroupLocationRepository.STOPPED_INTERVAL_MS
    }
    return next.sentAt - last.sentAt >= interval
}

/** 받은 차량 상태 모음. 순서가 뒤바뀐 옛 메시지는 버리고, 오래 소식 없는 차는 뺀다. 시각은 받은 쪽 시계 기준. */
internal class CarBoard(private val staleAfterMs: Long = SupabaseGroupLocationRepository.STALE_AFTER_MS) {
    private class Entry(val message: CarStateMessage, val receivedAt: Long)

    private val latest = linkedMapOf<String, Entry>()

    val messages: List<CarStateMessage> get() = latest.values.map { it.message }

    /** 새 메시지를 넣는다. 같은 차의 더 최근 메시지가 이미 있으면 버리고 false. */
    fun put(message: CarStateMessage, receivedAt: Long): Boolean {
        if ((latest[message.carId]?.message?.sentAt ?: Long.MIN_VALUE) > message.sentAt) return false
        latest[message.carId] = Entry(message, receivedAt)
        return true
    }

    /** [staleAfterMs] 넘게 소식 없는 차를 뺀다. 뺀 차가 있으면 true. */
    fun dropStale(now: Long): Boolean = latest.values.removeAll { now - it.receivedAt > staleAfterMs }
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
