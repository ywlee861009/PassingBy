package com.kero.passingby.experience.spaceship

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SpaceSceneryTest {
    @Test fun passingWorldsKeepLongCruiseIntervalsPopulatedWithoutFillingTheWindow() {
        val world = SpaceScenery.World()
        for (second in 0..5400) {
            var visible = 0
            for (lane in 0 until SpaceScenery.WORLD_LANES) {
                SpaceScenery.worldAt(second.toDouble(), lane, world)
                assertTrue(world.radius in .018f.. .031f)
                assertTrue(world.y in .1f.. .9f)
                if (world.x in .05f.. .95f && world.opacity > .5f) visible++
            }
            assertTrue("A long cruise must have a little world in view at $second seconds", visible >= 1)
        }
    }

    @Test fun visibleWorldsMoveContinuouslyAndSeekingBackRestoresTheirExactPosition() {
        val world = SpaceScenery.World()
        for (lane in 0 until SpaceScenery.WORLD_LANES) {
            for (frame in 0..6000) {
                val second = frame / 60.0
                SpaceScenery.worldAt(second, lane, world)
                val x = world.x; val opacity = world.opacity
                SpaceScenery.worldAt(second + 1.0 / 60, lane, world)
                if (opacity > .01f && world.opacity > .01f) assertTrue(abs(x - world.x) < .001f)
            }
            SpaceScenery.worldAt(30.25, lane, world)
            val before = listOf(world.x, world.y, world.radius, world.opacity)
            SpaceScenery.worldAt(5000.0, lane, world)
            SpaceScenery.worldAt(30.25, lane, world)
            assertEquals(before, listOf(world.x, world.y, world.radius, world.opacity))
        }
    }

    @Test fun shootingStarsAreBriefSmallAndRecurringThroughoutTheJourney() {
        val meteor = SpaceScenery.Meteor()
        var visibleFrames = 0
        var lastSeen = 0.0
        for (frame in 0..6000) {
            val seconds = frame / 10.0
            var visible = false
            for (lane in 0 until SpaceScenery.METEOR_LANES) {
                SpaceScenery.meteorAt(seconds, lane, meteor)
                assertTrue(meteor.opacity in 0f..1f)
                assertTrue(meteor.length in .034f.. .081f)
                if (meteor.opacity > .1f) {
                    assertTrue(meteor.x in 0f..1f && meteor.y in 0f..1f)
                    visible = true
                }
            }
            if (visible) { visibleFrames++; lastSeen = seconds }
            assertTrue("There should be a short glint every few seconds", seconds - lastSeen < 7)
        }
        assertTrue("Trails should occupy only a small part of the journey", visibleFrames in 900..1500)
        SpaceScenery.meteorAt(7.2, 1, meteor)
        val before = listOf(meteor.x, meteor.y, meteor.length, meteor.opacity)
        SpaceScenery.meteorAt(5000.0, 1, meteor)
        SpaceScenery.meteorAt(7.2, 1, meteor)
        assertEquals(before, listOf(meteor.x, meteor.y, meteor.length, meteor.opacity))
    }
}
