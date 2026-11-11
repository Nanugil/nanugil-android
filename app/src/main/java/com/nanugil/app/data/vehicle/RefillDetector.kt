package com.nanugil.app.data.vehicle

/**
 * 운행 중 잔량 변화로 주유·충전한 양을 센다. 연료(mL)와 배터리(Wh)에 같은 규칙을 쓴다.
 *
 * 최저점에서 [threshold] 이상 오르면 주유·충전으로 보고, 오른 만큼을 [added]에 더한다.
 * 주유기는 잔량이 1%씩 올라가므로 기준을 넘은 뒤에는 끝까지 이어서 센다.
 * 다시 내려가기 시작하면 그 주유는 끝난 것으로 보고 최저점을 새로 잡는다.
 * 기준보다 작은 오름(센서 흔들림, 회생 제동)은 세지 않는다.
 *
 * 저장하기 쉽게 값만 가진 불변 객체로 두고, [update]가 새 상태를 돌려준다.
 *
 * @property start 기록을 시작한 뒤 처음 받은 잔량. 정산의 출발 값.
 * @property latest 마지막으로 받은 잔량. 정산의 도착 값.
 * @property added 지금까지 센 주유·충전량 합계.
 * @property lowest 이번 주유를 재는 기준이 되는 최저점.
 * @property counted 이번 주유에서 이미 [added]에 넣은 양.
 */
data class RefillDetector(
    val start: Long? = null,
    val latest: Long? = null,
    val added: Long = 0,
    val lowest: Long? = null,
    val counted: Long = 0,
) {
    fun update(level: Long, threshold: Long): RefillDetector {
        var low = lowest ?: return RefillDetector(start = level, latest = level, lowest = level)
        var done = counted
        if (done > 0 && level < low + done) {
            // 주유가 끝나고 다시 쓰기 시작했다
            low = level
            done = 0
        } else if (level < low) {
            low = level
        }
        val rise = level - low
        var total = added
        if (rise >= threshold && rise > done) {
            total += rise - done
            done = rise
        }
        return copy(latest = level, added = total, lowest = low, counted = done)
    }
}
