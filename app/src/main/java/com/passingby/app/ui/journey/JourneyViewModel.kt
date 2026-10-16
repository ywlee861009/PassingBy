package com.passingby.app.ui.journey

import android.app.Activity
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
import com.passingby.app.audio.AmbientSound
import com.passingby.app.billing.Billing
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
import com.passingby.app.session.TripService
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

    // ── 코스 잠금·결제 ──
    var unlocked by mutableStateOf(store.unlocked)
        private set
    var devUnlockAll by mutableStateOf(store.devUnlockAll)
        private set
    /** 상품 ID → 표시 가격. 스토어에서 받아 오기 전에는 비어 있다. */
    var prices by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private val billing = Billing(
        app,
        Course.ALL.mapNotNull { it.productId },
        onPrices = { prices = it },
        onOwned = { ids ->
            val courses = Course.ALL.filter { it.productId in ids }.map { it.id }.toSet()
            if (!unlocked.containsAll(courses)) {
                unlocked = unlocked + courses
                store.unlocked = unlocked
            }
        },
    )

    // ── 설정 ──
    var soundOn by mutableStateOf(store.soundOn)
        private set
    var soundLevel by mutableIntStateOf(store.soundLevel)
        private set
    private val sound = AmbientSound().also { it.volume = SOUND_LEVELS[store.soundLevel.coerceIn(0, 2)] }
    /** 앱 화면이 보이는지. 소리는 보이는 동안에만 낸다. */
    private var visible = false
    var ledgeInfo by mutableStateOf(store.ledgeInfo)
        private set
    var overlayAlways by mutableStateOf(store.overlayAlways)
        private set
    var powerSave by mutableStateOf(store.powerSave)
        private set
    /** 잠깐 띄우는 안내 문구. [messageTick]이 바뀔 때마다 새로 보여 준다. */
    var message by mutableStateOf<String?>(null)
        private set
    var messageTick by mutableIntStateOf(0)
        private set

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
        store.weather?.let { name -> runCatching { Weather.valueOf(name) }.getOrNull() }?.let { applyWeather(it) }
        billing.connect()
        applyLand()
        applyStations()
        updateTicks(clock.seconds())
        // 앱을 다시 열었을 때 달리던 여행이 있으면 알림 서비스를 이어 붙인다.
        if (phase != TripPhase.DOCKED) syncService()
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
        sound.speed = (scene.speed / course.cruise).toFloat()
        sound.rain = scene.rainAmount
        sound.tunnel = if (inTunnel(scene.distanceU)) 1f else 0f
        sound.clack = if (course.cruise < Course.FAST) .9f else .45f
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

    /** 무료이거나 열린 코스인지. */
    fun owns(c: Course): Boolean = c.free || devUnlockAll || c.id in unlocked

    fun priceOf(c: Course): String? = c.productId?.let { prices[it] }

    /** 지금 고른(미리 보는) 코스를 산다. 스토어에 상품이 아직 없으면 안내만 띄운다. */
    fun buy(activity: Activity, c: Course = course) {
        val id = c.productId ?: return
        if (!billing.purchase(activity, id)) notify("아직 구매할 수 없어요. 스토어에 코스가 등록되면 열려요.")
    }

    fun restorePurchases() {
        billing.restore()
        notify("구매 내역을 다시 확인했어요")
    }

    /** 앱 화면이 보이거나 가려질 때 MainActivity가 알린다. */
    fun setVisible(v: Boolean) {
        visible = v
        applySound()
    }

    fun toggleSound() {
        soundOn = !soundOn
        store.soundOn = soundOn
        applySound()
    }

    fun selectSoundLevel(level: Int) {
        soundLevel = level
        store.soundLevel = level
        sound.volume = SOUND_LEVELS[level]
    }

    private fun applySound() {
        if (visible && soundOn) sound.start() else sound.stop()
    }

    private fun inTunnel(x: Double): Boolean {
        val r = scene.zones.tunnels
        var i = 0
        while (i < r.size) {
            if (x >= r[i] && x <= r[i + 1]) return true
            i += 2
        }
        return false
    }

    fun setLedge(v: Boolean) {
        ledgeInfo = v
        store.ledgeInfo = v
    }

    fun setOverlay(v: Boolean) {
        overlayAlways = v
        store.overlayAlways = v
    }

    fun setPower(v: Boolean) {
        powerSave = v
        store.powerSave = v
    }

    fun toggleDevUnlock() {
        devUnlockAll = !devUnlockAll
        store.devUnlockAll = devUnlockAll
    }

    private fun notify(text: String) {
        message = text
        messageTick++
    }

    /** 정차·도착·휴식 중 어디서든 출발한다. 도착·휴식 중이면 종착역에서 역방향으로 떠난다. 잠긴 코스는 출발하지 않는다. */
    fun depart() {
        if (!owns(course)) return
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

    /** 알림 권한을 이미 한 번 물었는지. 거절해도 매번 묻지 않는다. */
    val notificationAsked: Boolean
        get() = store.notificationAsked

    fun markNotificationAsked() {
        store.notificationAsked = true
    }

    fun markHintShown() {
        hintShown = true
        store.hintShown = true
    }

    // ── 상태 전환 ──

    private fun arrive() {
        // 앱 밖에 있는 동안 도착했으면 이미 알림으로 알렸으니 다시 울리지 않는다.
        val late = clock.seconds() - plan.durationSec > LATE_SEC
        clock.seek(plan.durationSec, 0.0)
        phase = TripPhase.ARRIVED
        store.recordArrival(tripMinutes)
        today = store.today()
        if (!late) signal(ARRIVAL_PATTERN)
        save()
    }

    private fun finishRest() {
        val late = SystemClock.elapsedRealtime() - restEndReal > LATE_SEC * 1000
        dockAtTerminus()
        restDone = true
        phase = TripPhase.DOCKED
        if (!late) signal(REST_PATTERN)
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
                    if (stop >= 0 && stoppedAt < 0) sound.chime()
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

    /**
     * 포그라운드 서비스와 알림을 지금 상태에 맞춘다. 달리는 중·쉬는 중에만 서비스를 띄운다.
     * 개발용 빨리 감기 중에는 실제 시간과 어긋나므로 알림 시계를 빼고 문구만 띄운다.
     */
    private fun syncService() {
        val ctx = getApplication<Application>()
        val title = course.title(direction)
        val terminus = stations.last().name
        val now = SystemClock.elapsedRealtime()
        val realtime = clock.scale == 1.0
        when (phase) {
            TripPhase.RUNNING -> {
                val left = (plan.durationSec - clock.seconds()).coerceAtLeast(0.0)
                TripService.update(
                    ctx, title, "${course.stopsLine(direction)} · 종착 $terminus",
                    if (realtime) now + (left * 1000).toLong() else 0L,
                    "${terminus}에 도착했어요", "${tripMinutes}분 머물렀어요. 쉬었다 가거나 여기서 내릴 수 있어요.",
                )
            }
            TripPhase.PAUSED -> {
                val left = (plan.durationSec - clock.seconds()).coerceAtLeast(0.0).toInt()
                TripService.update(ctx, title, "잠깐 멈춤 · 종착까지 ${formatClock(left)}", 0L, "", "")
            }
            TripPhase.RESTING -> TripService.update(
                ctx, "${terminus}에서 쉬는 중", "다음 여행 ${course.title(-direction)}",
                restEndReal, "쉬는 시간이 끝났어요", "${course.title(-direction)} 승차권이 준비됐어요.",
            )
            else -> TripService.stop(ctx)
        }
    }

    // ── 저장·복원 ──

    private fun save() {
        syncService()
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
        if (phase != TripPhase.DOCKED) {
            phase = TripPhase.DOCKED
            clock.seek(0.0, 0.0)
        }
        if (c != course) {
            selectCourse(c)
        } else {
            tripMinutes = minutes
            rebuildPlan()
            cutTick++
            save()
        }
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

    /** 창밖 날씨. null이면 무작위. 설정에 남는다. */
    fun selectWeather(w: Weather?) {
        applyWeather(w)
        store.weather = w?.name
    }

    private fun applyWeather(w: Weather?) {
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
        sound.stop()
        billing.end()
        renderer.release()
    }

    private companion object {
        const val MB = 1024.0 * 1024.0
        val PAPER = Pb.Paper.toArgb()
        /** 잠깐 멈추기·재개 때 열차가 서거나 다시 붙는 데 걸리는 시간(초). */
        const val STOP_SEC = 3.5
        /** 이 초보다 늦게 알아챈 도착·쉬는 시간 끝은 앱 밖에서 이미 알렸다고 보고 다시 울리지 않는다. */
        const val LATE_SEC = 3.0
        const val REST_SEC = 5 * 60
        val ARRIVAL_PATTERN = longArrayOf(0, 180, 140, 180, 140, 320)
        val REST_PATTERN = longArrayOf(0, 120, 120, 120)
        val SOUND_LEVELS = floatArrayOf(.35f, .6f, .9f)
    }
}
