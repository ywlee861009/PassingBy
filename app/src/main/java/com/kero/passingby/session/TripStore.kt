package com.kero.passingby.session

import android.content.Context
import android.provider.Settings
import com.kero.passingby.scene.Season
import com.kero.passingby.scene.TimeOfDay
import java.time.LocalDate

/**
 * 앱을 다시 열어도 남아야 하는 값. 값이 몇 개 안 돼서 SharedPreferences로 둔다.
 *
 * 진행 중인 여행도 여기에 둔다. SavedStateHandle은 최근 앱 목록에서 앱을 지우면 사라지지만,
 * 여정 시계는 앱이 닫혀 있어도 흐르므로(남은 시간 알림과 도착 알람은 앱 없이 동작) 앱을 다시 열었을 때 이어 붙여야 한다.
 * 여정 시계는 elapsedRealtime 기준이라 재부팅하면 무효가 되므로 부팅 횟수를 함께 저장해 확인한다.
 */
class TripStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("trip", Context.MODE_PRIVATE)

    var courseId: String?
        get() = prefs.getString(KEY_COURSE, null)
        set(v) = prefs.edit().putString(KEY_COURSE, v).apply()

    var direction: Int
        get() = prefs.getInt(KEY_DIRECTION, 1)
        set(v) = prefs.edit().putInt(KEY_DIRECTION, v).apply()

    var minutes: Int
        get() = prefs.getInt(KEY_MINUTES, 25)
        set(v) = prefs.edit().putInt(KEY_MINUTES, v).apply()

    var notificationAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(v) = prefs.edit().putBoolean(KEY_NOTIF_ASKED, v).apply()

    var hintShown: Boolean
        get() = prefs.getBoolean(KEY_HINT, false)
        set(v) = prefs.edit().putBoolean(KEY_HINT, v).apply()

    /** 오늘 도착한 횟수와 집중한 분. 날짜가 바뀌면 0부터 센다. */
    fun today(): Pair<Int, Int> {
        if (prefs.getString(KEY_DAY, null) != LocalDate.now().toString()) return 0 to 0
        return prefs.getInt(KEY_DAY_TRIPS, 0) to prefs.getInt(KEY_DAY_MINUTES, 0)
    }

    fun recordArrival(minutes: Int) {
        val (trips, total) = today()
        prefs.edit()
            .putString(KEY_DAY, LocalDate.now().toString())
            .putInt(KEY_DAY_TRIPS, trips + 1)
            .putInt(KEY_DAY_MINUTES, total + minutes)
            .apply()
    }

    // ── 여행 수첩 ──

    /** 들른 역("코스id:정방향 역 번호"). */
    var visited: Set<String>
        get() = prefs.getStringSet(KEY_VISITED, emptySet()) ?: emptySet()
        private set(v) = prefs.edit().putStringSet(KEY_VISITED, v).apply()

    fun visit(courseId: String, station: Int) {
        val key = "$courseId:$station"
        if (key !in visited) visited = visited + key
    }

    /** 코스를 끝까지 달린 횟수. */
    fun completions(courseId: String): Int = prefs.getInt(KEY_DONE + courseId, 0)

    /** 지금까지 도착한 횟수와 집중한 분. */
    fun lifetime(): Pair<Int, Int> = prefs.getInt(KEY_ALL_TRIPS, 0) to prefs.getInt(KEY_ALL_MINUTES, 0)

    fun recordCompletion(courseId: String, minutes: Int) {
        val (trips, total) = lifetime()
        prefs.edit()
            .putInt(KEY_DONE + courseId, completions(courseId) + 1)
            .putInt(KEY_ALL_TRIPS, trips + 1)
            .putInt(KEY_ALL_MINUTES, total + minutes)
            .apply()
    }

    // ── 코스 잠금 ──

    /** 열린 유료 코스 id. 결제 확인·구매 복원으로 채워진다. */
    var unlocked: Set<String>
        get() = prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_UNLOCKED, v).apply()

    /** 개발용: 모든 코스 열기. */
    var devUnlockAll: Boolean
        get() = prefs.getBoolean(KEY_DEV_UNLOCK, false)
        set(v) = prefs.edit().putBoolean(KEY_DEV_UNLOCK, v).apply()

    // ── 설정 ──

    /** 창 아래 벽에 다음 역까지 남은 분을 늘 표시(기본 꺼짐, 명세 6장). */
    var ledgeInfo: Boolean
        get() = prefs.getBoolean(KEY_LEDGE, false)
        set(v) = prefs.edit().putBoolean(KEY_LEDGE, v).apply()

    /** 주행 중에도 오버레이를 숨기지 않는다. */
    var overlayAlways: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, false)
        set(v) = prefs.edit().putBoolean(KEY_OVERLAY, v).apply()

    /** 절전 모드(30fps). */
    var powerSave: Boolean
        get() = prefs.getBoolean(KEY_POWER, false)
        set(v) = prefs.edit().putBoolean(KEY_POWER, v).apply()

    /** 환경음 켜짐(기본 켜짐)과 크기(0 작게, 1 보통, 2 크게). */
    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) = prefs.edit().putBoolean(KEY_SOUND, v).apply()

    var soundLevel: Int
        get() = prefs.getInt(KEY_SOUND_LEVEL, 1)
        set(v) = prefs.edit().putInt(KEY_SOUND_LEVEL, v).apply()

    /** 사용 통계·오류 보고를 Firebase로 보낼지(기본 켜짐). 끄면 [com.kero.passingby.telemetry.Telemetry]가 수집을 멈춘다. */
    var telemetryOn: Boolean
        get() = prefs.getBoolean(KEY_TELEMETRY, true)
        set(v) = prefs.edit().putBoolean(KEY_TELEMETRY, v).apply()

    /** null은 자동 순환. 이전 새벽/노을은 낮/밤으로 복원한다. */
    var timeOfDay: TimeOfDay?
        get() = TimeOfDay.fromSetting(prefs.getString(KEY_TIME, null))
        set(v) = prefs.edit().putString(KEY_TIME, TimeOfDay.fromSetting(v?.name)?.name).apply()

    var season: Season
        get() = Season.fromId(prefs.getString(KEY_SEASON, null))
        set(v) = prefs.edit().putString(KEY_SEASON, v.id).apply()

    // ── 진행 중인 여행 ──

    class Active(
        val phase: String,
        val tripMinutes: Int,
        val base: Double,
        val clock: DoubleArray,
        val restEnd: Long,
        val restDone: Boolean,
        val weatherSeed: Int,
    )

    fun saveActive(a: Active) {
        prefs.edit()
            .putString(KEY_A_PHASE, a.phase)
            .putInt(KEY_A_MINUTES, a.tripMinutes)
            .putString(KEY_A_BASE, a.base.toString())
            .putString(KEY_A_CLOCK, a.clock.joinToString(","))
            .putLong(KEY_A_REST_END, a.restEnd)
            .putBoolean(KEY_A_REST_DONE, a.restDone)
            .putInt(KEY_A_SEED, a.weatherSeed)
            .putInt(KEY_A_BOOT, bootCount())
            .apply()
    }

    /** 저장된 여행. 재부팅으로 시계가 무효가 됐으면 null. */
    fun loadActive(): Active? {
        val phase = prefs.getString(KEY_A_PHASE, null) ?: return null
        if (prefs.getInt(KEY_A_BOOT, -1) != bootCount()) return null
        val clock = prefs.getString(KEY_A_CLOCK, null)?.split(",")?.mapNotNull { it.toDoubleOrNull() }?.toDoubleArray()
            ?: return null
        return Active(
            phase,
            prefs.getInt(KEY_A_MINUTES, 25),
            prefs.getString(KEY_A_BASE, null)?.toDoubleOrNull() ?: 0.0,
            clock,
            prefs.getLong(KEY_A_REST_END, 0L),
            prefs.getBoolean(KEY_A_REST_DONE, false),
            prefs.getInt(KEY_A_SEED, 0),
        )
    }

    private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private companion object {
        const val KEY_COURSE = "course"
        const val KEY_DIRECTION = "direction"
        const val KEY_MINUTES = "minutes"
        const val KEY_HINT = "hint_shown"
        const val KEY_NOTIF_ASKED = "notification_asked"
        const val KEY_DAY = "day"
        const val KEY_DAY_TRIPS = "day_trips"
        const val KEY_DAY_MINUTES = "day_minutes"
        const val KEY_VISITED = "visited"
        const val KEY_DONE = "done_"
        const val KEY_ALL_TRIPS = "all_trips"
        const val KEY_ALL_MINUTES = "all_minutes"
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_DEV_UNLOCK = "dev_unlock_all"
        const val KEY_LEDGE = "ledge_info"
        const val KEY_OVERLAY = "overlay_always"
        const val KEY_POWER = "power_save"
        const val KEY_TIME = "time_of_day"
        const val KEY_SEASON = "season"
        const val KEY_SOUND = "sound"
        const val KEY_SOUND_LEVEL = "sound_level"
        const val KEY_TELEMETRY = "telemetry"
        const val KEY_A_PHASE = "a_phase"
        const val KEY_A_MINUTES = "a_minutes"
        const val KEY_A_BASE = "a_base"
        const val KEY_A_CLOCK = "a_clock"
        const val KEY_A_REST_END = "a_rest_end"
        const val KEY_A_REST_DONE = "a_rest_done"
        const val KEY_A_SEED = "a_seed"
        const val KEY_A_BOOT = "a_boot"
    }
}
