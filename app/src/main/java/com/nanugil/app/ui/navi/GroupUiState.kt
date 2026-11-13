package com.nanugil.app.ui.navi

import com.nanugil.app.R
import com.nanugil.app.share.SettlementCalculator
import com.nanugil.app.share.VehicleTrip

/**
 * 와이어프레임 v5(Figma "나누길 와이어프레임 v5")의 화면 흐름.
 * 위쪽 탭 [드라이브 | 내 차 | 정산] 중 어느 탭에 속하는지도 같이 들고 있다.
 */
enum class AppScreen(val tab: NaviTab) {
    /** 01 홈: 진행 중인 운행 · 내 그룹 · 최근 정산 */
    HOME(NaviTab.DRIVE),
    /** 02 그룹 만들기 */
    CREATE_GROUP(NaviTab.DRIVE),
    /** 03 초대 코드 (방장 대기실) */
    LOBBY(NaviTab.DRIVE),
    /** 04 코드로 참여 */
    JOIN_CODE(NaviTab.DRIVE),
    /** 05 운행 참가 (내 차·동승자) */
    JOIN_SETUP(NaviTab.DRIVE),
    /** 06·07 주행 중 (합류 제안 포함) */
    DRIVE(NaviTab.DRIVE),
    /** 내 차 상태 (와이어프레임에 없는 탭: 기존 차량 카드) */
    CAR(NaviTab.CAR),
    /** 08 도착 → 정산 내역 */
    ARRIVAL(NaviTab.SETTLE),
    /** 09 정산 요청 보냄 */
    SETTLE_SENT(NaviTab.SETTLE),
}

enum class NaviTab(val label: String, val iconRes: Int) {
    DRIVE("드라이브", R.drawable.ic_home),
    CAR("내 차", R.drawable.ic_car),
    SETTLE("정산", R.drawable.ic_card),
}

/** 모임 로고: 지도 위 일행 차량에 표시 */
enum class GroupLogo(val iconRes: Int) {
    CAR(R.drawable.ic_car),
    TENT(R.drawable.ic_logo_tent),
    SCHOOL(R.drawable.ic_logo_school),
    MOUNTAIN(R.drawable.ic_logo_mountain),
    WAVES(R.drawable.ic_logo_waves),
    MUSIC(R.drawable.ic_logo_music),
    BADGE(R.drawable.ic_logo_badge),
    STAR(R.drawable.ic_logo_star),
}

data class GroupMember(
    val name: String,
    val driver: Boolean,
    val host: Boolean = false,
    /** 타는 차 ("A차") */
    val car: String? = null,
) {
    val initial: String get() = name.take(1)
    val roleLabel: String get() = if (driver) "운전" else "동승"
}

data class GroupInfo(
    val name: String,
    val destinationName: String,
    val code: String,
    val logo: GroupLogo,
    val members: List<GroupMember>,
    val cars: Int,
) {
    val host: GroupMember? get() = members.firstOrNull { it.host }
}

/** 홈 "내 그룹" 목록 한 줄 */
data class GroupSummary(val name: String, val memberCount: Int, val logo: GroupLogo, val driving: Boolean)

/** 홈 "최근 정산" 목록 한 줄 */
data class RecentSettlement(val name: String, val date: String, val perPersonWon: Int, val status: String, val done: Boolean)

/** 02 그룹 만들기 입력값 */
data class CreateGroupForm(
    val name: String = "2026 신소재공학과 MT",
    val myName: String = "김민수",
    val logo: GroupLogo = GroupLogo.CAR,
)

/** 05 운행 참가: 동승자 선택 한 줄 */
data class PassengerChoice(val member: GroupMember, val selected: Boolean, val takenBy: String? = null)

/** 04·05 코드로 참여 진행 상태 */
data class JoinState(
    val code: String = "",
    val found: GroupInfo? = null,
    val carName: String = "B차",
    val fuelType: String = "휘발유",
    val passengers: List<PassengerChoice> = emptyList(),
) {
    val selectedCount: Int get() = passengers.count { it.selected }
}

enum class PriceBasis(val label: String) { AVERAGE("근처 평균"), LOWEST("근처 최저가") }

