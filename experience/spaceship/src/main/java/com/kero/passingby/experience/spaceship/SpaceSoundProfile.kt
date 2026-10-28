package com.kero.passingby.experience.spaceship

import kotlin.math.*

/** Inside-the-cabin sound; no external sound propagation through vacuum is implied. */
internal data class SpaceSoundProfile(
    val engine: Double, val air: Double, val maneuver: Double, val cabin: Double,
    val ufo: Double = 0.0, val ufoPitch: Double = 640.0,
) {
    companion object {
        val IDLE = SpaceSoundProfile(0.0, 0.0, 0.0, .48)

        fun at(progress: Float, duration: Double, count: Int, direction: Int, running: Boolean,
            eventSeed: Int = 0, courseId: String = ""): SpaceSoundProfile {
            if (!running) return IDLE
            val p = if (direction > 0) progress else 1f - progress
            val view = SpaceFlightPath.sample(p, duration, count)
            val launch = 1.0 - view.turn
            val closest = round(view.trackPosition).toInt()
            val near = if (closest in 1 until count - 1) exp(-((view.trackPosition - closest) / .09).pow(2)) else 0.0
            val ufoAge = SpaceUfo.ageAt(progress.toDouble() * duration, duration, count, direction, eventSeed, courseId)
            return SpaceSoundProfile(
                engine = .10 + launch * (.76 + view.ascent * .24),
                air = sin(view.ascent * PI) * launch * .85,
                maneuver = near * .65,
                cabin = .72,
                ufo = SpaceUfo.envelope(ufoAge),
                // Original theremin-style glide and vibrato; no sampled film/music recordings.
                ufoPitch = if (ufoAge >= 0) 640.0 + 86.0 * sin(2 * PI * .19 * ufoAge) +
                    28.0 * sin(2 * PI * 4.8 * ufoAge) else 640.0,
            )
        }
    }
}
