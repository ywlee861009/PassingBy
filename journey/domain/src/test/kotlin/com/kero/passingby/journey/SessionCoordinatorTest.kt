package com.kero.passingby.journey

import org.junit.Assert.*
import org.junit.Test

class SessionCoordinatorTest {
    private class Time(var millis: Long = 10_000L) {
        fun advance(seconds: Double) { millis += (seconds * 1000).toLong() }
        fun session(minutes: Int = 25) = SessionCoordinator(minutes) { millis }
    }

    @Test fun initialStateIsDockedAndTimeAloneDoesNotStartATrip() {
        val time = Time()
        val session = time.session()
        time.advance(100_000.0)
        assertNull(session.advance())
        assertEquals(SessionState(tripMinutes = 25), session.state)
        assertEquals(0.0, session.elapsedSeconds, 0.0)
    }

    @Test fun departureUsesSelectedDurationAndDuplicateDepartureDoesNotRestart() {
        val time = Time()
        val session = time.session()
        session.selectMinutes(50)
        assertEquals(SessionEvent.Departed(SessionPhase.DOCKED), session.depart())
        time.advance(60.0)
        assertNull(session.depart())
        assertEquals(3_000.0, session.durationSeconds, 0.0)
        assertEquals(60.0, session.elapsedSeconds, 1e-9)
    }

    @Test fun changingNextDurationDoesNotChangeTheRunningTrip() {
        val session = Time().session()
        session.depart()
        session.selectMinutes(50)
        assertEquals(25, session.state.tripMinutes)
        session.end()
        assertEquals(50, session.state.tripMinutes)
    }

    @Test fun pauseCoastsToAStopAndResumeStartsSmoothly() {
        val time = Time()
        val session = time.session()
        session.depart()
        time.advance(60.0)
        assertEquals(SessionEvent.Paused(4), session.pause())
        assertEquals(1.0, session.rate, 1e-9)
        time.advance(3.5)
        assertEquals(61.75, session.elapsedSeconds, 1e-9)
        assertEquals(0.0, session.rate, 0.0)
        time.advance(10_000.0)
        assertNull(session.advance())
        assertEquals(SessionPhase.PAUSED, session.state.phase)
        assertEquals(61.75, session.elapsedSeconds, 1e-9)
        assertEquals(SessionEvent.Resumed, session.resume())
        time.advance(3.5)
        assertEquals(63.5, session.elapsedSeconds, 1e-9)
        assertEquals(1.0, session.rate, 1e-9)
    }

    @Test fun repeatedPauseAndResumeDoNotRestartTheirSpeedRamps() {
        val time = Time()
        val session = time.session()
        session.depart()
        session.pause()
        time.advance(1.0)
        val rate = session.rate
        val position = session.elapsedSeconds
        assertNull(session.pause())
        assertNull(session.depart())
        assertEquals(rate, session.rate, 0.0)
        assertEquals(position, session.elapsedSeconds, 0.0)
        session.resume()
        time.advance(1.0)
        val resumedRate = session.rate
        assertNull(session.resume())
        assertEquals(resumedRate, session.rate, 0.0)
    }

    @Test fun arrivalDoesNotNeedRenderingAndEmitsOnlyOnce() {
        val time = Time()
        val session = time.session()
        session.depart()
        time.advance(1_500.0)
        assertEquals(SessionEvent.Arrived(false), session.advance())
        assertEquals(SessionPhase.ARRIVED, session.state.phase)
        assertEquals(1_500.0, session.elapsedSeconds, 0.0)
        assertEquals(0.0, session.rate, 0.0)
        time.advance(10_000.0)
        repeat(5) { assertNull(session.advance()) }
    }

    @Test fun aPauseThatCoastsAcrossTheFinishStillArrives() {
        val time = Time()
        val session = time.session()
        session.depart()
        session.seek(1_499.0)
        session.pause()
        time.advance(3.5)
        assertEquals(SessionEvent.Arrived(false), session.advance())
    }

