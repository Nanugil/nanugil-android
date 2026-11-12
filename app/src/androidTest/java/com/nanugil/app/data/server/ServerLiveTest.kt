package com.nanugil.app.data.server

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nanugil.app.BuildConfig
import com.nanugil.app.data.group.CarState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 Supabase 서버에 붙어 GroupTripApi·SupabaseGroupLocationRepository를 끝까지 돌려 본다.
 * local.properties에 SUPABASE_URL, SUPABASE_ANON_KEY가 있어야 하고, 실행할 때마다 시험용 그룹이 서버에 남는다.
 * 실행: ./gradlew connectedDebugAndroidTest (Pleos 에뮬레이터는 -dns-server 8.8.8.8,1.1.1.1 로 켠다)
 */
@RunWith(AndroidJUnit4::class)
class ServerLiveTest {

    /** 앱(A, 방장)과 별개로 로그인하는 두 번째 사용자(B). 세션을 메모리에만 둬서 A와 섞이지 않는다. */
    private fun otherUser() = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
        install(Auth) { sessionManager = MemorySessionManager(); codeVerifierCache = MemoryCodeVerifierCache() }
        install(Postgrest)
        install(Realtime)
    }

    private suspend fun expectError(code: String, block: suspend () -> Unit) {
        try { block(); fail("$code 오류가 나야 함") } catch (e: ServerException) { assertEquals(code, e.code) }
    }

    @Test
    fun groupInviteTripAndLocation() = runBlocking {
        assumeTrue("local.properties에 Supabase 키가 없음", BuildConfig.SUPABASE_URL.isNotBlank())
        withTimeout(60_000) {
            Log.i("ServerLiveTest", "step G1·G2")
            // G1·G2
            val group = GroupTripApi.createGroup("앱 시험", Place("가평", 37.83, 127.51), "A")
            assertTrue(group.inviteCode.matches(Regex("^[A-HJ-NP-Z2-9]{6}$")))
            assertNull(GroupTripApi.myGroups().first { it.group.id == group.id }.activeTrip)

            Log.i("ServerLiveTest", "step I3 오류 변환")

            // I3 오류 변환, I2 (B가 코드로 참여)
            expectError("INVALID_CODE") { GroupTripApi.previewInvite("ZZZZZZ") }
            val b = otherUser()
            b.auth.signInAnonymously()
            b.postgrest.rpc("join_group", buildJsonObject { put("p_code", group.inviteCode); put("p_display_name", "B") })
            val members = GroupTripApi.members(group.id)
            assertEquals(2, members.size)
            val bMemberId = members.first { it.displayName == "B" }.id

            Log.i("ServerLiveTest", "step T5: 운행")

            // T5: 운행 시작 알림을 받는다
            val tripEvents = Channel<Unit>(Channel.UNLIMITED)
            val watchingTrips = launch { GroupTripApi.tripChanges(group.id).collect { tripEvents.send(it) } }
            tripEvents.receive() // 구독이 붙었다는 첫 알림
            val trip = GroupTripApi.startTrip(group.id)
            tripEvents.receive() // 운행 시작 알림
            watchingTrips.cancel()
            assertEquals(trip.id, GroupTripApi.myGroups().first { it.group.id == group.id }.activeTrip?.id)
            expectError("TRIP_ALREADY_ACTIVE") { GroupTripApi.startTrip(group.id) }

            Log.i("ServerLiveTest", "step T2")

            // T2
            GroupTripApi.joinTrip(trip.id, "A차", FuelType.EV)
            b.postgrest.rpc("join_trip", buildJsonObject { put("p_trip_id", trip.id); put("p_vehicle_label", "B차"); put("p_fuel_type", "GASOLINE") })

            Log.i("ServerLiveTest", "step R1~R3")

            // R1~R3: B가 보낸 위치를 앱 저장소가 CarState로 받는다
            val received = CompletableDeferred<CarState>()
            val repo = SupabaseGroupLocationRepository()
            val watching = launch { repo.observeGroup(group.id).collect { cars -> cars.firstOrNull()?.let { received.complete(it) } } }
            val bChannel = b.channel(trip.channel) { isPrivate = true }
            bChannel.subscribe(blockUntilSubscribed = true)
            // 앱 저장소의 구독이 언제 붙을지 몰라서, 받을 때까지 1초마다 다시 보낸다
            var car: CarState? = null
            while (car == null) {
                bChannel.broadcast(SupabaseGroupLocationRepository.CAR_STATE_EVENT,
                    CarStateMessage(bMemberId, 37.6, 127.2, 80, 30, 70, DriveStatus.DRIVING, System.currentTimeMillis()))
                Log.i("ServerLiveTest", "step broadcast sent, waiting")
                car = withTimeoutOrNull(1_000) { received.await() }
            }
            assertEquals("B", car.driverName)
            assertEquals(37.6, car.position.latitude, 1e-9)
            watching.cancel()
            b.realtime.removeChannel(bChannel)

            Log.i("ServerLiveTest", "step I4·T7")

            // I4·T7
            val passenger = GroupTripApi.addPassenger(group.id, "할머니")
            assertEquals(1, GroupTripApi.setTripPassengers(trip.id, listOf(passenger.id)).size)
            assertEquals(3, GroupTripApi.tripMembers(trip.id).size)

            Log.i("ServerLiveTest", "step T6·T8")

            // T6·T8
            expectError("NOT_ARRIVED") { GroupTripApi.saveTripCost(trip.id, TripCostInput()) }
            GroupTripApi.markArrived(trip.id)
            val cost = GroupTripApi.saveTripCost(trip.id, TripCostInput(startBatteryWh = 60_000, endBatteryWh = 41_000, appliedPricePerKwh = 347))
            assertEquals(41_000L, cost.endBatteryWh)

            Log.i("ServerLiveTest", "step T4")

            // T4
            GroupTripApi.endTrip(trip.id)
            assertNull(GroupTripApi.activeTrip(group.id))
            b.close()
        }
    }
}
