package com.nanugil.app.data.server

import android.util.Log
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.distanceTo
import com.nanugil.app.data.group.CarState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 내 차의 지금 상태 (서버로 보낼 값). 지도 화면이 내장 내비·차량 상태에서 1초마다 만들어 준다. */
data class MyCarSnapshot(
    val position: Wgs84Point,
    /** 목적지까지 남은 시간(분). 안내 중이 아니면 0 */
    val etaMinutes: Int,
    /** 연료(전기차는 배터리) 0~100 */
    val fuelPercent: Int,
)

/**
 * 지도 화면용 일행 차 목록을 서버에서 받는다 (이슈 #18 2번). [SupabaseGroupLocationRepository]를 감싼다.
 *
 * - 그룹의 운행 시작·종료·차량 참가를 [GroupTripApi.tripChanges]로 알아채고, 진행 중 운행이 있을 때만 위치 채널에 붙는다.
 *   운행에 차가 새로 들어오면(내가 나중에 참가해도) 글자를 다시 붙이려고 채널에 다시 붙는다.
 * - 붙어 있는 동안 1초마다 내 차 상태를 보낸다. 실제 전송은 [shouldSendCarState] 규칙(주행 3초·정차 30초)대로만 나간다.
 *   정차 중 30초 재전송, 1분 넘게 소식 없는 차 빼기는 저장소가 한다.
 * - 목록 맨 앞은 나(isMe). 서버는 내 메시지를 돌려주지 않아서 여기서 붙인다.
 * - 차 이름 글자(A·B·C)는 운행의 운전자를 멤버 ID 순으로 정렬해 붙인다. 모든 기기에서 같은 글자가 나온다.
 * - 내가 그 운행의 운전자일 때만 보낸다 (서버도 운전자만 허락한다). 동승자 기기는 받기만 한다.
 * - 서버 오류가 나면 [RETRY_MS] 뒤 처음부터 다시 붙는다. 그동안 마지막 목록이 화면에 남는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerGroupCars(
    private val repo: SupabaseGroupLocationRepository = SupabaseGroupLocationRepository(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _activeTripId = MutableStateFlow<String?>(null)
    /** [cars]를 받는 동안 그 그룹의 진행 중 운행 ID. 값이 있다가 null이 되면 운행이 끝난 것(ended_at). */
    val activeTripId: StateFlow<String?> = _activeTripId

    private val _endedCars = MutableStateFlow(EndedCars(0, 0))
    /** 진행 중 운행에서 [운행 종료]를 누른(arrived_at) 차 수 / 전체 차 수 */
    val endedCars: StateFlow<EndedCars> = _endedCars

    /** 다른 차의 휴게소 합류 제안·답 */
    val meetups: Flow<MeetupMessage> get() = repo.meetups

    /** 이 운행에서 내 차(운전자 멤버 ID). 동승자 기기면 null (합류 제안을 보내지 않는다) */
    @Volatile private var myCarId: String? = null

    /** 휴게소 합류 제안·답을 보낸다. 운행 중 운전자가 아니면 false */
    suspend fun sendMeetup(action: MeetupAction, placeName: String, place: Wgs84Point): Boolean {
        val me = myCarId ?: return false
        return repo.sendMeetup(MeetupMessage(me, action, placeName, place.latitude, place.longitude, clock()))
    }

    fun cars(groupId: String, mine: () -> MyCarSnapshot?): Flow<List<CarState>> =
        GroupTripApi.tripChanges(groupId)
            .map {
                GroupTripApi.activeTrip(groupId)?.let { t ->
                    val members = GroupTripApi.tripMembers(t.id)
                    _endedCars.value = endedCars(members)
                    ActiveTrip(t.id, carLabels(members))
                } ?: null.also { _endedCars.value = EndedCars(0, 0) }
            }
            .distinctUntilChanged()
            .onEach { _activeTripId.value = it?.id }
            .flatMapLatest { trip -> if (trip == null) flowOf(emptyList()) else tripCars(groupId, trip, mine) }
            .retryWhen { cause, _ ->
                if (!currentCoroutineContext().isActive) return@retryWhen false
                Log.w(TAG, "서버 연결 실패, ${RETRY_MS / 1000}초 뒤 다시", cause)
                delay(RETRY_MS)
                true
            }
            // 재시도 뒤에 둔다: 서버 오류로 다시 붙는 동안은 운행이 끝난 것으로 보지 않는다
            .onCompletion { _activeTripId.value = null; _endedCars.value = EndedCars(0, 0); myCarId = null }

    /** 진행 중 운행과 그 운행의 차(운전자 멤버 ID → 글자). 이 값이 바뀔 때만 위치 채널에 다시 붙는다 */
    private data class ActiveTrip(val id: String, val labels: Map<String, String>)

    private fun tripCars(groupId: String, trip: ActiveTrip, mine: () -> MyCarSnapshot?): Flow<List<CarState>> = channelFlow {
        val myUserId = Supabase.ensureSignedIn()
        val me = GroupTripApi.members(groupId).firstOrNull { it.userId == myUserId }?.takeIf { it.id in trip.labels }
        val labels = trip.labels
        myCarId = me?.id

        val myCar = MutableStateFlow<CarState?>(null)
        val others = MutableStateFlow<List<CarState>>(emptyList())

        // 받기: 다른 차들
        launch {
            repo.observeGroup(groupId).collect { cars ->
                others.value = cars.map { it.copy(label = labels[it.carId] ?: "?") }
            }
        }

        // 보내기: 1초마다 내 차 상태 (운전자일 때만)
        if (me != null) launch {
            val motion = MyCarMotion()
            while (true) {
                val snap = mine()
                if (snap != null) {
                    val now = clock()
                    val (speed, status) = motion.update(snap.position, now)
                    val message = CarStateMessage(me.id, snap.position.latitude, snap.position.longitude,
                        speed, snap.etaMinutes, snap.fuelPercent, status, now)
                    myCar.value = message.toCarState(me.displayName).copy(isMe = true, label = labels.getValue(me.id))
                    ignoringFailure { repo.sendCarState(message) }
                }
                delay(SEND_TICK_MS)
            }
        }

        combine(myCar, others) { mine, rest -> listOfNotNull(mine) + rest }.collect { send(it) }
    }

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
        /** 내 차 상태를 확인하는 간격. 지도 화면의 내비 갱신(1초)과 같다 */
        const val SEND_TICK_MS = 1_000L
        const val RETRY_MS = 5_000L
        private const val TAG = "ServerGroupCars"
    }
}

