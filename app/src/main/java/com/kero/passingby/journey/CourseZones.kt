package com.kero.passingby.journey

import com.kero.passingby.content.Course
import com.kero.passingby.content.Feature
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.Landmark
import com.kero.passingby.scene.Scenery
import com.kero.passingby.scene.Zones
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 코스 구역 정의를 이번 세션의 세계 위치로 옮긴다. 정방향 기준 "몇 번 구간의 몇 %"를,
 * 계획의 구간 길이와 앞선 세션들의 누적 거리([base])로 환산한다. 역방향이면 위치를 뒤집는다.
 * 역 둘레 풍경([CityLook])은 역에서 화면 거리로 정해 두므로 세션 길이와 상관없이 역에서 같은 모습이다.
 */
class CourseZones(plan: CoursePlan, private val course: Course, direction: Int, private val base: Double) : Zones {
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
    /** 화면 거리 dx를 그 랜드마크 레이어의 이동 거리로 바꿔 더한다(레이어가 느릴수록 세계 거리는 길다). */
    override val landmarks = course.landmarks.map {
        val shift = it.dx / course.theme.layers[it.kind.layer].speed
        Landmark(it.kind, worldX(it.pos) + if (reverse) -shift else shift)
    }
    private val looks: Array<CityLook> = course.stations.map { it.look }.toTypedArray()
    /** 정방향 역 번호별 세계 위치. */
    private val stationX = DoubleArray(looks.size) { worldX(it.toDouble()) }
    private val speeds = DoubleArray(course.theme.layers.size) { course.theme.layers[it].speed }
    /** 풍경의 좌우는 정방향 기준이라 역방향이면 뒤집는다. */
    private val side = if (reverse) -1f else 1f

    override val water = sea.isNotEmpty() || rivers.isNotEmpty() || bridges.isNotEmpty() || looks.any { it.river != null }
    override val fields = fieldList.isNotEmpty()

    override fun sea(x: Double): Float = weight(sea, x)

    override fun river(x: Double): Float {
        val w = weight(rivers, x)
        val i = nearest(x)
        val r = looks[i].river ?: return w
        // 골짜기 강은 지형이 평평해지는 바닥보다 조금 넓게 채워 강가에 틈이 생기지 않게 한다.
        val o = offset(i, Scenery.MID, x)
        return max(w, 1 - smooth((abs(o - r.center) - r.half - 2f) / 8f))
    }

    override fun look(x: Double): CityLook = looks[nearest(x)]

    override fun shape(layer: Int, x: Double, h: Float): Float {
        val i = nearest(x)
        val lk = looks[i]
        return when (layer) {
            Scenery.FAR_A, Scenery.FAR_B -> {
                if (lk.mountains == 0f) return h
                // 화면 한 폭 남짓 안에서는 다 키우고, 그 밖에서 원래 높이로 돌아간다.
                val o = abs(offset(i, layer, x))
                h * (1 + lk.mountains * (1 - smooth((o - MOUNTAIN_IN) / (MOUNTAIN_OUT - MOUNTAIN_IN))))
            }
            Scenery.MID -> midShape(lk, offset(i, layer, x), h)
            else -> h
        }
    }

    override fun maxLift(layer: Int): Float = when (layer) {
        Scenery.FAR_A, Scenery.FAR_B -> {
            val m = looks.maxOf { it.mountains }
            m * HEIGHT_UNITS * course.theme.layers[layer].amp
        }
        Scenery.MID -> looks.maxOf { lk -> lk.hills.maxOfOrNull { it.height } ?: 0f }
        else -> 0f
    }

    /** 언덕은 꼭대기가 넓고 평평하며, 강이 있으면 강 바닥까지 가파르게 내려간다. 언덕 위에는 원래 지형의 굴곡을 조금 남긴다. */
    private fun midShape(lk: CityLook, o: Float, h: Float): Float {
        if (lk.hills.isEmpty() && lk.river == null) return h
        var hill = 0f
        for (hl in lk.hills) {
            val t = abs(o - hl.center) / hl.half
            if (t < 1f) hill = max(hill, hl.height * smooth((1 - t) / HILL_EDGE))
        }
        var out = if (hill > 0f) max(h, hill + h * .5f) else h
        val r = lk.river
        if (r != null) out *= smooth((abs(o - r.center) - r.half) / RIVER_BANK)
        return out
    }

    /** [i]번 역에 섰을 때 화면 가운데에서 세계 위치 [x]까지의 거리를 [layer] 레이어 화면 단위로. */
    private fun offset(i: Int, layer: Int, x: Double): Float = ((x - stationX[i]) * speeds[layer]).toFloat() * side

    private fun nearest(x: Double): Int {
        var best = 0
        var bd = Double.MAX_VALUE
        for (i in stationX.indices) {
            val d = abs(x - stationX[i])
            if (d < bd) {
                bd = d
                best = i
            }
        }
        return best
    }

    private fun smooth(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return c * c * (3 - 2 * c)
    }

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
        /** 지형 높이 단위 환산: 레이어 높이 폭(amp)은 세로 기준 길이(360단위)에 대한 비율이다. */
        const val HEIGHT_UNITS = 360f
        /** 뒷산을 다 키우는 범위와 원래대로 돌아오는 범위(원경 화면 단위). */
        const val MOUNTAIN_IN = 320f
        const val MOUNTAIN_OUT = 720f
        /** 언덕 가장자리에서 내려가는 폭(반폭 대비). */
        const val HILL_EDGE = .45f
        /** 강가 비탈 폭(중경 단위). */
        const val RIVER_BANK = 12f
    }
}
