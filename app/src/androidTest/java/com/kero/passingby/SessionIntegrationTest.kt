package com.kero.passingby

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.session.TripStore
import com.kero.passingby.session.TripTimer
import com.kero.passingby.ui.journey.JourneyViewModel
import org.junit.Assert.*
import org.junit.Test

/** 실제 앱 그래프에서 도메인 전이와 기차·저장 어댑터의 연결을 확인한다. */
class SessionIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun commandsUpdateTheUiAndPersistenceWithoutDuplicatingPauseOrEnd() = withSession { models ->
        val vm = models.vm
        val store = TripStore(context)
        vm.depart()
        assertEquals(SessionPhase.RUNNING, vm.phase)
        assertEquals("RUNNING", store.loadActive()!!.phase)
        vm.selectMinutes(50)
        assertEquals(25, vm.tripMinutes)
        vm.pause()
        val paused = store.loadActive()!!
        assertEquals(SessionPhase.PAUSED, vm.phase)
        vm.pause()
        assertArrayEquals(paused.clock, store.loadActive()!!.clock, 0.0)
        vm.resume()
        assertEquals(SessionPhase.RUNNING, vm.phase)
        val cut = vm.cutTick
        vm.endTrip()
        assertEquals(SessionPhase.DOCKED, vm.phase)
        assertEquals(50, vm.tripMinutes)
        assertEquals("DOCKED", store.loadActive()!!.phase)
        assertEquals(cut + 1, vm.cutTick)
        vm.endTrip()
        assertEquals(cut + 1, vm.cutTick)
    }

    @Test fun passingLocationStaysWithTheSceneAcrossPauseRestoreAndReturn() = withSession { models ->
        var vm = models.vm
        vm.depart()
        vm.seekPosition(22.0 / 166.0) // 서울 → 대전 사이 광명.
        vm.onFrame(0L, 60f)
        vm.pause()
        vm.onFrame(0L, 60f)
        val caption = vm.routeCaption
        assertEquals(com.kero.passingby.experience.RouteCaption.Kind.PASSING, caption?.kind)
        assertEquals(context.getString(com.kero.passingby.experience.train.R.string.place_gwangmyeong),
            context.getString(caption!!.name))
        repeat(3) { vm.onFrame(0L, 60f) }
        assertEquals(caption, vm.routeCaption)
        models.recreate()
        vm = models.vm
        vm.onFrame(0L, 60f)
        assertEquals(SessionPhase.PAUSED, vm.phase)
        assertEquals(caption, vm.routeCaption)
        vm.resume()
        vm.seekFraction(1f)
        vm.onFrame(0L, 60f)
        assertNull(vm.routeCaption)
        vm.getOff()
        vm.depart()
        vm.seekPosition(22.0 / 166.0)
        vm.onFrame(0L, 60f)
        assertEquals(-1, vm.direction)
        assertEquals(caption, vm.routeCaption)
    }

    @Test fun arrivalIsRecordedOnceAcrossFramesAndViewModelRecreation() = withSession { models ->
        var vm = models.vm
        val store = TripStore(context)
        vm.depart()
        vm.seekFraction(1f)
        vm.onFrame(0L, 60f)
        assertEquals(SessionPhase.ARRIVED, vm.phase)
        assertEquals(1 to 25, store.lifetime())
        assertEquals(1, store.completions(vm.course.id))
        assertTrue("${vm.course.id}:${vm.stations.lastIndex}" in store.visited)
        repeat(5) { vm.onFrame(0L, 60f) }
        models.recreate()
        vm = models.vm
        vm.onFrame(0L, 60f)
        assertEquals(SessionPhase.ARRIVED, vm.phase)
        assertEquals(1 to 25, store.lifetime())
        assertEquals(1, store.completions(vm.course.id))
        val terminus = vm.scene.distanceU
        vm.selectMinutes(50)
        vm.getOff()
        vm.onFrame(0L, 60f)
        assertEquals(SessionPhase.DOCKED, vm.phase)
        assertEquals(-1, vm.direction)
        assertEquals(50, vm.tripMinutes)
        assertEquals(terminus, vm.scene.distanceU, 1e-6)
    }

    @Test fun finishedRestKeepsTheTerminusSceneAndRestoresOneReverseTicket() = withSession { models ->
        var vm = models.vm
        vm.depart()
        vm.seekFraction(1f)
        vm.onFrame(0L, 60f)
        val terminus = vm.scene.distanceU
        vm.rest()
        assertEquals(SessionPhase.RESTING, vm.phase)
        vm.selectMinutes(50)
        vm.skipRest()
        vm.onFrame(0L, 60f)
        assertEquals(SessionPhase.DOCKED, vm.phase)
        assertTrue(vm.restDone)
        assertEquals(-1, vm.direction)
        assertEquals(50, vm.tripMinutes)
        assertEquals(terminus, vm.scene.distanceU, 1e-6)
        repeat(5) { vm.onFrame(0L, 60f) }
        models.recreate()
        vm = models.vm
        vm.onFrame(0L, 60f)
        assertEquals(SessionPhase.DOCKED, vm.phase)
        assertTrue(vm.restDone)
        assertEquals(-1, vm.direction)
        assertEquals(terminus, vm.scene.distanceU, 1e-6)
        assertEquals(1 to 25, TripStore(context).lifetime())
        vm.depart()
        assertFalse(vm.restDone)
        assertEquals(-1, vm.direction)
    }

    @Test fun departureFromArrivalOrRestPreparesExactlyOneReturnJourney() = withSession { models ->
        val vm = models.vm
        for (resting in listOf(false, true)) {
            vm.depart()
            vm.seekFraction(1f)
            vm.onFrame(0L, 60f)
            val terminus = vm.scene.distanceU
            val oldDirection = vm.direction
            if (resting) vm.rest()
            vm.depart()
            vm.onFrame(0L, 60f)
            assertEquals(SessionPhase.RUNNING, vm.phase)
            assertEquals(-oldDirection, vm.direction)
            assertEquals(terminus, vm.scene.distanceU, 1e-3)
            val active = TripStore(context).loadActive()!!
            vm.depart()
            assertEquals(active.weatherSeed, TripStore(context).loadActive()!!.weatherSeed)
            assertEquals(-oldDirection, vm.direction)
        }
    }

    @Test fun completionCheckpointCanBeRetriedWithoutDuplicatingAnyCounters() = withSession { models ->
        val vm = models.vm
        val store = TripStore(context)
        vm.depart()
        vm.seekFraction(1f)
        vm.onFrame(0L, 60f)
        val active = store.loadActive()!!
        assertTrue(active.sessionId.isNotBlank())
        repeat(3) {
            TripStore(context).saveSession(active, vm.course.id, vm.direction, setOf(vm.stations.lastIndex), arrived = true)
        }
        assertEquals(1 to 25, store.today())
        assertEquals(1 to 25, store.lifetime())
        assertEquals(1, store.completions(vm.course.id))
        assertEquals("ARRIVED", store.loadActive()!!.phase)
    }

    @Test fun legacySessionWithoutAnIdRestoresWithAStableIdentity() = withSession { models ->
        models.vm.depart()
        context.getSharedPreferences("trip", 0).edit().remove("a_session_id").commit()
        val first = TripStore(context).loadActive()!!
        val second = TripStore(context).loadActive()!!
        assertTrue(first.sessionId.startsWith("legacy:"))
        assertEquals(first.sessionId, second.sessionId)
        models.recreate()
        models.vm.pause()
        assertEquals(first.sessionId, TripStore(context).loadActive()!!.sessionId)
    }

    @Test fun headlessSessionCommitsArrivalAndRestWithoutCreatingAViewModelOrDrawingFrames() = withSession { models ->
        val runtime = models.runtime
        val store = TripStore(context)
        runtime.depart()
        runtime.seek(runtime.durationSeconds)
        assertEquals("ARRIVED", store.loadActive()!!.phase)
        assertEquals(1 to 25, store.lifetime())
        runtime.rest()
        runtime.skipRest()
        assertTrue(runtime.state.restDone)
        assertEquals(-1, runtime.program.direction)
        val savedBase = store.loadActive()!!.base
        models.recreate()
        assertEquals(-1, models.runtime.program.direction)
        assertEquals(savedBase, store.loadActive()!!.base, 0.0)
        assertEquals(1 to 25, store.lifetime())
    }

    @Test fun pendingCompletionAndAcknowledgementSurviveNewStoresWithoutDuplicatingRecords() = withSession { models ->
        val vm = models.vm
        val store = TripStore(context)
        vm.depart(); vm.seekFraction(1f)
        val active = store.loadActive()!!
        val effect = com.kero.passingby.session.CompletionEffect("delivery-test",
            com.kero.passingby.session.CompletionEffect.Kind.ARRIVED, vm.course.id, vm.direction, vm.tripMinutes)
        store.saveSession(active, vm.course.id, vm.direction, arrived = true, effect = effect)
        assertTrue(TripStore(context).pendingEffects().any { it.id == effect.id })
        assertEquals(1 to 25, TripStore(context).lifetime())
        TripStore(context).acknowledgeEffect(effect.id)
        assertFalse(TripStore(context).pendingEffects().any { it.id == effect.id })
        store.saveSession(active, vm.course.id, vm.direction, arrived = true, effect = effect)
        assertFalse(TripStore(context).pendingEffects().any { it.id == effect.id })
        assertEquals(1 to 25, store.lifetime())
    }

    private class Models(private val app: PassingByApp) : AutoCloseable {
        private var graph = AppGraph(app)
        private var store = ViewModelStore()
        val runtime get() = graph.session
        val vm get() = ViewModelProvider(store, graph.journeyViewModelFactory)[JourneyViewModel::class.java]
        fun recreate() { store.clear(); graph.closeSession(); store = ViewModelStore(); graph = AppGraph(app) }
        override fun close() { store.clear(); graph.closeSession() }
    }

    private fun withSession(block: (Models) -> Unit) {
        val prefs = context.getSharedPreferences("trip", 0)
        val saved = prefs.all
        try {
            prefs.edit().clear().commit()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val models = Models(context.applicationContext as PassingByApp)
                try { block(models) } finally {
                    models.close()
                    TripTimer.stop(context)
                }
            }
        } finally {
            val editor = prefs.edit().clear()
            for ((key, value) in saved) when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            editor.commit()
        }
    }
}
