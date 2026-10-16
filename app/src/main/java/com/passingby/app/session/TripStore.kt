package com.passingby.app.session

import android.content.Context
import android.provider.Settings
import java.time.LocalDate

/**
 * 앱을 다시 열어도 남아야 하는 값. 값이 몇 개 안 돼서 SharedPreferences로 둔다.
 *
 * 진행 중인 여행도 여기에 둔다. SavedStateHandle은 최근 앱 목록에서 앱을 지우면 사라지지만,
 * 여행은 포그라운드 서비스로 계속 흐르므로 앱을 다시 열었을 때 이어 붙여야 한다.
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
        const val KEY_DAY = "day"
        const val KEY_DAY_TRIPS = "day_trips"
        const val KEY_DAY_MINUTES = "day_minutes"
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
