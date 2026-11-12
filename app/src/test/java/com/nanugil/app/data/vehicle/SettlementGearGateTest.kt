package com.nanugil.app.data.vehicle

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettlementGearGateTest {

    @Test
    fun `P단만 정차로 본다`() {
        assertEquals(GearState.PARK, VehicleGear.GEAR_PARK.toGearState())
        listOf(
            VehicleGear.GEAR_DRIVE,
            VehicleGear.GEAR_REVERSE,
            VehicleGear.GEAR_NEUTRAL,
            VehicleGear.GEAR_1,
            VehicleGear.GEAR_9,
        ).forEach { assertEquals(it.name, GearState.MOVING, it.toGearState()) }
    }

    @Test
    fun `기어를 모르면 UNKNOWN`() {
        assertEquals(GearState.UNKNOWN, VehicleGear.GEAR_UNKNOWN.toGearState())
        assertEquals(GearState.UNKNOWN, (null as VehicleGear?).toGearState())
    }

    @Test
    fun `P단에서만 정산 화면을 연다`() {
        assertNull(settlementBlockReason(GearState.PARK))
        assertNotNull(settlementBlockReason(GearState.MOVING))
        assertNotNull(settlementBlockReason(GearState.UNKNOWN))
    }
}
