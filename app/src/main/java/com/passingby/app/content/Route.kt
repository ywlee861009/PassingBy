package com.passingby.app.content

import com.passingby.app.journey.JourneyPlan

/**
 * 노선. 역 사이 한 칸이 한 구간이고, 세션 하나가 구간 하나다(명세 7.2).
 * 실제 상표명은 쓰지 않는다("고속열차" 같은 일반 명칭).
 */
class Route(
    val id: String,
    val vehicle: String,
    val stations: List<String>,
    /** 시골 구간 순항 속도(장면 단위/초). */
    val cruise: Double,
) {
    /** [from]역에서 [direction](+1 하행, −1 상행) 쪽으로 가는 구간. 종점이면 방향을 뒤집는다. */
    fun legFrom(from: Int, direction: Int): Leg {
        val dir = when {
            from + direction in stations.indices -> direction
            else -> -direction
        }
        return Leg(this, from, from + dir)
    }

    companion object {
        val SEOUL_BUSAN = Route("seoul-busan", "고속열차", listOf("서울", "대전", "동대구", "부산"), JourneyPlan.FAST_TRAIN)

        val ALL = listOf(SEOUL_BUSAN)

        fun byId(id: String?): Route = ALL.firstOrNull { it.id == id } ?: SEOUL_BUSAN
    }
}

/** 노선의 한 구간. */
class Leg(val route: Route, val from: Int, val to: Int) {
    val direction: Int get() = if (to > from) 1 else -1
    val fromName: String get() = route.stations[from]
    val toName: String get() = route.stations[to]
    val title: String get() = "$fromName → $toName"

    /** 진행 방향 기준 몇 번째 구간인지. "첫 구간", "마지막 구간"처럼 쓴다. */
    val ordinal: String
        get() {
            val count = route.stations.size - 1
            val index = if (direction > 0) from else route.stations.size - 1 - from
            return when (index) {
                0 -> "첫 구간"
                count - 1 -> "마지막 구간"
                else -> "${index + 1}번째 구간"
            }
        }
}
