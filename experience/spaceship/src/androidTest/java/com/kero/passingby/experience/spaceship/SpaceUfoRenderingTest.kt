package com.kero.passingby.experience.spaceship

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.experience.voyage.VoyageScene
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SpaceUfoRenderingTest {
    @Test fun saucerRendersInBothOrientationsAndRestoresTheSamePictureAndAudioClock() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val course = SpaceCourses.all.single()
        val seed = (0..1000).first {
            !SpaceUfo.startAt(0, 1500.0, course.stations.size, 1, it, course.id).isNaN()
        }
        val start = SpaceUfo.startAt(0, 1500.0, course.stations.size, 1, seed, course.id)
        val quietSeed = (0..1000).first {
            SpaceUfo.startAt(0, 1500.0, course.stations.size, 1, it, course.id).isNaN()
        }
        val scene = VoyageScene(course).apply {
            eventSeed = seed
            progress = ((start + 6) / 1500).toFloat()
            durationSeconds = 1500.0
        }
        val renderer = SpaceRenderer(scene)
        val output = File(context.cacheDir, "space-ufo-validation").apply { mkdirs() }
        try {
            for ((w, h) in listOf(960 to 1600, 1600 to 960)) {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    renderer.render(Canvas(bitmap), w, h)
                    val original = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
                    File(output, "ufo-${w}x${h}.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    val view = SpaceUfo.View()
                    SpaceUfo.sample(scene.progress.toDouble() * 1500, 1500.0,
                        course.stations.size, 1, seed, course.id, view)
                    assertEquals(1f, view.opacity, 0f)
                    assertTrue(SpaceSoundProfile.at(scene.progress, 1500.0,
                        course.stations.size, 1, true, seed, course.id).ufo > .99)
                    scene.eventSeed = quietSeed
                    renderer.render(Canvas(bitmap), w, h)
                    val withoutUfo = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
                    val changed = original.indices.filter { original[it] != withoutUfo[it] }
                    assertTrue("The encounter must draw a visible saucer", changed.size > 500)
                    assertTrue("The UFO must stay above the controls", changed.all { it / w < h * .6 })
                    scene.eventSeed = seed
                    scene.progress = .99f
                    renderer.render(Canvas(bitmap), w, h)
                    scene.progress = ((start + 6) / 1500).toFloat()
                    renderer.render(Canvas(bitmap), w, h)
                    val restored = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
                    assertArrayEquals("Seeking back must restore the exact UFO scene", original, restored)
                    assertTrue(renderer.bitmapBytes <= 3L * 1024 * 1024 * 4)
                } finally { bitmap.recycle() }
            }
        } finally { renderer.release() }
        assertEquals(0L, renderer.bitmapBytes)
    }
}
