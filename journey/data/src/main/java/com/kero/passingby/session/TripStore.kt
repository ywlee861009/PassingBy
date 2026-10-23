package com.kero.passingby.session

import android.content.Context
import android.provider.Settings
import com.kero.passingby.session.JourneyStore.Active
import java.time.LocalDate

/**
 * 앱을 다시 열어도 남아야 하는 값. 값이 몇 개 안 돼서 SharedPreferences로 둔다.
 *
 * 진행 중인 여행도 여기에 둔다. SavedStateHandle은 최근 앱 목록에서 앱을 지우면 사라지지만,
 * 여정 시계는 앱이 닫혀 있어도 흐르므로(남은 시간 알림과 도착 알람은 앱 없이 동작) 앱을 다시 열었을 때 이어 붙여야 한다.
 * 여정 시계는 elapsedRealtime 기준이라 재부팅하면 무효가 되므로 부팅 횟수를 함께 저장해 확인한다.
 */
class TripStore(private val context: Context) : JourneyStore {
    private val prefs = context.getSharedPreferences("trip", Context.MODE_PRIVATE)

    override var courseId: String?
        get() = prefs.getString(KEY_COURSE, null)
        set(v) = prefs.edit().putString(KEY_COURSE, v).apply()

    override var direction: Int
        get() = prefs.getInt(KEY_DIRECTION, 1)
        set(v) = prefs.edit().putInt(KEY_DIRECTION, v).apply()

    override var minutes: Int
        get() = prefs.getInt(KEY_MINUTES, 25)
        set(v) = prefs.edit().putInt(KEY_MINUTES, v).apply()

    override var notificationAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(v) = prefs.edit().putBoolean(KEY_NOTIF_ASKED, v).apply()

    override var hintShown: Boolean
        get() = prefs.getBoolean(KEY_HINT, false)
        set(v) = prefs.edit().putBoolean(KEY_HINT, v).apply()

    /** 오늘 도착한 횟수와 집중한 분. 날짜가 바뀌면 0부터 센다. */
    override fun today(): Pair<Int, Int> {
        if (prefs.getString(KEY_DAY, null) != LocalDate.now().toString()) return 0 to 0
        return prefs.getInt(KEY_DAY_TRIPS, 0) to prefs.getInt(KEY_DAY_MINUTES, 0)
    }

    // ── 여행 수첩 ──

    /** 들른 역("코스id:정방향 역 번호"). */
    override val visited: Set<String>
        get() = prefs.getStringSet(KEY_VISITED, emptySet()) ?: emptySet()

    /** 코스를 끝까지 달린 횟수. */
    override fun completions(courseId: String): Int = prefs.getInt(KEY_DONE + courseId, 0)

    /** 지금까지 도착한 횟수와 집중한 분. */
    override fun lifetime(): Pair<Int, Int> = prefs.getInt(KEY_ALL_TRIPS, 0) to prefs.getInt(KEY_ALL_MINUTES, 0)

    // ── 코스 잠금 ──

