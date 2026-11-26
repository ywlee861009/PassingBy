package com.kero.passingby.audio

import com.kero.passingby.journey.WindowEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowEventSoundTest {
    @Test fun dropsFollowTheVisibleContactsWithoutReplayingOnResume() {
        val sound = WindowEventSound(22050)
        assertEquals(0f, sound.level(WindowEventKind.WINDOW_DROPS, .1f), .0001f)
        for (contact in listOf(.126f, .316f, .447f)) {
            assertTrue(sound.level(WindowEventKind.WINDOW_DROPS, contact) > .015f)
        }
        assertEquals(0f, sound.level(null, 0f), .0001f)
        assertEquals(0f, sound.level(WindowEventKind.WINDOW_DROPS, .7f), .0001f)
    }

    @Test fun meteorImpactIsStrongerThanItsApproachAndQuieterThanTheChime() {
        val sound = WindowEventSound(22050)
        val approach = sound.level(WindowEventKind.METEOR, .29f)
        val impact = sound.level(WindowEventKind.METEOR, .43f)
        assertTrue(approach > 0f)
        assertTrue(impact > approach && impact < .08f)
        assertEquals(0f, sound.level(WindowEventKind.METEOR, .9f), .0001f)
    }

    private fun WindowEventSound.level(kind: WindowEventKind?, progress: Float): Float {
        if (!update(kind, progress)) return 0f
        var output = 0f
        repeat(6000) { output = sample(.6f, .35f, .08f, .015f) }
        return output
    }
}
