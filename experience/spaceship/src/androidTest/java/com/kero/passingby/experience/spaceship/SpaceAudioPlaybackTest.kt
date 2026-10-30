package com.kero.passingby.experience.spaceship

import com.kero.passingby.experience.voyage.VoyageScene
import org.junit.Assert.*
import org.junit.Test

class SpaceAudioPlaybackTest {
    @Test fun androidAudioActuallyPlaysPausesResumesAndReleases() {
        val audio = SpaceAudio()
        val scene = VoyageScene(SpaceCourses.all.first()).apply { progress = 4f / 1500; durationSeconds = 1500.0 }
        try {
            audio.update(scene, true); audio.start(1)
            await { audio.playing && audio.playedFrames > 6000 }
            audio.pause(); await { !audio.playing }
            val course = SpaceCourses.all.first()
            val seed = (0..1000).first {
                !SpaceUfo.startAt(0, 1500.0, course.stations.size, 1, it, course.id).isNaN()
            }
            scene.eventSeed = seed
            scene.progress = ((SpaceUfo.startAt(0, 1500.0, course.stations.size, 1,
                seed, course.id) + 6) / 1500).toFloat()
            audio.update(scene, true)
            audio.setLevel(2); audio.start(2)
            await { audio.playing && audio.playedFrames > 6000 }
            audio.update(scene, false)
            assertEquals(0.0, SpaceSoundProfile.at(scene.progress, 1500.0, course.stations.size,
                1, false, seed, course.id).ufo, 0.0)
        } finally { audio.close() }
        await { !audio.playing }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 4_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("AudioTrack must change state and consume real PCM frames", condition())
    }
}
