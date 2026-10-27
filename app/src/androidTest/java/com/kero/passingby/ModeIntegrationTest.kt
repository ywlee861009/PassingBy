package com.kero.passingby

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.experience.*
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.journey.SessionEvent
import com.kero.passingby.journey.SessionState
import com.kero.passingby.session.TripStore
import com.kero.passingby.session.TripTimer
import com.kero.passingby.ui.journey.JourneyViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ModeIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PassingByApp

    @Test fun flybysUseTheSameTimetableAsTheMapAndNeverStopInEitherDirection() = isolated {
        val graph = AppGraph(app)
        val program = graph.experiences.providerFor("space_solar_escape").create(TripStore(app))
        try {
            program.selectCourse("space_solar_escape", 25)
            val course = program.course
            assertTrue(course.flyby)
            assertTrue(course.stations.size >= 4)
            val outbound = program.stationMarks.copyOf()
            assertEquals(12.0, program.arrivalAt(1), .001)
            for (direction in listOf(1, -1)) {
                if (direction < 0) program.onSessionEvent(SessionEvent.GotOff(SessionPhase.ARRIVED), SessionState(tripMinutes = 25))
                val ordered = course.ordered(direction)
                for (i in 1 until ordered.lastIndex) {
                    val t = program.arrivalAt(i)
                    val original = if (direction > 0) i else ordered.lastIndex - i
                    assertEquals(t, program.secondsAtPosition(original.toDouble()), .001)
                    assertEquals(-1, program.stoppedAt(t))
                    assertEquals(RouteCaption(ordered[i].name, RouteCaption.Kind.FLYBY), program.routeCaptionAt(t))
                    assertEquals(i + 1, program.nextStop(t + .01))
                    assertEquals((0..i).map { if (direction > 0) it else ordered.lastIndex - it }.toSet(), program.reachedStations(t + .01))
                }
            }
            for (i in outbound.indices) assertEquals(1f - outbound[outbound.lastIndex - i], program.stationMarks[i], .00001f)
        } finally { program.close(); graph.closeSession() }
    }

    @Test fun anOldSpaceCourseRestoresTheSamePausedClockOnTheEscapeRoute() = isolated {
        org.junit.Assume.assumeTrue(BuildConfig.PREVIEW_CODE_SHA256.isNotEmpty())
        File(app.noBackupFilesDir, "preview-mode-grant").writeText(BuildConfig.PREVIEW_CODE_SHA256)
        var graph = AppGraph(app)
        var models = ViewModelStore()
        try {
            var vm = ViewModelProvider(models, graph.journeyViewModelFactory)[JourneyViewModel::class.java]
            assertTrue(vm.selectJourneyMode(JourneyMode.SPACESHIP))
            vm.depart(); vm.seekFraction(.4f); vm.pause()
            val remaining = vm.remainingSec
            val sessionId = graph.store.loadActive()!!.sessionId
            models.clear(); graph.closeSession()
            graph.store.courseId = "space_trappist"
            graph = AppGraph(app); models = ViewModelStore()
            vm = ViewModelProvider(models, graph.journeyViewModelFactory)[JourneyViewModel::class.java]
            assertEquals("space_solar_escape", vm.course.id)
            assertEquals(JourneyMode.SPACESHIP, vm.journeyMode)
            assertEquals(SessionPhase.PAUSED, vm.phase)
            assertEquals(remaining, vm.remainingSec)
            assertEquals(sessionId, graph.store.loadActive()!!.sessionId)
            assertEquals(1, vm.courses.size)
        } finally { models.clear(); graph.closeSession() }
    }

    @Test fun publicLaunchUsesTheSamePickerWithoutASecretCode() = isolated {
        val graph = AppGraph(app)
        val models = ViewModelStore()
        val publicAccess = object : com.kero.passingby.ui.journey.ModeAccess {
            override val publicModes = setOf(JourneyMode.TRAIN, JourneyMode.SPACESHIP)
            override val codeEnabled = false
            override val unlocked = false
            override fun unlock(code: String) = false
            override fun lock() = Unit
        }
        try {
            val factory = JourneyViewModel.Factory(app,
                com.kero.passingby.ui.journey.JourneyOptions(false, publicAccess), graph.store, { graph.session }, graph.experiences)
            val vm = ViewModelProvider(models, factory)[JourneyViewModel::class.java]
            assertTrue(vm.showModeMenu)
            assertFalse(vm.previewUnlocked)
            assertTrue(vm.selectJourneyMode(JourneyMode.SPACESHIP))
            assertFalse(vm.selectJourneyMode(JourneyMode.SHIP))
        } finally { models.clear(); graph.closeSession() }
    }

    @Test fun lockedUsersSeeOnlyPublicModesAndCannotSelectHiddenCourses() = isolated {
        val graph = AppGraph(app)
        val models = ViewModelStore()
        try {
            val vm = ViewModelProvider(models, graph.journeyViewModelFactory)[JourneyViewModel::class.java]
            assertEquals(JourneyMode.TRAIN, vm.journeyMode)
            assertEquals(setOf(JourneyMode.TRAIN), vm.availableModes.toSet())
            assertFalse(vm.showModeMenu)
            assertFalse(vm.selectJourneyMode(JourneyMode.SPACESHIP))
            vm.forceCourse(graph.experiences.coursesFor(JourneyMode.SPACESHIP).first())
            assertEquals(JourneyMode.TRAIN, vm.journeyMode)
        } finally { models.clear(); graph.closeSession() }
    }

    @Test fun revokedPreviewCannotRestoreItsClockOntoTheDefaultTrain() = isolated {
        val code = InstrumentationRegistry.getArguments().getString("previewCode")
        org.junit.Assume.assumeNotNull(code)
        var graph = AppGraph(app)
        var models = ViewModelStore()
        try {
            var vm = ViewModelProvider(models, graph.journeyViewModelFactory)[JourneyViewModel::class.java]
            assertTrue(vm.unlockModes(code!!))
            assertTrue(vm.selectJourneyMode(JourneyMode.SPACESHIP))
            vm.depart(); vm.seekFraction(.4f)
            assertNotNull(graph.store.loadActive())
            models.clear(); graph.closeSession()
            PreviewModeAccess(app).lock()
            graph = AppGraph(app); models = ViewModelStore()
            vm = ViewModelProvider(models, graph.journeyViewModelFactory)[JourneyViewModel::class.java]
            assertEquals(JourneyMode.TRAIN, vm.journeyMode)
            assertEquals(SessionPhase.DOCKED, vm.phase)
            assertNull(graph.store.loadActive())
            assertEquals(0 to 0, graph.store.lifetime())
        } finally { models.clear(); graph.closeSession() }
    }

    @Test fun previewModesRestoreFinishOnceAndReturnWithoutLosingTheirDestination() = isolated {
        // Supply the private code at test invocation; never embed it in a tracked test source.
        val code = InstrumentationRegistry.getArguments().getString("previewCode")
        org.junit.Assume.assumeNotNull(code)
        var graph = AppGraph(app)
        var models = ViewModelStore()
        try {
            var vm = ViewModelProvider(models, graph.journeyViewModelFactory)[JourneyViewModel::class.java]
            assertFalse(vm.unlockModes("incorrect-test-code"))
            assertTrue(vm.unlockModes(code!!))
            assertEquals(JourneyMode.entries.toSet(), vm.availableModes.toSet())
            assertTrue(vm.selectJourneyMode(JourneyMode.SPACESHIP))
            assertEquals(1, vm.courses.size)
            assertEquals("The first flyby countdown is ready before departure", 12, vm.nextStopSec)
            vm.depart()
            assertFalse(vm.selectJourneyMode(JourneyMode.SHIP))
            vm.seekFraction(.6f)
            val courseId = vm.course.id
            val left = vm.remainingSec
            models.clear(); graph.closeSession()
            graph = AppGraph(app); models = ViewModelStore()
            vm = ViewModelProvider(models, graph.journeyViewModelFactory)[JourneyViewModel::class.java]
            assertEquals(courseId, vm.course.id)
            assertEquals(JourneyMode.SPACESHIP, vm.journeyMode)
            assertTrue(vm.previewUnlocked)
            assertTrue(kotlin.math.abs(left - vm.remainingSec) <= 2)
            vm.seekFraction(1f)
            assertEquals(SessionPhase.ARRIVED, vm.phase)
            assertEquals(1, TripStore(app).completions(courseId))
            graph.session.refresh()
            assertEquals(1, TripStore(app).completions(courseId))
            val destination = vm.stations.last().name
            vm.getOff()
            assertEquals(destination, vm.stations.first().name)
            assertTrue(vm.selectJourneyMode(JourneyMode.SHIP))
            assertTrue(vm.selectJourneyMode(JourneyMode.AIRPLANE))
            vm.lockPreviewModes()
            assertEquals(JourneyMode.TRAIN, vm.journeyMode)
            assertFalse(vm.showModeMenu)
        } finally { models.clear(); graph.closeSession() }
    }

    @Test fun everyNewCourseRendersPortraitLandscapeAndArrivalWithoutWritingSessionState() = isolated {
        val graph = AppGraph(app)
        val output = File(app.getExternalFilesDir(null), "mode-validation").apply { mkdirs() }
        try {
            val store = TripStore(app)
            for (course in graph.experiences.courses.filter { graph.experiences.modeFor(it.id) != JourneyMode.TRAIN }) {
                val program = graph.experiences.providerFor(course.id).create(store)
                try {
                    program.selectCourse(course.id, 25)
                    for ((w, h) in listOf(1080 to 1920, 1920 to 1080)) {
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap)
                            for (p in listOf(0f, .5f, 1f)) {
                                program.scene.setViewport(w > h, if (p == 0f) minOf(w, h) * .6f else 0f)
                                program.frame(p * 1500.0, 1.0, if (p == 1f) SessionPhase.ARRIVED else SessionPhase.RUNNING, .016f)
                                program.renderer.render(canvas, w, h)
                                assertTrue(program.renderer.lastDrawNanos > 0)
                                val colors = (1..8).flatMap { x -> (1..8).map { y -> bitmap.getPixel(w * x / 10, h * y / 10) } }.toSet()
                                assertTrue("Blank landscape: ${course.id}", colors.size > 1)
                                File(output, "${course.id}-${w}x${h}-${(p * 100).toInt()}.png").outputStream().use {
                                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                                }
                            }
                        } finally { bitmap.recycle() }
                    }
                } finally { program.close() }
            }
            assertNull(store.courseId)
            assertEquals(0 to 0, store.lifetime())
        } finally { graph.closeSession() }
    }

    private fun isolated(test: () -> Unit) {
        val prefs = app.getSharedPreferences("trip", 0)
        val saved = prefs.all
        val grant = File(app.noBackupFilesDir, "preview-mode-grant")
        val savedGrant = grant.takeIf { it.exists() }?.readBytes()
        prefs.edit().clear().commit(); grant.delete()
        var failure: Throwable? = null
        try {
            instrumentation.runOnMainSync { try { test() } catch (error: Throwable) { failure = error } }
            failure?.let { throw it }
        } finally {
            instrumentation.runOnMainSync { TripTimer.stop(app) }
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
            if (savedGrant == null) grant.delete() else grant.writeBytes(savedGrant)
        }
    }
}
