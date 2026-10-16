package com.passingby.app.ui.journey

import android.os.Debug
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.passingby.app.perf.FrameStats
import com.passingby.app.scene.RenderMode
import com.passingby.app.scene.SceneRenderer
import com.passingby.app.scene.SceneState
import com.passingby.app.scene.TimeOfDay
import java.util.Locale

/**
 * 장면 상태와 렌더러를 화면 회전에도 유지한다.
 * 시제품이라 SavedStateHandle(프로세스 종료 대비)은 아직 쓰지 않는다.
 */
class JourneyViewModel : ViewModel() {
    val scene = SceneState()
    val renderer = SceneRenderer(scene)
    private val stats = FrameStats()

    var hud by mutableStateOf("")
        private set
    var timeOfDay by mutableStateOf(scene.timeOfDay)
        private set
    var mode by mutableStateOf(scene.mode)
        private set
    var fast by mutableStateOf(true)
        private set
    var raining by mutableStateOf(false)
        private set
    var boxes by mutableStateOf(false)
        private set
    var windowFrame by mutableStateOf(scene.windowFrame)
        private set
    var cap60 by mutableStateOf(false)
        private set
    var showHud by mutableStateOf(true)
        private set

    fun selectTime(t: TimeOfDay) {
        timeOfDay = t
        scene.setTimeOfDay(t)
    }

    fun selectMode(m: RenderMode) {
        mode = m
        scene.mode = m
    }

    fun selectFast(value: Boolean) {
        fast = value
        scene.speedTarget = if (value) SceneState.FAST_SPEED else SceneState.SLOW_SPEED
    }

    fun toggleRain() {
        raining = !raining
        scene.raining = raining
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

    /** 프레임마다 호출된다. 0.5초마다 HUD 문자열을 갱신한다. */
    fun onFrame(intervalNanos: Long, refreshHz: Float) {
        if (!stats.record(intervalNanos, renderer.lastDrawNanos, refreshHz)) return
        if (!showHud) return
        val rt = Runtime.getRuntime()
        val javaMb = (rt.totalMemory() - rt.freeMemory()) / MB
        val nativeMb = Debug.getNativeHeapAllocatedSize() / MB
        val bitmapMb = renderer.bitmapBytes / MB
        val sec = scene.elapsedSec.toLong()
        hud = String.format(
            Locale.US,
            "%.0fHz · %s\n프레임 %.1fms (p95 %.1f) · 끊김 %d\n그리기 기록 %.2fms (최대 %.1f)\n비트맵 %.1fMB · 네이티브 %.1fMB · 자바 %.1fMB\n경과 %d:%02d:%02d",
            refreshHz, mode.label,
            stats.avgInterval(), stats.p95Interval(), stats.janks,
            stats.avgDraw(), stats.maxDraw(),
            bitmapMb, nativeMb, javaMb,
            sec / 3600, sec / 60 % 60, sec % 60,
        )
    }

    override fun onCleared() {
        renderer.release()
    }

    private companion object {
        const val MB = 1024.0 * 1024.0
    }
}