    @Test fun arrivalIsLateOnlyAfterTheExistingThreeSecondGracePeriod() {
        for ((seconds, late) in listOf(1_503.0 to false, 1_503.001 to true)) {
            val time = Time()
            val session = time.session()
            session.depart()
            time.advance(seconds)
            assertEquals(SessionEvent.Arrived(late), session.advance())
        }
    }

    @Test fun endingATripReturnsOriginalDurationAndResetsForTheNextChoice() {
        val time = Time()
        val session = time.session()
        session.depart()
        time.advance(750.0)
        session.selectMinutes(50)
        assertEquals(SessionEvent.Ended(25, 50), session.end())
        assertEquals(SessionState(tripMinutes = 50), session.state)
        assertEquals(0.0, session.elapsedSeconds, 0.0)
        assertNull(session.end())
    }

    @Test fun invalidCommandsDoNotChangeDockedOrArrivedState() {
        val time = Time()
        val session = time.session()
        val docked = session.state
        assertNull(session.pause())
        assertNull(session.resume())
        assertNull(session.end())
        assertNull(session.rest())
        assertNull(session.getOff())
        session.seek(100.0)
        session.skipRest()
        assertEquals(docked, session.state)
        session.depart()
        time.advance(1_500.0)
        session.advance()
        val arrived = session.state
        assertNull(session.pause())
        assertNull(session.resume())
        assertNull(session.end())
        assertEquals(arrived, session.state)
    }

    @Test fun restUsesWallDurationAndFinishesOnceWithNextSelectedMinutes() {
        val time = Time()
        val session = time.session()
        session.depart()
        time.advance(1_500.0)
        session.advance()
        assertEquals(SessionEvent.RestStarted, session.rest())
        val end = session.state.restEndElapsedMillis
        assertNull(session.rest())
        assertEquals(end, session.state.restEndElapsedMillis)
        session.selectMinutes(50)
        session.setScale(60.0)
        time.advance(299.0)
        assertNull(session.advance())
        assertEquals(1, session.restRemainingSeconds)
        time.advance(1.0)
        assertEquals(SessionEvent.RestFinished(false), session.advance())
        assertEquals(SessionState(tripMinutes = 50, restDone = true), session.state)
        assertEquals(0.0, session.elapsedSeconds, 0.0)
        assertNull(session.advance())
        session.depart()
        assertFalse(session.state.restDone)
    }

    @Test fun overdueRestReportsLateWithoutRepeatingCompletion() {
        val time = Time()
        val session = time.session()
        session.depart()
        time.advance(1_500.0)
        session.advance()
        session.rest()
        time.advance(304.0)
        assertEquals(SessionEvent.RestFinished(true), session.advance())
        assertNull(session.advance())
    }

    @Test fun departureAndGettingOffExposeThePreviousPhaseForTheExperienceAdapter() {
        for (resting in listOf(false, true)) {
            val time = Time()
            val session = time.session()
            session.depart()
            time.advance(1_500.0)
            session.advance()
            if (resting) session.rest()
            val from = if (resting) SessionPhase.RESTING else SessionPhase.ARRIVED
            val snapshot = session.snapshot()
            assertEquals(SessionEvent.Departed(from), session.depart())
            assertEquals(SessionPhase.RUNNING, session.state.phase)
            session.restore(snapshot)
            assertEquals(SessionEvent.GotOff(from), session.getOff())
            assertEquals(SessionState(tripMinutes = 25), session.state)
        }
    }

    @Test fun runningSnapshotRestoresElapsedTimeAndOnlyArrivesOnce() {
        val time = Time()
        val original = time.session()
        original.depart()
        time.advance(500.0)
        val snapshot = original.snapshot()
        time.advance(1_100.0)
        val restored = time.session(50)
        restored.restore(snapshot)
        assertEquals(25, restored.state.tripMinutes)
        assertEquals(1_600.0, restored.elapsedSeconds, 1e-9)
        assertEquals(SessionEvent.Arrived(true), restored.advance())
        val arrived = restored.snapshot()
        val again = time.session(50)
        again.restore(arrived)
        assertNull(again.advance())
        again.getOff()
        assertEquals(50, again.state.tripMinutes)
    }

