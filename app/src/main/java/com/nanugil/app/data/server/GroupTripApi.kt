package com.nanugil.app.data.server

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 그룹·초대·운행 API (docs/group-trip-api.md의 G·I·T).
 * 읽기는 테이블 조회, 쓰기는 모두 RPC. 실패하면 [ServerException] (code = 오류 코드).
 * SQL은 supabase/migrations/20261111000000_group_trip.sql.
 */
object GroupTripApi {

    /** [tripChanges]가 알림 없이도 다시 읽게 하는 간격. PM 확인 결과 첫 알림이 빠질 수 있어 10~15초 권장. */
    const val TRIP_RECHECK_MS = 15_000L

    private val db get() = Supabase.client.postgrest

    private suspend inline fun <reified T : Any> rpc(name: String, noinline params: JsonObjectBuilder.() -> Unit = {}): T =
        serverCall { db.rpc(name, buildJsonObject(params)).decodeAs<T>() }

    // ── 그룹 ──

    /** G1. 그룹을 만들고 나를 방장·첫 멤버로 등록한다. 초대 코드가 함께 나온다. */
    suspend fun createGroup(name: String, destination: Place, myName: String, logoUrl: String? = null): Group =
        rpc("create_group") {
            put("p_name", name); put("p_display_name", myName); put("p_logo_url", logoUrl)
            put("p_destination_name", destination.name)
            put("p_destination_lat", destination.lat); put("p_destination_lng", destination.lng)
        }

    /** G2. 내가 속한 그룹 목록과 그룹별 진행 중 운행 (RLS가 내 그룹만 돌려준다). */
    suspend fun myGroups(): List<MyGroup> = serverCall {
        val groups = db.from("groups").select().decodeList<Group>()
        val active = db.from("trips").select { filter { exact("ended_at", null) } }.decodeList<Trip>().associateBy { it.groupId }
        groups.map { MyGroup(it, active[it.id]) }
    }

