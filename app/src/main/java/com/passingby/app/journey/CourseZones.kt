package com.passingby.app.journey

import com.passingby.app.content.Course
import com.passingby.app.content.Feature
import com.passingby.app.scene.FieldStyle
import com.passingby.app.scene.Landmark
import com.passingby.app.scene.Zones
import kotlin.math.max
import kotlin.math.min

/**
 * 코스 구역 정의를 이번 세션의 세계 위치로 옮긴다. 정방향 기준 "몇 번 구간의 몇 %"를,
 * 계획의 구간 길이와 앞선 세션들의 누적 거리([base])로 환산한다. 역방향이면 위치를 뒤집는다.
 */
class CourseZones(plan: CoursePlan, course: Course, direction: Int, private val base: Double) : Zones {
    private val segs = plan.segments
    private val segDist = plan.segDist
    private val reverse = direction < 0
    private val ramp = RAMP_SEG * segDist

    private val sea = ranges(course, Feature.Kind.SEA)
    private val rivers = ranges(course, Feature.Kind.RIVER)
    private val fieldList = course.features.filter { it.kind == Feature.Kind.FIELD }
    private val fieldRanges = DoubleArray(fieldList.size * 2).also { fill(it, fieldList) }

    override val tunnels = ranges(course, Feature.Kind.TUNNEL)
    override val bridges = ranges(course, Feature.Kind.BRIDGE)
    override val landmarks = course.landmarks.map { Landmark(it.kind, worldX(it.seg + it.at.toDouble())) }
    override val water = sea.isNotEmpty() || rivers.isNotEmpty() || bridges.isNotEmpty()
    override val fields = fieldList.isNotEmpty()

    override fun sea(x: Double): Float = weight(sea, x)

    override fun river(x: Double): Float = weight(rivers, x)

    override fun fieldStyle(x: Double): FieldStyle {
        var best = FieldStyle.NONE
        var bestW = 0f
        for (i in fieldList.indices) {
            val w = trapezoid(x, fieldRanges[i * 2], fieldRanges[i * 2 + 1])
            if (w > bestW) {
                bestW = w
                best = fieldList[i].style
            }
        }
        return best
    }

    override fun field(x: Double): Float = weight(fieldRanges, x)

    /** 정방향 위치 [p](구간 단위, 0 = 출발역) → 세계 위치. */
    private fun worldX(p: Double): Double = base + (if (reverse) segs - p else p) * segDist

    private fun ranges(course: Course, kind: Feature.Kind): DoubleArray {
        val list = course.features.filter { it.kind == kind }
        return DoubleArray(list.size * 2).also { fill(it, list) }
    }

    private fun fill(out: DoubleArray, list: List<Feature>) {
        for (i in list.indices) {
            val f = list[i]
            val a = worldX(f.seg + f.from.toDouble())
            val b = worldX(f.seg + f.to.toDouble())
            out[i * 2] = min(a, b)
            out[i * 2 + 1] = max(a, b)
        }
    }

    private fun weight(r: DoubleArray, x: Double): Float {
        var w = 0f
        var i = 0
        while (i < r.size) {
            w = max(w, trapezoid(x, r[i], r[i + 1]))
            i += 2
        }
        return w
    }

    /** 범위 안은 1, 양 끝에서 [ramp]에 걸쳐 0으로 부드럽게 줄어든다. */
    private fun trapezoid(x: Double, a: Double, b: Double): Float {
        if (x < a - ramp || x > b + ramp) return 0f
        val t = when {
            x < a -> 1 - (a - x) / ramp
            x > b -> 1 - (x - b) / ramp
            else -> 1.0
        }
        return (t * t * (3 - 2 * t)).toFloat()
    }

    private companion object {
        /** 구역 가장자리가 흐려지는 폭(구간 길이 대비). */
        const val RAMP_SEG = .03
    }
}
