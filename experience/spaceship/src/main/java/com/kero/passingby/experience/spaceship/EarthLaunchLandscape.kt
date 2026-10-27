package com.kero.passingby.experience.spaceship

import android.graphics.*
import kotlin.math.*

/** Layered, original Canvas landscape in the same visual language as the train scenery. */
internal class EarthLaunchLandscape {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()

    fun draw(c: Canvas, width: Float, height: Float, ascent: Float) {
        val fade = 1f - SpaceFlightPath.smooth((ascent - .36f) / .35f)
        if (fade <= 0) return
        val save = c.saveLayerAlpha(0f, 0f, width, height, (fade * 255).toInt())
        val lift = ascent * height * 1.35f
        c.translate(0f, lift)
        val horizon = height * .52f
        paint.shader = LinearGradient(0f, horizon, 0f, height,
            intArrayOf(0xff83b6ba.toInt(), 0xff558f9a.toInt(), 0xff356d7d.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, width, height * 1.4f, paint); paint.shader = null
        ridge(c, width, height, .51f, .025f, 0xff849c9b.toInt(), 1.8f)
        ridge(c, width, height, .55f, .037f, 0xff678d87.toInt(), 4.2f)
        // Sinuous estuary and coves remain in world coordinates during the vertical climb.
        path.rewind(); path.moveTo(-width * .1f, height * .66f)
        path.cubicTo(width * .21f, height * .54f, width * .43f, height * .69f, width * .62f, height * .65f)
        path.cubicTo(width * .80f, height * .62f, width * .82f, height * .72f, width * 1.1f, height * .67f)
        path.lineTo(width * 1.1f, height * 1.5f); path.lineTo(-width * .1f, height * 1.5f); path.close()
        paint.color = 0xffb8b696.toInt(); c.drawPath(path, paint)
        val shore = Path(path)
        val land = c.save(); c.clipPath(shore)
        paint.shader = LinearGradient(0f, height * .65f, 0f, height,
            0xff789478.toInt(), 0xff4c745d.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, height * .675f, width, height * 1.5f, paint); paint.shader = null
        for (row in 0..7) for (col in 0..13) {
            val x = width * (col / 12f - .06f + sin(row * 1.7f) * .02f)
            val y = height * (.7f + row * .055f)
            paint.color = if ((row + col) % 3 == 0) 0x265c694b else 0x276da58b
            path.rewind(); path.moveTo(x, y); path.rLineTo(width * .07f, -height * .01f)
            path.rLineTo(width * .025f, height * .04f); path.rLineTo(-width * .08f, height * .012f)
            path.close(); c.drawPath(path, paint)
        }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        for (i in 0..4) {
            val y = height * (.7f + i * .075f)
            path.rewind(); path.moveTo(-width * .1f, y)
            path.cubicTo(width * .3f, y - height * .06f, width * .4f, y + height * .07f, width * .65f, y - height * .035f)
            path.cubicTo(width * .85f, y - height * .09f, width, y, width * 1.1f, y - height * .04f)
            paint.color = 0x7c799d96; paint.strokeWidth = height * (.003f + i * .001f); c.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        repeat(160) { i ->
            val x = ((i * .618034f) % 1f) * width
            val y = height * (.72f + ((i * .754877f) % 1f) * .40f)
            val r = height * (.002f + (y / height - .7f) * .009f)
            paint.color = 0x42405f4d; oval.set(x - r * 2, y - r, x + r * 2, y + r); c.drawOval(oval, paint)
            paint.color = 0x88648663.toInt(); oval.offset(-r * .3f, -r * .5f); c.drawOval(oval, paint)
        }
        c.restoreToCount(land)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = height * .0015f; paint.color = 0x86dae2cf.toInt()
        c.drawPath(shore, paint)
        repeat(75) { i ->
            val y = height * (.535f + ((i * .618f) % 1f) * .10f)
            val x = ((i * .754877f) % 1f) * width
            paint.color = 0x378fc2c4; paint.strokeWidth = height * .0009f
            c.drawLine(x, y, x + width * (.006f + (i % 5) * .004f), y, paint)
        }
        paint.style = Paint.Style.FILL; c.restoreToCount(save)
    }

    private fun ridge(c: Canvas, w: Float, h: Float, base: Float, amplitude: Float, color: Int, phase: Float) {
        path.rewind(); path.moveTo(-w * .1f, h * 1.5f)
        for (i in -5..55) {
            val x = i / 50f
            val y = base - amplitude * (sin(x * 9 + phase) * .5f + sin(x * 22 + phase) * .2f + .7f)
            path.lineTo(x * w, y * h)
        }
        path.lineTo(w * 1.1f, h * .60f); path.lineTo(-w * .1f, h * .61f); path.close()
        paint.color = color; c.drawPath(path, paint)
    }

    fun clouds(c: Canvas, width: Float, height: Float, ascent: Float) {
        val opacity = (sin(ascent * PI).toFloat() * .72f).coerceAtLeast(0f)
        if (opacity <= 0) return
        val unit = min(width, height)
        for (deck in 0..2) for (i in 0..16) {
            val x = width * (i / 14f - .1f)
            val y = height * (.12f + deck * .33f + ascent * 1.5f) - height * .75f
            val shape = sin(i * 3.17f + deck) * height * .025f
            val radius = unit * (.07f + (i % 3) * .025f)
            paint.shader = RadialGradient(x, y + shape, radius,
                intArrayOf(Color.argb((opacity * 175).toInt(), 240, 246, 242), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            val save = c.save(); c.scale(1.8f, .62f, x, y + shape)
            c.drawCircle(x, y + shape, radius, paint); c.restoreToCount(save)
        }
        paint.shader = null
    }
}
