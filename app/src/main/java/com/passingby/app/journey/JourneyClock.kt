package com.passingby.app.journey

import android.os.SystemClock

/**
 * 여정 시계(초). [SystemClock.elapsedRealtime] 기준이라 앱이 백그라운드에 있어도 흐르고,
 * 프레임 간격을 누적하지 않으므로 오차가 쌓이지 않는다.
 * [scale]은 개발용 빨리 감기다. 바꿀 때마다 기준점을 다시 잡아 시각이 튀지 않는다.
 */
class JourneyClock(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private var anchorReal = now()
    private var anchorSec = 0.0

    var scale = 1.0
        private set

    fun seconds(): Double = anchorSec + (now() - anchorReal) / 1000.0 * scale

    fun setScale(s: Double) {
        anchorSec = seconds()
        anchorReal = now()
        scale = s
    }

    fun seek(sec: Double) {
        anchorSec = sec
        anchorReal = now()
    }
}
