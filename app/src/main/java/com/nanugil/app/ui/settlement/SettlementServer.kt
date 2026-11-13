package com.nanugil.app.ui.settlement

import com.nanugil.app.data.server.GroupTripApi
import com.nanugil.app.data.server.ServerException
import com.nanugil.app.data.server.Supabase
import com.nanugil.app.data.server.TripCostInput
import com.nanugil.app.data.server.TripMember
import kotlin.math.roundToLong

/**
 * 서버의 진행 중 운행. [cars]는 내 차(A차)가 맨 앞.
 * @property iDrive 이 기기 사용자가 운전자인지. 동승자면 [cars]의 맨 앞은 남의 차라 이 기기 기록으로 채우거나 저장하지 않는다.
 */
data class ServerTrip(val tripId: String, val cars: List<CarInput>, val iDrive: Boolean = true)

/** 정산이 서버(#15 Supabase)에서 쓰는 것: 운행 참가자·차량별 비용 읽기(T3), 내 차 비용 저장(T6·T8). */
interface SettlementServer {
    /** 내가 속한 그룹의 진행 중 운행. 없으면 null(데모 값으로 정산한다). */
    suspend fun loadTrip(): ServerTrip?

    /** 도착 처리 후 내 차 비용을 저장한다. 다시 부르면 덮어쓴다. */
    suspend fun saveMyCost(tripId: String, car: CarInput, choice: PriceChoice)
}

/** [SettlementServer]의 Supabase 구현. 그룹·운행 만들기·참가는 그룹 화면 담당이고, 여기서는 읽기와 내 차 비용 저장만 한다. */
class SupabaseSettlementServer : SettlementServer {

    override suspend fun loadTrip(): ServerTrip? {
        val me = Supabase.ensureSignedIn()
        // shortcut: 진행 중 운행이 여러 그룹에 있으면 첫 번째를 쓴다. 그룹 화면이 서버에 붙으면 고른 그룹의 운행을 넘겨받는다.
        val trip = GroupTripApi.myGroups().firstNotNullOfOrNull { it.activeTrip } ?: return null
        val members = GroupTripApi.members(trip.groupId)
        val names = members.associate { it.id to it.displayName }
        val myMemberId = members.firstOrNull { it.userId == me }?.id
        val tripMembers = GroupTripApi.tripMembers(trip.id)
        val iDrive = tripMembers.any { it.isDriver && it.memberId == myMemberId }
        return ServerTrip(trip.id, carsFromTrip(tripMembers, names, myMemberId), iDrive)
    }

    override suspend fun saveMyCost(tripId: String, car: CarInput, choice: PriceChoice) {
        try {
            GroupTripApi.markArrived(tripId)
        } catch (e: ServerException) {
            // 누가 운행을 먼저 끝냈으면(T4) 도착 처리는 안 되지만, 끝난 운행에도 비용은 저장된다(T8)
            if (e.code != "TRIP_NOT_ACTIVE") throw e
        }
        GroupTripApi.saveTripCost(tripId, car.toCostInput(choice))
    }
}

/**
 * 운행 참가자(trip_members)를 정산 입력으로 바꾼다. 운전자 행 하나가 차 한 대, 그 차 동승자는 vehicle_driver_member_id로 묶는다.
 * 다른 운전자가 아직 비용을 저장(T8)하지 않았으면 그 칸은 비워 두고, 08 화면에 "값을 입력하세요"가 뜬다.
 */
internal fun carsFromTrip(members: List<TripMember>, names: Map<String, String>, myMemberId: String?): List<CarInput> {
    val drivers = members.filter { it.isDriver }.sortedByDescending { it.memberId == myMemberId }
    return drivers.map { d ->
        val passengers = members.filter { !it.isDriver && it.vehicleDriverMemberId == d.memberId }
            .mapNotNull { names[it.memberId] }
        val type = vehicleTypeOf(d.fuelType)
        CarInput(
            type = type,
            driver = names[d.memberId].orEmpty(),
            memberId = d.memberId,
            passengers = passengers.joinToString(", "),
            fuelPrice = d.appliedPrice?.takeIf { it > 0 }?.toString() ?: type.defaultFuelPrice,
            startFuelL = d.startFuelMl?.let(::milliToDecimalText).orEmpty(),
            refueledL = milliToDecimalText(d.refueledMl ?: 0),
            endFuelL = d.endFuelMl?.let(::milliToDecimalText).orEmpty(),
            electricityPrice = d.appliedPricePerKwh?.takeIf { it > 0 }?.let { milliToDecimalText((it * 1000).roundToLong()) } ?: ChargerTier.KW100.price,
            startBatteryKwh = d.startBatteryWh?.let(::milliToDecimalText).orEmpty(),
            chargedKwh = milliToDecimalText(d.chargedWh ?: 0),
            endBatteryKwh = d.endBatteryWh?.let(::milliToDecimalText).orEmpty(),
            tollWon = (d.tollAdjusted ?: d.tollEstimated ?: 0).toString(),
        )
    }
}

/** 서버 fuel_type → 정산 차종. LPG·수소는 정산 미지원이라 휘발유로 두고 값 수정에서 고친다. */
internal fun vehicleTypeOf(fuelType: String?): VehicleType = when (fuelType) {
    "EV" -> VehicleType.ELECTRIC
    "HYBRID" -> VehicleType.HYBRID
    "DIESEL" -> VehicleType.DIESEL
    "PREMIUM_GASOLINE" -> VehicleType.PREMIUM_GASOLINE
    else -> VehicleType.GASOLINE
}

/**
 * 내 차 입력값 → T8 저장값. 차종이 안 쓰는 값은 0.
 * @throws IllegalArgumentException 값이 비었거나 숫자가 아닐 때(화면에 이미 같은 안내가 떠 있다).
 */
internal fun CarInput.toCostInput(choice: PriceChoice): TripCostInput {
    val trip = toTrips(listOf(this)).single()
    return TripCostInput(
        startFuelMl = trip.startFuelMl, refueledMl = trip.refueledMl, endFuelMl = trip.endFuelMl,
        fuelCalcMethod = if (type.usesFuel) "PERCENT_CAPACITY" else null,
        // 서버 유가 기준은 NEAREST·CHEAPEST뿐이다. 근처 평균·직접 고름은 기준 없이 적용 유가만 남긴다.
        priceBasis = if (type.usesFuel && choice == PriceChoice.NEARBY_LOWEST) "CHEAPEST" else null,
        appliedPrice = trip.fuelPriceWonPerLiter.toInt(),
        startBatteryWh = trip.startBatteryWh, chargedWh = trip.chargedWh, endBatteryWh = trip.endBatteryWh,
        appliedPricePerKwh = trip.electricityWonPerMwh / 1000.0,
        tollEstimated = trip.tollWon.toInt(),
    )
}
