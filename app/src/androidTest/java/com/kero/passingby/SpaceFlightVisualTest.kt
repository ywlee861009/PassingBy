package com.kero.passingby

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.session.TripStore
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SpaceFlightVisualTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun launchAndRealGlobeViewsRenderDeterministicallyWithinTheBitmapBudget() {
        val context = instrumentation.targetContext
        val output = File(context.cacheDir, "space-flight-validation").apply { mkdirs() }
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val graph = AppGraph(context.applicationContext as PassingByApp)
            val store = TripStore(context)
            try {
                val provider = graph.experiences.providerFor("space_jupiter_pluto")
                val program = provider.create(store)
                try {
                    program.selectCourse("space_jupiter_pluto", 25)
                    for ((w, h) in listOf(960 to 1600, 1600 to 960)) {
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap)
                            val hashes = mutableSetOf<String>()
                            val times = listOf(0, 15, 30, 55, 75, 90, 110, 240, 750, 1500)
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
                            program.frame(55.0, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(canvas, w, h)
                            val rising = hash(bitmap)
                            program.frame(900.0, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(canvas, w, h)
                            program.frame(55.0, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(canvas, w, h)
                            assertEquals("Restoring the clock must restore the exact camera", rising, hash(bitmap))
                            program.frame(30.0, 1.0, SessionPhase.RUNNING, .016f)
                            program.renderer.render(canvas, w, h)
                            assertTrue("Seeking back to the coast must evict unused cruise images",
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
                } finally { program.close() }
            } catch (error: Throwable) { failure = error }
            finally { graph.closeSession() }
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
