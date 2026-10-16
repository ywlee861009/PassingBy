package com.passingby.app.journey

import com.passingby.app.scene.UrbanField
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 한 구간(출발역 → 도착역)의 여정. 경과 시간만으로 위치·속도·도시화 정도가 정해진다.
 *
 * 시간 구성(25분 기준): 출발 도시 3분 → 근교 2분 → 시골 → 근교 2분 → 도착 도시 3분.
 * 세션이 길어지면 가운데 시골만 늘어난다. 출발하며 가속하고, 도착 전 감속해 [durationSec]에 정확히 선다.
 *
 * 거리는 프레임마다 누적하지 않고 속도 곡선을 적분해 구한다. 그래서 백그라운드에 다녀오거나
 * 화면을 돌려도 같은 시각이면 같은 위치다.
 *
 * @param cruise 시골 구간의 순항 속도(장면 단위/초).
 */
class JourneyPlan(val durationSec: Double, val cruise: Double) : UrbanField {
    private val accel = min(ACCEL_SEC, durationSec * .1)
    private val decel = min(DECEL_SEC, durationSec * .1)
    private val cityOut = min(CITY_SEC, durationSec * .12)
    private val cityIn = min(CITY_SEC, durationSec * .12)
    private val ramp = min(SUBURB_SEC, durationSec * .08)

    /** 정수 초마다의 누적 거리. 마지막 칸은 [durationSec] 시점. */
    private val distAtSec: DoubleArray

    /** 세계 위치 [URBAN_STEP] 간격의 도시화 정도. 렌더러가 사물마다 읽으므로 미리 표로 만든다. */
    private val urbanByDist: FloatArray

    val totalDistance: Double

    init {
        val n = ceil(durationSec).toInt()
        distAtSec = DoubleArray(n + 1)
        for (i in 1..n) distAtSec[i] = distAtSec[i - 1] + integrate(i - 1.0, min(i.toDouble(), durationSec), 8)
        totalDistance = distAtSec[n]

        // 거리 → 시각을 거꾸로 찾아 도시화 정도를 거리 기준 표로 옮긴다.
        val m = ceil(totalDistance / URBAN_STEP).toInt() + 2
        urbanByDist = FloatArray(m)
        var sec = 0
        for (j in 0 until m) {
            val x = j * URBAN_STEP
            while (sec < n - 1 && distAtSec[sec + 1] < x) sec++
            val d0 = distAtSec[sec]
            val d1 = distAtSec[sec + 1]
            val f = if (d1 > d0) ((x - d0) / (d1 - d0)).coerceIn(0.0, 1.0) else 0.0
            val t = sec + f * (min(sec + 1.0, durationSec) - sec)
            urbanByDist[j] = urbanAtTime(t).toFloat()
        }
    }

    /** [t]초 시점의 누적 이동 거리(장면 단위). */
    fun distanceAt(t: Double): Double {
        if (t <= 0) return 0.0
        if (t >= durationSec) return totalDistance
        val i = floor(t).toInt()
        return distAtSec[i] + integrate(i.toDouble(), t, 4)
    }

    /** [t]초 시점의 속도(장면 단위/초). 출발 전과 도착 후에는 0. */
    fun speedAt(t: Double): Double {
        if (t <= 0 || t >= durationSec) return 0.0
        val envelope = smooth(t / accel) * smooth((durationSec - t) / decel)
        return cruise * (1 - CITY_SLOW * urbanAtTime(t)) * envelope
    }

    /** [t]초 시점에 열차가 있는 곳의 도시화 정도. */
    fun urbanAtTime(t: Double): Double {
        val leaving = 1 - smooth((t - cityOut) / ramp)
        val arriving = smooth((t - (durationSec - cityIn - ramp)) / ramp)
        return max(leaving, arriving)
    }

    /** 세계 위치 [x]의 도시화 정도. 구간 밖(출발역 뒤, 도착역 너머)은 도시다. */
    override fun at(x: Double): Float {
        if (x <= 0) return urbanByDist[0]
        val f = x / URBAN_STEP
        val i = f.toInt()
        if (i >= urbanByDist.size - 1) return urbanByDist[urbanByDist.size - 1]
        val a = urbanByDist[i]
        return a + (urbanByDist[i + 1] - a) * (f - i).toFloat()
    }

    /** 심프슨 적분. [n]은 짝수. */
    private fun integrate(a: Double, b: Double, n: Int): Double {
        if (b <= a) return 0.0
        val h = (b - a) / n
        var sum = speedAt(a) + speedAt(b)
        for (k in 1 until n) sum += speedAt(a + k * h) * if (k % 2 == 1) 4 else 2
        return sum * h / 3
    }

    companion object {
        const val FAST_TRAIN = 300.0
        const val SLOW_TRAIN = 110.0

        private const val ACCEL_SEC = 50.0
        private const val DECEL_SEC = 60.0
        private const val CITY_SEC = 180.0
        private const val SUBURB_SEC = 120.0
        /** 도시 안에서 줄이는 속도 비율. */
        private const val CITY_SLOW = .3
        private const val URBAN_STEP = 40.0

        private fun smooth(x: Double): Double {
            val c = x.coerceIn(0.0, 1.0)
            return c * c * (3 - 2 * c)
        }
    }
}
