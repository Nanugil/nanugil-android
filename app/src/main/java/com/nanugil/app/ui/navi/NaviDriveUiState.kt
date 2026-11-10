package com.nanugil.app.ui.navi

import com.nanugil.app.data.fare.TripCost
import com.nanugil.app.data.group.CarState
import com.nanugil.app.data.mock.MockGroupDriveData
import com.nanugil.app.data.navi.NaviState
import com.nanugil.app.data.search.PlaceResult
import com.nanugil.app.data.vehicle.VehicleStatus

/**
 * 나누길 내비 화면이 그리는 모든 값 (한 번에 하나의 불변 스냅샷).
 * ViewModel이 길안내·차량·그룹·정산·테마를 합쳐 만들고, 화면은 이것만 보고 그린다.
 */
data class NaviDriveUiState(
    val navi: NaviState = NaviState(connected = true),
    val vehicle: VehicleStatus = VehicleStatus(),
    /** 같은 그룹 차량 (내 차 포함) */
    val groupCars: List<CarState> = emptyList(),
    /** 다른 차의 합류(주유소 경유) 제안. 없으면 null */
    val proposal: MeetupProposal? = null,
    /** 출발부터 지금까지 쓴 유류비·통행료와 1인 금액 */
    val cost: TripCost = TripCost(0.0, 0, 0, MockGroupDriveData.SHARE_MEMBERS),
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val search: SearchUiState = SearchUiState(),
) {
    val carsDriving: Int get() = groupCars.size
}

/** 목적지 검색 패널 상태 */
data class SearchUiState(
    val open: Boolean = false,
    val query: String = "",
    val results: List<PlaceResult> = emptyList(),
    val searching: Boolean = false,
    /** 한 번이라도 검색했는지 (결과 없음 안내용) */
    val searched: Boolean = false,
    val error: String? = null,
    val listening: Boolean = false,
    /** 음성 인식 중 실시간 글자 */
    val partial: String = "",
)
