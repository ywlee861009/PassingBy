package com.kero.passingby.experience.spaceship

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SpaceUfoTest {
    private val course = SpaceCourses.all.single()
    private fun start(slot: Int, duration: Double, direction: Int, seed: Int) =
        SpaceUfo.startAt(slot, duration, course.stations.size, direction, seed, course.id)

    @Test fun encountersAreRareBoundedAndNeverOverlapPlanetsInEitherDirection() {
        for (duration in listOf(1500.0, 3000.0, 5400.0)) for (direction in listOf(1, -1)) {
            var encounters = 0
            val opportunities = SpaceUfo.slotCount(duration)
            for (seed in 0 until 2000) {
                var previous = Double.NEGATIVE_INFINITY
                for (slot in 0 until opportunities) {
                    val t = start(slot, duration, direction, seed)
                    if (t.isNaN()) continue
                    encounters++
                    assertTrue(t >= 180.0)
                    assertTrue(t + SpaceUfo.DURATION_SECONDS <= duration - 60.0)
                    assertTrue("Even neighboring selected slots must stay rare", t - previous >= 900.0)
                    previous = t
                    for (index in course.stations.indices) {
                        val mark = SpaceFlightPath.markAt(index, duration, course.stations.size).toDouble()
                        val arrival = (if (direction > 0) mark else 1.0 - mark) * duration
                        assertTrue(arrival < t - 24.0 || arrival > t + SpaceUfo.DURATION_SECONDS + 24.0)
                    }
                }
            }
            val fraction = encounters.toDouble() / (2000 * opportunities)
            assertTrue("About one in four opportunities, was $fraction", fraction in .20.. .30)
        }
        assertEquals(1, SpaceUfo.slotCount(1500.0))
        assertEquals(2, SpaceUfo.slotCount(3000.0))
        assertEquals(3, SpaceUfo.slotCount(5400.0))
        assertTrue(start(0, 300.0, 1, 0).isNaN())
    }

    @Test fun pauseSeekingAndRestorationRecoverTheSameViewWithoutAccumulatedFrameState() {
        for (direction in listOf(1, -1)) {
            val seed = (0..1000).first { !start(0, 1500.0, direction, it).isNaN() }
            val t = start(0, 1500.0, direction, seed) + 6.0
            val view = SpaceUfo.View()
            fun sample(seconds: Double) {
                SpaceUfo.sample(seconds, 1500.0, course.stations.size, direction, seed, course.id, view)
            }
            fun snapshot() = listOf(view.x, view.y, view.radius, view.bank, view.opacity)
            sample(t)
            val expected = snapshot()
            assertEquals(1f, view.opacity, 0f)
            assertTrue(view.x in .0f..1f && view.y in .2f.. .5f)
            repeat(10) { sample(t); assertEquals(expected, snapshot()) }
            sample(1499.0)
            assertEquals(0f, view.opacity, 0f)
            sample(t)
            assertEquals(expected, snapshot())
            sample(t + 1.0 / 60)
            assertTrue(abs(view.x - expected[0]) in .001f.. .003f)
            sample(t + 7.0)
            assertEquals(0f, view.opacity, 0f)
        }
    }

    @Test fun dedicatedSoundAndVisualEnvelopeUseTheSameEncounterClockAndPauseMutesTheEffect() {
        val seed = (0..1000).first { !start(0, 1500.0, 1, it).isNaN() }
        val begin = start(0, 1500.0, 1, seed)
        for (age in listOf(-1.0, .5, 2.0, 6.0, 11.5, 13.0)) {
            val p = ((begin + age) / 1500).toFloat()
            val view = SpaceUfo.View()
            SpaceUfo.sample(p.toDouble() * 1500, 1500.0, course.stations.size, 1, seed, course.id, view)
            val profile = SpaceSoundProfile.at(p, 1500.0, course.stations.size, 1, true, seed, course.id)
            assertEquals(view.opacity.toDouble(), profile.ufo, .00001)
            assertTrue(profile.ufoPitch in 526.0..754.0)
            assertEquals(0.0, SpaceSoundProfile.at(p, 1500.0, course.stations.size,
                1, false, seed, course.id).ufo, 0.0)
        }
    }
}
