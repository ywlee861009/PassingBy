package com.kero.passingby.scene

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

    /** 설정에서 고른 시간대. null이면 세션 진행에 따라 하늘이 돈다([SkyCycle]). */
    var fixedTime: TimeOfDay? = null
        private set
    private var cycleTime = TimeOfDay.NIGHT
    /** 지금 하늘에 가장 가까운 시간대. 타일 방식의 캐시 키로 쓴다. */
    val timeOfDay: TimeOfDay
        get() = fixedTime ?: cycleTime
    private val from = Palette()
    /** 전환이 끝났을 때의 목표 팔레트(비 효과 제외). */
    val target = Palette()
    private val current = Palette()
    private val seasonal = Palette()
    var season = Season.SUMMER
        set(value) {
            if (field == value) return
            field = value
            if (value != Season.WINTER && autoWeather) clearSnow()
        }
    var snowy = true
        set(value) {
            field = value
            if (!value && autoWeather) clearSnow()
        }
    /** 계절 기본 설경과 날씨로 쌓인 눈을 별도로 관리한다. */
    val surfaceSnowCover: Float get() = max(season.baseSnow(snowy), snowCover)

    private fun clearSnow() {
        autoSnow = 0f
        snowAmount = 0f
        snowCover = 0f
        glassSnow = 0f
    }
    /** 이번 프레임에 실제로 그릴 팔레트. */
    val effective = Palette()

    /** Exposes the current indoor surface color without exporting palette interpolation internals. */
    fun paperColor(baseColor: Int): Int =
        mix(baseColor, effective.c[Palette.PANEL], .3f * effective.f[Palette.LIT])

    private var transition = 1f
    private var transitionSec = 1.4f
    /** 세션 진행(0~1). 여정이 매 프레임 정한다. */
    private var skyProgress = 0f
    private var skyComputedAt = -1f

    /** 날씨를 직접 고른 경우의 날씨. [autoWeather]면 무시한다. */
    var weather = Weather.CLEAR
    /** 무작위 날씨(기본). 세기는 여정이 [autoRain]·[autoSnow]로 정한다. */
    var autoWeather = true
    var autoRain = 0f
    var autoSnow = 0f
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

    /**
     * 정차 중 창을 가리며 아래에서 올라오는(가로 화면은 옆에서 들어오는) 벽 패널. 카드는 이 패널 위에 놓인다.
     * [seatEdgePx]는 화면 아래(또는 끝) 가장자리에서 패널 가장자리까지의 거리(px)이며, UI가 매 프레임 정한다.
     * 창 크기를 바꾸면 풍경을 다시 구워야 하므로 창은 그대로 두고 패널로 가린다(명세 5.3).
     */
    var seatFromEnd = false
    var seatEdgePx = 0f

    var showBoxes = false
    /** 위치별 도시화 정도. 여정이 정하고, 개발용으로 시골·도시 고정도 넣을 수 있다. */
    var urban: UrbanField = SceneKind.COUNTRY.field
    /** 코스 테마. 바뀌면 렌더러가 지형 조각을 다시 굽는다. */
    var theme: Theme = Themes.KOREA
    /** 위치마다 달라지는 바다·밭·터널·다리·랜드마크. */
    var zones: Zones = Zones.NONE
    var windowFrame = true

    /** 역 위치(세계 단위)와 이름판 글자. 여정이 계획을 바꿀 때 정한다. 렌더러가 플랫폼과 이름판을 그린다. */
    var stationX = DoubleArray(0)
        private set
    var stationLocal: Array<String> = emptyArray()
        private set
    var stationRoman: Array<String> = emptyArray()
        private set

    fun setStations(x: DoubleArray, local: Array<String>, roman: Array<String>) {
        stationX = x
        stationLocal = local
        stationRoman = roman
    }

    init {
        cycleTime = SkyCycle.paletteAt(0f, target)
        skyComputedAt = 0f
        from.copyFrom(target)
        current.copyFrom(target)
        effective.copyFrom(target)
    }

    /** 시간대를 고정한다. null이면 세션 진행을 따른다. 어느 쪽이든 1.4초에 걸쳐 바뀐다. */
    fun setTimeOfDay(t: TimeOfDay?) {
        if (t == fixedTime) return
        fixedTime = t
        from.copyFrom(current)
        if (t != null) {
            target.copyFrom(Palette.of(t))
        } else {
            cycleTime = SkyCycle.paletteAt(skyProgress, target)
            skyComputedAt = skyProgress
        }
        transition = 0f
        transitionSec = 1.4f
    }

    fun setTravel(distanceU: Double, speed: Double) {
        this.distanceU = distanceU
        this.speed = speed
    }

    /**
     * @param dt 지난 프레임 이후 흐른 시간(초). 날씨·장식 애니메이션에 쓴다.
     * @param progress 세션 진행(0~1). 하늘을 정한다.
     */
    fun update(dt: Float, progress: Float) {
        lastDt = dt
        elapsedSec += dt
        skyProgress = progress
        // 하늘은 진행이 조금씩 바뀔 때만 다시 계산한다. 색이 바뀔 때마다 그라디언트 셰이더를 새로 만들기 때문이다.
        if (fixedTime == null && abs(progress - skyComputedAt) >= SKY_STEP) {
            if (progress < skyComputedAt - .5f) {
                // 새 세션: 밤에서 새벽 직전으로 몇 초에 걸쳐 넘어간다.
                from.copyFrom(current)
                transition = 0f
                transitionSec = 8f
            }
            cycleTime = SkyCycle.paletteAt(progress, target)
            skyComputedAt = progress
        }
        transition = min(1f, transition + dt / transitionSec)
        val e = transition * transition * (3 - 2 * transition)
        lerpInto(current, from, target, e)
        // 무작위 날씨는 목표 자체가 몇 분에 걸쳐 움직이므로, 따라가는 속도도 느리게 둬 갑자기 바뀌지 않게 한다.
        val rainTarget = if (autoWeather) autoRain else weather.rain
        val snowTarget = if (autoWeather) autoSnow else weather.snow
        val follow = if (autoWeather) .08f else 1f
        rainAmount = approach(rainAmount, rainTarget, dt * .6f * follow)
        snowAmount = approach(snowAmount, snowTarget, dt * .5f * follow)
        val coverCap = if (snowAmount > .05f) .35f + .65f * snowAmount else 0f
        snowCover = if (snowCover < coverCap) min(coverCap, snowCover + dt * snowAmount / 40f) else max(coverCap, snowCover - dt / 40f)
        val wetTarget = min(1f, rainAmount * 1.3f)
        glassWet = approach(glassWet, wetTarget, dt * if (wetTarget > glassWet) .25f else .04f)
        glassSnow = approach(glassSnow, snowAmount, dt * if (snowAmount > glassSnow) .04f else .03f)
        seasonInto(seasonal, current, season)
        wetInto(effective, seasonal, rainAmount)
        snowInto(effective, snowAmount, surfaceSnowCover)
    }

    /** 개발용: 시간대 전환 애니메이션을 건너뛴다. */
    fun snapTimeOfDay() {
        transition = 1f
    }

    /** 개발용: 서서히 쌓이는 효과를 기다리지 않고 현재 날씨의 최종 상태로 바로 맞춘다. */
    fun snapWeather() {
        val rain = if (autoWeather) autoRain else weather.rain
        val snow = if (autoWeather) autoSnow else weather.snow
        rainAmount = rain
        snowAmount = snow
        snowCover = if (snow > 0f) .35f + .65f * snow else 0f
        glassWet = min(1f, rain * 1.3f)
        glassSnow = snow
    }

    private fun approach(v: Float, target: Float, step: Float) =
        if (v < target) min(target, v + step) else max(target, v - step)

    private companion object {
        /** 하늘을 다시 계산하는 진행 간격. 25분 세션에서 약 1초. */
        const val SKY_STEP = 1f / 1500f
    }
}
