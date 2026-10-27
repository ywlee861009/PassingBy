package com.kero.passingby.ui.journey

import com.kero.passingby.feature.journey.R
import android.app.Activity
import android.app.Application
import android.os.Debug
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
import com.kero.passingby.experience.*
import com.kero.passingby.experience.JourneyCourse as Course
import com.kero.passingby.experience.JourneyStation as Station
import com.kero.passingby.billing.Billing
import com.kero.passingby.billing.RestoreStatus
import com.kero.passingby.journey.SessionRuntime
import com.kero.passingby.journey.SessionEvent
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.perf.FrameStats
import com.kero.passingby.session.JourneyStore
import com.kero.passingby.telemetry.Telemetry
import com.kero.passingby.ui.theme.Pb
import java.util.Locale
import kotlin.math.ceil

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
    private val session: SessionRuntime,
    private val registry: ExperienceRegistry,
) : AndroidViewModel(app) {
    private val experience: ExperienceProgram get() = session.program as ExperienceProgram
    val scene: ExperienceScene get() = experience.scene
    val renderer: ExperienceRenderer get() = experience.renderer
    val journeyMode: JourneyMode get() = registry.modeFor(course.id)
    val courses: List<Course> get() = registry.coursesFor(journeyMode)
    var previewUnlocked by mutableStateOf(options.modeAccess.unlocked)
        private set
    val availableModes: List<JourneyMode> get() = registry.modes.filter { previewUnlocked || it in options.modeAccess.publicModes }
    val journalCourses: List<Course> get() = registry.courses.filter { registry.modeFor(it.id) in availableModes }
    val showModeMenu: Boolean get() = previewUnlocked || availableModes.size > 1

    fun unlockModes(code: String): Boolean {
        val accepted = options.modeAccess.unlock(code)
        previewUnlocked = options.modeAccess.unlocked
        return accepted
    }

    fun selectJourneyMode(mode: JourneyMode): Boolean {
        if (mode !in availableModes || phase != SessionPhase.DOCKED) return false
        if (mode != journeyMode) registry.coursesFor(mode).firstOrNull()?.let { changeCourse(it, false) }
        return journeyMode == mode
    }

    fun lockPreviewModes() {
        if (phase != SessionPhase.DOCKED) return
        if (journeyMode !in options.modeAccess.publicModes) selectJourneyMode(JourneyMode.TRAIN)
        options.modeAccess.lock()
        previewUnlocked = false
    }
    private val stats = FrameStats()
    private var sessionState by mutableStateOf(session.state)
    private val unsubscribe: () -> Unit

    var course by mutableStateOf(experience.course)
        private set
    /** 1이면 정방향(서울 → 부산), −1이면 역방향. */
    var direction by mutableIntStateOf(experience.direction)
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
    /** 세션과 같은 시계로 계산한 현재 지역 안내. 렌더링 프레임과 독립적으로 갱신한다. */
    var routeCaption by mutableStateOf<RouteCaption?>(null)
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
        registry.courses.mapNotNull { it.productId },
        onPrices = { prices = it },
        onOwned = { ids, _ ->
            val courses = courses.filter { it.productId in ids }.map { it.id }.toSet()
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
    var season by mutableStateOf(scene.season)
        private set
    var soundOn by mutableStateOf(store.soundOn)
        private set
    var soundLevel by mutableIntStateOf(store.soundLevel)
        private set
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
    var mode by mutableStateOf(RenderMode.BAKED)
        private set
    /** 직접 고른 날씨. null이면 무작위. */
    var fixedWeather by mutableStateOf<Weather?>(null)
        private set
    var boxes by mutableStateOf(false)
        private set
    var windowFrame by mutableStateOf(true)
        private set
    var cap60 by mutableStateOf(false)
        private set
    var showHud by mutableStateOf(false)
        private set

    init {
        unsubscribe = session.observe { event ->
            syncPresentation()
            if (event is SessionEvent.Ended) cutTick++
        }
        syncPresentation()
        experience.selectSeasonChoice(seasonChoice)
        experience.selectTime(fixedTime)
        experience.selectLand(null)
        experience.selectMode(mode)
        experience.setBoxes(boxes)
        experience.setWindowFrame(windowFrame)
        billing.connect()
        experience.setSoundLevel(soundLevel)
        reportSettings()
    }

    /** Rendering samples the current clock; it never advances sessions, writes storage or sends alerts. */
    fun onFrame(intervalNanos: Long, refreshHz: Float) {
        val dt = (intervalNanos / 1e9).toFloat().coerceAtMost(.05f)
        experience.frame(session.elapsedSeconds, session.rate, phase, dt)
        updateSurfaces()
        if (stats.record(intervalNanos, renderer.lastDrawNanos, refreshHz) && showHud) hud = hudText(refreshHz, session.elapsedSeconds)
    }

    private fun syncPresentation() {
        sessionState = session.state
        course = experience.course
        direction = experience.direction
        minutes = store.minutes
        season = scene.season
        timeScale = session.scale
        stationMarks = experience.stationMarks
        today = store.today()
        lifetime = store.lifetime()
        visited = store.visited
        val t = session.elapsedSeconds
        routeCaption = if (phase == SessionPhase.RUNNING || phase == SessionPhase.PAUSED)
            experience.routeCaptionAt(t) else null
        updateTicks(t)
    }

    private fun moving() = session.moving

    // ── 사용자 동작 ──

    fun selectMinutes(m: Int) = session.selectMinutes(m)

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
        experience.setSoundLevel(level)
        reportSettings()
    }

    private fun applySound() = experience.setVisible(visible, soundOn)

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
        if (owns(course)) session.depart()
    }

    fun pause() = session.pause()

    fun resume() = session.resume()

    fun endTrip() = session.end()

    fun rest() = session.rest()

    fun getOff() = session.getOff()

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

    private fun updateTicks(t: Double) {
        when (phase) {
            SessionPhase.RUNNING, SessionPhase.PAUSED -> {
                val left = ceil(session.durationSeconds - t).toInt().coerceAtLeast(0)
                if (left != remainingSec) {
                    remainingSec = left
                    progress = (t / session.durationSeconds).toFloat().coerceIn(0f, 1f)
                    val stop = experience.stoppedAt(t)
                    if (stop >= 0 && stoppedAt < 0) {
                        experience.chime()
                    }
                    stoppedAt = stop
                    nextStop = experience.nextStop(t)
                    nextStopSec = ceil(experience.arrivalAt(nextStop) - t).toInt().coerceAtLeast(0)
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
                nextStopSec = ceil(experience.arrivalAt(nextStop)).toInt().coerceAtLeast(0)
            }
        }
    }

    /** 카드는 실내와 같은 재질로 보이게, 밤이면 종이색을 벽색 쪽으로 조금 어둡게 한다(명세 4.4). */
    private fun updateSurfaces() {
        val wall = scene.panelColor
        val sheet = scene.paperColor(PAPER)
        if (wall != panel) panel = wall
        if (sheet != paper) paper = sheet
    }

    fun courseById(id: String?): Course = registry.courseById(id)

    fun selectCourse(c: Course) {
        if (phase != SessionPhase.DOCKED || c.id == course.id) return
        Telemetry.event("course_select", "course" to c.id, "owned" to owns(c))
        changeCourse(c, force = false)
    }

    private fun changeCourse(c: Course, force: Boolean) {
        if (registry.modeFor(c.id) !in availableModes) return
        val old = experience
        val replacement = if (registry.providerFor(c.id).id != registry.providerFor(course.id).id)
            registry.create(c.id, store) else null
        if (session.selectCourse(c.id, force, replacement)) {
            if (replacement != null) {
                old.releasePresentation()
                experience.selectTime(fixedTime)
                experience.selectSeasonChoice(seasonChoice)
                experience.setSoundLevel(soundLevel)
                experience.setWindowFrame(windowFrame)
                experience.selectMode(mode)
                applySound()
                syncPresentation()
            }
            cutTick++
        } else replacement?.close()
    }

    // ── 개발용 ──

    /** 개발용: 여행 중이어도 끊고 코스를 바꾼다. */
    fun forceCourse(c: Course) = changeCourse(c, force = true)

    fun selectLand(k: SceneKind?) {
        land = k
        experience.selectLand(k)
    }

    fun selectSeason(value: Season) = selectSeasonChoice(SeasonChoice.fromSeason(value))

    fun selectSeasonChoice(value: SeasonChoice) {
        seasonChoice = value
        store.seasonChoiceId = value.id
        experience.selectSeasonChoice(value)
        fixedWeather = null
        season = scene.season
        session.checkpoint()
    }

    fun selectTime(t: TimeOfDay?) {
        fixedTime = t
        store.timeOfDayId = TimeOfDay.fromSetting(t?.name)?.name
        experience.selectTime(t)
    }

    /**
     * 개발용: 정방향 기준 노선 위치 [pos](구간 단위, 2.97이면 2번 구간의 97%)로 옮긴다.
     * 랜드마크·구역은 이 단위로 정의하므로 그 자리를 바로 확인할 때 쓴다.
     */
    fun seekPosition(pos: Double) {
        if (!moving()) depart()
        if (!moving()) return
        session.seek(experience.secondsAtPosition(pos))
    }

    /** 세션의 [fraction](0~1) 지점으로 옮긴다. 달리는 중이 아니면 먼저 출발한다. */
    fun seekFraction(fraction: Float) {
        if (!moving()) depart()
        // 잠긴 코스라 출발하지 못했으면 건너뛰지 않는다.
        if (!moving()) return
        session.seek(session.durationSeconds * fraction.coerceIn(0f, 1f))
    }

    /** 도착 20초 전으로 건너뛴다. */
    fun seekNearEnd() {
        if (!moving()) depart()
        if (!moving()) return
        seekFraction(((session.durationSeconds - 20) / session.durationSeconds).toFloat())
    }

    /** 다음 정차역 5초 전으로 건너뛴다. */
    fun seekNextStop() {
        if (!moving()) depart()
        if (!moving()) return
        session.seek((experience.arrivalAt(nextStop) - 5).coerceAtLeast(0.0))
    }

    /** 쉬는 시간을 바로 끝낸다. */
    fun skipRest() {
        session.skipRest()
        sessionState = session.state
    }

    fun selectScale(s: Double) {
        session.setScale(s)
        timeScale = s
    }

    fun selectMode(m: RenderMode) {
        mode = m
        experience.selectMode(m)
    }

    /** 개발 검증용 날씨 고정. 일반 설정에 노출하거나 저장하지 않는다. */
    fun selectWeather(w: Weather?) {
        if (options.developerTools) applyWeather(w)
    }

    private fun applyWeather(w: Weather?) {
        fixedWeather = w
        experience.selectWeather(w)
    }

    fun toggleBoxes() {
        boxes = !boxes
        experience.setBoxes(boxes)
    }

    fun toggleWindowFrame() {
        windowFrame = !windowFrame
        experience.setWindowFrame(windowFrame)
    }

    fun toggleCap60() {
        cap60 = !cap60
    }

    fun toggleHud() {
        showHud = !showHud
    }

    private fun hudText(refreshHz: Float, t: Double): String {
        val rt = Runtime.getRuntime()
        val shown = t.coerceIn(0.0, session.durationSeconds).toLong()
        val total = session.durationSeconds.toLong()
        return String.format(
            Locale.US,
            "%.0fHz · %s\n프레임 %.1fms (p95 %.1f) · 끊김 %d\n그리기 기록 %.2fms (최대 %.1f)\n비트맵 %.1fMB · 네이티브 %.1fMB · 자바 %.1fMB\n%s %d:%02d / %d:%02d ×%.0f · 도시 %.2f · 속도 %.0f · %s · 비 %.2f 눈 %.2f",
            refreshHz, mode.label,
            stats.avgInterval(), stats.p95Interval(), stats.janks,
            stats.avgDraw(), stats.maxDraw(),
            renderer.bitmapBytes / MB, Debug.getNativeHeapAllocatedSize() / MB, (rt.totalMemory() - rt.freeMemory()) / MB,
            phase.name, shown / 60, shown % 60, total / 60, total % 60, timeScale,
            scene.urbanDensity, scene.speed, getApplication<Application>().getString(scene.timeOfDay.label),
            scene.rainAmount, scene.snowAmount,
        )
    }

    override fun onCleared() {
        unsubscribe()
        experience.releasePresentation()
        billing.end()
    }

    class Factory(
        private val app: Application,
        private val options: JourneyOptions,
        private val store: JourneyStore,
        private val session: () -> SessionRuntime,
        private val registry: ExperienceRegistry,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == JourneyViewModel::class.java)
            return JourneyViewModel(app, options, store, session(), registry) as T
        }
    }

    private companion object {
        const val MB = 1024.0 * 1024.0
        val PAPER = Pb.Paper.toArgb()
        val SOUND_NAMES = arrayOf("low", "mid", "high")
    }
}
