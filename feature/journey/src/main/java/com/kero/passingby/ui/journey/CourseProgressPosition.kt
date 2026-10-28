package com.kero.passingby.ui.journey

/** Equal visual stop spacing, interpolated using the experience's actual arrival timetable. */
internal fun courseMarkerPosition(marks: FloatArray, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (marks.size < 2) return p
    if (p <= marks.first()) return 0f
    for (index in 1 until marks.size) {
        if (p <= marks[index]) {
            val span = marks[index] - marks[index - 1]
            val fraction = if (span > 0f) (p - marks[index - 1]) / span else 1f
            return ((index - 1 + fraction) / marks.lastIndex).coerceIn(0f, 1f)
        }
    }
    return 1f
}
