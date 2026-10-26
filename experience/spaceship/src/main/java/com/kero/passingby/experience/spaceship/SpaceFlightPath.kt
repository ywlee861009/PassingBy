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
        val firstWaypoint = 1f / (waypoints - 1)
        val launchEnd = (minOf(75.0, duration * .05) / duration).toFloat().coerceAtMost(firstWaypoint * .35f)
        val turnEnd = (launchEnd + (minOf(35.0, duration * .025) / duration).toFloat())
            .coerceAtMost(firstWaypoint * .65f)
        val ascent = smooth(p / launchEnd)
        val turn = smooth((p - launchEnd) / (turnEnd - launchEnd))
        // Preserve the course's recorded waypoint times while reserving the opening for launch.
        val position = if (p <= turnEnd) 0f
            else if (p < firstWaypoint) (p - turnEnd) / (firstWaypoint - turnEnd)
            else p * (waypoints - 1)
        return View(ascent, turn, position, smooth((ascent - .72f) / .28f))
    }

    fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