/** 08 도착: 차별 비용 한 줄. [energyLabel]은 연료비 이름(주유·충전·주유·충전). */
data class CarCost(
    val label: String,
    val fuelWon: Long,
    val tollWon: Long,
    val energyLabel: String = "주유",
    val tollLabel: String = "통행료",
    /** 서버 운행에서 이 차 운전자가 아직 비용을 저장하지 않음(08에 "기다리는 중") */
    val waiting: Boolean = false,
) {
    val totalWon: Long get() = fuelWon + tollWon
}

/** 08 도착: 주고받을 돈 한 줄 (+ 받음, - 보냄) */
data class MoneyLine(val initial: String, val name: String, val detail: String, val amountWon: Long, val highlight: Boolean)

/** 09 정산 요청 보냄: 송금 한 줄. 누르면 받음 체크가 바뀐다(카카오페이가 송금 결과를 알려 주지 않는다). */
data class PaymentStatus(val initial: String, val name: String, val paid: Boolean, val amountWon: Long = 0)

data class SettlementUi(
    val perPersonWon: Long,
    val totalWon: Long,
    val people: Int,
    val cars: List<CarCost>,
    /** 근처 평균·최저가 중 고른 것. 값 수정에서 주유소를 직접 골랐으면 null. */
    val basis: PriceBasis?,
    val lines: List<MoneyLine>,
    val sumCheckWon: Long,
    val payments: List<PaymentStatus>,
    /** 유가를 가져온 곳 (예: "강산주유소 1,830원/L"). 근처 주유소를 못 불러왔으면 그 이유. */
    val basisDetail: String? = null,
    /** 값이 비었거나 틀려 정산할 수 없을 때 고칠 내용 */
    val problem: String? = null,
    /** 이 차 운전자 이름과 카카오페이 송금 링크 (09 QR). 링크가 없으면 등록 버튼을 보여 준다. */
    val myName: String = "",
    val payLink: String? = null,
    /** 서버 연결 상태 안내 (예시 값 사용, 비용 저장 실패). 문제없으면 null. */
    val serverNote: String? = null,
    /** 다른 차가 비용을 저장하기 전이라 정산을 미룰 때 안내 (예: "B차(이서준) 비용 저장을 기다리는 중"). 모두 모였으면 null. */
    val waiting: String? = null,
) {
    val paidCount: Int get() = payments.count { it.paid }
}

/** 화면 흐름 전체 상태 (서버가 붙기 전까지는 목업 데이터) */
data class GroupUiState(
    val screen: AppScreen = AppScreen.HOME,
    val group: GroupInfo? = null,
    /** 운행을 시작했는지 (홈 "진행 중인 운행" 카드) */
    val tripStarted: Boolean = false,
    val myGroups: List<GroupSummary> = MockGroups.myGroups,
    val recent: List<RecentSettlement> = MockGroups.recent,
    val createForm: CreateGroupForm = CreateGroupForm(),
    val join: JoinState = JoinState(),
    val settlement: SettlementUi = MockGroups.settlement(PriceBasis.AVERAGE),
)

/**
 * 와이어프레임에 있는 시연용 목업. TODO(서버): 그룹·멤버·초대 코드·송금 상태는 서버에서 받는다.
 */
object MockGroups {
    const val DESTINATION = "청평호 펜션"

    /** 초대 코드에 쓰는 글자 (헷갈리는 0/O, 1/I 제외) */
    const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 6

    val members = listOf(
        GroupMember("김민수", driver = true, host = true, car = "A차"),
        GroupMember("이서준", driver = true, car = "B차"),
        GroupMember("박지우", driver = false, car = "B차"),
        GroupMember("최하은", driver = false, car = "B차"),
        GroupMember("정예린", driver = false, car = "A차"),
        GroupMember("한도윤", driver = false, car = "A차"),
        GroupMember("오지민", driver = false, car = "A차"),
        GroupMember("윤서아", driver = false, car = "B차"),
    )

    /** "동승자 추가"를 누를 때 차례로 들어오는 사람 */
    val extraPassengers = members.drop(4)

