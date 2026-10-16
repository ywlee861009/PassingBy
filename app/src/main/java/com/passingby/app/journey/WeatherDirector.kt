package com.passingby.app.journey

import kotlin.math.max

/**
 * 세션마다 무작위로 정하는 날씨(명세 7.4). 실제 날씨와는 연동하지 않는다.
 *
 * 세션을 시작할 때 성격(맑음 위주·비·눈)을 하나 고르고, 세기는 여정 시각의 부드러운 노이즈로 몇 분에 걸쳐 오르내린다.
 * 같은 세션이면 같은 시각에 같은 날씨라 회전·복원 후에도 이어진다.
 */
class WeatherDirector(private val seed: Int, snowy: Boolean) {
    private val character: Int

    init {
        val r = hash(seed, 1)
        character = when {
            snowy && r < .14 -> SNOW
            r < .52 -> RAIN
            else -> CLEAR
        }
    }

    /** [t]초 시점의 (비, 눈) 목표 세기(0~1). */
    fun rainAt(t: Double): Float = if (character == RAIN) wave(t, 0.0) else if (character == CLEAR) .45f * shower(t) else 0f

    fun snowAt(t: Double): Float = if (character == SNOW) wave(t, 37.0) else 0f

    /** 비·눈이 오다 그치다를 몇 분 단위로 되풀이한다. 절반쯤은 그친 상태. */
    private fun wave(t: Double, phase: Double): Float {
        val n = noise(t / PERIOD + phase)
        return (max(0.0, n - .42) / .58 * 1.25).coerceAtMost(1.0).toFloat()
    }

    /** 맑은 세션에도 드물게 지나가는 부슬비. */
    private fun shower(t: Double): Float {
        val n = noise(t / (PERIOD * 1.7) + 91.0)
        return (max(0.0, n - .78) / .22).coerceAtMost(1.0).toFloat()
    }

    private fun noise(x: Double): Double {
        val i = kotlin.math.floor(x)
        val f = x - i
        val u = f * f * (3 - 2 * f)
        val a = hash(seed + 7, i.toInt())
        val b = hash(seed + 7, i.toInt() + 1)
        return a * (1 - u) + b * u
    }

    private fun hash(s: Int, i: Int): Double {
        var x = s * 0x27d4eb2d + i * 0x165667b1
        x = x xor (x ushr 15)
        x *= 0x2c1b3c6d
        x = x xor (x ushr 12)
        x *= 0x297a2d39
        x = x xor (x ushr 15)
        return (x.toLong() and 0xffffffffL) / 4294967296.0
    }

    private companion object {
        const val CLEAR = 0
        const val RAIN = 1
        const val SNOW = 2
        /** 노이즈 한 칸(초). 날씨가 바뀌는 대략의 간격. */
        const val PERIOD = 420.0
    }
}
