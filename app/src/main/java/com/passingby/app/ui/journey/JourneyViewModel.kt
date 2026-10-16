package com.passingby.app.ui.journey

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.passingby.app.content.Leg
import com.passingby.app.content.Route
import com.passingby.app.journey.JourneyClock
import com.passingby.app.journey.JourneyPlan
import com.passingby.app.perf.FrameStats
import com.passingby.app.scene.Palette
import com.passingby.app.scene.RenderMode
import com.passingby.app.scene.SceneKind
import com.passingby.app.scene.SceneRenderer
import com.passingby.app.scene.SceneState
import com.passingby.app.scene.TimeOfDay
import com.passingby.app.scene.UrbanField
import com.passingby.app.scene.Weather
import com.passingby.app.scene.mix
import com.passingby.app.session.TripStore
import com.passingby.app.ui.theme.Pb
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/** 열차 상태(명세 5.0). 화면을 바꾸지 않고 이 상태에 따라 창 위의 UI만 바뀐다. */
enum class TripPhase { DOCKED, RUNNING, PAUSED, ARRIVED, RESTING }

/**
 * 여행 상태 머신과 장면을 들고 있는다. 화면 회전에는 ViewModel로, 프로세스 종료에는 [SavedStateHandle]로 이어진다.
 *
 * ```
 * DOCKED ──출발──▶ RUNNING ◀──재개── PAUSED
 *   ▲  ▲             │  └──멈추기──▶   │
 *   │  │          시간 종료          끝내기(출발역으로)
 *   │  │             ▼                  │
 *   │  └─내리기── ARRIVED ──쉬기──▶ RESTING ──쉬는 시간 끝──▶ DOCKED(도착역)
 *   └──────────────────────────────────┘
 * ```
 */
class JourneyViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    val scene = SceneState()
    val renderer = SceneRenderer(scene)
    private val stats = FrameStats()
    private val store = TripStore(app)
    private val clock = JourneyClock()
    private val route: Route = Route.byId(store.routeId)

    var phase by mutableStateOf(TripPhase.DOCKED)
        private set
    var leg by mutableStateOf(route.legFrom(store.station, store.direction))
        private set
    /** 출발 카드에서 고른 집중 시간(분). */
    var minutes by mutableIntStateOf(store.minutes)
        private set
    /** 지금 달리는(또는 방금 마친) 구간의 집중 시간(분). */
    var tripMinutes by mutableIntStateOf(store.minutes)
        private set
    /** 도착까지 남은 초. 1초에 한 번만 바뀐다. */
    var remainingSec by mutableIntStateOf(0)
        private set
    /** 구간 진행(0~1). [remainingSec]와 함께 1초에 한 번 갱신한다. */
    var progress by mutableFloatStateOf(0f)
        private set
    /** 쉬는 시간 남은 초. */
    var restLeftSec by mutableIntStateOf(0)
        private set
    /** 쉬는 시간이 끝나 도착역에 서 있는 상태인지. 출발 카드 문구가 달라진다. */
    var restDone by mutableStateOf(false)
        private set
    var today by mutableStateOf(store.today())
        private set
    /** 장면이 순간 이동할 때(여행 중간에 끝내기) 늘어난다. 화면은 이 값이 바뀌면 검은 화면에서 서서히 밝아진다. */
    var cutTick by mutableIntStateOf(0)
        private set
    var hintShown by mutableStateOf(store.hintShown)
        private set
    /** 카드 종이색과 실내 벽색(ARGB). 하늘 팔레트가 바뀔 때만 갱신돼 카드가 1초에 많아야 한 번 다시 그려진다. */
    var paper by mutableIntStateOf(Pb.Paper.toArgb())
        private set
    var panel by mutableIntStateOf(0)
        private set

    private var plan = JourneyPlan(tripMinutes * 60.0, route.cruise)
    /** 지난 구간들의 누적 거리. 구간이 바뀌어도 풍경이 이어지게 다음 구간 계획을 이만큼 뒤로 민다. */
    private var baseDistance = 0.0
    private val planField = UrbanField { x -> plan.at(x - baseDistance) }
    private var restEndReal = 0L
    /** 개발용 빨리 감기·건너뛰기로 하늘 시각이 실제보다 앞선 만큼. 출시 빌드에서는 늘 0. */
    private var skyShiftMs = 0.0

    // ── 개발용 ──
    var hud by mutableStateOf("")
        private set
    var land by mutableStateOf<SceneKind?>(null)
        private set
    var fixedTime by mutableStateOf<TimeOfDay?>(null)
        private set
    var timeScale by mutableStateOf(1.0)
        private set
    var mode by mutableStateOf(scene.mode)
        private set
    var weather by mutableStateOf(scene.weather)
        private set
    var boxes by mutableStateOf(false)
        private set
    var windowFrame by mutableStateOf(scene.windowFrame)
        private set
    var cap60 by mutableStateOf(false)
        private set
    var showHud by mutableStateOf(false)
        private set

    init {
        restore()
        applyLand()
        updateTicks(clock.seconds())
    }

    /** 프레임마다 호출된다. 여정 시각으로 위치·하늘을 정하고, 상태 전환(도착·쉬는 시간 끝)을 확인한다. */
    fun onFrame(intervalNanos: Long, refreshHz: Float) {
        val dt = (intervalNanos / 1e9).toFloat().coerceAtMost(.05f)
        var t = clock.seconds()
        if ((phase == TripPhase.RUNNING || phase == TripPhase.PAUSED) && t >= plan.durationSec) {
            arrive()
            t = plan.durationSec
        }
        if (phase == TripPhase.RESTING && SystemClock.elapsedRealtime() >= restEndReal) finishRest()
        val tt = min(t, plan.durationSec)
        scene.setTravel(baseDistance + plan.distanceAt(tt), plan.speedAt(tt) * clock.rate())
        if (clock.scale != 1.0) skyShiftMs += (clock.scale - 1) * clock.rate() * dt * 1000
        scene.update(dt, System.currentTimeMillis() + skyShiftMs.toLong())
        updateTicks(t)
        updateSurfaces()
        if (!stats.record(intervalNanos, renderer.lastDrawNanos, refreshHz)) return
        if (showHud) hud = hudText(refreshHz, t)
    }

    // ── 사용자 동작 ──

    fun selectMinutes(m: Int) {
        minutes = m
        store.minutes = m
    }

    /** 정차·도착·휴식 중 어디서든 다음 구간으로 출발한다. */
    fun depart() {
        if (phase == TripPhase.ARRIVED || phase == TripPhase.RESTING) dockAtDestination()
        tripMinutes = minutes
        plan = JourneyPlan(tripMinutes * 60.0, route.cruise)
        clock.seek(0.0, 1.0)
        restDone = false
        phase = TripPhase.RUNNING
        save()
    }

    /** 열차가 몇 초에 걸쳐 서서히 선다. */
    fun pause() {
        if (phase != TripPhase.RUNNING) return
        clock.setRate(0.0, STOP_SEC)
        phase = TripPhase.PAUSED
        save()
    }

    fun resume() {
        if (phase != TripPhase.PAUSED) return
        clock.setRate(1.0, STOP_SEC)
        phase = TripPhase.RUNNING
        save()
    }

    /** 여행을 중간에 끝낸다. 출발역 정차 상태로 돌아가며 장면은 순간 이동한다. */
    fun endTrip() {
        if (phase != TripPhase.RUNNING && phase != TripPhase.PAUSED) return
        baseDistance = 0.0
        plan = JourneyPlan(minutes * 60.0, route.cruise)
        clock.seek(0.0, 0.0)
        phase = TripPhase.DOCKED
        cutTick++
        save()
    }

    fun rest() {
        if (phase != TripPhase.ARRIVED) return
        restEndReal = SystemClock.elapsedRealtime() + REST_SEC * 1000L
        phase = TripPhase.RESTING
        save()
    }

    /** 도착역에서 내린다. 그 역에서 다음 구간 승차권이 준비된다. */
    fun getOff() {
        if (phase != TripPhase.ARRIVED && phase != TripPhase.RESTING) return
        dockAtDestination()
        phase = TripPhase.DOCKED
        save()
    }

    fun markHintShown() {
        hintShown = true
        store.hintShown = true
    }

    // ── 상태 전환 ──

    private fun arrive() {
        clock.seek(plan.durationSec, 0.0)
        phase = TripPhase.ARRIVED
        store.station = leg.to
        store.direction = leg.direction
        store.recordArrival(tripMinutes)
        today = store.today()
        signal(ARRIVAL_PATTERN)
        save()
    }

    private fun finishRest() {
        dockAtDestination()
        restDone = true
        phase = TripPhase.DOCKED
        signal(REST_PATTERN)
        save()
    }

    /** 방금 도착한 역에 선 채로 다음 구간을 준비한다. 풍경은 그 자리에서 이어진다. */
    private fun dockAtDestination() {
        baseDistance += plan.totalDistance
        leg = route.legFrom(leg.to, leg.direction)
        plan = JourneyPlan(minutes * 60.0, route.cruise)
        clock.seek(0.0, 0.0)
    }

    private fun updateTicks(t: Double) {
        when (phase) {
            TripPhase.RUNNING, TripPhase.PAUSED -> {
                val left = ceil(plan.durationSec - t).toInt().coerceAtLeast(0)
                if (left != remainingSec) {
                    remainingSec = left
                    progress = (t / plan.durationSec).toFloat().coerceIn(0f, 1f)
                }
            }
            TripPhase.RESTING -> {
                val left = ceil((restEndReal - SystemClock.elapsedRealtime()) / 1000.0).toInt().coerceAtLeast(0)
                if (left != restLeftSec) restLeftSec = left
            }
            else -> {
                remainingSec = tripMinutes * 60
                progress = if (phase == TripPhase.ARRIVED) 1f else 0f
            }
        }
    }

    /** 카드는 실내와 같은 재질로 보이게, 밤이면 종이색을 벽색 쪽으로 조금 어둡게 한다(명세 4.4). */
    private fun updateSurfaces() {
        val p = scene.effective
        val wall = p.c[Palette.PANEL]
        val sheet = mix(PAPER, wall, .3f * p.f[Palette.LIT])
        if (wall != panel) panel = wall
        if (sheet != paper) paper = sheet
    }

    /** 도착·쉬는 시간 끝 신호. 화면 밖에서도 알 수 있게 진동과 알림음을 쓴다. */
    private fun signal(pattern: LongArray) {
        val ctx = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    }

    // ── 저장·복원 ──

    private fun save() {
        saved[S_PHASE] = phase.name
        saved[S_LEG] = intArrayOf(leg.from, leg.to)
        saved[S_TRIP_MINUTES] = tripMinutes
        saved[S_BASE] = baseDistance
        saved[S_CLOCK] = clock.save()
        saved[S_REST_END] = restEndReal
        saved[S_REST_DONE] = restDone
    }

    private fun restore() {
        val name = saved.get<String>(S_PHASE) ?: return
        phase = TripPhase.valueOf(name)
        saved.get<IntArray>(S_LEG)?.let { leg = Leg(route, it[0], it[1]) }
        tripMinutes = saved[S_TRIP_MINUTES] ?: tripMinutes
        baseDistance = saved[S_BASE] ?: 0.0
        saved.get<DoubleArray>(S_CLOCK)?.let(clock::restore)
        restEndReal = saved[S_REST_END] ?: 0L
        restDone = saved[S_REST_DONE] ?: false
        val running = phase == TripPhase.RUNNING || phase == TripPhase.PAUSED || phase == TripPhase.ARRIVED || phase == TripPhase.RESTING
        plan = JourneyPlan((if (running) tripMinutes else minutes) * 60.0, route.cruise)
    }

    // ── 개발용 ──

    fun selectLand(k: SceneKind?) {
        land = k
        applyLand()
    }

    fun selectTime(t: TimeOfDay?) {
        fixedTime = t
        scene.setTimeOfDay(t)
    }

    /** 구간의 [fraction](0~1) 지점으로 옮긴다. 달리는 중이 아니면 먼저 출발한다. */
    fun seekFraction(fraction: Float) {
        if (phase != TripPhase.RUNNING && phase != TripPhase.PAUSED) depart()
        val before = clock.seconds()
        val t = plan.durationSec * fraction.coerceIn(0f, 1f)
        clock.seek(t)
        skyShiftMs += (t - before) * 1000
        save()
    }

    /** 도착 20초 전으로 건너뛴다. */
    fun seekNearEnd() {
        if (phase != TripPhase.RUNNING && phase != TripPhase.PAUSED) depart()
        seekFraction(((plan.durationSec - 20) / plan.durationSec).toFloat())
    }

    /** 쉬는 시간을 바로 끝낸다. */
    fun skipRest() {
        if (phase == TripPhase.RESTING) restEndReal = SystemClock.elapsedRealtime()
    }

    fun selectScale(s: Double) {
        timeScale = s
        clock.setScale(s)
        save()
    }

    fun selectMode(m: RenderMode) {
        mode = m
        scene.mode = m
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

    private fun applyLand() {
        scene.urban = land?.field ?: planField
    }

    private fun hudText(refreshHz: Float, t: Double): String {
        val rt = Runtime.getRuntime()
        val shown = t.coerceIn(0.0, plan.durationSec).toLong()
        val total = plan.durationSec.toLong()
        return String.format(
            Locale.US,
            "%.0fHz · %s\n프레임 %.1fms (p95 %.1f) · 끊김 %d\n그리기 기록 %.2fms (최대 %.1f)\n비트맵 %.1fMB · 네이티브 %.1fMB · 자바 %.1fMB\n%s %d:%02d / %d:%02d ×%.0f · 도시 %.2f · 속도 %.0f · %s",
            refreshHz, mode.label,
            stats.avgInterval(), stats.p95Interval(), stats.janks,
            stats.avgDraw(), stats.maxDraw(),
            renderer.bitmapBytes / MB, Debug.getNativeHeapAllocatedSize() / MB, (rt.totalMemory() - rt.freeMemory()) / MB,
            phase.name, shown / 60, shown % 60, total / 60, total % 60, timeScale,
            scene.urban.at(scene.distanceU), scene.speed, scene.timeOfDay.label,
        )
    }

    override fun onCleared() {
        renderer.release()
    }

    private companion object {
        const val MB = 1024.0 * 1024.0
        val PAPER = Pb.Paper.toArgb()
        /** 잠깐 멈추기·재개 때 열차가 서거나 다시 붙는 데 걸리는 시간(초). */
        const val STOP_SEC = 3.5
        const val REST_SEC = 5 * 60
        val ARRIVAL_PATTERN = longArrayOf(0, 180, 140, 180, 140, 320)
        val REST_PATTERN = longArrayOf(0, 120, 120, 120)

        const val S_PHASE = "phase"
        const val S_LEG = "leg"
        const val S_TRIP_MINUTES = "trip_minutes"
        const val S_BASE = "base"
        const val S_CLOCK = "clock"
        const val S_REST_END = "rest_end"
        const val S_REST_DONE = "rest_done"
    }
}
