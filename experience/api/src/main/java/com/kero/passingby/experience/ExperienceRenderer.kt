package com.kero.passingby.experience

import android.graphics.Canvas

/** Frame-only rendering contract. Session effects and persistence never run through it. */
interface ExperienceRenderer : AutoCloseable {
    val lastDrawNanos: Long
    val bitmapBytes: Long
    fun render(canvas: Canvas, w: Int, h: Int)
    fun release()
    override fun close() = release()
}
