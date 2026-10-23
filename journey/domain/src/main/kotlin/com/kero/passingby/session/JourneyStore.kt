package com.kero.passingby.session

/** Storage boundary. Experience-specific setting IDs are interpreted by their owner. */
interface JourneyStore {
    var courseId: String?
    var direction: Int
    var minutes: Int
    var notificationAsked: Boolean
    var hintShown: Boolean
    val visited: Set<String>
    var unlocked: Set<String>
    var devUnlockAll: Boolean
    var ledgeInfo: Boolean
    var overlayAlways: Boolean
    var powerSave: Boolean
    var soundOn: Boolean
    var soundLevel: Int
    var telemetryOn: Boolean
    var timeOfDayId: String?
    var seasonChoiceId: String

    fun today(): Pair<Int, Int>
    fun recordArrival(minutes: Int)
    fun visit(courseId: String, station: Int)
    fun completions(courseId: String): Int
    fun lifetime(): Pair<Int, Int>
    fun recordCompletion(courseId: String, minutes: Int)
    fun saveActive(active: Active)
    fun loadActive(): Active?

    /** One atomic storage edit: session, route, stamps and optional completion share a checkpoint. */
    fun saveSession(active: Active, courseId: String, direction: Int, stations: Set<Int> = emptySet(), arrived: Boolean = false)


    class Active(
        val phase: String,
        val tripMinutes: Int,
        val base: Double,
        val clock: DoubleArray,
        val restEnd: Long,
        val restDone: Boolean,
        val weatherSeed: Int,
        val sessionId: String = "",
    )
}
