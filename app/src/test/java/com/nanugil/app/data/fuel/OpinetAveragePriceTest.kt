package com.nanugil.app.data.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpinetAveragePriceTest {

    private val client = OpinetClient(apiKey = "test")

    @Test
    fun parsesNationalAverageByProduct() {
        // avgAllPrice.do 응답 형식. 가격은 문자열 소수로 온다.
        val body = """
            {"RESULT":{"OIL":[
              {"TRADE_DT":"20261109","PRODCD":"B034","PRODNM":"고급휘발유","PRICE":"1953.21","DIFF":"+0.12"},
              {"TRADE_DT":"20261109","PRODCD":"B027","PRODNM":"휘발유","PRICE":"1699.50","DIFF":"-0.30"},
              {"TRADE_DT":"20261109","PRODCD":"D047","PRODNM":"자동차용경유","PRICE":"1580.49","DIFF":"0.00"},
              {"TRADE_DT":"20261109","PRODCD":"C004","PRODNM":"실내등유","PRICE":"1300.00","DIFF":"0.00"}
            ]}}
        """.trimIndent()
        assertEquals(
            mapOf(
                FuelProduct.PREMIUM_GASOLINE to 1953,
                FuelProduct.GASOLINE to 1700, // 1699.50 반올림
                FuelProduct.DIESEL to 1580,
            ),
            client.parseAveragePrices(body),
        )
    }

    @Test
    fun notJson_rejected() {
        assertThrows(OpinetException::class.java) { client.parseAveragePrices("인증키가 올바르지 않습니다") }
    }
}
