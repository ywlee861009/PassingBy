package com.kero.passingby.experience.spaceship

import android.graphics.*
import kotlin.math.*

/** Coastal ground and successive cloud decks fall down the window as altitude increases. */
internal class EarthLaunchLandscape(private val assets: android.content.res.AssetManager) : AutoCloseable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    private var coast: Bitmap? = null
    val bytes: Long get() = coast?.allocationByteCount?.toLong() ?: 0L

    fun draw(canvas: Canvas, width: Float, height: Float, ascent: Float) {
        val fade = 1f - SpaceFlightPath.smooth((ascent - .3f) / .34f)
        if (fade <= 0) { releaseGround(); return }
        val image = coast ?: assets.open("space/launch-coast.webp").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }.also { coast = it }
        val scale = max(width / image.width, height / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val verticalLift = ascent * height * 1.45f
        val top = (height - h) * .5f + verticalLift
        oval.set((width - w) * .5f, top, (width + w) * .5f, top + h)
        paint.shader = null; paint.isFilterBitmap = true
        paint.alpha = (fade * 255).toInt()
        canvas.drawBitmap(image, null, oval, paint)
        paint.alpha = 255
    }

    fun releaseGround() { coast?.recycle(); coast = null }
    override fun close() = releaseGround()

    fun clouds(canvas: Canvas, width: Float, height: Float, ascent: Float) {
        val opacity = (sin(ascent * PI).toFloat() * .72f).coerceAtLeast(0f)
        if (opacity <= 0) return
        val unit = min(width, height)
        for (deck in 0..2) for (i in 0..13) {
            val x = width * (i / 11f - .1f)
            val y = height * (.12f + deck * .33f + ascent * 1.5f) - height * .75f
            val shape = sin(i * 3.17f + deck) * height * .025f
            val radius = unit * (.09f + (i % 3) * .025f)
            paint.shader = RadialGradient(x, y + shape, radius,
                intArrayOf(Color.argb((opacity * 165).toInt(), 242, 248, 251), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            oval.set(x - radius * 2, y + shape - radius, x + radius * 2, y + shape + radius)
            val save = canvas.save(); canvas.scale(1.8f, .62f, x, y + shape)
            canvas.drawCircle(x, y + shape, radius, paint); canvas.restoreToCount(save)
        }
        paint.shader = null
    }
}
