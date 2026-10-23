package com.kero.passingby.content

import androidx.annotation.StringRes
import com.kero.passingby.experience.RouteCaption
import kotlin.math.abs
import kotlin.math.floor

/** 실제 노선 위 대표 지점. km는 코스 출발점부터의 대략적인 철도 영업거리다. */
data class RoutePlace(@param:StringRes val name: Int, val km: Double)

/**
 * 장면의 역 사이 거리 비율을 실제 노선의 거리로 옮긴다. GPS나 별도 타이머를 쓰지 않으므로
 * 세션 길이·일시정지·재실행과 무관하게 풍경과 같은 위치를 가리킨다.
 * 지명은 대표 지점 가까이에서만 표시하며, 행정구역 경계를 재현하지는 않는다.
 */
class RouteGuide(val stops: List<RoutePlace>, val via: List<RoutePlace>) {
    init {
        require(stops.size >= 2 && stops.first().km == 0.0)
        require(stops.all { it.km.isFinite() })
        require(stops.zipWithNext().all { (a, b) -> a.km < b.km })
        require(via.all { it.km.isFinite() && it.km > 0 && it.km < stops.last().km })
        require(via.zipWithNext().all { (a, b) -> a.km < b.km })
        require(via.none { place -> stops.any { it.km == place.km } })
    }

    private data class Window(val place: RoutePlace, val stop: Int?, val radius: Double)

    private val windows = (stops.mapIndexed { i, place -> place to i } + via.map { it to null })
        .sortedBy { it.first.km }
        .let { points ->
            points.mapIndexed { i, (place, stop) ->
                val before = if (i > 0) place.km - points[i - 1].first.km else Double.POSITIVE_INFINITY
                val after = if (i < points.lastIndex) points[i + 1].first.km - place.km else Double.POSITIVE_INFINITY
                // 가까운 지점의 안내가 겹치지 않게 하고, 먼 도시 이름이 오래 남지 않게 한다.
                Window(place, stop, minOf(6.0, before * .4, after * .4))
            }
        }

    /** position은 지금 진행 방향의 구간 단위(0=출발, 1=첫 정차역). */
    fun kmAt(position: Double, direction: Int): Double {
        require(position.isFinite() && (direction == -1 || direction == 1))
        val segments = stops.lastIndex
        val p = position.coerceIn(0.0, segments.toDouble())
        val canonical = if (direction < 0) segments - p else p
        val segment = floor(canonical).toInt().coerceAtMost(segments - 1)
        val from = stops[segment].km
        return from + (stops[segment + 1].km - from) * (canonical - segment)
    }

    /** stoppedAt도 현재 진행 방향의 역 번호. 정차 중에는 통과 안내보다 정차 안내를 우선한다. */
    fun captionAt(position: Double, direction: Int, stoppedAt: Int = -1): RouteCaption? {
        require(direction == -1 || direction == 1)
        val segments = stops.lastIndex
        if (stoppedAt in stops.indices) {
            val index = if (direction < 0) segments - stoppedAt else stoppedAt
            return RouteCaption(stops[index].name, RouteCaption.Kind.STOPPED)
        }
        if (!position.isFinite() || position < 0 || position >= segments) return null
        val km = kmAt(position, direction)
        val nearest = windows.minBy { abs(it.place.km - km) }
        if (abs(nearest.place.km - km) > nearest.radius) return null
        val segment = floor(position).toInt()
        val from = if (direction < 0) segments - segment else segment
        val kind = when (nearest.stop) {
            null -> RouteCaption.Kind.PASSING
            from -> RouteCaption.Kind.DEPARTING
            else -> RouteCaption.Kind.APPROACHING
        }
        return RouteCaption(nearest.place.name, kind)
    }
}
