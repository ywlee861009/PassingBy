package com.kero.passingby.experience.spaceship

import android.graphics.*

/** Like the train's terrain tiles: bake original Canvas paths once, then reuse at frame rate. */
internal class PlanetGlobes : AutoCloseable {
    private val cache = LinkedHashMap<CelestialBody, Bitmap>(4, .75f, true)
    private val painter = PlanetPainter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    val bytes: Long get() = cache.values.sumOf { it.allocationByteCount.toLong() }

    fun retain(body: CelestialBody) {
        val entries = cache.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            if (entry.key != body) { entry.value.recycle(); entries.remove() }
        }
    }

    fun draw(canvas: Canvas, body: CelestialBody, x: Float, y: Float, radius: Float, opacity: Float) {
        if (radius <= 0 || opacity <= 0) return
        val bitmap = cache[body] ?: run {
            if (cache.size >= 3) {
                val entries = cache.entries.iterator()
                entries.next().value.recycle(); entries.remove()
            }
            Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).also {
                painter.draw(Canvas(it), body, 512f, 512f, 496f)
                cache[body] = it
            }
        }
        val flattening = when (body) {
            CelestialBody.JUPITER -> .935f
            CelestialBody.SATURN -> .902f
            else -> 1f
        }
        val extent = radius * 512f / 496f
        destination.set(x - extent, y - extent * flattening, x + extent, y + extent * flattening)
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    override fun close() { cache.values.forEach(Bitmap::recycle); cache.clear() }
}