    val mtGroup = GroupInfo(
        name = "2026 신소재공학과 MT",
        destinationName = DESTINATION,
        code = "4F7K2Q",
        logo = GroupLogo.CAR,
        members = members.take(4),
        cars = 2,
    )

    val myGroups = listOf(
        GroupSummary("2026 신소재공학과 MT", 8, GroupLogo.CAR, driving = false),
        GroupSummary("가족 여행", 4, GroupLogo.MOUNTAIN, driving = false),
    )

    val recent = listOf(
        RecentSettlement("가족 여행", "11월 2일", 23_400, "완료", done = true),
        RecentSettlement("신입생 OT", "10월 19일", 9_800, "미확인 1명", done = false),
    )

    /** 05 운행 참가에서 보이는 동승자 후보 (정예린은 이미 A차에 탔다) */
    val joinPassengers = listOf(
        PassengerChoice(GroupMember("박지우", driver = false), selected = true),
        PassengerChoice(GroupMember("최하은", driver = false), selected = true),
        PassengerChoice(GroupMember("정예린", driver = false), selected = false, takenBy = "A차 탑승"),
    )

    // 휘발유 가격(원/L): 오피넷 근처 평균·최저가 자리. TODO(정산): OpinetClient.averagePrices()로 바꾼다.
    private const val PRICE_AVERAGE = 1_600L
    private const val PRICE_LOWEST = 1_520L

    /** 08 도착: 정산 담당의 SettlementCalculator로 1/N과 송금 목록을 계산한다 */
    // TODO(정산): 휘발유(mL·원/L) 목업. 시연 차량은 전기차라 #12 전기차 열·#14 전기차 정산(배터리 Wh·충전 단가)이 들어오면 바꾼다.
    fun settlement(basis: PriceBasis): SettlementUi {
        val price = if (basis == PriceBasis.AVERAGE) PRICE_AVERAGE else PRICE_LOWEST
        val aPassengers = listOf("정예린", "한도윤", "오지민")
        val bPassengers = listOf("박지우", "최하은", "윤서아")
        val trips = listOf(
            VehicleTrip("A차", "김민수", aPassengers, startFuelMl = 40_000, endFuelMl = 26_875, fuelPriceWonPerLiter = price, tollWon = 10_000),
            VehicleTrip("B차", "이서준", bPassengers, startFuelMl = 50_000, endFuelMl = 20_000, fuelPriceWonPerLiter = price, tollWon = 15_000),
        )
        val result = SettlementCalculator.settle(trips, roundingUnitWon = 10)
        val cars = trips.map { t ->
            val fuel = SettlementCalculator.fuelCostWon(
                SettlementCalculator.usedFuelMl(t.startFuelMl, t.refueledMl, t.endFuelMl), t.fuelPriceWonPerLiter,
            )
            CarCost(t.vehicleId, fuel, t.tollWon)
        }
        val people = 2 + aPassengers.size + bPassengers.size
        val share = result.perPersonShareWon
        val lines = buildList {
            add(MoneyLine("김", "김민수", "A차 운전", result.balancesWon["김민수"] ?: 0, highlight = true))
            add(MoneyLine("이", "이서준", "B차 운전", result.balancesWon["이서준"] ?: 0, highlight = false))
            add(MoneyLine("${aPassengers.size + bPassengers.size}", "동승자 ${aPassengers.size + bPassengers.size}명", "각자 보냄", -share, highlight = false))
        }
        return SettlementUi(
            perPersonWon = share,
            totalWon = result.totalCostWon,
            people = people,
            cars = cars,
            basis = basis,
            lines = lines,
            sumCheckWon = lines.filter { it.amountWon > 0 }.sumOf { it.amountWon },
            payments = listOf(
                PaymentStatus("박", "박지우", paid = true),
                PaymentStatus("정", "정예린", paid = true),
                PaymentStatus("한", "한도윤", paid = true),
                PaymentStatus("최", "최하은", paid = false),
                PaymentStatus("오", "오지민", paid = false),
                PaymentStatus("윤", "윤서아", paid = false),
            ),
        )
    }

    /** 새 초대 코드 (0/O, 1/I 제외) */
    fun newCode(): String = (1..CODE_LENGTH).map { CODE_CHARS.random() }.joinToString("")
}
