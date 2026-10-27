package com.kero.passingby.experience.spaceship

import kotlin.math.*

/** Small passing worlds and short light trails, sampled without frame history or allocations. */
internal object SpaceScenery {
    const val WORLD_LANES = 2
    const val METEOR_LANES = 2

    class World {
        var x = 0f
        var y = 0f
        var radius = 0f
        var opacity = 0f
    }

    class Meteor {
        var x = 0f
        var y = 0f
        var length = 0f
        var opacity = 0f
    }

    fun worldAt(seconds: Double, lane: Int, out: World) {
        val cycle = (seconds + lane * 23.0 + 11.0) / 46.0
        val pass = floor(cycle)
        val f = (cycle - pass).toFloat()
        out.x = 1.12f - f * 1.24f
        out.y = (if (lane == 0) .23f else .74f) + sin(pass * 2.4 + lane).toFloat() * .07f + f * .035f
        out.radius = .018f + (.5f + .5f * sin(pass * 1.7 + lane).toFloat()) * .012f
        out.opacity = SpaceFlightPath.smooth(f / .12f) * SpaceFlightPath.smooth((1f - f) / .12f) * .88f
    }

    fun meteorAt(seconds: Double, lane: Int, out: Meteor) {
        val cycle = (seconds + lane * 6.5) / 13.0
        val pass = floor(cycle)
        val age = ((cycle - pass) * 13.0).toFloat()
        val f = (age / 1.4f).coerceIn(0f, 1f)
        val seed = .5f + .5f * sin(pass * 2.39 + lane * 1.7).toFloat()
        out.x = .5f + seed * .4f - f * .48f
        out.y = .12f + (.5f + .5f * sin(pass * 1.43 + lane * 2.1).toFloat()) * .5f + f * .17f
        out.length = .035f + sin(f * PI).toFloat() * .045f
        out.opacity = if (age < 1.4f) sin(f * PI).toFloat().coerceAtLeast(0f) else 0f
    }
}