    /**
     * T5. 그룹의 운행이 시작·종료되거나 차량이 도착하면 한 번씩 알린다. 받으면 [activeTrip]·[tripMembers]를 다시 읽는다.
     * 구독이 붙는 순간에도 한 번 알린다. 그때 처음 상태를 읽으면 "읽은 뒤 구독 전" 사이의 변화를 놓치지 않는다.
     * 'group:{그룹 ID}' private 채널 (멤버만 들어올 수 있다). 수집을 멈추면 채널을 닫는다.
     * 알림이 빠지는 때가 있어서(서버가 쉬다 깨어난 직후 첫 알림) [TRIP_RECHECK_MS]마다 알림 없이도 한 번씩 알린다.
     */
    fun tripChanges(groupId: String): Flow<Unit> = flow {
        Supabase.ensureSignedIn()
        val channel = Supabase.client.channel("group:$groupId") { isPrivate = true }
        val trips = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "trips"; filter("group_id", FilterOperator.EQ, groupId)
        }
        // trip_members에는 group_id가 없어 거르지 않는다. RLS가 내 그룹 행만 보내 준다.
        val members = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "trip_members" }
        channel.subscribe(blockUntilSubscribed = true)
        try {
            emit(Unit)
            val recheck = flow { while (true) { delay(TRIP_RECHECK_MS); emit(Unit) } }
            emitAll(merge(trips.map { }, members.map { }, recheck))
        } finally {
            withContext(NonCancellable) { Supabase.client.realtime.removeChannel(channel) }
        }
    }

    /** G3. 그룹 멤버 목록. 앱 없는 동승자는 userId가 null. */
    suspend fun members(groupId: String): List<GroupMember> = serverCall {
        db.from("group_members").select { filter { eq("group_id", groupId) } }.decodeList<GroupMember>()
    }

    /** G4. 이름·로고·목적지만 바꾼다 (방장). null인 값은 그대로 둔다. */
    suspend fun updateGroup(groupId: String, name: String? = null, logoUrl: String? = null, destination: Place? = null): Group =
        rpc("update_group") {
            put("p_group_id", groupId); put("p_name", name); put("p_logo_url", logoUrl)
            put("p_destination_name", destination?.name)
            put("p_destination_lat", destination?.lat); put("p_destination_lng", destination?.lng)
        }

    /** G5. memberId가 없으면 내가 나간다. 남을 빼는 건 방장만. 방장은 나갈 수 없다(OWNER_CANNOT_LEAVE). */
    suspend fun leaveGroup(groupId: String, memberId: String? = null) {
        serverCall { db.rpc("leave_group", buildJsonObject { put("p_group_id", groupId); put("p_member_id", memberId) }) }
    }

    // ── 초대 ──

    /** I1. 초대 코드 다시 만들기 (방장). 이전 코드는 바로 무효. */
    suspend fun resetInviteCode(groupId: String): InviteCode =
        rpc<List<InviteCode>>("reset_invite_code") { put("p_group_id", groupId) }.first()

    /** I2. 코드로 참여. 실패: INVALID_CODE, TOO_MANY_ATTEMPTS, ALREADY_MEMBER. */
    suspend fun joinGroup(code: String, myName: String): InvitePreview =
        inviteResult(rpc("join_group") { put("p_code", code); put("p_display_name", myName) })

    /** I3. 참여 전에 그룹 이름·인원 확인. 실패: INVALID_CODE, TOO_MANY_ATTEMPTS. */
    suspend fun previewInvite(code: String): InvitePreview =
        inviteResult(rpc("preview_invite") { put("p_code", code) })

    // 실패 횟수를 서버에 남기려고 이 두 RPC는 오류를 예외 대신 error 열로 돌려준다.
    private fun inviteResult(rows: List<InviteRow>): InvitePreview {
        val row = rows.first()
        row.error?.let { throw ServerException(it) }
        return InvitePreview(checkNotNull(row.groupId), checkNotNull(row.groupName), checkNotNull(row.memberCount))
    }

    /** I4. 앱 없는 동승자를 이름만으로 추가. */
    suspend fun addPassenger(groupId: String, name: String): GroupMember =
        rpc("add_passenger") { put("p_group_id", groupId); put("p_display_name", name) }

    // ── 운행 ──

    /** T1. 운행 시작 (방장). 목적지를 안 주면 그룹 목적지. 위치 채널 이름은 [Trip.channel]. */
    suspend fun startTrip(groupId: String, destination: Place? = null): Trip =
        rpc("start_trip") {
            put("p_group_id", groupId)
            put("p_destination_name", destination?.name)
            put("p_destination_lat", destination?.lat); put("p_destination_lng", destination?.lng)
        }

    /** T2. 내 차를 이번 운행에 등록. 다시 부르면 차 이름·연료만 고친다. */
    suspend fun joinTrip(tripId: String, vehicleLabel: String, fuelType: FuelType): TripMember =
        rpc("join_trip") { put("p_trip_id", tripId); put("p_vehicle_label", vehicleLabel); put("p_fuel_type", fuelType.name) }

    /** T3. 그룹의 진행 중 운행. 없으면 null. */
    suspend fun activeTrip(groupId: String): Trip? = serverCall {
        db.from("trips").select { filter { eq("group_id", groupId); exact("ended_at", null) } }.decodeList<Trip>().firstOrNull()
    }

    /** 내 그룹들의 끝난 운행, 최근 끝난 순으로 [limit]개 (홈 "최근 정산"). RLS가 내 그룹 운행만 돌려준다. */
    suspend fun endedTrips(limit: Long): List<Trip> = serverCall {
        db.from("trips").select {
            filter { filterNot("ended_at", FilterOperator.IS, "null") }
            order("ended_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<Trip>()
    }

    /** T3. 운행에 탄 사람 전체 (운전자 행 + 동승자 행). 1/N의 N = 개수. */
    suspend fun tripMembers(tripId: String): List<TripMember> = serverCall {
        db.from("trip_members").select { filter { eq("trip_id", tripId) } }.decodeList<TripMember>()
    }

    /** T4. 운행 종료 (방장). */
    suspend fun endTrip(tripId: String) {
        serverCall { db.rpc("end_trip", buildJsonObject { put("p_trip_id", tripId) }) }
    }

    /** T6. 내 차 도착. */
    suspend fun markArrived(tripId: String) {
        serverCall { db.rpc("mark_arrived", buildJsonObject { put("p_trip_id", tripId) }) }
    }

    /** T7. 이번 운행에서 내 차에 탄 동승자 목록을 통째로 바꾼다. */
    suspend fun setTripPassengers(tripId: String, memberIds: List<String>): List<TripMember> =
        rpc("set_trip_passengers") { put("p_trip_id", tripId); putJsonArray("p_member_ids") { memberIds.forEach { add(it) } } }

    /** T8. 도착 후 내 차 비용 저장 (다시 부르면 덮어쓴다). */
    suspend fun saveTripCost(tripId: String, cost: TripCostInput): TripMember =
        rpc("save_trip_cost") {
            put("p_trip_id", tripId)
            put("p_start_fuel_ml", cost.startFuelMl); put("p_refueled_ml", cost.refueledMl); put("p_end_fuel_ml", cost.endFuelMl)
            put("p_fuel_calc_method", cost.fuelCalcMethod); put("p_price_basis", cost.priceBasis)
            put("p_nearest_price", cost.nearestPrice); put("p_cheapest_price", cost.cheapestPrice); put("p_applied_price", cost.appliedPrice)
            put("p_start_battery_wh", cost.startBatteryWh); put("p_charged_wh", cost.chargedWh); put("p_end_battery_wh", cost.endBatteryWh)
            put("p_applied_price_per_kwh", cost.appliedPricePerKwh)
            put("p_toll_estimated", cost.tollEstimated); put("p_toll_adjusted", cost.tollAdjusted)
        }
}

data class Place(val name: String, val lat: Double, val lng: Double)

enum class FuelType { GASOLINE, PREMIUM_GASOLINE, DIESEL, LPG, EV, HYBRID, HYDROGEN }

@Serializable
data class Group(
    val id: String,
    val name: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("destination_name") val destinationName: String,
    @SerialName("destination_lat") val destinationLat: Double,
    @SerialName("destination_lng") val destinationLng: Double,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("invite_code_expires_at") val inviteCodeExpiresAt: String,
)

/** G2 결과: 그룹과 진행 중 운행(없으면 null). */
data class MyGroup(val group: Group, val activeTrip: Trip?)

@Serializable
data class GroupMember(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class InviteCode(
    @SerialName("invite_code") val code: String,
    @SerialName("invite_code_expires_at") val expiresAt: String,
)

data class InvitePreview(val groupId: String, val groupName: String, val memberCount: Int)

@Serializable
internal data class InviteRow(
    val error: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("member_count") val memberCount: Int? = null,
)

@Serializable
data class Trip(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("destination_name") val destinationName: String,
    @SerialName("destination_lat") val destinationLat: Double,
    @SerialName("destination_lng") val destinationLng: Double,
) {
    /** 위치를 주고받는 Realtime private 채널 이름 (R1). */
    val channel: String get() = "trip:$id"
}

/** 운행에 탄 사람 한 명. 운전자 행(memberId == vehicleDriverMemberId)에만 차량·비용 값이 있다. */
@Serializable
data class TripMember(
    @SerialName("trip_id") val tripId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("vehicle_driver_member_id") val vehicleDriverMemberId: String,
    @SerialName("vehicle_label") val vehicleLabel: String? = null,
    @SerialName("fuel_type") val fuelType: String? = null,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("start_fuel_ml") val startFuelMl: Long? = null,
    @SerialName("refueled_ml") val refueledMl: Long? = null,
    @SerialName("end_fuel_ml") val endFuelMl: Long? = null,
    @SerialName("fuel_calc_method") val fuelCalcMethod: String? = null,
    @SerialName("price_basis") val priceBasis: String? = null,
    @SerialName("nearest_price") val nearestPrice: Int? = null,
    @SerialName("cheapest_price") val cheapestPrice: Int? = null,
    @SerialName("applied_price") val appliedPrice: Int? = null,
    @SerialName("start_battery_wh") val startBatteryWh: Long? = null,
    @SerialName("charged_wh") val chargedWh: Long? = null,
    @SerialName("end_battery_wh") val endBatteryWh: Long? = null,
    @SerialName("applied_price_per_kwh") val appliedPricePerKwh: Double? = null,
    @SerialName("toll_estimated") val tollEstimated: Int? = null,
    @SerialName("toll_adjusted") val tollAdjusted: Int? = null,
) {
    val isDriver: Boolean get() = memberId == vehicleDriverMemberId
}

/** T8 입력. 연료차는 배터리 값을, 전기차는 연료 값을 0으로 둔다. 하이브리드는 둘 다. */
data class TripCostInput(
    val startFuelMl: Long = 0, val refueledMl: Long = 0, val endFuelMl: Long = 0,
    val fuelCalcMethod: String? = null, val priceBasis: String? = null,
    val nearestPrice: Int? = null, val cheapestPrice: Int? = null, val appliedPrice: Int = 0,
    val startBatteryWh: Long = 0, val chargedWh: Long = 0, val endBatteryWh: Long = 0,
    val appliedPricePerKwh: Double = 0.0,
    val tollEstimated: Int = 0, val tollAdjusted: Int? = null,
)