/** 운행 종료를 누른 차 수 / 전체 차 수. 차가 한 대 이상이고 모두 눌렀으면 [allEnded]. */
data class EndedCars(val ended: Int, val total: Int) {
    val allEnded: Boolean get() = total > 0 && ended == total
}

/** 운전자 행(차)마다 arrived_at이 있으면 운행을 끝낸 차 */
fun endedCars(tripMembers: List<TripMember>): EndedCars {
    val cars = tripMembers.filter { it.isDriver }.distinctBy { it.memberId }
    return EndedCars(cars.count { it.arrivedAt != null }, cars.size)
}

/**
 * 운행의 운전자(차)마다 이름 글자. 서버 vehicle_label("A차")에서 "차"를 뗀 값이라 모든 기기에서 같다.
 * 이름이 없으면 멤버 ID 순으로 A·B·C…. 동승자 행은 뺀다.
 */
fun carLabels(tripMembers: List<TripMember>): Map<String, String> =
    tripMembers.filter { it.isDriver }.distinctBy { it.memberId }.sortedBy { it.memberId }
        .mapIndexed { i, m -> m.memberId to (m.vehicleLabel?.trim()?.removeSuffix("차")?.trim()?.ifBlank { null } ?: ('A' + i).toString()) }
        .toMap()

/**
 * 내 차 속도와 주행 상태를 위치 변화로 판정한다 (내장 내비는 속도를 주지 않는다).
 * - 속도: 위치가 바뀌었을 때 직전 위치와의 거리 ÷ 시간. [STILL_AFTER_MS] 넘게 그대로면 0.
 * - 상태(R2 규칙): 5 km/h 이상이면 DRIVING, 3 km/h 미만이 30초 이어지면 STOPPED, 그 사이는 이전 상태 유지.
 *   처음엔 서 있는 것으로 본다.
 */
class MyCarMotion {
    private var lastPos: Wgs84Point? = null
    private var lastMovedAt = 0L
    private var speedKmh = 0
    private var status = DriveStatus.STOPPED
    private var slowSince: Long? = null

    fun update(position: Wgs84Point, now: Long): Pair<Int, DriveStatus> {
        val prev = lastPos
        when {
            prev == null -> { lastPos = position; lastMovedAt = now }
            prev != position -> {
                val seconds = (now - lastMovedAt) / 1000.0
                speedKmh = if (seconds > 0) (prev.distanceTo(position) / seconds * 3.6).roundToInt() else speedKmh
                lastPos = position
                lastMovedAt = now
            }
            now - lastMovedAt > STILL_AFTER_MS -> speedKmh = 0
        }
        when {
            speedKmh >= DRIVING_KMH -> { status = DriveStatus.DRIVING; slowSince = null }
            speedKmh < STOPPED_KMH -> {
                val since = slowSince ?: now.also { slowSince = it }
                if (now - since >= STOPPED_AFTER_MS) status = DriveStatus.STOPPED
            }
            else -> slowSince = null
        }
        return speedKmh to status
    }

    companion object {
        const val DRIVING_KMH = 5
        const val STOPPED_KMH = 3
        const val STOPPED_AFTER_MS = 30_000L
        /** 내비 위치는 1초마다 바뀐다. 이만큼 그대로면 선 것으로 본다 */
        const val STILL_AFTER_MS = 3_000L
    }
}
