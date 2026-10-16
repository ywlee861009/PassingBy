package com.passingby.app.scene

import kotlin.math.abs
import kotlin.math.max
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

    /** 하늘을 실제 시각으로 정한다. [fixedTime]이 있으면 그 시간대로 고정한다. */
    val skyClock = SkyClock()
    var fixedTime: TimeOfDay? = null
        private set
    /** 지금 하늘에 가장 가까운 시간대. 타일 방식의 캐시 키로 쓴다. */
    val timeOfDay: TimeOfDay
        get() = fixedTime ?: skyClock.nearest
    private val from = Palette()
    /** 전환이 끝났을 때의 목표 팔레트(비 효과 제외). */
    val target = Palette()
    private val current = Palette()
    /** 이번 프레임에 실제로 그릴 팔레트. */
    val effective = Palette()
    private var transition = 1f
    /** 하늘 시각(epoch ms). 여정 시계가 매 프레임 정한다. */
    private var skyMillis = System.currentTimeMillis()
    private var skyComputedAt = skyMillis

    var weather = Weather.CLEAR
    /** 실제 비·눈 세기(0~1). 날씨를 바꾸면 몇 초에 걸쳐 따라간다. */
    var rainAmount = 0f
        private set
    var snowAmount = 0f
        private set
    /** 땅에 쌓인 눈(0~1). 눈이 계속 와야 수십 초에 걸쳐 쌓이고, 그치면 천천히 녹는다. */
    var snowCover = 0f
        private set
    /** 유리의 젖음·눈 붙음(0~1). 비가 그쳐도 한동안 남아 있다. */
    var glassWet = 0f
        private set
    var glassSnow = 0f
        private set

    /** 열차 속도(장면 단위/초). 여정이 정한다. */
    var speed = 0.0
        private set

    /**
     * 누적 이동 거리(장면 단위). 여정이 경과 시간으로 계산해 넣는다.
     * Float로 1시간 이상 누적하면 정밀도가 떨어져 풍경이 떨리므로 Double로 둔다.
     */
    var distanceU = 0.0
        private set
    /** 화면을 켠 뒤 흐른 시간. 별 반짝임·창틀 이동 같은 장식 애니메이션에만 쓴다. */
    var elapsedSec = 0.0
        private set
    var lastDt = 0f
        private set

    var showBoxes = false
    /** 위치별 도시화 정도. 여정이 정하고, 개발용으로 시골·도시 고정도 넣을 수 있다. */
    var urban: UrbanField = SceneKind.COUNTRY.field
    var windowFrame = true

    init {
        skyClock.paletteAt(skyMillis, target)
        from.copyFrom(target)
        current.copyFrom(target)
        effective.copyFrom(target)
    }

    /** 시간대를 고정한다. null이면 실제 시각을 따른다. 어느 쪽이든 1.4초에 걸쳐 바뀐다. */
    fun setTimeOfDay(t: TimeOfDay?) {
        if (t == fixedTime) return
        fixedTime = t
        from.copyFrom(current)
        if (t != null) {
            target.copyFrom(Palette.of(t))
        } else {
            skyClock.paletteAt(skyMillis, target)
            skyComputedAt = skyMillis
        }
        transition = 0f
    }

    fun setTravel(distanceU: Double, speed: Double) {
        this.distanceU = distanceU
        this.speed = speed
    }

    /**
     * @param dt 지난 프레임 이후 흐른 시간(초). 날씨·장식 애니메이션에 쓴다.
     * @param skyMillis 하늘을 계산할 시각(epoch ms).
     */
    fun update(dt: Float, skyMillis: Long) {
        lastDt = dt
        elapsedSec += dt
        this.skyMillis = skyMillis
        // 실제 시각의 하늘은 1초에 한 번만 다시 계산한다. 색이 바뀔 때마다 그라디언트 셰이더를 새로 만들기 때문이다.
        if (fixedTime == null && abs(skyMillis - skyComputedAt) >= SKY_REFRESH_MS) {
            skyClock.paletteAt(skyMillis, target)
            skyComputedAt = skyMillis
        }
        transition = min(1f, transition + dt / 1.4f)
        val e = transition * transition * (3 - 2 * transition)
        lerpInto(current, from, target, e)
        rainAmount = approach(rainAmount, weather.rain, dt * .6f)
        snowAmount = approach(snowAmount, weather.snow, dt * .5f)
        val coverCap = if (snowAmount > .05f) .35f + .65f * snowAmount else 0f
        snowCover = if (snowCover < coverCap) min(coverCap, snowCover + dt * snowAmount / 40f) else max(coverCap, snowCover - dt / 40f)
        val wetTarget = min(1f, rainAmount * 1.3f)
        glassWet = approach(glassWet, wetTarget, dt * if (wetTarget > glassWet) .25f else .04f)
        glassSnow = approach(glassSnow, snowAmount, dt * if (snowAmount > glassSnow) .04f else .03f)
        wetInto(effective, current, rainAmount)
        snowInto(effective, snowAmount, snowCover)
    }

    /** 개발용: 시간대 전환 애니메이션을 건너뛴다. */
    fun snapTimeOfDay() {
        transition = 1f
    }

    /** 개발용: 서서히 쌓이는 효과를 기다리지 않고 현재 날씨의 최종 상태로 바로 맞춘다. */
    fun snapWeather() {
        rainAmount = weather.rain
        snowAmount = weather.snow
        snowCover = if (weather.snow > 0f) .35f + .65f * weather.snow else 0f
        glassWet = min(1f, weather.rain * 1.3f)
        glassSnow = weather.snow
    }

    private fun approach(v: Float, target: Float, step: Float) =
        if (v < target) min(target, v + step) else max(target, v - step)

    private companion object {
        const val SKY_REFRESH_MS = 1000L
    }
}
