package com.kero.passingby.experience.spaceship

/** Camera motion is sampled from the session clock, including pause, seek and reverse journeys. */
internal object SpaceFlightPath {
    data class View(
        val ascent: Float,
        val turn: Float,
        val trackPosition: Float,
        val stars: Float,
    )

    fun sample(routeProgress: Float, durationSeconds: Double, waypoints: Int): View {
        require(waypoints >= 2)
        val p = routeProgress.coerceIn(0f, 1f)
        val duration = durationSeconds.coerceAtLeast(60.0)
        val firstWaypoint = markAt(1, duration, waypoints)
        val launchEnd = (minOf(10.0, duration * .05) / duration).toFloat().coerceAtMost(firstWaypoint * .35f)
        val turnEnd = (launchEnd + (minOf(4.0, duration * .025) / duration).toFloat())
            .coerceAtMost(firstWaypoint * .65f)
        val ascent = smooth(p / launchEnd)
        val turn = smooth((p - launchEnd) / (turnEnd - launchEnd))
        // The nearby Moon is reached early; the remaining focus time is split across deep-space legs.
        val position = if (p <= turnEnd) 0f
            else if (p < firstWaypoint) (p - turnEnd) / (firstWaypoint - turnEnd)
            else if (waypoints == 2) p
            else 1f + (p - firstWaypoint) / (1f - firstWaypoint) * (waypoints - 2)
        return View(ascent, turn, position, smooth((ascent - .72f) / .28f))
    }

    fun markAt(index: Int, durationSeconds: Double, waypoints: Int): Float {
        require(waypoints >= 2 && index in 0 until waypoints)
        if (index == 0) return 0f
        if (waypoints == 2) return 1f
        val first = (minOf(55.0, durationSeconds.coerceAtLeast(60.0) * .16) / durationSeconds.coerceAtLeast(60.0)).toFloat()
        return first + (1f - first) * (index - 1) / (waypoints - 2)
    }

    fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
