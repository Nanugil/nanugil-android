package com.nanugil.app.data.server

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nanugil.app.BuildConfig
import com.nanugil.app.core.geo.Wgs84Point
import com.nanugil.app.core.geo.offset
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
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 지도 화면이 쓰는 [ServerGroupCars]를 실서버에서 끝까지 돌려 본다 (이슈 #18 2번).
 * 앱(A)과 두 번째 사용자(B)가 한 운행에 차로 참가한 뒤
 * - A는 B차를 글자·이름과 함께 받고, 목록 맨 앞에 나(isMe)가 있다
 * - B는 A가 1초 루프로 보낸 위치를 받는다
 * - 운행이 끝나면 A의 목록이 빈다
 * 실행할 때마다 시험용 그룹이 서버에 남는다 (ServerLiveTest와 같다).
 */
@RunWith(AndroidJUnit4::class)
class ServerGroupCarsLiveTest {

    private fun otherUser() = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
        install(Auth) { sessionManager = MemorySessionManager(); codeVerifierCache = MemoryCodeVerifierCache() }
        install(Postgrest)
        install(Realtime)
    }

    @Test
    fun aSendsAndSeesB() = runBlocking {
        assumeTrue("local.properties에 Supabase 키가 없음", BuildConfig.SUPABASE_URL.isNotBlank())
        withTimeout(90_000) {
            // 그룹·운행 준비: A가 만들고 B가 코드로 참여, 둘 다 차로 운행 참가
            val group = GroupTripApi.createGroup("[테스트] 지도 일행", Place("가평", 37.83, 127.51), "A")
            val b = otherUser()
            b.auth.signInAnonymously()
            b.postgrest.rpc("join_group", buildJsonObject { put("p_code", group.inviteCode); put("p_display_name", "B") })
            val members = GroupTripApi.members(group.id)
            val aId = members.first { it.displayName == "A" }.id
            val bId = members.first { it.displayName == "B" }.id
            val trip = GroupTripApi.startTrip(group.id)
            GroupTripApi.joinTrip(trip.id, "A차", FuelType.GASOLINE)
            b.postgrest.rpc("join_trip", buildJsonObject { put("p_trip_id", trip.id); put("p_vehicle_label", "B차"); put("p_fuel_type", "GASOLINE") })
            val labels = carLabels(GroupTripApi.tripMembers(trip.id))
            Log.i(TAG, "준비 끝: labels=$labels")

            // A: 지도 화면처럼 구독. 부를 때마다 25m씩 북쪽으로 움직여 주행(3초마다 전송)이 되게 한다
            var aPos = Wgs84Point(37.5665, 126.9780)
            val latest = MutableStateFlow<List<CarState>>(emptyList())
            val watching = launch {
                ServerGroupCars().cars(group.id) {
                    aPos = aPos.offset(0.0, 25.0)
                    MyCarSnapshot(aPos, etaMinutes = 40, fuelPercent = 55)
                }.collect { latest.value = it }
            }

            // B: 같은 위치 채널에 붙어 A 메시지를 기다리면서, 받을 때까지 1초마다 자기 위치를 보낸다
            val bChannel = b.channel(trip.channel) { isPrivate = true }
            val fromA = bChannel.broadcastFlow<CarStateMessage>(SupabaseGroupLocationRepository.CAR_STATE_EVENT)
            bChannel.subscribe(blockUntilSubscribed = true)
            val aMessage = async { fromA.first { it.carId == aId } }
            var cars: List<CarState>? = null
            while (cars == null) {
                bChannel.broadcast(SupabaseGroupLocationRepository.CAR_STATE_EVENT,
                    CarStateMessage(bId, 37.6, 127.2, 80, 30, 9, DriveStatus.DRIVING, System.currentTimeMillis()))
                cars = withTimeoutOrNull(1_000) { latest.first { list -> list.any { it.carId == bId } && list.firstOrNull()?.isMe == true } }
            }
            Log.i(TAG, "A가 받은 목록: $cars")

            val me = cars.first()
            assertEquals(aId, me.carId)
            assertEquals(labels[aId], me.label)
            assertEquals("A", me.driverName)
            val bCar = cars.first { it.carId == bId }
            assertEquals(labels[bId], bCar.label)
            assertEquals("B", bCar.driverName)
            assertTrue(bCar.isLowFuel)

            val sent = aMessage.await()
            Log.i(TAG, "B가 받은 A: $sent")
            assertEquals(55, sent.fuelPercent)
            assertEquals(40, sent.etaMinutes)
            assertEquals(DriveStatus.DRIVING, sent.driveStatus)

            // 운행이 끝나면 목록이 빈다 (tripChanges 알림 또는 15초 재확인)
            b.realtime.removeChannel(bChannel)
            GroupTripApi.endTrip(trip.id)
            latest.first { it.isEmpty() }
            watching.cancel()
            b.close()
        }
    }

    private companion object {
        const val TAG = "ServerGroupCarsLive"
    }
}
