package com.kero.passingby.journey

import com.kero.passingby.session.JourneyStore

/** Experience-owned route progress. No renderer, UI or platform types cross this boundary. */
interface JourneyProgram {
    val courseId: String
    val direction: Int
    fun configure(minutes: Int)
    fun selectCourse(id: String, minutes: Int)
    fun restore(active: JourneyStore.Active)
    fun onSessionEvent(event: SessionEvent, state: SessionState)
    fun reachedStations(seconds: Double): Set<Int>
    fun checkpoint(snapshot: SessionSnapshot, sessionId: String): JourneyStore.Active
}

/** One-shot scheduling on the same serialized execution context as user commands. */
fun interface SessionScheduler {
    fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit
}
