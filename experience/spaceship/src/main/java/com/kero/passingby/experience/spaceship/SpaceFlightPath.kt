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
        val launchEnd = (minOf(3.0, duration * .05) / duration).toFloat().coerceAtMost(firstWaypoint * .35f)
        val turnEnd = (launchEnd + (minOf(1.0, duration * .025) / duration).toFloat())
            .coerceAtMost(firstWaypoint * .65f)
        val ascent = smooth(p / launchEnd)
        val turn = smooth((p - launchEnd) / (turnEnd - launchEnd))
        // Clear each planet in seconds, then cruise between encounters for the selected focus time.
        var position = 0f
        if (p > turnEnd) {
            val leg = (0 until waypoints - 1).firstOrNull { p < markAt(it + 1, duration, waypoints) } ?: waypoints - 2
            val start = if (leg == 0) turnEnd else markAt(leg, duration, waypoints)
            val end = markAt(leg + 1, duration, waypoints)
            val f = ((p - start) / (end - start)).coerceIn(0f, 1f)
            val edge = (8.0 / (duration * (end - start))).toFloat().coerceAtMost(.25f)
            position = leg + when {
                f < edge -> .4f * smooth(f / edge)
                f > 1f - edge -> .6f + .4f * smooth((f - 1f + edge) / edge)
                else -> .4f + .2f * smooth((f - edge) / (1f - 2f * edge))
            }
        }
        return View(ascent, turn, position, smooth((ascent - .72f) / .28f))
    }

    fun markAt(index: Int, durationSeconds: Double, waypoints: Int): Float {
        require(waypoints >= 2 && index in 0 until waypoints)
        if (index == 0) return 0f
        if (waypoints == 2) return 1f
        val first = (minOf(12.0, durationSeconds.coerceAtLeast(60.0) * .16) / durationSeconds.coerceAtLeast(60.0)).toFloat()
        return first + (1f - first) * (index - 1) / (waypoints - 2)
    }

    fun progressAtPosition(position: Double, durationSeconds: Double, waypoints: Int): Double {
        val target = position.coerceIn(0.0, (waypoints - 1).toDouble())
        if (target == target.toInt().toDouble()) return markAt(target.toInt(), durationSeconds, waypoints).toDouble()
        var low = 0f; var high = 1f
        repeat(25) {
            val mid = (low + high) / 2f
            if (sample(mid, durationSeconds, waypoints).trackPosition < target) low = mid else high = mid
        }
        return ((low + high) / 2f).toDouble()
    }

    fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
