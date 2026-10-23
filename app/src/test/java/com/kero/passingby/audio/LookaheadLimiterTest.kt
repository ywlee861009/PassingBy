package com.kero.passingby.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class LookaheadLimiterTest {
    @Test fun peakAndPcmMatchOriginalScan() {
        val random = Random(71)
        val signals = listOf(
            FloatArray(4096),
            FloatArray(4096) { if (it == 0 || it == 127 || it == 1000) -2f else 0f },
            FloatArray(4096) { if (it % 5 == 0) 1.4f else -1.4f },
            FloatArray(8192) { (it % 2048 - 1024) / 350f },
            FloatArray(8192) { (1024 - it % 2048) / 350f },
            FloatArray(22050 * 4) { random.nextFloat() * 6 - 3 },
        )
        for (signal in signals) {
            val ring = FloatArray(128)
            val peak = SlidingPeak(128)
            val limiter = LookaheadLimiter()
            var at = 0
            var gain = 1f
            for ((i, sample) in signal.withIndex()) {
                ring[at] = sample
                at = (at + 1) % ring.size
                var top = 0f
                for (v in ring) top = max(top, abs(v))
                assertEquals("peak at $i", top, peak.push(sample), 0f)
                val want = if (top > .9f) .9f / top else 1f
                gain += (want - gain) * if (want < gain) .04f else 1f / (22050 * .5f)
                val expected = ((ring[at] * gain).coerceIn(-1f, 1f) * 32000).toInt().toShort()
                assertEquals("PCM at $i", expected, limiter.process(sample))
            }
        }
    }

    @Test fun expiryAndEqualPeaksWorkBeyondIntSampleIndex() {
        val peak = SlidingPeak(3, Int.MAX_VALUE.toLong() - 2)
        val input = floatArrayOf(3f, -3f, 1f, 0f, 2f, 0f, 0f, 0f)
        val expected = floatArrayOf(3f, 3f, 3f, 3f, 2f, 2f, 2f, 0f)
        input.indices.forEach { assertEquals(expected[it], peak.push(input[it]), 0f) }
    }

    @Test fun singleSampleWindow() {
        val peak = SlidingPeak(1)
        assertEquals(2f, peak.push(-2f), 0f)
        assertEquals(0f, peak.push(0f), 0f)
    }
}
