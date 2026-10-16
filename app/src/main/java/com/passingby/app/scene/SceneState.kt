package com.passingby.app.scene

import kotlin.math.min

enum class RenderMode(val label: String) {
    /** 매 프레임 지형 곡선을 다시 그린다. 메모리 최소, 프레임 비용 최대. */
    DIRECT("직접"),
    /** 지형을 조각 비트맵으로 한 번만 굽고 옮겨 그린다. 권장 방식. */
    BAKED("조각"),
    /** 원래 명세의 레이어 이미지 반복을 흉내 낸 비교용. 메모리 최대. */
    TILES("타일"),
}

/** 렌더러가 읽는 장면 상태. UI 스레드에서만 바뀐다. */
class SceneState {
    @Volatile var mode = RenderMode.BAKED

    var timeOfDay = TimeOfDay.DUSK
        private set
    private val from = Palette()
    /** 전환이 끝났을 때의 목표 팔레트(비 효과 제외). */
    val target = Palette()
    private val current = Palette()
    /** 이번 프레임에 실제로 그릴 팔레트. */
    val effective = Palette()
    private var transition = 1f

    var raining = false
    var rainAmount = 0f
        private set

    var speedTarget = FAST_SPEED
    var speed = FAST_SPEED
        private set

    /**
     * 누적 이동 거리(장면 단위). Float로 1시간 누적하면 정밀도가 떨어져 풍경이 떨리므로 Double로 둔다.
     * 시제품이라 프레임 간격을 누적하지만, 실제 세션에서는 경과 시간 × 속도로 계산해야 타이머와 어긋나지 않는다.
     */
    var distanceU = 0.0
        private set
    var elapsedSec = 0.0
        private set
    var lastDt = 0f
        private set

    var showBoxes = false
    var windowFrame = true

    init {
        val p = Palette.of(timeOfDay)
        from.copyFrom(p)
        target.copyFrom(p)
        current.copyFrom(p)
        effective.copyFrom(p)
    }

    fun setTimeOfDay(t: TimeOfDay) {
        if (t == timeOfDay) return
        timeOfDay = t
        from.copyFrom(current)
        target.copyFrom(Palette.of(t))
        transition = 0f
    }

    fun update(dt: Float) {
        lastDt = dt
        elapsedSec += dt
        speed += (speedTarget - speed) * min(1.0, dt * 1.2)
        distanceU += speed * dt
        transition = min(1f, transition + dt / 1.4f)
        val e = transition * transition * (3 - 2 * transition)
        lerpInto(current, from, target, e)
        rainAmount += ((if (raining) 1f else 0f) - rainAmount) * min(1f, dt * 1.5f)
        wetInto(effective, current, rainAmount)
    }

    companion object {
        const val FAST_SPEED = 300.0
        const val SLOW_SPEED = 110.0
    }
}
