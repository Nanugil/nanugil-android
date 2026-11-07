package com.nanugil.app.data.fuel

/**
 * 오피넷 API에서 쓰는 고정 값 모음.
 *
 * 문서: https://www.opinet.co.kr/user/custapi/custApiInfo.do
 */
object OpinetConstants {
    const val BASE_URL = "https://www.opinet.co.kr/api"

    /** 반경 내 주유소 API가 받는 최대 반경 */
    const val MAX_RADIUS_METERS = 5_000

    /**
     * 하루 호출 한도. 일반 API 키는 300건, 기업·기관 제휴 시 1,500건.
     * 앱은 연료 부족 차량이 새로 생길 때와 버튼을 누를 때만 호출한다.
     */
    const val DAILY_CALL_LIMIT = 300
}

/** 유종(제품) 코드 */
enum class FuelProduct(val code: String, val label: String) {
    GASOLINE("B027", "휘발유"),
    DIESEL("D047", "경유"),
    PREMIUM_GASOLINE("B034", "고급휘발유"),
    LPG("K015", "LPG"),
}

/** 결과 정렬 방식 */
enum class StationSort(val code: String) {
    PRICE("1"),
    DISTANCE("2"),
}

/** 상표 코드 → 화면에 보여 줄 이름 */
object OpinetBrand {
    private val names = mapOf(
        "SKE" to "SK에너지",
        "GSC" to "GS칼텍스",
        "HDO" to "HD현대오일뱅크",
        "SOL" to "S-OIL",
        "RTO" to "자영알뜰",
        "RTX" to "고속도로알뜰",
        "NHO" to "농협알뜰",
        "ETC" to "자가상표",
        "E1G" to "E1",
        "SKG" to "SK가스",
    )

    fun displayName(code: String): String = names[code] ?: code
}
