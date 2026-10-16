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
import com.passingby.app.content.Course
import com.passingby.app.content.Station
import com.passingby.app.journey.CoursePlan
import com.passingby.app.journey.CourseZones
import com.passingby.app.journey.JourneyClock
import com.passingby.app.journey.WeatherDirector
import com.passingby.app.perf.FrameStats
import com.passingby.app.scene.Palette
import com.passingby.app.scene.RenderMode
import com.passingby.app.scene.SceneKind
import com.passingby.app.scene.SceneRenderer
import com.passingby.app.scene.SceneState
import com.passingby.app.scene.Themes
import com.passingby.app.scene.TimeOfDay
import com.passingby.app.scene.UrbanField
import com.passingby.app.scene.Weather
import com.passingby.app.scene.mix
import com.passingby.app.session.TripStore
import com.passingby.app.ui.theme.Pb
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

/** 열차 상태(명세 5.0). 화면을 바꾸지 않고 이 상태에 따라 창 위의 UI만 바뀐다. */
enum class TripPhase { DOCKED, RUNNING, PAUSED, ARRIVED, RESTING }

/**
 * 여행 상태 머신과 장면을 들고 있는다. 세션 하나가 코스 하나이고, 중간 역마다 20초 정차한다(명세 7.2).
 *
 * ```
 * DOCKED ──출발──▶ RUNNING ◀──재개── PAUSED
 *   ▲  ▲             │  └──멈추기──▶   │
 *   │  │          시간 종료          끝내기(출발역으로)
 *   │  │             ▼                  │
 *   │  └─내리기── ARRIVED ──쉬기──▶ RESTING ──쉬는 시간 끝──▶ DOCKED(종착역, 역방향)
 *   └──────────────────────────────────┘
 * ```
 *
 * 진행 중인 여행은 [TripStore]에 저장해, 앱을 지웠다 다시 열어도(포그라운드 서비스로 계속 흐르는 동안) 이어 붙인다.
 */
class JourneyViewModel(app: Application) : AndroidViewModel(app) {
    val scene = SceneState()
    val renderer = SceneRenderer(scene)
    private val stats = FrameStats()
    private val store = TripStore(app)
    private val clock = JourneyClock()

    var course by mutableStateOf(Course.byId(store.courseId))
        private set
    /** 1이면 정방향(서울 → 부산), −1이면 역방향. */
    var direction by mutableIntStateOf(store.direction)
        private set
    var phase by mutableStateOf(TripPhase.DOCKED)
        private set
    /** 출발 카드에서 고른 집중 시간(분). */
    var minutes by mutableIntStateOf(store.minutes)
        private set
    /** 지금 달리는(또는 방금 마친) 세션의 집중 시간(분). */
    var tripMinutes by mutableIntStateOf(store.minutes)
        private set
    /** 종착까지 남은 초. 1초에 한 번만 바뀐다. */
    var remainingSec by mutableIntStateOf(0)
        private set
    /** 세션 진행(0~1, 시간 기준). [remainingSec]와 함께 갱신한다. */
    var progress by mutableFloatStateOf(0f)
        private set
    /** 지금 서 있는 중간 역 번호(정차 중이 아니면 −1). */
    var stoppedAt by mutableIntStateOf(-1)
        private set
    /** 다음에 설 역 번호와 거기까지 남은 초. */
    var nextStop by mutableIntStateOf(1)
        private set
    var nextStopSec by mutableIntStateOf(0)
        private set
    var restLeftSec by mutableIntStateOf(0)
        private set
    /** 쉬는 시간이 끝나 종착역에 서 있는 상태인지. 출발 카드 문구가 달라진다. */
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
    /** 진행 바에 찍을 역 위치(0~1, 시간 기준). */
    var stationMarks by mutableStateOf(FloatArray(0))
        private set

    /** 지금 방향으로 늘어놓은 역. */
    val stations: List<Station>
        get() = course.ordered(direction)

