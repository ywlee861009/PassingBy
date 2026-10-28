package com.kero.passingby.experience.spaceship

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class SpaceUfoSoundTest {
    private fun rms(samples: ShortArray) = sqrt(samples.sumOf { (it / 32768.0).pow(2) } / samples.size)

    /** Isolate the hovering tone instead of requiring the whole mix to become much louder. */
    private fun hoverBandRms(samples: ShortArray): Double {
        val omega = 2 * PI * 650 / SpaceSynth.RATE
        val alpha = sin(omega) / (2 * 1.8)
        val b0 = alpha / (1 + alpha)
        val a1 = -2 * cos(omega) / (1 + alpha)
        val a2 = (1 - alpha) / (1 + alpha)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0; var power = 0.0
        for (sample in samples) {
            val x = sample / 32768.0
            val y = b0 * (x - x2) - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            power += y * y
        }
        return sqrt(power / samples.size)
    }

    @Test fun ufoHasItsOwnAudibleVoiceAndRespectsGainWithoutClippingOrAbruptEdges() {
        val course = SpaceCourses.all.single()
        val seed = (0..1000).first {
            !SpaceUfo.startAt(0, 1500.0, course.stations.size, 1, it, course.id).isNaN()
        }
        val start = SpaceUfo.startAt(0, 1500.0, course.stations.size, 1, seed, course.id)
        val blockSize = 480
        val length = SpaceSynth.RATE * 16
        fun render(gain: Double, encounter: Boolean): ShortArray {
            val synth = SpaceSynth()
            val result = ShortArray(length)
            val block = ShortArray(blockSize)
            for (offset in result.indices step blockSize) {
                val seconds = start - 2.0 + offset.toDouble() / SpaceSynth.RATE
                val profile = SpaceSoundProfile.at((seconds / 1500).toFloat(), 1500.0,
                    course.stations.size, 1, true, seed, if (encounter) course.id else "")
                synth.render(block, profile, gain)
                block.copyInto(result, offset)
            }
            return result
        }
        val withUfo = render(.72, true)
        val withoutUfo = render(.72, false)
        val low = render(.48, true)
        val high = render(1.0, true)
        val center = 6 * SpaceSynth.RATE until 10 * SpaceSynth.RATE
        fun middle(samples: ShortArray) = samples.copyOfRange(center.first, center.last + 1)
        assertTrue("The dedicated hovering band must stand out from cabin machinery",
            hoverBandRms(middle(withUfo)) > hoverBandRms(middle(withoutUfo)) * 1.8)
        assertTrue(rms(middle(high)) > rms(middle(low)) * 1.8)
        assertTrue(high.all { abs(it.toInt()) < 30000 })
        // Difference from the identical cabin signal isolates the event, including its fade-out tail.
        val effect = ShortArray(length) { (withUfo[it] - withoutUfo[it]).toShort() }
        assertTrue(rms(effect.copyOfRange(0, SpaceSynth.RATE)) < .0001)
        assertTrue(rms(effect.copyOfRange(15 * SpaceSynth.RATE, length)) < .0001)
        assertTrue("Continuous oscillator phases must not click at control-block boundaries",
            (1 until effect.size).maxOf { abs(effect[it].toInt() - effect[it - 1].toInt()) } < 1800)

        val data = ByteBuffer.allocate(44 + withUfo.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()); data.putInt(36 + withUfo.size * 2); data.put("WAVEfmt ".toByteArray())
        data.putInt(16); data.putShort(1); data.putShort(1); data.putInt(SpaceSynth.RATE)
        data.putInt(SpaceSynth.RATE * 2); data.putShort(2); data.putShort(16)
        data.put("data".toByteArray()); data.putInt(withUfo.size * 2); withUfo.forEach { data.putShort(it) }
        File("build/reports/space-ufo-preview.wav").apply { parentFile?.mkdirs(); writeBytes(data.array()) }
    }
}
