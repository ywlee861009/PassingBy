package com.kero.passingby.ui.journey

import com.kero.passingby.feature.journey.R
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
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kero.passingby.experience.ExperienceRenderer
import com.kero.passingby.audio.AmbientSound
import com.kero.passingby.billing.Billing
import com.kero.passingby.billing.RestoreStatus
import com.kero.passingby.content.Course
import com.kero.passingby.content.Station
import com.kero.passingby.journey.CoursePlan
import com.kero.passingby.journey.CourseZones
import com.kero.passingby.journey.SessionCoordinator
import com.kero.passingby.journey.SessionEvent
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.journey.SessionSnapshot
import com.kero.passingby.journey.SessionState
import com.kero.passingby.journey.WeatherDirector
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.perf.FrameStats
import com.kero.passingby.scene.Palette
import com.kero.passingby.scene.RenderMode
import com.kero.passingby.scene.SceneKind
import com.kero.passingby.scene.SceneRenderer
import com.kero.passingby.scene.SceneState
import com.kero.passingby.scene.Season
import com.kero.passingby.scene.SeasonChoice
import com.kero.passingby.scene.TimeOfDay
import com.kero.passingby.scene.UrbanField
import com.kero.passingby.scene.Weather
import com.kero.passingby.session.TripTimer
import com.kero.passingby.session.JourneyStore
import com.kero.passingby.telemetry.Telemetry
import com.kero.passingby.ui.theme.Pb
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

/**
 * 도메인 세션의 결과를 화면·기차 장면과 Android 어댑터에 연결한다. 세션 하나가 코스 하나이고, 중간 역마다 20초 정차한다(명세 7.2).
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
 * 진행 중인 여행은 [JourneyStore]에 저장해, 백그라운드에서 앱 프로세스가 종료되거나 최근 앱 목록에서 지웠다 다시 열어도 이어 붙인다.
 */
