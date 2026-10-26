package com.kero.passingby.experience.spaceship

import android.content.res.AssetManager
import android.graphics.*
import java.util.Locale

/** Bounded, renderer-owned cache. Globes are baked offline, not shaded per pixel on each frame. */
internal class PlanetImages(private val assets: AssetManager) : AutoCloseable {
    private val cache = LinkedHashMap<CelestialBody, Bitmap>(4, .75f, true)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { isDither = true }
    private val rectangle = RectF()
    val bytes: Long get() = cache.values.sumOf { it.allocationByteCount.toLong() }

    fun retain(body: CelestialBody) {
        val entries = cache.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            if (entry.key != body) { entry.value.recycle(); entries.remove() }
        }
    }

    fun draw(canvas: Canvas, body: CelestialBody, x: Float, y: Float, radius: Float, opacity: Float = 1f) {
        if (radius <= 0 || opacity <= 0) return
        val bitmap = cache[body] ?: assets.open("space/planets/${body.name.lowercase(Locale.ROOT)}.webp").use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Unreadable globe: $body" }
        }.also { loaded ->
            if (cache.size >= 3) {
                val oldest = cache.entries.iterator()
                oldest.next().value.recycle()
                oldest.remove()
            }
            cache[body] = loaded
        }
        val flattening = when (body) {
            CelestialBody.JUPITER -> .935f
            CelestialBody.SATURN -> .902f
            else -> 1f
        }
        // Baked maps retain a small antialiasing margin outside the mathematical limb.
        val extent = radius / .97f
        rectangle.set(x - extent, y - extent * flattening, x + extent, y + extent * flattening)
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawBitmap(bitmap, null, rectangle, paint)
        paint.alpha = 255
    }

    override fun close() { cache.values.forEach(Bitmap::recycle); cache.clear() }
}
