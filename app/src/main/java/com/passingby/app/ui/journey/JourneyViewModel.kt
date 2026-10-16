package com.passingby.app.ui.journey

import android.os.Debug
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.passingby.app.journey.JourneyClock
import com.passingby.app.journey.JourneyPlan
import com.passingby.app.perf.FrameStats
import com.passingby.app.scene.RenderMode
import com.passingby.app.scene.SceneKind
import com.passingby.app.scene.SceneRenderer
import com.passingby.app.scene.SceneState
import com.passingby.app.scene.TimeOfDay
import com.passingby.app.scene.Weather
import java.util.Locale

/**
 * 여정·장면 상태와 렌더러를 화면 회전에도 유지한다.
 * 여정은 앱을 켜자마자 출발한다. 정차 화면·세션 상태 머신·SavedStateHandle은 2단계에서 붙인다.
 */
class JourneyViewModel : ViewModel() {
    val scene = SceneState()
    val renderer = SceneRenderer(scene)
    private val stats = FrameStats()
    private val clock = JourneyClock()
    private var plan = JourneyPlan(25 * 60.0, JourneyPlan.FAST_TRAIN)
    /** 여정 0초에 해당하는 실제 시각. 하늘은 여정 시계를 따라가므로 빨리 감기하면 하늘도 함께 흐른다. */
    private var departWallMillis = System.currentTimeMillis()

    var hud by mutableStateOf("")
        private set
    /** 개발용 장소 고정. null이면 여정을 따른다. */
    var land by mutableStateOf<SceneKind?>(null)
        private set
    /** 개발용 시간대 고정. null이면 실제 시각. */
    var fixedTime by mutableStateOf<TimeOfDay?>(null)
        private set
    var minutes by mutableStateOf(25)
        private set
    var timeScale by mutableStateOf(1.0)
        private set
    var mode by mutableStateOf(scene.mode)
        private set
    var fast by mutableStateOf(true)
        private set
    var weather by mutableStateOf(scene.weather)
        private set
    var boxes by mutableStateOf(false)
        private set
    var windowFrame by mutableStateOf(scene.windowFrame)
        private set
    var cap60 by mutableStateOf(false)
        private set
    var showHud by mutableStateOf(true)
        private set

    init {
        applyLand()
    }

    /** 프레임마다 호출된다. 여정 시각으로 위치·하늘을 정하고, 0.5초마다 HUD 문자열을 갱신한다. */
    fun onFrame(intervalNanos: Long, refreshHz: Float) {
        val t = clock.seconds()
        scene.setTravel(plan.distanceAt(t), plan.speedAt(t))
        scene.update((intervalNanos / 1e9).toFloat().coerceAtMost(.05f), departWallMillis + (t * 1000).toLong())
        if (!stats.record(intervalNanos, renderer.lastDrawNanos, refreshHz)) return
        if (!showHud) return
        val rt = Runtime.getRuntime()
        val javaMb = (rt.totalMemory() - rt.freeMemory()) / MB
        val nativeMb = Debug.getNativeHeapAllocatedSize() / MB
        val bitmapMb = renderer.bitmapBytes / MB
        val shown = t.coerceIn(0.0, plan.durationSec).toLong()
        val total = plan.durationSec.toLong()
        hud = String.format(
            Locale.US,
            "%.0fHz · %s\n프레임 %.1fms (p95 %.1f) · 끊김 %d\n그리기 기록 %.2fms (최대 %.1f)\n비트맵 %.1fMB · 네이티브 %.1fMB · 자바 %.1fMB\n여정 %d:%02d / %d:%02d ×%.0f · 도시 %.2f · 속도 %.0f · %s",
            refreshHz, mode.label,
            stats.avgInterval(), stats.p95Interval(), stats.janks,
            stats.avgDraw(), stats.maxDraw(),
            bitmapMb, nativeMb, javaMb,
            shown / 60, shown % 60, total / 60, total % 60, timeScale,
            scene.urban.at(scene.distanceU), scene.speed, scene.timeOfDay.label,
        )
    }

    fun selectLand(k: SceneKind?) {
        land = k
        applyLand()
    }

    fun selectTime(t: TimeOfDay?) {
        fixedTime = t
        scene.setTimeOfDay(t)
    }

    /** 구간 길이를 바꾸고 처음부터 다시 출발한다. */
    fun selectMinutes(m: Int) {
        minutes = m
        rebuildPlan()
        restart()
    }

    fun restart() {
        clock.seek(0.0)
        departWallMillis = System.currentTimeMillis()
    }

    /** 구간의 [fraction](0~1) 지점으로 옮긴다. 하늘 시각은 출발 시각을 그만큼 앞당겨 맞춘다. */
    fun seekFraction(fraction: Float) {
        val t = plan.durationSec * fraction.coerceIn(0f, 1f)
        clock.seek(t)
        departWallMillis = System.currentTimeMillis() - (t * 1000).toLong()
    }

    fun selectScale(s: Double) {
        timeScale = s
        clock.setScale(s)
    }

    fun selectMode(m: RenderMode) {
        mode = m
        scene.mode = m
    }

    /** 완행·고속. 경과 시간은 그대로 두고 계획만 바꾸므로 풍경 위치가 한 번 건너뛴다(개발용). */
    fun selectFast(value: Boolean) {
        fast = value
        rebuildPlan()
    }

    fun selectWeather(w: Weather) {
        weather = w
        scene.weather = w
    }

    fun toggleBoxes() {
        boxes = !boxes
        scene.showBoxes = boxes
    }

    fun toggleWindowFrame() {
        windowFrame = !windowFrame
        scene.windowFrame = windowFrame
    }

    fun toggleCap60() {
        cap60 = !cap60
    }

    fun toggleHud() {
        showHud = !showHud
    }

    private fun rebuildPlan() {
        plan = JourneyPlan(minutes * 60.0, if (fast) JourneyPlan.FAST_TRAIN else JourneyPlan.SLOW_TRAIN)
        applyLand()
    }

    private fun applyLand() {
        scene.urban = land?.field ?: plan
    }

    override fun onCleared() {
        renderer.release()
    }

    private companion object {
        const val MB = 1024.0 * 1024.0
    }
}
