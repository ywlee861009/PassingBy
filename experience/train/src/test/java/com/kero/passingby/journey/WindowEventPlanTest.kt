package com.kero.passingby.journey

import com.kero.passingby.experience.TimeOfDay
import com.kero.passingby.scene.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowEventPlanTest {
    private val plan = CoursePlan(25 * 60.0, 4, 170.0)
    private val base = 82_000.0

    @Test fun eachSeedSelectsAtMostOneSceneAtTheExpectedRates() {
        val planner = WindowEventPlanner(plan, Zones.NONE, "india-pamban", base) { TimeOfDay.DAY }
        assertEquals(WindowEventKind.METEOR, planner.select(166_452)?.kind)
        val scenes = (0 until 60_000).mapNotNull(planner::select)
        assertTrue(scenes.size in 17_400..18_600)
        assertTrue(scenes.count { it.kind == WindowEventKind.METEOR } <= 3)
        for (seed in 0..100) assertEquals(planner.select(seed), planner.select(seed))
        for (scene in scenes) {
            assertNull(scene.progressAt(scene.startsAt - .01))
            assertEquals(0f, scene.progressAt(scene.startsAt)!!, 0f)
            assertEquals(.5f, scene.progressAt((scene.startsAt + scene.endsAt) * .5)!!, .001f)
            assertNull(scene.progressAt(scene.endsAt))
            assertEquals(plan.segmentAt(scene.startsAt), plan.segmentAt(scene.endsAt))
            assertTrue(plan.speedAt(scene.startsAt) > 0 && plan.speedAt(scene.endsAt) > 0)
        }
    }

    @Test fun universalGlassScenesCanPlayInTunnelsOnEveryMovingLeg() {
        val enclosed = object : Zones by Zones.NONE {
            override val tunnels = doubleArrayOf(base, base + plan.totalDistance)
        }
        val planner = WindowEventPlanner(plan, enclosed, "japan", base) { TimeOfDay.NIGHT }
        val scenes = (0..500).mapNotNull(planner::select)
        assertEquals(setOf(WindowEventKind.GLASS_GLINT, WindowEventKind.WINDOW_DROPS), scenes.map { it.kind }.toSet())
        assertEquals((0 until plan.segments).toSet(), scenes.map { plan.segmentAt(it.startsAt) }.toSet())
    }

    @Test fun landscapeScenesStayInsideTheMatchingSceneryAndAvoidTunnelPortals() {
        val region = (base + plan.segDist * .95)..(base + plan.segDist * 1.55)
        val tunnel = doubleArrayOf(base + plan.segDist * 1.06, base + plan.segDist * 1.14)
        val landmark = object : Zones by Zones.NONE {
            override val tunnels = tunnel
            override val pambanRanges = doubleArrayOf(region.start, region.endInclusive)
            override fun pambanCrossing(x: Double) = if (x in region) 1f else 0f
            override fun batasia(x: Double) = if (x in region) 1f else 0f
            override fun prairie(x: Double) = if (x in region) 1f else 0f
        }
        val cases = mapOf(
            "india-darjeeling" to WindowEventKind.DARJEELING_SUNBREAK,
            "usa-grand-canyon" to WindowEventKind.CANYON_DEER,
            "india-pamban" to WindowEventKind.SEA_BIRDS,
        )
        for ((course, kind) in cases) {
            val planner = WindowEventPlanner(plan, landmark, course, base) { TimeOfDay.DAY }
            val scenes = (0..1_000).mapNotNull(planner::select)
            assertTrue(scenes.any { it.kind == kind })
            for (scene in scenes) {
                val from = base + plan.distanceAt(scene.startsAt)
                val to = base + plan.distanceAt(scene.endsAt)
                if (!scene.kind.glass) assertTrue(to < tunnel[0] || from > tunnel[1])
                if (scene.kind == kind) assertTrue(from in region && to in region)
                if (scene.kind in cases.values - WindowEventKind.SEA_BIRDS) assertEquals(kind, scene.kind)
            }
        }
    }

    @Test fun fixedLightingUsesTheSameIntervalForACompatibleGlassScene() {
        val planner = WindowEventPlanner(plan, Zones.NONE, "japan", base) { TimeOfDay.NIGHT }
        val scene = (0..1_000).firstNotNullOf { seed -> planner.select(seed)?.takeIf { it.kind == WindowEventKind.SHOOTING_STAR } }
        assertEquals(WindowEventKind.SHOOTING_STAR, scene.kindAt(TimeOfDay.NIGHT))
        assertEquals(WindowEventKind.GLASS_GLINT, scene.kindAt(TimeOfDay.DAY))
        assertEquals(.5f, scene.progressAt((scene.startsAt + scene.endsAt) * .5)!!, .001f)
        val birds = WindowEventPlan(WindowEventKind.BIRD_FLOCK, scene.startsAt)
        assertEquals(WindowEventKind.BIRD_FLOCK, birds.kindAt(TimeOfDay.DAY))
        assertEquals(WindowEventKind.WINDOW_DROPS, birds.kindAt(TimeOfDay.NIGHT))
    }
}
