package com.kero.passingby.experience.spaceship

import kotlin.math.*
import kotlin.random.Random

/** Continuous PCM synthesis: rocket roar, audible machinery harmonics, air and maneuver jets. */
internal class SpaceSynth(seed: Int = 901) {
    private val random = Random(seed)
    private var sample = 0L
    private var low = 0.0
    private var mid = 0.0
    private var high = 0.0
    private var engine = 0.0
    private var air = 0.0
    private var maneuver = 0.0
    private var cabin = 0.0
    private var gain = 0.0

    fun render(out: ShortArray, target: SpaceSoundProfile, volume: Double) {
        val follow = 1.0 / (RATE * .09)
        for (i in out.indices) {
            engine += (target.engine - engine) * follow
            air += (target.air - air) * follow
            maneuver += (target.maneuver - maneuver) * follow
            cabin += (target.cabin - cabin) * follow
            gain += (volume - gain) / (RATE * .045)
            val t = sample++.toDouble() / RATE
            val white = random.nextDouble() * 2 - 1
            low += (white - low) * .022
            mid += (white - mid) * .19
            high += (white - high) * .43
            val band = mid - low
            val flutter = .86 + .14 * sin(2 * PI * 7.3 * t)
            // Mid-range components make the motor audible even on small phone speakers.
            val motor = sin(2 * PI * 94 * t) * .048 + sin(2 * PI * 188 * t) * .051 +
                sin(2 * PI * 376 * t) * .036 + sin(2 * PI * 564 * t) * .014
            val roar = (low * .80 + band * .62 + motor) * engine * flutter
            val atmosphere = (high - low) * air * .17
            val fans = (band * .12 + sin(2 * PI * 196 * t) * .022 +
                sin(2 * PI * 392 * t) * .019 + sin(2 * PI * 588 * t) * .009) * cabin
            val jets = (band * .32 + (high - mid) * .09) * maneuver
            val value = tanh((roar + atmosphere + fans + jets) * gain * 1.5) * .88
            out[i] = (value * Short.MAX_VALUE).roundToInt().toShort()
        }
    }

    companion object { const val RATE = 24000 }
}