class JourneyViewModel(
    app: Application,
    val options: JourneyOptions,
    private val store: JourneyStore,
) : AndroidViewModel(app) {
    val scene = SceneState()
    val renderer: ExperienceRenderer = SceneRenderer(scene)
    private val stats = FrameStats()
    private val session = SessionCoordinator(store.minutes, SystemClock::elapsedRealtime)
    private var sessionState by mutableStateOf(session.state)

    var course by mutableStateOf(Course.byId(store.courseId))
        private set
    /** 1이면 정방향(서울 → 부산), −1이면 역방향. */
    var direction by mutableIntStateOf(store.direction)
        private set
    val phase: SessionPhase
        get() = sessionState.phase
    /** 출발 카드에서 고른 집중 시간(분). */
    var minutes by mutableIntStateOf(store.minutes)
        private set
    /** 지금 달리는(또는 방금 마친) 세션의 집중 시간(분). */
    val tripMinutes: Int
        get() = sessionState.tripMinutes
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
    val restDone: Boolean
        get() = sessionState.restDone
    var today by mutableStateOf(store.today())
        private set
    /** 여행 수첩: 들른 역과 누적 기록. 바뀔 때마다 새로 읽는다. */
    var visited by mutableStateOf(store.visited)
        private set
    var lifetime by mutableStateOf(store.lifetime())
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
    private var weather = WeatherDirector(weatherSeed, course.snowy, SeasonChoice.fromId(store.seasonChoiceId).resolve(weatherSeed))

    // ── 코스 잠금·결제 ──
    var unlocked by mutableStateOf(store.unlocked)
        private set
    var devUnlockAll by mutableStateOf(store.devUnlockAll)
        private set
    /** 상품 ID → 표시 가격. 스토어에서 받아 오기 전에는 비어 있다. */
    var prices by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var restoringPurchases by mutableStateOf(false)
        private set
    private val billing = Billing(
        app,
        Course.ALL.mapNotNull { it.productId },
        onPrices = { prices = it },
        onOwned = { ids, _ ->
            val courses = Course.ALL.filter { it.productId in ids }.map { it.id }.toSet()
            if (!unlocked.containsAll(courses)) {
                for (id in courses - unlocked) Telemetry.event("course_unlock", "course" to id)
                unlocked = unlocked + courses
                store.unlocked = unlocked
            }
        },
        onRestore = { status ->
            restoringPurchases = status == RestoreStatus.CHECKING
            when (status) {
                RestoreStatus.RESTORED -> notify(R.string.purchases_restored)
                RestoreStatus.EMPTY -> notify(R.string.purchases_empty)
                RestoreStatus.FAILED -> notify(R.string.purchases_restore_failed)
                RestoreStatus.PENDING -> notify(R.string.purchase_pending)
                else -> Unit
            }
        },
        onPending = { notify(R.string.purchase_pending) },
        onPurchaseFailed = { notify(R.string.purchase_unavailable) },
    )

    // ── 설정 ──
    /** 직접 고른 시간대. null이면 여행에 맞춰 자동 순환한다. */
    var fixedTime by mutableStateOf(TimeOfDay.fromSetting(store.timeOfDayId))
        private set
    var seasonChoice by mutableStateOf(SeasonChoice.fromId(store.seasonChoiceId))
        private set
    var season by mutableStateOf(seasonChoice.resolve(weatherSeed))
        private set
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
    /** 사용 통계·오류 보고 보내기(설정). */
    var telemetryOn by mutableStateOf(store.telemetryOn)
        private set
    /** 잠깐 띄우는 안내 문구(문자열 리소스). [messageTick]이 바뀔 때마다 새로 보여 준다. */
    var message by mutableStateOf<Int?>(null)
        private set
    var messageTick by mutableIntStateOf(0)
        private set

    // ── 개발용 ──
    var hud by mutableStateOf("")
        private set
    var land by mutableStateOf<SceneKind?>(null)
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
        scene.season = season
        scene.setTimeOfDay(fixedTime)
        scene.snapTimeOfDay()
        // 이전 수동 날씨 저장값은 읽지 않는다. 계절·코스에 맞춘 자동 날씨로 시작한다.
        applyWeather(null)
        billing.connect()
        applyLand()
        applyStations()
        updateTicks(session.elapsedSeconds)
        // 앱을 다시 열었을 때 진행 중인 여행이 있으면 남은 시간 알림과 알람을 다시 맞춘다.
        if (phase != SessionPhase.DOCKED) syncTimer()
        Telemetry.state(phase.name, course.id, tripMinutes, direction)
        reportSettings()
    }

    /** 프레임마다 호출된다. 여정 시각으로 위치·하늘·날씨를 정하고, 상태 전환(도착·쉬는 시간 끝)을 확인한다. */
    fun onFrame(intervalNanos: Long, refreshHz: Float) {
        val dt = (intervalNanos / 1e9).toFloat().coerceAtMost(.05f)
        applySessionEvent(session.advance())
        val t = session.elapsedSeconds
        val tt = t.coerceIn(0.0, plan.durationSec)
        scene.setTravel(baseDistance + plan.distanceAt(tt), plan.speedAt(tt) * session.rate)
        scene.autoRain = weather.rainAt(tt)
        scene.autoSnow = weather.snowAt(tt)
        val sky = when (phase) {
            SessionPhase.ARRIVED, SessionPhase.RESTING -> 1f
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

    private fun moving() = session.moving

    // ── 사용자 동작 ──

    fun selectMinutes(m: Int) {
        session.selectMinutes(m)
        sessionState = session.state
        minutes = m
        store.minutes = m
        if (phase == SessionPhase.DOCKED) rebuildPlan()
    }

    /** 여행 수첩에 역 도장을 찍는다. [i]는 지금 방향 기준 역 번호. */
    private fun stamp(i: Int) {
        val forward = if (direction > 0) i else course.stations.size - 1 - i
        store.visit(course.id, forward)
        visited = store.visited
    }

    fun completions(c: Course): Int = store.completions(c.id)

    /** 무료이거나 열린 코스인지. */
    fun owns(c: Course): Boolean = c.free || devUnlockAll || c.id in unlocked

    fun priceOf(c: Course): String? = c.productId?.let { prices[it] }

    /** 지금 고른(미리 보는) 코스를 산다. 연결·상품 조회가 안 됐으면 재시도 안내를 띄운다. */
    fun buy(activity: Activity, c: Course = course) {
        val id = c.productId ?: return
        val launched = billing.purchase(activity, id)
        Telemetry.event("purchase_tap", "course" to c.id, "launched" to launched)
        if (!launched) notify(R.string.purchase_unavailable)
    }

    fun restorePurchases() {
        if (restoringPurchases) return
        Telemetry.event("restore_tap", "ready" to billing.ready)
        billing.restore()
    }

    /** 앱 밖에서 완료된 대기 구매도 복귀할 때 확인한다. 초기 연결 조회와 겹치면 합쳐진다. */
    fun onResume() = billing.onResume()

    /** 앱 화면이 보이거나 가려질 때 MainActivity가 알린다. */
    fun setVisible(v: Boolean) {
        visible = v
        applySound()
    }

    fun toggleSound() {
        soundOn = !soundOn
        store.soundOn = soundOn
        applySound()
        reportSettings()
    }

    fun selectSoundLevel(level: Int) {
        soundLevel = level
        store.soundLevel = level
        sound.volume = SOUND_LEVELS[level]
        reportSettings()
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
        reportSettings()
    }

    fun setTelemetry(v: Boolean) {
        telemetryOn = v
        store.telemetryOn = v
        Telemetry.setEnabled(v)
        if (v) {
            Telemetry.state(phase.name, course.id, tripMinutes, direction)
            reportSettings()
        }
    }

    /** 소리·절전 설정을 사용자 속성으로 남긴다. 설정별로 이용 패턴이 다른지 볼 때 쓴다. */
    private fun reportSettings() {
        Telemetry.property("sound", if (soundOn) SOUND_NAMES[soundLevel.coerceIn(0, 2)] else "off")
        Telemetry.property("power_save", powerSave.toString())
    }

    fun toggleDevUnlock() {
        devUnlockAll = !devUnlockAll
        store.devUnlockAll = devUnlockAll
    }

    private fun notify(@StringRes text: Int) {
        message = text
        messageTick++
    }

    /** 잠긴 코스는 출발하지 않는다. 세션 전이의 허용 여부는 도메인이 결정한다. */
    fun depart() {
        if (owns(course)) applySessionEvent(session.depart())
    }

    fun pause() = applySessionEvent(session.pause())

    fun resume() = applySessionEvent(session.resume())

    fun endTrip() = applySessionEvent(session.end())

    fun rest() = applySessionEvent(session.rest())

    fun getOff() = applySessionEvent(session.getOff())

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

    /** 반환된 전이만 한 번 소비한다. 경험별 이동과 저장·알림은 아직 이 어댑터가 처리한다. */
    private fun applySessionEvent(event: SessionEvent?) {
        if (event == null) return
        sessionState = session.state
        when (event) {
            is SessionEvent.Departed -> {
                if (event.from == SessionPhase.ARRIVED || event.from == SessionPhase.RESTING) prepareReturn()
                weatherSeed = Random.nextInt()
                season = seasonChoice.resolve(weatherSeed)
                scene.season = season
                rebuildPlan()
                stamp(0)
                Telemetry.event(
                    "trip_start", "course" to course.id, "minutes" to tripMinutes, "direction" to direction,
                    "from" to event.from.name.lowercase(),
                )
            }
            is SessionEvent.Paused -> Telemetry.event("trip_pause", "course" to course.id, "progress" to event.progressPercent)
            SessionEvent.Resumed -> Telemetry.event("trip_resume", "course" to course.id)
            is SessionEvent.Ended -> {
                Telemetry.event("trip_end_early", "course" to course.id, "minutes" to event.tripMinutes, "progress" to event.progressPercent)
                rebuildPlan()
                cutTick++
            }
            SessionEvent.RestStarted -> Telemetry.event("rest_start", "course" to course.id)
            is SessionEvent.GotOff -> {
                Telemetry.event("trip_get_off", "course" to course.id, "from" to event.from.name.lowercase())
                prepareReturn()
                rebuildPlan()
            }
            is SessionEvent.Arrived -> {
                store.recordArrival(tripMinutes)
                store.recordCompletion(course.id, tripMinutes)
                stamp(stations.size - 1)
                today = store.today()
                lifetime = store.lifetime()
                Telemetry.event("trip_arrive", "course" to course.id, "minutes" to tripMinutes, "late" to event.late)
                if (!event.late) signal(ARRIVAL_PATTERN)
            }
            is SessionEvent.RestFinished -> {
                Telemetry.event("rest_end", "course" to course.id, "late" to event.late)
                prepareReturn()
                rebuildPlan()
                if (!event.late) signal(REST_PATTERN)
            }
        }
        save()
    }

    /** 종착역의 풍경을 이어 붙이고 방향을 뒤집는 처리는 기차 경험에만 해당한다. */
    private fun prepareReturn() {
        baseDistance += plan.totalDistance
        direction = -direction
        store.direction = direction
    }

    private fun rebuildPlan() {
        plan = CoursePlan(tripMinutes * 60.0, course.stations.size, course.cruise)
        // 역 수가 다른 코스로 바뀌면 다음 틱 전까지 이전 코스의 역 번호가 남는다(개발용 코스 강제 전환 등).
        nextStop = nextStop.coerceIn(1, plan.segments)
        if (stoppedAt >= plan.segments) stoppedAt = -1
        weather = WeatherDirector(weatherSeed, course.snowy, season)
        applyStations()
    }

    /** 역 위치·이름판·진행 바 표시·코스 구역을 지금 계획에 맞춘다. */
    private fun applyStations() {
        scene.theme = course.theme
        scene.snowy = course.snowy
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
            SessionPhase.RUNNING, SessionPhase.PAUSED -> {
                val left = ceil(plan.durationSec - t).toInt().coerceAtLeast(0)
                if (left != remainingSec) {
                    remainingSec = left
                    progress = (t / plan.durationSec).toFloat().coerceIn(0f, 1f)
                    val stop = plan.stoppedAt(t)
                    if (stop >= 0 && stoppedAt < 0) {
                        sound.chime()
                        stamp(stop)
                    }
                    stoppedAt = stop
                    val next = if (stop >= 0) stop + 1 else plan.segmentAt(t) + 1
                    nextStop = min(next, plan.segments)
                    nextStopSec = ceil(plan.arrivalAt(nextStop) - t).toInt().coerceAtLeast(0)
                }
            }
            SessionPhase.RESTING -> {
                val left = session.restRemainingSeconds
                if (left != restLeftSec) restLeftSec = left
            }
            else -> {
                remainingSec = tripMinutes * 60
                progress = if (phase == SessionPhase.ARRIVED) 1f else 0f
                stoppedAt = -1
                nextStop = 1
            }
        }
    }

    /** 카드는 실내와 같은 재질로 보이게, 밤이면 종이색을 벽색 쪽으로 조금 어둡게 한다(명세 4.4). */
    private fun updateSurfaces() {
        val p = scene.effective
        val wall = p.c[Palette.PANEL]
        val sheet = scene.paperColor(PAPER)
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
     * 남은 시간 알림과 알람을 지금 상태에 맞춘다. 달리는 중·멈춘 동안·쉬는 중에만 알림을 띄운다.
     * 개발용 빨리 감기 중에는 실제 시간과 어긋나므로 알림 시계를 빼고 문구만 띄운다.
     * 알림 문구는 앱 언어로 여기서 미리 만들어 넘긴다.
     */
    private fun syncTimer() {
        val ctx = getApplication<Application>()
        val res = AppLanguage.localized(ctx).resources
        val title = course.title(res, direction)
        val terminus = res.getString(stations.last().name)
        val now = SystemClock.elapsedRealtime()
        val realtime = session.scale == 1.0
        when (phase) {
            SessionPhase.RUNNING -> {
                val left = (plan.durationSec - session.elapsedSeconds).coerceAtLeast(0.0)
                TripTimer.update(
                    ctx, title, res.getString(R.string.notif_running, course.stopsLine(res, direction), terminus),
                    if (realtime) now + (left * 1000).toLong() else 0L,
                    res.getString(R.string.arrived_at, terminus), res.getString(R.string.notif_arrived, tripMinutes),
                )
            }
            SessionPhase.PAUSED -> {
                val left = (plan.durationSec - session.elapsedSeconds).coerceAtLeast(0.0).toInt()
                TripTimer.update(ctx, title, res.getString(R.string.notif_paused, formatClock(left)), 0L, "", "")
            }
            SessionPhase.RESTING -> {
                val next = course.title(res, -direction)
                TripTimer.update(
                    ctx, res.getString(R.string.resting_at, terminus), res.getString(R.string.notif_next_trip, next),
                    sessionState.restEndElapsedMillis, res.getString(R.string.rest_over), res.getString(R.string.notif_ticket_ready, next),
                )
            }
            else -> TripTimer.stop(ctx)
        }
    }

    // ── 저장·복원 ──

    private fun save() {
        Telemetry.state(phase.name, course.id, tripMinutes, direction)
        syncTimer()
        val snapshot = session.snapshot()
        store.saveActive(
            JourneyStore.Active(
                snapshot.state.phase.name, snapshot.state.tripMinutes, baseDistance, snapshot.clock,
                snapshot.state.restEndElapsedMillis, snapshot.state.restDone, weatherSeed,
            ),
        )
    }

    private fun restore() {
        val a = store.loadActive() ?: return
        val restored = runCatching {
            session.restore(
                SessionSnapshot(
                    SessionState(
                        runCatching { SessionPhase.valueOf(a.phase) }.getOrDefault(SessionPhase.DOCKED),
                        a.tripMinutes, a.restEnd, a.restDone,
                    ),
                    a.clock,
                ),
            )
        }
        if (restored.isFailure) return
        sessionState = session.state
        timeScale = session.scale
        baseDistance = a.base
        weatherSeed = a.weatherSeed
        season = seasonChoice.resolve(weatherSeed)
        plan = CoursePlan(tripMinutes * 60.0, course.stations.size, course.cruise)
        weather = WeatherDirector(weatherSeed, course.snowy, season)
    }

    /** 코스를 바꾼다. 정차 중에만 되고, 새 코스의 출발역으로 순간 이동한다. */
    fun selectCourse(c: Course) {
        if (phase != SessionPhase.DOCKED || c == course) return
        Telemetry.event("course_select", "course" to c.id, "owned" to owns(c))
        course = c
        store.courseId = c.id
        direction = 1
        store.direction = 1
        baseDistance = 0.0
        session.reset()
        sessionState = session.state
        rebuildPlan()
        cutTick++
        save()
    }

    // ── 개발용 ──

    /** 개발용: 여행 중이어도 끊고 코스를 바꾼다. */
    fun forceCourse(c: Course) {
        session.reset()
        sessionState = session.state
        if (c != course) {
            selectCourse(c)
        } else {
            rebuildPlan()
            cutTick++
            save()
        }
    }

    fun selectLand(k: SceneKind?) {
        land = k
        applyLand()
    }

    fun selectSeason(value: Season) = selectSeasonChoice(SeasonChoice.fromSeason(value))

    fun selectSeasonChoice(value: SeasonChoice) {
        seasonChoice = value
        store.seasonChoiceId = value.id
        season = value.resolve(weatherSeed)
        applyWeather(null)
        scene.season = season
        weather = WeatherDirector(weatherSeed, course.snowy, season)
    }

    fun selectTime(t: TimeOfDay?) {
        fixedTime = t
        store.timeOfDayId = TimeOfDay.fromSetting(t?.name)?.name
        scene.setTimeOfDay(t)
    }

    /**
     * 개발용: 정방향 기준 노선 위치 [pos](구간 단위, 2.97이면 2번 구간의 97%)로 옮긴다.
     * 랜드마크·구역은 이 단위로 정의하므로 그 자리를 바로 확인할 때 쓴다.
     */
    fun seekPosition(pos: Double) {
        if (!moving()) depart()
        if (!moving()) return
        val p = pos.coerceIn(0.0, plan.segments.toDouble())
        val target = (if (direction < 0) plan.segments - p else p) * plan.segDist
        // 거리는 시간에 대해 줄지 않으므로 이분 탐색으로 그 거리에 닿는 시각을 찾는다.
        var lo = 0.0
        var hi = plan.durationSec
        repeat(60) {
            val mid = (lo + hi) / 2
            if (plan.distanceAt(mid) < target) lo = mid else hi = mid
        }
        session.seek(hi)
        save()
    }

    /** 세션의 [fraction](0~1) 지점으로 옮긴다. 달리는 중이 아니면 먼저 출발한다. */
    fun seekFraction(fraction: Float) {
        if (!moving()) depart()
        // 잠긴 코스라 출발하지 못했으면 건너뛰지 않는다.
        if (!moving()) return
        session.seek(plan.durationSec * fraction.coerceIn(0f, 1f))
        save()
    }

    /** 도착 20초 전으로 건너뛴다. */
    fun seekNearEnd() {
        if (!moving()) depart()
        if (!moving()) return
        seekFraction(((plan.durationSec - 20) / plan.durationSec).toFloat())
    }

    /** 다음 정차역 5초 전으로 건너뛴다. */
    fun seekNextStop() {
        if (!moving()) depart()
        if (!moving()) return
        session.seek((plan.arrivalAt(nextStop) - 5).coerceAtLeast(0.0))
        save()
    }

    /** 쉬는 시간을 바로 끝낸다. */
    fun skipRest() {
        session.skipRest()
        sessionState = session.state
    }

    fun selectScale(s: Double) {
        session.setScale(s)
        timeScale = s
        save()
    }

    fun selectMode(m: RenderMode) {
        mode = m
        scene.mode = m
    }

    /** 개발 검증용 날씨 고정. 일반 설정에 노출하거나 저장하지 않는다. */
    fun selectWeather(w: Weather?) {
        if (options.developerTools) applyWeather(w)
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
            scene.urban.at(scene.distanceU), scene.speed, getApplication<Application>().getString(scene.timeOfDay.label),
            scene.rainAmount, scene.snowAmount,
        )
    }

    override fun onCleared() {
        sound.stop()
        billing.end()
        renderer.release()
    }

    class Factory(
        private val app: Application,
        private val options: JourneyOptions,
        private val store: JourneyStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == JourneyViewModel::class.java)
            return JourneyViewModel(app, options, store) as T
        }
    }

    private companion object {
        const val MB = 1024.0 * 1024.0
        val PAPER = Pb.Paper.toArgb()
        val ARRIVAL_PATTERN = longArrayOf(0, 180, 140, 180, 140, 320)
        val REST_PATTERN = longArrayOf(0, 120, 120, 120)
        val SOUND_LEVELS = floatArrayOf(.35f, .6f, .9f)
        val SOUND_NAMES = arrayOf("low", "mid", "high")
    }
}
