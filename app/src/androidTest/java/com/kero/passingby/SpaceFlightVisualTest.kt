package com.kero.passingby

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.session.TripStore
import com.kero.passingby.experience.JourneyMode
import com.kero.passingby.experience.spaceship.CelestialBody
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SpaceFlightVisualTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun launchAndCanvasGlobesRenderDeterministicallyWithinTheBitmapBudget() {
        val context = instrumentation.targetContext
        val output = File(context.cacheDir, "space-flight-validation").apply { mkdirs() }
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val graph = AppGraph(context.applicationContext as PassingByApp)
            val store = TripStore(context)
            try {
                val provider = graph.experiences.providerFor("space_solar_escape")
                val program = provider.create(store)
                try {
                    program.selectCourse("space_solar_escape", 25)
                    for ((w, h) in listOf(960 to 1600, 1600 to 960)) {
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap)
                            val hashes = mutableSetOf<String>()
                            val times = listOf(0, 1, 2, 3, 4, 6, 10, 12, 14, 20, 240, 750, 1500)
                            for (seconds in times) {
                                program.frame(seconds.toDouble(), 1.0, SessionPhase.RUNNING, .016f)
                                program.renderer.render(canvas, w, h)
                                hashes += hash(bitmap)
                                File(output, "flight-${w}x${h}-${seconds}s.png").outputStream().use {
                                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                                }
                                assertTrue(program.renderer.bitmapBytes <= 3L * 1024 * 1024 * 4)
                            }
                            assertEquals("Every launch stage must have its own visible scene", times.size, hashes.size)
                            program.frame(4.0, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(canvas, w, h)
                            val rising = hash(bitmap)
                            program.frame(900.0, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(canvas, w, h)
                            program.frame(4.0, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(canvas, w, h)
                            assertEquals("Restoring the clock must restore the exact camera", rising, hash(bitmap))
                            program.frame(7.0, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(canvas, w, h)
                            assertTrue("Seeking back to the coast must evict unused cruise globes",
                                program.renderer.bitmapBytes <= 3L * 1024 * 1024 * 4)
                        } finally { bitmap.recycle() }
                    }
                    // The owned bitmap cache is released when leaving/recreating the presentation.
                    val renderer = program.renderer
                    program.releasePresentation()
                    assertEquals(0L, renderer.bitmapBytes)
                    program.frame(750.0, 1.0, SessionPhase.RUNNING, .016f)
                    val recreated = Bitmap.createBitmap(960, 1600, Bitmap.Config.ARGB_8888)
                    try { program.renderer.render(Canvas(recreated), 960, 1600) } finally { recreated.recycle() }
                    if (InstrumentationRegistry.getArguments().getString("renderPreview") == "true") {
                        program.configure(25)
                        val preview = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888)
                        try {
                            val previewDir = File(output, "launch-preview").apply { mkdirs() }
                            for (frame in 0..200) {
                                program.frame(frame / 10.0, 1.0, SessionPhase.RUNNING, .1f)
                                program.renderer.render(Canvas(preview), 800, 480)
                                File(previewDir, "%04d.png".format(frame)).outputStream().use {
                                    preview.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                            }
                        } finally { preview.recycle() }
                    }
                } finally { program.close() }
            } catch (error: Throwable) { failure = error }
            finally { graph.closeSession() }
        }
        failure?.let { throw it }
    }

    @Test fun starsMoveAtTheSameSpeedInShortAndLongSessionsAndFreezeWhenPaused() {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val graph = AppGraph(instrumentation.targetContext.applicationContext as PassingByApp)
            val program = graph.experiences.providerFor("space_solar_escape").create(TripStore(instrumentation.targetContext))
            val bitmap = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888)
            try {
                val sequences = listOf(25, 90).map { minutes ->
                    program.selectCourse("space_solar_escape", minutes)
                    listOf(30.0, 30.25).map { seconds ->
                        program.frame(seconds, 1.0, SessionPhase.RUNNING, .016f)
                        program.renderer.render(Canvas(bitmap), 800, 480)
                        hash(bitmap)
                    }
                }
                assertEquals("Session length must not slow down the starfield", sequences[0], sequences[1])
                assertNotEquals("Stars should visibly advance within a quarter second", sequences[0][0], sequences[0][1])
                program.frame(30.25, 0.0, SessionPhase.PAUSED, .016f)
                program.renderer.render(Canvas(bitmap), 800, 480)
                assertEquals(sequences[1][1], hash(bitmap))
            } catch (error: Throwable) { failure = error }
            finally { bitmap.recycle(); program.close(); graph.closeSession() }
        }
        failure?.let { throw it }
    }

    @Test fun everyCanvasPlanetRendersDistinctlyAcrossCourseChangesWithoutImageAssets() {
        val context = instrumentation.targetContext
        val output = File(context.cacheDir, "space-flight-validation").apply { mkdirs() }
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val graph = AppGraph(context.applicationContext as PassingByApp)
            val store = TripStore(context)
            val bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888)
            try {
                assertTrue("Space artwork must not load photos or pre-rendered texture files",
                    context.assets.list("space").isNullOrEmpty())
                val seen = mutableSetOf<CelestialBody>()
                val hashes = mutableSetOf<String>()
                val program = graph.experiences.providerFor("space_solar_escape").create(store)
                try {
                    for (course in graph.experiences.coursesFor(JourneyMode.SPACESHIP)) {
                        program.selectCourse(course.id, 25)
                        course.stations.forEachIndexed { index, station ->
                            val body = CelestialBody.entries.singleOrNull { it.nameResource == station.name } ?: return@forEachIndexed
                            if (!seen.add(body)) return@forEachIndexed
                            val seconds = if (index == 0) 4.0 else program.arrivalAt(index)
                            program.frame(seconds, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(Canvas(bitmap), 1200, 1200)
                            hashes += hash(bitmap)
                            File(output, "planet-${body.name.lowercase()}.png").outputStream().use {
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                            assertTrue(program.renderer.bitmapBytes in 1..3L * 1024 * 1024 * 4)
                        }
                    }
                    assertEquals(setOf(CelestialBody.EARTH, CelestialBody.MOON, CelestialBody.MARS, CelestialBody.JUPITER,
                        CelestialBody.SATURN, CelestialBody.URANUS, CelestialBody.NEPTUNE, CelestialBody.PLUTO), seen)
                    assertEquals("Each planet needs its own visible character", seen.size, hashes.size)
                } finally { program.close() }
            } catch (error: Throwable) { failure = error }
            finally { bitmap.recycle(); graph.closeSession() }
        }
        failure?.let { throw it }
    }

    private fun hash(bitmap: Bitmap): String {
        val pixels = IntArray(64 * 64)
        var index = 0
        for (y in 0 until 64) for (x in 0 until 64)
            pixels[index++] = bitmap.getPixel((x + 1) * bitmap.width / 65, (y + 1) * bitmap.height / 65)
        return pixels.contentHashCode().toString()
    }
}
