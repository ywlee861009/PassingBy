package com.passingby.app.session

import android.content.Context
import java.time.LocalDate

/**
 * 앱을 다시 열어도 남아야 하는 값: 지금 서 있는 역, 고른 집중 시간, 첫 여행 안내 여부, 오늘의 기록.
 * 값이 몇 개 안 돼서 SharedPreferences로 둔다. 설정 화면이 생기면 DataStore로 옮긴다(명세 9장).
 */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("trip", Context.MODE_PRIVATE)

    var routeId: String?
        get() = prefs.getString(KEY_ROUTE, null)
        set(v) = prefs.edit().putString(KEY_ROUTE, v).apply()

    var station: Int
        get() = prefs.getInt(KEY_STATION, 0)
        set(v) = prefs.edit().putInt(KEY_STATION, v).apply()

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

    private companion object {
        const val KEY_ROUTE = "route"
        const val KEY_STATION = "station"
        const val KEY_DIRECTION = "direction"
        const val KEY_MINUTES = "minutes"
        const val KEY_HINT = "hint_shown"
        const val KEY_DAY = "day"
        const val KEY_DAY_TRIPS = "day_trips"
        const val KEY_DAY_MINUTES = "day_minutes"
    }
}
