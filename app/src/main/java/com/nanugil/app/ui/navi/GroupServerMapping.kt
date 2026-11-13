package com.nanugil.app.ui.navi

import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.server.FuelType
import com.nanugil.app.data.server.Group
import com.nanugil.app.data.server.ServerException
import com.nanugil.app.data.server.TripMember
import com.nanugil.app.ui.settlement.VehicleType
import com.nanugil.app.data.server.GroupMember as ServerMember

/**
 * 서버 그룹(#15) → 02·03·04·05 화면 값.
 * @param riders 진행 중 운행의 탄 사람들(T3). 운행 전이면 비어 있다.
 */
internal fun groupInfoOf(group: Group, members: List<ServerMember>, riders: List<TripMember>): GroupInfo {
    val drivers = riders.filter { it.isDriver }
    val labels = drivers.associate { it.memberId to it.vehicleLabel }
    return GroupInfo(
        id = group.id,
        name = group.name,
        destinationName = group.destinationName,
        destination = Wgs84Point(group.destinationLat, group.destinationLng),
        code = group.inviteCode,
        logo = logoOf(group.logoUrl),
        members = members.map { m ->
            val ride = riders.firstOrNull { it.memberId == m.id }
            GroupMember(
                name = m.displayName,
                // 운행 전에는 앱 사용자를 운전자로 본다 (앱 없는 동승자는 user_id가 없다)
                driver = ride?.isDriver ?: (m.userId != null),
                host = m.userId == group.ownerId,
                car = ride?.let { labels[it.vehicleDriverMemberId] },
                id = m.id,
            )
        }.sortedByDescending { it.host },
        cars = if (drivers.isEmpty()) members.count { it.userId != null } else drivers.size,
    )
}

/** 로고는 서버 logo_url 칸에 [GroupLogo] 이름으로 넣는다 (그림 파일은 앱에 있다). */
internal fun logoOf(saved: String?): GroupLogo = GroupLogo.entries.firstOrNull { it.name == saved } ?: GroupLogo.CAR

/**
 * 05 운행 참가: 내 차에 태울 수 있는 사람. 운전자와 나는 빼고, 남의 차에 탄 사람은 "B차 탑승"으로 잠근다.
 * 이미 내 차에 탄 사람은 골라 둔다(다시 들어왔을 때).
 */
internal fun passengerChoices(group: GroupInfo, riders: List<TripMember>, myMemberId: String?): List<PassengerChoice> {
    val labels = riders.filter { it.isDriver }.associate { it.memberId to (it.vehicleLabel ?: "다른 차") }
    return group.members.filter { it.id != myMemberId && it.id !in labels }.map { m ->
        when (val car = riders.firstOrNull { it.memberId == m.id }?.vehicleDriverMemberId) {
            null -> PassengerChoice(m, selected = false)
            myMemberId -> PassengerChoice(m, selected = true)
            else -> PassengerChoice(m, selected = false, takenBy = "${labels[car]} 탑승")
        }
    }
}

/** 05 내 차 이름: 지금 이름이 다른 차와 겹치면 아직 안 쓴 "C차"·"D차"…로 바꾼다. */
internal fun freeCarName(current: String, taken: List<String>): String =
    if (current !in taken) current else ('A'..'Z').map { "${it}차" }.first { it !in taken }

/** 정산 차종 → 서버 fuel_type. 모르면 휘발유 (정산 값 수정에서 고친다). */
internal fun fuelTypeOf(type: VehicleType?): FuelType = when (type) {
    VehicleType.ELECTRIC -> FuelType.EV
    VehicleType.HYBRID -> FuelType.HYBRID
    VehicleType.DIESEL -> FuelType.DIESEL
    VehicleType.PREMIUM_GASOLINE -> FuelType.PREMIUM_GASOLINE
    VehicleType.GASOLINE, null -> FuelType.GASOLINE
}

/** 서버 오류 → 화면 안내 */
internal fun groupErrorText(e: Throwable): String = when (val code = (e as? ServerException)?.code) {
    "INVALID_CODE" -> "코드가 맞지 않거나 만료됐어요"
    "TOO_MANY_ATTEMPTS" -> "틀린 코드를 여러 번 넣었어요. 10분 뒤 다시 해 보세요"
    "NOT_OWNER" -> "방장만 할 수 있어요"
    "ALREADY_RIDING" -> "이미 다른 차 동승자로 등록돼 있어요"
    "PASSENGER_TAKEN" -> "고른 사람 중 이미 다른 차에 탄 사람이 있어요"
    "TRIP_NOT_ACTIVE" -> "진행 중인 운행이 없어요. 방장이 운행을 시작하면 출발할 수 있어요"
    "NOT_MEMBER" -> "이 그룹 멤버가 아니에요"
    null -> "서버에 연결하지 못했어요"
    else -> "서버 오류: $code"
}