    @Test fun pausedSnapshotPreservesTheSpeedRampAndRemainsPausedAfterDowntime() {
        val time = Time()
        val session = time.session()
        session.depart()
        time.advance(500.0)
        session.pause()
        time.advance(1.0)
        val snapshot = session.snapshot()
        time.advance(10_000.0)
        val restored = time.session()
        restored.restore(snapshot)
        assertEquals(SessionPhase.PAUSED, restored.state.phase)
        assertEquals(501.75, restored.elapsedSeconds, 1e-9)
        assertEquals(0.0, restored.rate, 0.0)
        assertNull(restored.advance())
        restored.resume()
        time.advance(3.5)
        assertEquals(503.5, restored.elapsedSeconds, 1e-9)
    }

    @Test fun restingSnapshotUsesItsOriginalDeadlineAfterRecreation() {
        val time = Time()
        val session = time.session()
        session.depart()
        time.advance(1_500.0)
        session.advance()
        session.rest()
        time.advance(120.0)
        val restored = time.session()
        restored.restore(session.snapshot())
        assertEquals(180, restored.restRemainingSeconds)
        time.advance(184.0)
        assertEquals(SessionEvent.RestFinished(true), restored.advance())
        assertTrue(restored.state.restDone)
    }

    @Test fun developerSeekingAndSkippingRestStillUseTheNormalTransitions() {
        val session = Time().session()
        session.depart()
        session.pause()
        session.seek(10_000.0)
        assertEquals(SessionPhase.PAUSED, session.state.phase)
        assertEquals(SessionEvent.Arrived(false), session.advance())
        session.rest()
        session.skipRest()
        assertEquals(SessionEvent.RestFinished(false), session.advance())
    }

    @Test fun acceleratedTripAndResetRetainScaleButResetPhaseAndTime() {
        val time = Time()
        val session = time.session()
        session.setScale(60.0)
        session.depart()
        time.advance(25.0)
        assertEquals(SessionEvent.Arrived(false), session.advance())
        session.reset()
        assertEquals(SessionState(tripMinutes = 25), session.state)
        assertEquals(0.0, session.elapsedSeconds, 0.0)
        assertEquals(60.0, session.scale, 0.0)
    }

    @Test fun alarmDeadlineIncludesSmoothResumeAndCoastingArrival() {
        val time = Time()
        val session = time.session()
        session.depart()
        time.advance(60.0)
        session.pause()
        time.advance(3.5)
        assertNull(session.deadlineElapsedMillis)
        session.resume()
        assertEquals(time.millis + 1_440_000L, session.deadlineElapsedMillis)
        session.seek(1_499.0)
        session.pause()
        val deadline = session.deadlineElapsedMillis!!
        time.millis = deadline - 1
        assertNull(session.advance())
        time.millis = deadline
        assertEquals(SessionEvent.Arrived(false), session.advance())
        assertNull(session.deadlineElapsedMillis)
    }

    @Test fun invalidParametersAndSnapshotsDoNotPartiallyChangeTheState() {
        val session = Time().session()
        val state = session.state
        assertThrows(IllegalArgumentException::class.java) { session.selectMinutes(0) }
        assertThrows(IllegalArgumentException::class.java) { session.setScale(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { session.setScale(0.0) }
        assertThrows(IllegalArgumentException::class.java) { session.seek(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) {
            session.restore(SessionSnapshot(SessionState(SessionPhase.RUNNING, 25), doubleArrayOf(0.0)))
        }
        assertEquals(state, session.state)
        assertEquals(0.0, session.elapsedSeconds, 0.0)
    }
}
