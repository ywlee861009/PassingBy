package com.kero.passingby.journey

import android.os.SystemClock
import kotlin.math.min

/**
 * 여정 시계(초). [SystemClock.elapsedRealtime] 기준이라 앱이 백그라운드에 있어도 흐르고,
 * 프레임 간격을 누적하지 않으므로 오차가 쌓이지 않는다.
 *
 * 흐르는 빠르기([rate], 0~1)를 몇 초에 걸쳐 바꿀 수 있다. 일시정지는 빠르기를 0으로 낮추는 것이라
 * 열차가 그 자리에서 뚝 서지 않고 서서히 선다. 빠르기 변화는 실제 시각의 함수(부드러운 계단)라
 * 적분값을 바로 계산할 수 있고, 그래서 여전히 누적 없이 "지금 몇 초인지"를 구한다.
 *
 * [scale]은 개발용 빨리 감기다.
 */
class JourneyClock(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private var anchorReal = now()
    private var anchorSec = 0.0
    private var rateFrom = 0.0
    private var rateTo = 0.0
    private var rampSec = 0.0

    var scale = 1.0
        private set

    /** 목표 빠르기. 0이면 서 있거나 서는 중이다. */
    val targetRate: Double
        get() = rateTo

    fun seconds(): Double = anchorSec + scale * integral(elapsed())

    /** 지금 빠르기(0~1). 화면에 보이는 속도는 계획 속도 × 빠르기다. */
    fun rate(): Double {
        val tau = elapsed()
        return if (rampSec <= 0 || tau >= rampSec) rateTo else rateFrom + (rateTo - rateFrom) * smooth(tau / rampSec)
    }

    /** 빠르기를 [rampSec]초에 걸쳐 [target]으로 바꾼다. */
    fun setRate(target: Double, rampSec: Double) {
        val r = rate()
        reanchor(seconds())
        rateFrom = r
        rateTo = target
        this.rampSec = rampSec
    }

    /** [sec]초로 옮기고 빠르기를 [rate]로 바로 맞춘다. */
    fun seek(sec: Double, rate: Double = rateTo) {
        reanchor(sec)
        rateFrom = rate
        rateTo = rate
        rampSec = 0.0
    }

    fun setScale(s: Double) {
        val t = seconds()
        val r = rate()
        reanchor(t)
        rateFrom = r
        rateTo = r
        rampSec = 0.0
        scale = s
    }

    /** 프로세스가 종료돼도 이어 가도록 상태를 숫자 배열로 저장한다. elapsedRealtime은 재부팅 전까지 이어진다. */
    fun save(): DoubleArray = doubleArrayOf(anchorReal.toDouble(), anchorSec, rateFrom, rateTo, rampSec, scale)

    fun restore(s: DoubleArray) {
        anchorReal = s[0].toLong()
        anchorSec = s[1]
        rateFrom = s[2]
        rateTo = s[3]
        rampSec = s[4]
        scale = s[5]
    }

    private fun elapsed() = (now() - anchorReal) / 1000.0

    private fun reanchor(sec: Double) {
        anchorSec = sec
        anchorReal = now()
    }

    /** 빠르기를 0부터 [tau]초까지 적분한 값(장식 없는 초 단위). */
    private fun integral(tau: Double): Double {
        if (tau <= 0) return 0.0
        if (rampSec <= 0) return rateTo * tau
        val x = min(tau / rampSec, 1.0)
        // 부드러운 계단 s(x) = 3x² − 2x³ 의 적분은 x³ − x⁴/2.
        val ramp = rateFrom * x * rampSec + (rateTo - rateFrom) * rampSec * (x * x * x - x * x * x * x / 2)
        return if (tau <= rampSec) ramp else ramp + rateTo * (tau - rampSec)
    }

    private fun smooth(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }
}
