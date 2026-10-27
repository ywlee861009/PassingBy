package com.kero.passingby.experience.spaceship

import kotlin.math.*

/** Inside-the-cabin sound; no external sound propagation through vacuum is implied. */
internal data class SpaceSoundProfile(val engine: Double, val air: Double, val maneuver: Double, val cabin: Double) {
    companion object {
        val IDLE = SpaceSoundProfile(0.0, 0.0, 0.0, .48)

        fun at(progress: Float, duration: Double, count: Int, direction: Int, running: Boolean): SpaceSoundProfile {
            if (!running) return IDLE
            val p = if (direction > 0) progress else 1f - progress
            val view = SpaceFlightPath.sample(p, duration, count)
            val launch = 1.0 - view.turn
            val closest = round(view.trackPosition).toInt()
            val near = if (closest in 1 until count - 1) exp(-((view.trackPosition - closest) / .09).pow(2)) else 0.0
            return SpaceSoundProfile(
                engine = .10 + launch * (.76 + view.ascent * .24),
                air = sin(view.ascent * PI) * launch * .85,
                maneuver = near * .65,
                cabin = .72,
            )
        }
    }
}
