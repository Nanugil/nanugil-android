package com.nanugil.app.ui.navi

import com.nanugil.app.data.vehicle.GearState
import com.nanugil.app.data.fare.TripCost
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.vehicle.VehicleStatus

/**
 * 나누길 내비 화면이 그리는 모든 값 (한 번에 하나의 불변 스냅샷).
 * ViewModel이 길안내·차량·그룹·정산·테마를 합쳐 만들고, 화면은 이것만 보고 그린다.
 */
data class NaviDriveUiState(
    val navi: NaviState = NaviState(),
    val vehicle: VehicleStatus = VehicleStatus(),
    /** 같은 그룹 차량 (내 차 포함) */
    val groupCars: List<CarState> = emptyList(),
    /** 시나리오 5단계: 다른 차가 보낸 휴게소 정차 제안 (큰 팝업). 없으면 null */
    val proposal: MeetupProposal? = null,
    /** 시나리오 4단계: 내 차 연료가 부족할 때 "근처 휴게소 들를까요?" 카드. 없으면 null */
    val suggestion: RestStopSuggestion? = null,
    /** 합류 팝업에 말로 답하는 중인지, 들린 글자 */
    val answerListening: Boolean = false,
    val answerHeard: String = "",
    /** 출발부터 지금까지 쓴 유류비·통행료와 1인 금액 */
    val cost: TripCost = TripCost(0.0, 0, 0, MockGroupDriveData.SHARE_MEMBERS),
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val search: SearchUiState = SearchUiState(),
    /** 와이어프레임 v5 화면 흐름 (홈 · 그룹 만들기 · 초대 코드 · 참여 · 주행 · 정산) */
    val group: GroupUiState = GroupUiState(),
    /** 내 차 기어. 글자 입력 화면은 P단에서만 연다 */
    val gear: GearState = GearState.UNKNOWN,
) {
    val carsDriving: Int get() = groupCars.size
    /** 주행 중 글자 입력 금지 (#9 기본 규칙, App Market 심사). 기어를 모를 때도 막는다 */
    val canType: Boolean get() = gear == GearState.PARK
}

/** 내 차 연료 부족 → 그룹에 휴게소 정차를 제안할지 묻는 카드 */
data class RestStopSuggestion(
    val placeName: String,
    val distanceMeters: Int?,
    val energyPercent: Int,
    val isEv: Boolean,
    /** 제안을 보내고 다른 차의 답을 기다리는 중 */
    val sent: Boolean = false,
)

/** 목적지 검색 패널 상태 */
data class SearchUiState(
    val open: Boolean = false,
    val query: String = "",
    val error: String? = null,
    val listening: Boolean = false,
    /** 음성 인식 중 실시간 글자 */
    val partial: String = "",
)
