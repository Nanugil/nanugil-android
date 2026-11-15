package com.nanugil.app.ui.navi

import com.nanugil.app.data.server.FuelType
import com.nanugil.app.data.server.Group
import com.nanugil.app.data.server.ServerException
import com.nanugil.app.data.server.TripMember
import com.nanugil.app.ui.settlement.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.nanugil.app.data.server.GroupMember as ServerMember

class GroupServerMappingTest {

    private val group = Group(
        id = "g", name = "MT", logoUrl = "TENT", destinationName = "청평호 펜션",
        destinationLat = 37.7, destinationLng = 127.4, ownerId = "u-host", inviteCode = "4F7K2Q",
        inviteCodeExpiresAt = "2026-11-15T09:00:00+09:00",
    )
    private val members = listOf(
        ServerMember("m-b", "g", "u-b", "이서준"),
        ServerMember("m-host", "g", "u-host", "김민수"),
        ServerMember("m-p1", "g", null, "정예린"),
        ServerMember("m-p2", "g", null, "박지우"),
    )
    private fun ride(member: String, car: String, label: String? = null) = TripMember("t", member, car, vehicleLabel = label)

    @Test
    fun `운행 전에는 앱 사용자를 운전자로, 방장을 맨 앞에`() {
        val info = groupInfoOf(group, members, riders = emptyList())
        assertEquals(listOf("김민수", "이서준", "정예린", "박지우"), info.members.map { it.name })
        assertEquals(listOf(true, true, false, false), info.members.map { it.driver })
        assertEquals("m-host", info.host?.id)
        assertEquals(2, info.cars)
        assertEquals(GroupLogo.TENT, info.logo)
        assertEquals(37.7, info.destination?.latitude)
    }

    @Test
    fun `운행 중에는 탄 차 이름을 붙이고 차 수는 운전자 행 수`() {
        val riders = listOf(ride("m-host", "m-host", "A차"), ride("m-p1", "m-host"))
        val info = groupInfoOf(group, members, riders)
        assertEquals("A차", info.members.first { it.name == "정예린" }.car)
        assertNull(info.members.first { it.name == "박지우" }.car)
        assertEquals(1, info.cars)
    }

    @Test
    fun `동승자 후보는 운전자와 나를 빼고 남의 차에 탄 사람은 잠근다`() {
        val riders = listOf(
            ride("m-host", "m-host", "A차"), ride("m-p1", "m-host"),
            ride("m-b", "m-b", "B차"), ride("m-p2", "m-b"),
        )
        val choices = passengerChoices(groupInfoOf(group, members, riders), riders, myMemberId = "m-b")
        assertEquals(listOf("정예린", "박지우"), choices.map { it.member.name })
        assertEquals("A차 탑승", choices[0].takenBy)
        assertEquals(true, choices[1].selected) // 이미 내 차에 탔으면 골라 둔다
        assertNull(choices[1].takenBy)
    }

    @Test
    fun `운행 전에도 방장은 동승자 후보에서 빠진다`() {
        val choices = passengerChoices(groupInfoOf(group, members, riders = emptyList()), riders = emptyList(), myMemberId = "m-b")
        assertEquals(listOf("정예린", "박지우"), choices.map { it.member.name })
    }

    @Test
    fun `최근 정산은 끝난 운행의 저장된 비용으로 1인 부담액을 다시 계산한다`() {
        val names = mapOf("m-host" to "김민수", "m-p1" to "정예린", "m-b" to "이서준")
        // B차 휘발유 50L → 20L, 1,700원/L, 통행료 15,000원 = 66,000원 ÷ 3명
        val riders = listOf(
            TripMember("t", "m-host", "m-host", fuelType = "EV", startBatteryWh = 70_000, endBatteryWh = 70_000, appliedPricePerKwh = 300.0, tollEstimated = 0),
            TripMember("t", "m-p1", "m-host"),
            TripMember("t", "m-b", "m-b", fuelType = "GASOLINE", startFuelMl = 50_000, endFuelMl = 20_000, appliedPrice = 1_700, tollEstimated = 15_000),
        )
        val r = recentSettlementOf("MT", "2026-11-14T19:35:59.97+09:00", riders, names)
        assertEquals(22_000, r.perPersonWon)
        assertEquals("11월 14일", r.date)
        assertEquals("정산 끝", r.status)
    }

    @Test
    fun `비용을 안 저장한 차가 있으면 비용 미입력`() {
        val riders = listOf(TripMember("t", "m-host", "m-host", fuelType = "GASOLINE"))
        val r = recentSettlementOf("MT", "2026-11-04T09:00:00+09:00", riders, mapOf("m-host" to "김민수"))
        assertEquals("비용 미입력", r.status)
        assertEquals("11월 4일", r.date)
    }

    @Test
    fun `차 이름이 겹치면 안 쓴 글자로`() {
        assertEquals("B차", freeCarName("B차", listOf("A차")))
        assertEquals("C차", freeCarName("B차", listOf("A차", "B차")))
        assertEquals("우리 차", freeCarName("우리 차", listOf("A차", "B차")))
    }

    @Test
    fun `차종과 오류 코드`() {
        assertEquals(FuelType.EV, fuelTypeOf(VehicleType.ELECTRIC))
        assertEquals(FuelType.GASOLINE, fuelTypeOf(null))
        assertEquals(GroupLogo.CAR, logoOf("https://example.com/x.png"))
        assertEquals("코드가 맞지 않거나 만료됐어요", groupErrorText(ServerException("INVALID_CODE")))
        assertEquals("서버 오류: WEIRD", groupErrorText(ServerException("WEIRD")))
        assertEquals("서버에 연결하지 못했어요", groupErrorText(RuntimeException()))
    }
}