    private var plan = CoursePlan(tripMinutes * 60.0, course.stations.size, course.cruise)
    /** 지난 세션들의 누적 거리. 세션이 바뀌어도 풍경이 이어지게 다음 계획을 이만큼 뒤로 민다. */
    private var baseDistance = 0.0
    private val planField = UrbanField { x -> plan.at(x - baseDistance) }
    private var weatherSeed = Random.nextInt()
    private var weather = WeatherDirector(weatherSeed, course.snowy)
    private var restEndReal = 0L

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
    /** 직접 고른 날씨. null이면 무작위. */
    var fixedWeather by mutableStateOf<Weather?>(null)
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
        applyStations()
        updateTicks(clock.seconds())
    }

    /** 프레임마다 호출된다. 여정 시각으로 위치·하늘·날씨를 정하고, 상태 전환(도착·쉬는 시간 끝)을 확인한다. */
    fun onFrame(intervalNanos: Long, refreshHz: Float) {
        val dt = (intervalNanos / 1e9).toFloat().coerceAtMost(.05f)
        var t = clock.seconds()
        if (moving() && t >= plan.durationSec) {
            arrive()
            t = plan.durationSec
        }
        if (phase == TripPhase.RESTING && SystemClock.elapsedRealtime() >= restEndReal) finishRest()
        val tt = t.coerceIn(0.0, plan.durationSec)
        scene.setTravel(baseDistance + plan.distanceAt(tt), plan.speedAt(tt) * clock.rate())
        scene.autoRain = weather.rainAt(tt)
        scene.autoSnow = weather.snowAt(tt)
        val sky = when (phase) {
            TripPhase.ARRIVED, TripPhase.RESTING -> 1f
            else -> (tt / plan.durationSec).toFloat()
        }
        scene.update(dt, sky)
        updateTicks(t)
        updateSurfaces()
        if (!stats.record(intervalNanos, renderer.lastDrawNanos, refreshHz)) return
        if (showHud) hud = hudText(refreshHz, t)
    }

    private fun moving() = phase == TripPhase.RUNNING || phase == TripPhase.PAUSED

    // ── 사용자 동작 ──

    fun selectMinutes(m: Int) {
        minutes = m
        store.minutes = m
        if (phase == TripPhase.DOCKED) {
            tripMinutes = m
            rebuildPlan()
        }
    }

    /** 정차·도착·휴식 중 어디서든 출발한다. 도착·휴식 중이면 종착역에서 역방향으로 떠난다. */
    fun depart() {
        if (phase == TripPhase.ARRIVED || phase == TripPhase.RESTING) dockAtTerminus()
        tripMinutes = minutes
        weatherSeed = Random.nextInt()
        rebuildPlan()
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
        if (!moving()) return
        tripMinutes = minutes
        rebuildPlan()
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

    /** 종착역에서 내린다. 그 역에서 역방향 승차권이 준비된다. */
    fun getOff() {
        if (phase != TripPhase.ARRIVED && phase != TripPhase.RESTING) return
        dockAtTerminus()
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
        store.recordArrival(tripMinutes)
        today = store.today()
        signal(ARRIVAL_PATTERN)
        save()
    }

    private fun finishRest() {
        dockAtTerminus()
        restDone = true
        phase = TripPhase.DOCKED
        signal(REST_PATTERN)
        save()
    }

    /** 방금 도착한 종착역에 선 채로 역방향 세션을 준비한다. 풍경은 그 자리에서 이어진다. */
    private fun dockAtTerminus() {
        baseDistance += plan.totalDistance
        direction = -direction
        store.direction = direction
        tripMinutes = minutes
        rebuildPlan()
        clock.seek(0.0, 0.0)
    }

    private fun rebuildPlan() {
        plan = CoursePlan(tripMinutes * 60.0, course.stations.size, course.cruise)
        weather = WeatherDirector(weatherSeed, course.snowy)
        applyStations()
    }

    /** 역 위치·이름판·진행 바 표시·코스 구역을 지금 계획에 맞춘다. */
    private fun applyStations() {
        scene.theme = Themes.byId(course.id)
        scene.zones = CourseZones(plan, course, direction, baseDistance)
        val list = stations
        scene.setStations(
            DoubleArray(list.size) { baseDistance + plan.stationDistance(it) },
            Array(list.size) { list[it].local },
            Array(list.size) { list[it].roman },
        )
        stationMarks = FloatArray(list.size) {
            when (it) {
                0 -> 0f
                list.size - 1 -> 1f
                else -> (plan.arrivalAt(it) / plan.durationSec).toFloat()
            }
        }
    }

    private fun updateTicks(t: Double) {
        when (phase) {
            TripPhase.RUNNING, TripPhase.PAUSED -> {
                val left = ceil(plan.durationSec - t).toInt().coerceAtLeast(0)
                if (left != remainingSec) {
                    remainingSec = left
                    progress = (t / plan.durationSec).toFloat().coerceIn(0f, 1f)
                    val stop = plan.stoppedAt(t)
                    stoppedAt = stop
                    val next = if (stop >= 0) stop + 1 else plan.segmentAt(t) + 1
                    nextStop = min(next, plan.segments)
                    nextStopSec = ceil(plan.arrivalAt(nextStop) - t).toInt().coerceAtLeast(0)
                }
            }
            TripPhase.RESTING -> {
                val left = ceil((restEndReal - SystemClock.elapsedRealtime()) / 1000.0).toInt().coerceAtLeast(0)
                if (left != restLeftSec) restLeftSec = left
            }
            else -> {
                remainingSec = tripMinutes * 60
                progress = if (phase == TripPhase.ARRIVED) 1f else 0f
                stoppedAt = -1
                nextStop = 1
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
        store.saveActive(
            TripStore.Active(
                phase.name, tripMinutes, baseDistance, clock.save(), restEndReal, restDone, weatherSeed,
            ),
        )
    }

    private fun restore() {
        val a = store.loadActive() ?: return
        phase = runCatching { TripPhase.valueOf(a.phase) }.getOrDefault(TripPhase.DOCKED)
        tripMinutes = a.tripMinutes
        baseDistance = a.base
        if (a.clock.size == 6) clock.restore(a.clock)
        restEndReal = a.restEnd
        restDone = a.restDone
        weatherSeed = a.weatherSeed
        plan = CoursePlan(tripMinutes * 60.0, course.stations.size, course.cruise)
        weather = WeatherDirector(weatherSeed, course.snowy)
    }

    /** 코스를 바꾼다. 정차 중에만 되고, 새 코스의 출발역으로 순간 이동한다. */
    fun selectCourse(c: Course) {
        if (phase != TripPhase.DOCKED || c == course) return
        course = c
        store.courseId = c.id
        direction = 1
        store.direction = 1
        baseDistance = 0.0
        restDone = false
        rebuildPlan()
        clock.seek(0.0, 0.0)
        cutTick++
        save()
    }

    // ── 개발용 ──

    /** 개발용: 여행 중이어도 끊고 코스를 바꾼다. */
    fun forceCourse(c: Course) {
        if (moving() || phase == TripPhase.ARRIVED || phase == TripPhase.RESTING) {
            phase = TripPhase.DOCKED
            clock.seek(0.0, 0.0)
        }
        selectCourse(c)
    }

    fun selectLand(k: SceneKind?) {
        land = k
        applyLand()
    }

    fun selectTime(t: TimeOfDay?) {
        fixedTime = t
        scene.setTimeOfDay(t)
    }

    /** 세션의 [fraction](0~1) 지점으로 옮긴다. 달리는 중이 아니면 먼저 출발한다. */
    fun seekFraction(fraction: Float) {
        if (!moving()) depart()
        clock.seek(plan.durationSec * fraction.coerceIn(0f, 1f))
        save()
    }

    /** 도착 20초 전으로 건너뛴다. */
    fun seekNearEnd() {
        if (!moving()) depart()
        seekFraction(((plan.durationSec - 20) / plan.durationSec).toFloat())
    }

    /** 다음 정차역 5초 전으로 건너뛴다. */
    fun seekNextStop() {
        if (!moving()) depart()
        clock.seek((plan.arrivalAt(nextStop) - 5).coerceAtLeast(0.0))
        save()
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

    fun selectWeather(w: Weather?) {
        fixedWeather = w
        scene.autoWeather = w == null
        if (w != null) scene.weather = w
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
            "%.0fHz · %s\n프레임 %.1fms (p95 %.1f) · 끊김 %d\n그리기 기록 %.2fms (최대 %.1f)\n비트맵 %.1fMB · 네이티브 %.1fMB · 자바 %.1fMB\n%s %d:%02d / %d:%02d ×%.0f · 도시 %.2f · 속도 %.0f · %s · 비 %.2f 눈 %.2f",
            refreshHz, mode.label,
            stats.avgInterval(), stats.p95Interval(), stats.janks,
            stats.avgDraw(), stats.maxDraw(),
            renderer.bitmapBytes / MB, Debug.getNativeHeapAllocatedSize() / MB, (rt.totalMemory() - rt.freeMemory()) / MB,
            phase.name, shown / 60, shown % 60, total / 60, total % 60, timeScale,
            scene.urban.at(scene.distanceU), scene.speed, scene.timeOfDay.label, scene.rainAmount, scene.snowAmount,
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
    }
}
