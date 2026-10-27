package com.kero.passingby.experience.spaceship

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpaceSoundTest {
    private fun profile(seconds: Double, running: Boolean = true) =
        SpaceSoundProfile.at((seconds / 1500).toFloat(), 1500.0, 4, 1, running)

    private fun signal(profile: SpaceSoundProfile, gain: Double = .72): ShortArray {
        val synth = SpaceSynth()
        val out = ShortArray(SpaceSynth.RATE)
        repeat(2) { synth.render(out, profile, gain) }
        return out
    }
    private fun rms(samples: ShortArray) = sqrt(samples.map { (it / 32768.0).pow(2) }.average())
    private fun tone(samples: ShortArray, frequency: Double): Double {
        var real = 0.0; var imaginary = 0.0
        for (i in samples.indices) {
            val angle = 2 * PI * frequency * i / SpaceSynth.RATE
            real += samples[i] / 32768.0 * cos(angle); imaginary += samples[i] / 32768.0 * sin(angle)
        }
        return 2 * hypot(real, imaginary) / samples.size
    }

    @Test fun launchIsAudiblyStrongerThanCruiseAndContainsPhoneSpeakerHarmonics() {
        val launch = signal(profile(4.0)); val cruise = signal(profile(450.0))
        assertTrue("Launch roar must be clearly audible", rms(launch) > .04)
        assertTrue("Engine cutoff must be noticeable", rms(launch) > rms(cruise) * 2)
        assertTrue("Do not rely on sub-bass alone", tone(launch, 376.0) > .015)
        assertTrue("Cabin machinery stays audible in cruise", tone(cruise, 392.0) > .006)
    }

    @Test fun pausedAndIdleStatesDoNotReplayTakeoffButKeepQuietCabinSound() {
        val paused = profile(4.0, false)
        assertEquals(0.0, paused.engine, 0.0)
        assertEquals(0.0, paused.air, 0.0)
        assertTrue(rms(signal(paused)) > .004)
        assertTrue(profile(4.0).engine > .8)
        assertEquals(.1, profile(14.0).engine, .0001)
    }

    @Test fun maneuverJetsFollowTheMoonFlybyAndReverseJourneysUseTheirActualLocation() {
        assertTrue(profile(55.0).maneuver > .6)
        assertEquals(0.0, profile(450.0).maneuver, .0001)
        val reverseDeparture = SpaceSoundProfile.at(0f, 1500.0, 4, -1, true)
        val reverseLanding = SpaceSoundProfile.at(.998f, 1500.0, 4, -1, true)
        assertEquals(.1, reverseDeparture.engine, .0001)
        assertTrue(reverseLanding.engine > .8)
    }

    @Test fun outputIsBoundedAndSoundLevelsChangeTheActualSignal() {
        val low = signal(profile(4.0), .48); val high = signal(profile(4.0), 1.0)
        assertTrue(rms(high) > rms(low) * 1.7)
        for (second in listOf(0.0, 4.0, 9.0, 12.0, 55.0, 750.0)) {
            val samples = signal(profile(second), 1.0)
            assertTrue(samples.all { abs(it.toInt()) < 30000 })
        }
    }

    @Test fun renderAContinuousPreviewAcrossTakeoffAndEngineCutoff() {
        val synth = SpaceSynth(); val samples = ShortArray(SpaceSynth.RATE * 20)
        val block = ShortArray(480)
        for (offset in samples.indices step block.size) {
            synth.render(block, profile(offset.toDouble() / SpaceSynth.RATE), .72)
            block.copyInto(samples, offset)
        }
        assertTrue(rms(samples.copyOfRange(SpaceSynth.RATE, SpaceSynth.RATE * 8)) >
            rms(samples.copyOfRange(SpaceSynth.RATE * 16, SpaceSynth.RATE * 20)) * 2)
        val data = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()); data.putInt(36 + samples.size * 2); data.put("WAVEfmt ".toByteArray())
        data.putInt(16); data.putShort(1); data.putShort(1); data.putInt(SpaceSynth.RATE)
        data.putInt(SpaceSynth.RATE * 2); data.putShort(2); data.putShort(16)
        data.put("data".toByteArray()); data.putInt(samples.size * 2); samples.forEach { data.putShort(it) }
        File("build/reports/space-launch-preview.wav").apply { parentFile.mkdirs(); writeBytes(data.array()) }
    }
}
