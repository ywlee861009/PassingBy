package com.kero.passingby.session

/** Storage boundary. Experience-specific setting IDs are interpreted by their owner. */
data class CompletionEffect(val id: String, val kind: Kind, val courseId: String, val direction: Int, val minutes: Int) {
    enum class Kind { ARRIVED, REST_FINISHED }
}

interface SessionPersistence {
    var minutes: Int
    fun pendingEffects(): List<CompletionEffect>
    fun acknowledgeEffect(id: String)
    fun loadActive(): JourneyStore.Active?
    fun saveSession(active: JourneyStore.Active, courseId: String, direction: Int, stations: Set<Int> = emptySet(), arrived: Boolean = false, effect: CompletionEffect? = null)
}

interface JourneyStore : SessionPersistence {
    var courseId: String?
    var direction: Int
    override var minutes: Int
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
    fun completions(courseId: String): Int
    fun lifetime(): Pair<Int, Int>
    override fun loadActive(): Active?

    /** One atomic storage edit: session, route, stamps and optional completion share a checkpoint. */
    override fun saveSession(active: Active, courseId: String, direction: Int, stations: Set<Int>, arrived: Boolean, effect: CompletionEffect?)

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
