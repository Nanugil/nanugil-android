package com.nanugil.app.ui.navi

import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.data.fuel.FuelProduct
import com.nanugil.app.data.fuel.GasStation
import com.nanugil.app.share.SettlementRequest
import com.nanugil.app.ui.settlement.CarInput
import com.nanugil.app.ui.settlement.PriceChoice
import com.nanugil.app.ui.settlement.PriceSource
import com.nanugil.app.ui.settlement.SettlementState
import com.nanugil.app.ui.settlement.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettlementUiMappingTest {

    private val cars = listOf(
        // 전기차 20 kWh × 350원 = 7,000 + 통행료 3,000
        CarInput(VehicleType.ELECTRIC, "김민수", "정예린", electricityPrice = "350", startBatteryKwh = "70", endBatteryKwh = "50", tollWon = "3000"),
        // 휘발유 10 L × 1,700원 = 17,000
        CarInput(VehicleType.GASOLINE, "이서준", "박지우", fuelPrice = "1700", startFuelL = "40", endFuelL = "30"),
    )
    private val station = GasStation("s1", "강산주유소", "GSC", 1_700, 300, Wgs84Point(37.5, 127.0))

    @Test
    fun arrival_showsEnergyLabelsDriversAndPassengers() {
        val ui = SettlementState(
            cars = cars,
            priceSource = PriceSource.NEARBY,
            chosen = mapOf(FuelProduct.GASOLINE to station),
            choice = PriceChoice.NEARBY_LOWEST,
        ).toUi(payLink = null)

        assertEquals(listOf("충전", "주유"), ui.cars.map { it.energyLabel })
        assertEquals(7_000L, ui.cars[0].fuelWon)
        assertEquals(3_000L, ui.cars[0].tollWon)
        assertEquals(27_000L, ui.totalWon)
        assertEquals(6_750L, ui.perPersonWon) // 27,000 ÷ 4
        assertEquals(4, ui.people)
        // 운전자 2줄 + 동승자 묶음 1줄
        assertEquals(listOf("김민수", "이서준", "동승자 2명"), ui.lines.map { it.name })
        assertEquals(PriceBasis.LOWEST, ui.basis)
        assertEquals("강산주유소 · 1,700원/L", ui.basisDetail)
        assertNull(ui.problem)
    }

    @Test
    fun sent_listsTransfersToMeByNameAndOthersWithArrow() {
        val state = SettlementState(cars = cars)
        val request = SettlementRequest.of(state.settled.first!!, myId = "김민수").toggled(0)
        val ui = state.copy(request = request).toUi(payLink = "https://qr.kakaopay.com/x")

        assertEquals(request.transfers.size, ui.payments.size)
        assertEquals(1, ui.paidCount)
        ui.payments.zip(request.transfers).forEach { (pay, t) ->
            assertEquals(if (t.toId == "김민수") t.fromId else "${t.fromId} → ${t.toId}", pay.name)
            assertEquals(t.amountWon, pay.amountWon)
        }
        assertEquals("김민수", ui.myName)
    }

    @Test
    fun badInput_showsProblemInsteadOfCrashing() {
        val ui = SettlementState(cars = listOf(cars[0].copy(driver = ""))).toUi(payLink = null)
        assertEquals("1번 차 운전자 이름을 입력하세요.", ui.problem)
        assertEquals(0L, ui.totalWon)
    }

    @Test
    fun paymentPages_fourPerPage() {
        assertEquals(1, paymentPages(0))
        assertEquals(1, paymentPages(4))
        assertEquals(2, paymentPages(5))
        assertEquals(2, paymentPages(7))
        assertEquals(3, paymentPages(9))
    }

    @Test
    fun serverTrip_otherCarNotSavedYet_showsWaitingInsteadOfZero() {
        // 서버 운행에서 B차(이서준)가 아직 비용을 저장하지 않았다
        val ui = SettlementState(
            cars = listOf(cars[0], CarInput(VehicleType.DIESEL, "이서준", "박지우", memberId = "b")),
            tripId = "t",
        ).toUi(payLink = null)

        assertEquals("B차(이서준) 비용 저장을 기다리는 중", ui.waiting)
        assertEquals(listOf(false, true), ui.cars.map { it.waiting })
        assertEquals(10_000L, ui.cars[0].totalWon) // 값이 있는 A차는 그대로 보인다
        assertEquals(10_000L, ui.totalWon) // 지금까지 모인 비용
        assertNull(ui.problem) // "2번 차 출발 연료 값을 입력하세요"를 띄우지 않는다
    }

    @Test
    fun myOwnCarEmpty_isStillAProblemToFix() {
        // 내 차(A차) 값이 비었으면 기다릴 일이 아니라 직접 넣어야 한다
        val ui = SettlementState(cars = listOf(CarInput(VehicleType.DIESEL, "김민수"), cars[1]), tripId = "t").toUi(payLink = null)

        assertNull(ui.waiting)
        assertEquals("1번 차 출발 연료 값을 입력하세요.", ui.problem)
    }

    @Test
    fun passengerDevice_waitsForMyRideDriverToo() {
        // 동승자 기기에서 A차는 남의 차라, 비었으면 직접 넣을 게 아니라 그 운전자가 저장하길 기다린다
        val ui = SettlementState(cars = listOf(CarInput(VehicleType.DIESEL, "김민수"), cars[1]), tripId = "t", iDrive = false).toUi(payLink = null)

        assertEquals("A차(김민수) 비용 저장을 기다리는 중", ui.waiting)
        assertNull(ui.problem)
    }
}