    /** 열린 유료 코스 id. 결제 확인·구매 복원으로 채워진다. */
    override var unlocked: Set<String>
        get() = prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_UNLOCKED, v).apply()

    /** 개발용: 모든 코스 열기. */
    override var devUnlockAll: Boolean
        get() = prefs.getBoolean(KEY_DEV_UNLOCK, false)
        set(v) = prefs.edit().putBoolean(KEY_DEV_UNLOCK, v).apply()

    // ── 설정 ──

    /** 창 아래 벽에 다음 역까지 남은 분을 늘 표시(기본 꺼짐, 명세 6장). */
    override var ledgeInfo: Boolean
        get() = prefs.getBoolean(KEY_LEDGE, false)
        set(v) = prefs.edit().putBoolean(KEY_LEDGE, v).apply()

    /** 주행 중에도 오버레이를 숨기지 않는다. */
    override var overlayAlways: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, false)
        set(v) = prefs.edit().putBoolean(KEY_OVERLAY, v).apply()

    /** 절전 모드(30fps). */
    override var powerSave: Boolean
        get() = prefs.getBoolean(KEY_POWER, false)
        set(v) = prefs.edit().putBoolean(KEY_POWER, v).apply()

    /** 환경음 켜짐(기본 켜짐)과 크기(0 작게, 1 보통, 2 크게). */
    override var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) = prefs.edit().putBoolean(KEY_SOUND, v).apply()

    override var soundLevel: Int
        get() = prefs.getInt(KEY_SOUND_LEVEL, 1)
        set(v) = prefs.edit().putInt(KEY_SOUND_LEVEL, v).apply()

    /** 사용 통계·오류 보고를 Firebase로 보낼지(기본 켜짐). 끄면 telemetry 어댑터가 수집을 멈춘다. */
    override var telemetryOn: Boolean
        get() = prefs.getBoolean(KEY_TELEMETRY, true)
        set(v) = prefs.edit().putBoolean(KEY_TELEMETRY, v).apply()

    /** null은 자동 순환. 값의 해석은 경험 구현에서 한다. */
    override var timeOfDayId: String?
        get() = prefs.getString(KEY_TIME, null)
        set(v) = prefs.edit().putString(KEY_TIME, v).apply()

    override var seasonChoiceId: String
        get() = prefs.getString(KEY_SEASON, "summer") ?: "summer"
        set(v) = prefs.edit().putString(KEY_SEASON, v).apply()

    // ── 진행 중인 여행 ──

    private fun android.content.SharedPreferences.Editor.active(active: Active) = apply {
        putString(KEY_A_PHASE, active.phase)
        putInt(KEY_A_MINUTES, active.tripMinutes)
        putString(KEY_A_BASE, active.base.toString())
        putString(KEY_A_CLOCK, active.clock.joinToString(","))
        putLong(KEY_A_REST_END, active.restEnd)
        putBoolean(KEY_A_REST_DONE, active.restDone)
        putInt(KEY_A_SEED, active.weatherSeed)
        putString(KEY_A_ID, active.sessionId)
        putInt(KEY_A_BOOT, bootCount())
    }

    override fun saveSession(active: Active, courseId: String, direction: Int, stations: Set<Int>, arrived: Boolean, effect: CompletionEffect?) {
        require(active.sessionId.isNotBlank())
        synchronized(prefs) {
            val editor = prefs.edit().active(active)
                .putString(KEY_COURSE, courseId).putInt(KEY_DIRECTION, direction)
            if (stations.isNotEmpty()) editor.putStringSet(KEY_VISITED, visited + stations.map { "$courseId:$it" })
            if (arrived && prefs.getString(KEY_RECORDED_SESSION, null) != active.sessionId) {
                val (dayTrips, dayMinutes) = today()
                val (allTrips, allMinutes) = lifetime()
                editor.putString(KEY_DAY, LocalDate.now().toString())
                    .putInt(KEY_DAY_TRIPS, dayTrips + 1).putInt(KEY_DAY_MINUTES, dayMinutes + active.tripMinutes)
                    .putInt(KEY_DONE + courseId, completions(courseId) + 1)
                    .putInt(KEY_ALL_TRIPS, allTrips + 1).putInt(KEY_ALL_MINUTES, allMinutes + active.tripMinutes)
                    .putString(KEY_RECORDED_SESSION, active.sessionId)
            }
            if (effect != null && prefs.getString(KEY_ACKNOWLEDGED_EFFECT, null) != effect.id) {
                val pending = pendingEffects()
                if (pending.none { it.id == effect.id }) editor.putString(KEY_PENDING_EFFECTS, encodeEffects(pending + effect))
            }
            // SharedPreferences publishes one map and writes it with its atomic-file backup protocol.
            editor.apply()
        }
    }

    override fun pendingEffects(): List<CompletionEffect> = runCatching {
        val array = org.json.JSONArray(prefs.getString(KEY_PENDING_EFFECTS, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            CompletionEffect(item.getString("id"), CompletionEffect.Kind.valueOf(item.getString("kind")),
                item.getString("course"), item.getInt("direction"), item.getInt("minutes"))
        }
    }.getOrDefault(emptyList())

    private fun encodeEffects(effects: List<CompletionEffect>): String = org.json.JSONArray().apply {
        effects.forEach { effect -> put(org.json.JSONObject().put("id", effect.id).put("kind", effect.kind.name)
            .put("course", effect.courseId).put("direction", effect.direction).put("minutes", effect.minutes)) }
    }.toString()

    override fun acknowledgeEffect(id: String) {
        synchronized(prefs) {
            val pending = pendingEffects()
            if (pending.none { it.id == id }) return
            prefs.edit().putString(KEY_PENDING_EFFECTS, encodeEffects(pending.filterNot { it.id == id }))
                .putString(KEY_ACKNOWLEDGED_EFFECT, id).apply()
        }
    }

    /** 저장된 여행. 재부팅으로 시계가 무효가 됐으면 null. */
    override fun loadActive(): Active? {
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
            prefs.getString(KEY_A_ID, null)?.takeIf { it.isNotBlank() }
                ?: "legacy:${bootCount()}:${prefs.getString(KEY_COURSE, null)}:${clock.firstOrNull()}",
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
        const val KEY_PENDING_EFFECTS = "pending_completion_effects"
        const val KEY_ACKNOWLEDGED_EFFECT = "acknowledged_completion_effect"
        const val KEY_A_ID = "a_session_id"
        const val KEY_RECORDED_SESSION = "recorded_session_id"
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
