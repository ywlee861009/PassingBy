package com.kero.passingby.perf

/**
 * 프레임 간격·그리기 시간 통계. 최근 240프레임을 고리 버퍼로 들고 있어 메모리가 늘지 않는다.
 * 끊김은 "화면 주사율 간격의 1.5배를 넘긴 프레임"으로 센다.
 */
class FrameStats {
    private val intervals = FloatArray(N)
    private val draws = FloatArray(N)
    private val scratch = FloatArray(N)
    private var size = 0
    private var pos = 0
    private var sincePublishNanos = 0L

    var janks = 0L
        private set

    /** @return 화면 표시를 갱신할 때(0.5초마다)면 true */
    fun record(intervalNanos: Long, drawNanos: Long, refreshHz: Float): Boolean {
        val ms = intervalNanos / 1e6f
        intervals[pos] = ms
        draws[pos] = drawNanos / 1e6f
        pos = (pos + 1) % N
        if (size < N) size++
        if (ms > 1.5f * 1000f / refreshHz) janks++
        sincePublishNanos += intervalNanos
        if (sincePublishNanos >= 500_000_000L) {
            sincePublishNanos = 0L
            return true
        }
        return false
    }

    fun avgInterval() = average(intervals)
    fun avgDraw() = average(draws)

    fun maxDraw(): Float {
        var m = 0f
        for (i in 0 until size) if (draws[i] > m) m = draws[i]
        return m
    }

    fun p95Interval(): Float {
        if (size == 0) return 0f
        intervals.copyInto(scratch, 0, 0, size)
        scratch.sort(0, size)
        return scratch[((size - 1) * 0.95f).toInt()]
    }

    private fun average(a: FloatArray): Float {
        if (size == 0) return 0f
        var s = 0f
        for (i in 0 until size) s += a[i]
        return s / size
    }

    private companion object {
        const val N = 240
    }
}
