package com.passingby.app.journey

import com.passingby.app.scene.UrbanField
import kotlin.math.floor
import kotlin.math.min

/**
 * 세션 하나 = 코스 하나의 여정(명세 7.2). 경과 시간만으로 위치·속도·정차 여부가 정해진다.
 *
 * 시간 구성: [구간 0][정차 1][구간 1][정차 2] … [구간 N−2]. 구간은 모두 같은 길이로, 역을 떠나며 가속하고
 * 다음 역 앞에서 감속해 선다. 중간 역마다 [stopSec]초 정차하고, 종착역에는 [durationSec]에 정확히 선다.
 * 속도 곡선이 부드러운 계단(smoothstep)이라 적분값을 식으로 바로 구한다(표·누적 없음).
 *
 * 도시화 정도는 구간 안 거리 비율로 정한다. 역 가까이는 도시, 가운데는 시골이다.
 *
 * @param stations 역 수(출발·종착 포함, 2 이상).
 * @param cruise 순항 속도(장면 단위/초).
 */
class CoursePlan(
    val durationSec: Double,
    val stations: Int,
    val cruise: Double,
    val stopSec: Double = STOP_SEC,
) : UrbanField {
    val segments = stations - 1
    private val stops = segments - 1
    /** 구간 하나를 달리는 시간(정차 제외). */
    val segRun = (durationSec - stops * stopSec) / segments
    private val period = segRun + stopSec
    private val accel = min(ACCEL_SEC, segRun * .2)
    private val decel = min(DECEL_SEC, segRun * .2)
    /** 구간 하나의 거리. 가속·감속 구간의 평균 속도는 순항의 절반이다. */
    val segDist = cruise * (segRun - accel / 2 - decel / 2)
    val totalDistance = segDist * segments

    fun distanceAt(t: Double): Double {
        if (t <= 0) return 0.0
        if (t >= durationSec) return totalDistance
        val k = segmentAt(t)
        val tau = t - k * period
        return k * segDist + if (tau >= segRun) segDist else cruise * runIntegral(tau)
    }

    /** 정차 중이거나 출발 전·도착 후에는 0. */
    fun speedAt(t: Double): Double {
        if (t <= 0 || t >= durationSec) return 0.0
        val tau = t - segmentAt(t) * period
        if (tau >= segRun) return 0.0
        return cruise * smooth(tau / accel) * smooth((segRun - tau) / decel)
    }

    /** [t]에 열차가 있는(또는 향하는) 구간 번호. */
    fun segmentAt(t: Double): Int = floor(t / period).toInt().coerceIn(0, segments - 1)

    /** 중간 역에 서 있으면 그 역 번호, 아니면 −1. */
    fun stoppedAt(t: Double): Int {
        if (t <= 0 || t >= durationSec) return -1
        val k = segmentAt(t)
        return if (t - k * period >= segRun) k + 1 else -1
    }

    /** [i]번 역(1 이상)에 도착하는 시각. */
    fun arrivalAt(i: Int): Double = (i - 1) * period + segRun

    /** [i]번 역(0 이상, 종착 제외)을 떠나는 시각. */
    fun departureAt(i: Int): Double = i * period

    /** [i]번 역의 세계 위치(이 계획의 시작이 0). */
    fun stationDistance(i: Int): Double = i * segDist

    /** 세계 위치 [x]의 도시화 정도. 역에서 [CITY_F]까지는 도시, 이어서 [RAMP_F]만큼 근교. 구간 밖은 도시다. */
    override fun at(x: Double): Float {
        if (x <= 0 || x >= totalDistance) return 1f
        val s = x / segDist
        val f = s - floor(s)
        val edge = min(f, 1 - f)
        return (1 - smooth((edge - CITY_F) / RAMP_F)).toFloat()
    }

    /** 구간을 τ초 달렸을 때 간 거리 ÷ 순항 속도. 가속 → 순항 → 감속 순서를 식으로 적분한다. */
    private fun runIntegral(tau: Double): Double {
        var d = accel * ramp(min(tau, accel) / accel)
        val cruiseEnd = segRun - decel
        if (tau > accel) d += min(tau, cruiseEnd) - accel
        if (tau > cruiseEnd) d += decel * (ramp(1.0) - ramp(1 - (tau - cruiseEnd) / decel))
        return d
    }

    companion object {
        const val STOP_SEC = 20.0
        private const val ACCEL_SEC = 50.0
        private const val DECEL_SEC = 60.0
        private const val CITY_F = .1
        private const val RAMP_F = .08

        private fun smooth(x: Double): Double {
            val c = x.coerceIn(0.0, 1.0)
            return c * c * (3 - 2 * c)
        }

        /** smooth(x)를 0부터 x까지 적분한 값: x³ − x⁴/2. */
        private fun ramp(x: Double): Double {
            val c = x.coerceIn(0.0, 1.0)
            return c * c * c - c * c * c * c / 2
        }
    }
}
