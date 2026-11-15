package com.nanugil.app.data.server

import org.junit.Assert.assertEquals
import org.junit.Test

class PassengerWebTest {

    @Test
    fun `끝 슬래시를 떼고 코드를 대문자로 바꾼다`() {
        assertEquals("https://host/join?code=4F7K2Q", passengerJoinUrl("https://host/", "4f7k2q"))
        assertEquals("https://host/join?code=4F7K2Q", passengerJoinUrl("https://host", "4F7K2Q"))
    }
}
