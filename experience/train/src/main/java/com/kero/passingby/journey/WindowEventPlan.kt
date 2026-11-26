package com.kero.passingby.journey

import com.kero.passingby.experience.TimeOfDay
import com.kero.passingby.scene.Zones
import kotlin.random.Random

internal enum class WindowEventKind(val durationSeconds: Double) {
    GLASS_GLINT(6.0),
    WINDOW_DROPS(7.0),
    BIRD_FLOCK(7.0),
    SHOOTING_STAR(5.0),
    SEA_BIRDS(7.0),
    DARJEELING_SUNBREAK(8.0),
    CANYON_DEER(6.0),
    PASSING_TRAIN(5.0),
    METEOR(9.0);

    val glass: Boolean get() = this == GLASS_GLINT || this == WINDOW_DROPS

    fun fitsLight(time: TimeOfDay): Boolean = when (this) {
        BIRD_FLOCK, SEA_BIRDS, DARJEELING_SUNBREAK, CANYON_DEER -> time != TimeOfDay.NIGHT
        SHOOTING_STAR -> time == TimeOfDay.NIGHT || time == TimeOfDay.DUSK
        else -> true
    }
}

internal data class WindowEventPlan(val kind: WindowEventKind, val startsAt: Double) {
    val endsAt: Double get() = startsAt + kind.durationSeconds

    fun progressAt(seconds: Double): Float? = if (seconds >= startsAt && seconds < endsAt) {
        ((seconds - startsAt) / kind.durationSeconds).toFloat()
    } else {
        null
    }

    /** A fixed sky selection can change which scene belongs at the already scheduled time. */
    fun kindAt(time: TimeOfDay): WindowEventKind = when {
        kind.fitsLight(time) -> kind
        kind == WindowEventKind.SHOOTING_STAR -> WindowEventKind.GLASS_GLINT
        else -> WindowEventKind.WINDOW_DROPS
    }
}

/** One optional scene per journey, derived from the same saved seed as the weather. */
internal class WindowEventPlanner(
    private val plan: CoursePlan,
    private val zones: Zones,
    private val courseId: String,
    private val baseDistance: Double,
    private val timeOfDayAt: (Float) -> TimeOfDay,
) {
    private val regularKinds = buildList {
        add(WindowEventKind.GLASS_GLINT)
        add(WindowEventKind.WINDOW_DROPS)
        add(WindowEventKind.BIRD_FLOCK)
        add(WindowEventKind.SHOOTING_STAR)
        if (zones.seaRanges.isNotEmpty() || zones.pambanRanges.isNotEmpty()) add(WindowEventKind.SEA_BIRDS)
        if (courseId == "india-darjeeling") add(WindowEventKind.DARJEELING_SUNBREAK)
        if (courseId == "usa-grand-canyon") add(WindowEventKind.CANYON_DEER)
        if (courseId in DOUBLE_TRACK_COURSES) add(WindowEventKind.PASSING_TRAIN)
    }

    fun select(seed: Int): WindowEventPlan? {
        val random = Random(seed xor EVENT_SEED_SALT)
        val roll = random.nextInt(ROLLS)
        if (roll >= EVENT_ROLLS) return null
        // Every departure seed can hit the one meteor outcome, including the first journey.
        val kind = if (roll == 0) WindowEventKind.METEOR else regularKinds.random(random)
        val start = findStart(kind, random)
        if (start != null) return WindowEventPlan(kind, start)

        val glass = if (random.nextBoolean()) WindowEventKind.GLASS_GLINT else WindowEventKind.WINDOW_DROPS
        return findStart(glass, random)?.let { WindowEventPlan(glass, it) }
    }

    private fun findStart(kind: WindowEventKind, random: Random): Double? {
        // A station dwell falls between moving legs, so every eligible event finishes on its own leg.
        val space = plan.segRun - 2 * STATION_GAP - kind.durationSeconds
        if (space <= 0.0) return null
        repeat(LOCATION_TRIES) {
            val leg = random.nextInt(plan.segments)
            val start = plan.departureAt(leg) + STATION_GAP + random.nextDouble(space)
            if (kind.glass || fitsOutside(kind, start)) return start
        }
        return null
    }

    private fun fitsOutside(kind: WindowEventKind, start: Double): Boolean {
        val end = start + kind.durationSeconds
        val middle = (start + end) * .5
        if (!kind.fitsLight(timeOfDayAt((middle / plan.durationSec).toFloat()))) return false
        val from = baseDistance + plan.distanceAt(start)
        val to = baseDistance + plan.distanceAt(end)
        if (overlaps(zones.tunnels, from, to, PORTAL_GAP)) return false

        val center = baseDistance + plan.distanceAt(middle)
        return when (kind) {
            WindowEventKind.SEA_BIRDS -> isSea(from) && isSea(center) && isSea(to)
            WindowEventKind.DARJEELING_SUNBREAK -> minOf(zones.batasia(from), zones.batasia(to)) >= .65f
            WindowEventKind.CANYON_DEER -> minOf(zones.prairie(from), zones.prairie(to)) >= .65f
            WindowEventKind.PASSING_TRAIN -> !overlaps(zones.bridges, from, to, PORTAL_GAP)
            else -> true
        }
    }

    private fun isSea(x: Double): Boolean = maxOf(zones.sea(x), zones.pambanCrossing(x)) >= .65f

    private fun overlaps(ranges: DoubleArray, from: Double, to: Double, gap: Double): Boolean {
        for (i in ranges.indices step 2) {
            if (from <= ranges[i + 1] + gap && to >= ranges[i] - gap) return true
        }
        return false
    }

    private companion object {
        const val EVENT_SEED_SALT = 0x5D37CA62
        const val ROLLS = 100_000
        const val EVENT_ROLLS = 30_000
        const val LOCATION_TRIES = 48
        const val STATION_GAP = 12.0
        const val PORTAL_GAP = 240.0
        val DOUBLE_TRACK_COURSES = setOf("korea", "japan", "china-north", "france")
    }
}
