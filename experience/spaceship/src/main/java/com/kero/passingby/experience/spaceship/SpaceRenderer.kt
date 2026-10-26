package com.kero.passingby.experience.spaceship

import android.graphics.*
import com.kero.passingby.experience.voyage.*
import kotlin.math.*
import kotlin.random.Random

/** Layered starfield, interplanetary haze and procedural planets, without image downloads. */
class SpaceRenderer(scene: VoyageScene) : VoyageRenderer(scene) {
    private val stars = FloatArray(220 * 4).apply {
        val random = Random(108)
        for (i in indices step 4) {
            this[i] = random.nextFloat(); this[i + 1] = random.nextFloat()
            this[i + 2] = random.nextFloat(); this[i + 3] = random.nextFloat()
        }
    }
    private val circle = Path()
    private val oval = RectF()
    override val roundness = .12f

    override fun drawWorld(canvas: Canvas, width: Float, height: Float) {
        val course = scene.course as SpaceCourse
        val p = scene.routePosition
        val u = min(width, height)
        fill(canvas, width, height, 0xff050c1c.toInt(), 0xff152038.toInt())
        // Wide, translucent gas clouds sit behind stars. Fixed route coordinates avoid flicker.
        for (i in 0..4) {
            val x = width * (.12f + i * .23f) - p * width * .09f
            val y = height * (.7f - i * .105f)
            val radius = max(width, height) * (.34f + i * .015f)
            val alpha = 18
            paint.shader = RadialGradient(x, y, radius, intArrayOf((course.glow and 0xffffff) or (alpha shl 24), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width, height, paint)
        }
        paint.shader = null
        for (i in stars.indices step 4) {
            val depth = stars[i + 2]
            val drift = (scene.distance * (.025 + depth * .11)).toFloat()
            val x = ((stars[i] - drift) % 1f + 1f) % 1f * width
            val y = stars[i + 1] * height
            paint.color = Color.argb((75 + stars[i + 3] * 175).toInt(), 206 + (depth * 45).toInt(), 219 + (depth * 30).toInt(), 255)
            val r = (.0008f + depth * .0015f) * u
            canvas.drawCircle(x, y, r.coerceAtLeast(.6f), paint)
            if (depth > .97f) {
                paint.strokeWidth = .65f
                canvas.drawLine(x - r * 3, y, x + r * 3, y, paint)
                canvas.drawLine(x, y - r * 3, x, y + r * 3, paint)
            }
        }
        // Each real waypoint moves continuously through the window; there is no scene cut at a flyby.
        val position = p * course.bodies.lastIndex
        course.bodies.forEachIndexed { index, body ->
            val offset = index - position
            if (offset in -1.4f..1.5f) {
                val radius = u * (.085f + .28f * exp(-abs(offset) * 1.8f))
                // An interstellar target is not a nearby, full-sized planet beside Earth.
                val interstellar = course.bodies.getOrNull(1)?.ordinal?.let { it >= CelestialBody.PROXIMA_B.ordinal } == true
                val visibility = if (interstellar && position < 1f) when (index) {
                    0 -> (1f - position / .28f).coerceIn(0f, 1f)
                    1 -> ((position - .72f) / .28f).coerceIn(0f, 1f)
                    else -> 0f
                } else 1f
                if (visibility > 0f) drawBody(canvas, body, width * (.34f + offset * .95f),
                    height * (.48f + sin(index * 1.9f) * .07f), radius * visibility)
            }
        }
        val leg = position.toInt().coerceIn(0, course.bodies.lastIndex - 1)
        if (course.bodies[leg] == CelestialBody.MARS && course.bodies[leg + 1] == CelestialBody.JUPITER)
            drawAsteroids(canvas, width, height, (position - leg).coerceIn(0f, 1f))
        // Gentle glass reflection, never flashing or tied to a wall-clock animation.
        paint.color = 0x08d2edff
        path.rewind(); path.moveTo(width * .09f, 0f); path.lineTo(width * .13f, 0f)
        path.lineTo(width * .56f, height); path.lineTo(width * .5f, height); path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawBody(canvas: Canvas, body: CelestialBody, x: Float, y: Float, r: Float) {
        if (x + r * 2 < 0 || r <= 0) return
        if (body == CelestialBody.SATURN) rings(canvas, x, y, r, front = false)
        paint.color = Color.WHITE
        paint.shader = RadialGradient(x, y, r * 1.18f, intArrayOf(Color.TRANSPARENT, (body.color and 0xffffff) or 0x44000000, Color.TRANSPARENT), floatArrayOf(0f, .84f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r * 1.18f, paint)
        paint.color = Color.WHITE
        paint.shader = RadialGradient(x - r * .36f, y - r * .4f, r * 1.9f,
            intArrayOf(body.color, body.shade, 0xff070e20.toInt()), floatArrayOf(0f, .64f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, paint)
        paint.shader = null
        val saved = canvas.save()
        circle.rewind(); circle.addCircle(x, y, r, Path.Direction.CW); canvas.clipPath(circle)
        when (body) {
            CelestialBody.EARTH -> {
                // Soft continent silhouettes, positioned in normalized globe coordinates.
                paint.color = 0xff709d91.toInt()
                polygon(canvas, x, y, r, floatArrayOf(-.8f,-.25f,-.5f,-.55f,-.2f,-.42f,-.05f,-.14f,-.25f,.08f,-.2f,.28f,-.42f,.42f,-.67f,.18f))
                polygon(canvas, x, y, r, floatArrayOf(.05f,-.7f,.42f,-.65f,.76f,-.3f,.56f,-.05f,.74f,.2f,.4f,.35f,.23f,.05f,.02f,-.1f))
                paint.color = 0x60e8f7ff
                repeat(5) { i ->
                    val cy = y - r * .7f + i * r * .34f
                    oval.set(x - r, cy, x + r * .5f, cy + r * .07f)
                    canvas.drawOval(oval, paint)
                }
            }
            CelestialBody.MOON, CelestialBody.MERCURY, CelestialBody.MARS, CelestialBody.PLUTO,
            CelestialBody.PROXIMA_B, CelestialBody.TRAPPIST_E, CelestialBody.TOI700_E, CelestialBody.TOI700_D -> {
                val random = Random(body.ordinal + 9)
                repeat(18) {
                    val dx = (random.nextFloat() * 1.8f - .9f) * r
                    val dy = (random.nextFloat() * 1.8f - .9f) * r
                    val cr = r * (.025f + random.nextFloat() * .085f)
                    paint.color = 0x28505a67; canvas.drawCircle(x + dx, y + dy, cr, paint)
                    paint.color = 0x24e6d6c7; canvas.drawCircle(x + dx - cr * .15f, y + dy - cr * .18f, cr * .75f, paint)
                }
                if (body == CelestialBody.PLUTO) {
                    paint.color = 0x88e5d7be.toInt()
                    path.rewind(); path.moveTo(x + r * .12f, y + r * .38f)
                    path.cubicTo(x - r * .6f, y - r * .05f, x - r * .13f, y - r * .5f, x + r * .12f, y - r * .12f)
                    path.cubicTo(x + r * .48f, y - r * .45f, x + r * .65f, y + r * .1f, x + r * .12f, y + r * .38f)
                    canvas.drawPath(path, paint)
                }
                if (body == CelestialBody.MARS) { paint.color = 0x99e4dfd2.toInt(); canvas.drawOval(x - r * .4f, y - r * 1.05f, x + r * .4f, y - r * .86f, paint) }
            }
            CelestialBody.TRAPPIST_F, CelestialBody.TRAPPIST_G, CelestialBody.LHS1140_B -> {
                // One possible icy scenario; the course note explicitly marks the surface as unknown.
                paint.color = 0x36eef1ed
                val random = Random(body.ordinal * 31)
                repeat(18) {
                    val cx = x + (random.nextFloat() * 1.8f - .9f) * r
                    val cy = y + (random.nextFloat() * 1.8f - .9f) * r
                    val span = r * (.1f + random.nextFloat() * .23f)
                    path.rewind(); path.moveTo(cx - span, cy)
                    path.cubicTo(cx - span, cy - span, cx + span * .2f, cy - span * .8f, cx + span, cy)
                    path.cubicTo(cx + span * .5f, cy + span * .9f, cx - span * .4f, cy + span * .4f, cx - span, cy)
                    canvas.drawPath(path, paint)
                }
            }
            CelestialBody.CANCRI_E -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .013f
                paint.color = 0xb8ffc478.toInt()
                repeat(9) { i ->
                    val dx = x - r + i * r * .24f
                    path.rewind(); path.moveTo(dx, y - r)
                    path.cubicTo(dx + r * .5f, y - r * .3f, dx - r * .3f, y + r * .1f, dx + r * .2f, y + r)
                    canvas.drawPath(path, paint)
                }
                paint.style = Paint.Style.FILL
            }
            CelestialBody.VENUS, CelestialBody.JUPITER, CelestialBody.SATURN, CelestialBody.URANUS, CelestialBody.NEPTUNE -> {
                repeat(12) { i ->
                    val yy = y - r + i * r / 6
                    paint.color = if (i % 2 == 0) 0x24f4e6cc else 0x22503548
                    canvas.drawRect(x - r, yy, x + r, yy + r * .08f, paint)
                }
                if (body == CelestialBody.JUPITER) {
                    paint.color = 0x889c5c4c.toInt(); canvas.drawOval(x + r * .1f, y + r * .17f, x + r * .58f, y + r * .37f, paint)
                }
            }
        }
        // Night-side shading covers surface detail as well as the base sphere.
        paint.color = Color.WHITE
        paint.shader = LinearGradient(x - r, y - r, x + r, y + r, intArrayOf(0x00ffffff, 0x14071120, 0xc9071120.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, paint); paint.shader = null
        canvas.restoreToCount(saved)
        if (body == CelestialBody.SATURN) rings(canvas, x, y, r, front = true)
    }

    private fun rings(canvas: Canvas, x: Float, y: Float, r: Float, front: Boolean) {
        val saved = canvas.save()
        canvas.rotate(-22f, x, y)
        if (front) canvas.clipRect(x - r * 2, y, x + r * 2, y + r)
        paint.style = Paint.Style.STROKE
        repeat(8) { i ->
            val span = r * (1.23f + i * .075f)
            paint.strokeWidth = r * .035f
            paint.color = if (i == 4) 0x557d7474 else 0x99cfc1a1.toInt()
            oval.set(x - span, y - span * .33f, x + span, y + span * .33f)
            canvas.drawOval(oval, paint)
        }
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(saved)
    }

    private fun polygon(canvas: Canvas, x: Float, y: Float, r: Float, points: FloatArray) {
        path.rewind(); path.moveTo(x + points[0] * r, y + points[1] * r)
        for (i in 2 until points.size step 2) path.lineTo(x + points[i] * r, y + points[i + 1] * r)
        path.close(); canvas.drawPath(path, paint)
    }

    private fun drawAsteroids(canvas: Canvas, w: Float, h: Float, p: Float) {
        val amount = (sin(p * PI).toFloat() * .8f).coerceAtLeast(0f)
        val random = Random(91)
        repeat(24) {
            val x = ((random.nextFloat() - p * .6f + 1f) % 1f) * w
            val y = random.nextFloat() * h
            val r = min(w, h) * (.002f + random.nextFloat() * .008f)
            paint.color = Color.argb((amount * 120).toInt(), 153, 141, 138)
            canvas.drawCircle(x, y, r, paint)
        }
    }
}
