package com.kero.passingby.journey

import com.kero.passingby.session.JourneyStore
import com.kero.passingby.session.SessionPersistence
import org.junit.Assert.*
import org.junit.Test

class SessionRuntimeTest {
    private class Harness(val store: MemoryStore = MemoryStore()) {
        var now = 10_000L
        var pending: (() -> Unit)? = null
        val events = mutableListOf<SessionEvent>()
        val program = Program()
        val runtime = SessionRuntime(store, program, { now }, SessionScheduler { _, task ->
            pending = task
            { if (pending === task) pending = null }
        }).also { it.observe { event -> if (event != null) events += event }; it.start() }
        fun advance(seconds: Double) { now += (seconds * 1000).toLong(); pending?.also { pending = null }?.invoke() }
    }

    private class MemoryStore : SessionPersistence {
        override var minutes = 25
        var active: JourneyStore.Active? = null
        var arrivals = 0
        var completed: String? = null
        val stamps = mutableSetOf<Int>()
        var writes = 0
        override fun loadActive() = active
        override fun saveSession(active: JourneyStore.Active, courseId: String, direction: Int, stations: Set<Int>, arrived: Boolean) {
            this.active = active
            stamps += stations
            if (arrived && completed != active.sessionId) { arrivals++; completed = active.sessionId }
            writes++
        }
    }

    private class Program : JourneyProgram {
        override var courseId = "test"
        override var direction = 1
        var base = 0.0
        override fun configure(minutes: Int) = Unit
        override fun selectCourse(id: String, minutes: Int) { courseId = id; direction = 1; base = 0.0 }
        override fun restore(active: JourneyStore.Active) { base = active.base }
        override fun onSessionEvent(event: SessionEvent, state: SessionState) {
            if (event is SessionEvent.RestFinished || event is SessionEvent.GotOff ||
                event is SessionEvent.Departed && event.from in setOf(SessionPhase.ARRIVED, SessionPhase.RESTING)) {
                base += 100.0; direction = -direction
            }
        }
        override fun reachedStations(seconds: Double) = (0..(seconds / 500).toInt().coerceIn(0, 3)).toSet()
        override fun checkpoint(snapshot: SessionSnapshot, sessionId: String) = JourneyStore.Active(
            snapshot.state.phase.name, snapshot.state.tripMinutes, base, snapshot.clock,
            snapshot.state.restEndElapsedMillis, snapshot.state.restDone, 42, sessionId)
    }

    @Test fun schedulerCompletesAndCommitsWithoutAnyFramesOrObservers() {
        val h = Harness()
        h.runtime.depart()
        h.advance(1_500.0)
        assertEquals(SessionPhase.ARRIVED, h.runtime.state.phase)
        assertEquals("ARRIVED", h.store.active!!.phase)
        assertEquals(1, h.store.arrivals)
        assertEquals(setOf(0, 1, 2, 3), h.store.stamps)
        assertEquals(SessionEvent.Arrived(false), h.events.last())
        assertNull(h.pending)
    }

    @Test fun completedCheckpointDoesNotReplayAfterProcessRecreation() {
        val h = Harness()
        h.runtime.depart(); h.advance(1_500.0); h.runtime.close()
        val restored = Harness(h.store)
        restored.runtime.refresh()
        assertEquals(SessionPhase.ARRIVED, restored.runtime.state.phase)
        assertEquals(1, restored.store.arrivals)
        assertTrue(restored.events.isEmpty())
    }

    @Test fun overdueRunningSnapshotCommitsOnceBeforeAUserCommand() {
        val h = Harness()
        h.runtime.depart()
        h.now += 1_600_000L
        h.runtime.pause()
        assertEquals(SessionPhase.ARRIVED, h.runtime.state.phase)
        assertEquals(1, h.store.arrivals)
        assertEquals(listOf(SessionEvent.Departed(SessionPhase.DOCKED), SessionEvent.Arrived(true)), h.events)
    }

    @Test fun pausedSchedulerStopsAfterCoastingAndResumesWithoutTimeLoss() {
        val h = Harness()
        h.runtime.depart(); h.advance(60.0); h.runtime.pause(); h.advance(3.5)
        assertNull(h.pending)
        h.advance(5_000.0)
        assertEquals(61.75, h.runtime.elapsedSeconds, 1e-9)
        h.runtime.resume(); h.advance(3.5)
        assertEquals(63.5, h.runtime.elapsedSeconds, 1e-9)
        assertEquals(0, h.store.arrivals)
    }

    @Test fun stationCheckpointsDoNotWriteOnEverySchedulerTick() {
        val h = Harness()
        h.runtime.depart()
        val before = h.store.writes
        repeat(10) { h.advance(.25) }
        assertEquals(before, h.store.writes)
        h.advance(500.0)
        assertEquals(before + 1, h.store.writes)
        assertEquals(setOf(0, 1), h.store.stamps)
    }

    @Test fun restFinishesWithoutFramesAndReturnsOnlyOnce() {
        val h = Harness()
        h.runtime.depart(); h.advance(1_500.0); h.runtime.rest(); h.runtime.selectMinutes(50)
        h.advance(300.0)
        assertEquals(SessionState(tripMinutes = 50, restDone = true), h.runtime.state)
        assertEquals(-1, h.program.direction)
        assertEquals(100.0, h.store.active!!.base, 0.0)
        repeat(3) { h.runtime.refresh() }
        assertEquals(100.0, h.program.base, 0.0)
        assertEquals(1, h.store.arrivals)
    }

    @Test fun aNewDepartureGetsANewCompletionIdentity() {
        val h = Harness()
        h.runtime.depart(); val first = h.store.active!!.sessionId
        h.advance(1_500.0); h.runtime.depart()
        assertNotEquals(first, h.store.active!!.sessionId)
        h.advance(1_500.0)
        assertEquals(2, h.store.arrivals)
        assertEquals(-1, h.program.direction)
    }

    @Test fun closeCancelsSchedulingAndUnsubscribeStopsDelivery() {
        val h = Harness()
        var calls = 0
        val unsubscribe = h.runtime.observe { calls++ }
        h.runtime.depart(); unsubscribe(); val before = calls
        h.advance(20.0)
        assertEquals(before, calls)
        h.runtime.close()
        assertNull(h.pending)
        assertThrows(IllegalStateException::class.java) { h.runtime.refresh() }
    }

    @Test fun courseChangesRespectActiveSessionsAndForceCancelsOldDeadline() {
        val h = Harness()
        h.runtime.depart()
        assertFalse(h.runtime.selectCourse("other"))
        assertTrue(h.runtime.selectCourse("other", force = true))
        assertEquals(SessionPhase.DOCKED, h.runtime.state.phase)
        assertEquals("other", h.program.courseId)
        assertEquals("DOCKED", h.store.active!!.phase)
        assertNull(h.pending)
    }

    @Test fun selectionWhileDockedIsSavedAndRestoresWithTheNewDuration() {
        val h = Harness()
        h.runtime.selectMinutes(50)
        h.runtime.close()
        assertEquals(50, Harness(h.store).runtime.state.tripMinutes)
    }
}
