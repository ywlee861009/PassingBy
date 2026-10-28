package com.kero.passingby.experience.spaceship

import kotlin.math.PI
import kotlin.math.sin

/** A rare, clock-sampled encounter. The persisted session seed is shared by picture and sound. */
internal object SpaceUfo {
    const val DURATION_SECONDS = 12.0
    private const val SLOT_SECONDS = 1500.0
    private const val CHANCE = .25
    private const val PLANET_CLEARANCE_SECONDS = 24.0

    class View {
        var opacity = 0f
        var x = 0f
        var y = 0f
        var radius = 0f
        var bank = 0f
    }

    fun slotCount(duration: Double): Int = (duration / SLOT_SECONDS).toInt().coerceAtLeast(1)

    /** NaN means this opportunity was not selected, or has no safe cruise interval. */
    fun startAt(slot: Int, duration: Double, count: Int, direction: Int, seed: Int, courseId: String): Double {
        if (duration < 600.0 || count < 2 || courseId.isEmpty()) return Double.NaN
        val slots = slotCount(duration)
        if (slot !in 0 until slots) return Double.NaN
        val key = seed xor courseId.hashCode() xor (slot * 0x1e35a7bd) xor 0x55464f
        if (unit(key) >= CHANCE) return Double.NaN
        val width = duration / slots
        // Middle 40% of each 25+ minute slot: neighboring encounters remain at least 15 minutes apart.
        val earliest = maxOf(180.0, slot * width + width * .30)
        val latest = minOf(duration - 60.0, slot * width + width * .70) - DURATION_SECONDS
        if (latest <= earliest) return Double.NaN
        for (attempt in 1..6) {
            val start = earliest + unit(key + attempt * 0x45d9f3b) * (latest - earliest)
            var clear = true
            for (index in 0 until count) {
                val mark = SpaceFlightPath.markAt(index, duration, count).toDouble()
                val arrival = (if (direction > 0) mark else 1.0 - mark) * duration
                if (arrival >= start - PLANET_CLEARANCE_SECONDS &&
                    arrival <= start + DURATION_SECONDS + PLANET_CLEARANCE_SECONDS) {
                    clear = false
                    break
                }
            }
            if (clear) return start
        }
        return Double.NaN
    }

    fun ageAt(seconds: Double, duration: Double, count: Int, direction: Int, seed: Int, courseId: String): Double {
        if (seconds < 0.0 || seconds >= duration || duration < 600.0) return -1.0
        val slot = (seconds / (duration / slotCount(duration))).toInt()
        val start = startAt(slot, duration, count, direction, seed, courseId)
        val age = seconds - start
        return if (age >= 0.0 && age < DURATION_SECONDS) age else -1.0
    }

    fun envelope(age: Double): Double {
        if (age < 0.0 || age >= DURATION_SECONDS) return 0.0
        return SpaceFlightPath.smooth((age / 2.0).toFloat()).toDouble() *
            SpaceFlightPath.smooth(((DURATION_SECONDS - age) / 2.0).toFloat())
    }

    fun sample(seconds: Double, duration: Double, count: Int, direction: Int, seed: Int, courseId: String, out: View) {
        val age = ageAt(seconds, duration, count, direction, seed, courseId)
        out.opacity = envelope(age).toFloat()
        if (out.opacity <= 0f) return
        val f = (age / DURATION_SECONDS).toFloat()
        val slot = (seconds / (duration / slotCount(duration))).toInt()
        val variation = unit(seed xor courseId.hashCode() xor slot)
        val rightward = (variation < .5) == (direction > 0)
        out.x = if (rightward) -.16f + 1.32f * f else 1.16f - 1.32f * f
        out.y = .28f + variation.toFloat() * .13f + sin(age * .72).toFloat() * .018f
        out.radius = .045f + sin(f * PI).toFloat() * .01f
        out.bank = sin(age * .85).toFloat() * 5f
    }

    // A stable integer hash avoids frame-dependent randomness and allocations.
    private fun unit(value: Int): Double {
        var x = value
        x = (x xor (x ushr 16)) * 0x45d9f3b
        x = (x xor (x ushr 16)) * 0x45d9f3b
        x = x xor (x ushr 16)
        return (x.toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
}
