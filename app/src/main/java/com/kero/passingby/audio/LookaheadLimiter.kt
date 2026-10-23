package com.kero.passingby.audio

import kotlin.math.abs

/** 최근 windowSize개 샘플의 최대 절댓값. 각 항목은 한 번 들어오고 한 번 나가며 할당하지 않는다. */
internal class SlidingPeak(private val windowSize: Int, startIndex: Long = 0L) {
    init { require(windowSize > 0) }
    private val values = FloatArray(windowSize)
    private val indices = LongArray(windowSize)
    private var head = 0
    private var size = 0
    private var nextIndex = startIndex

    fun push(sample: Float): Float {
        val index = nextIndex++
        while (size > 0 && index - indices[head] >= windowSize) {
            head = (head + 1) % windowSize
            size--
        }
        val value = abs(sample)
        while (size > 0 && values[(head + size - 1) % windowSize] <= value) size--
        val tail = (head + size) % windowSize
        values[tail] = value
        indices[tail] = index
        size++
        return values[head]
    }
}

/** 기존 링 버퍼의 쓰기 → 포인터 이동 → 지연 출력 순서와 gain 수식을 유지한다. */
internal class LookaheadLimiter(look: Int = 128, private val rate: Int = 22050) {
    private val ahead = FloatArray(look)
    private val peak = SlidingPeak(look)
    private var at = 0
    private var gain = 1f

    fun process(sample: Float): Short {
        ahead[at] = sample
        at = (at + 1) % ahead.size
        val top = peak.push(sample)
        val want = if (top > .9f) .9f / top else 1f
        gain += (want - gain) * if (want < gain) .04f else 1f / (rate * .5f)
        return ((ahead[at] * gain).coerceIn(-1f, 1f) * 32000).toInt().toShort()
    }
}
