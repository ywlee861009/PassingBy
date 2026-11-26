package com.kero.passingby.scene

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.kero.passingby.journey.WindowEventKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Brief changes in the view. Coordinates are local to the window opening. */
internal class WindowEventPainter {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val reflection = Paint(Paint.ANTI_ALIAS_FLAG)
    private val warmGlint = glintShader(0xFFE4B5)
    private val coolGlint = glintShader(0xDDEBFF)
    private val sunlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(0x00FFF3BA, 0x32FFF3BA, 0x1CFFE4A7, 0x00FFE4A7),
            floatArrayOf(0f, .33f, .70f, 1f), Shader.TileMode.CLAMP,
        )
    }

    fun draw(canvas: Canvas, layout: SceneLayout, p: Palette, kind: WindowEventKind, progress: Float) {
        val fade = smooth(progress / .18f) * smooth((1f - progress) / .23f)
        if (fade <= 0f || layout.u <= 0f) return
        when (kind) {
            WindowEventKind.GLASS_GLINT -> drawGlassGlint(canvas, layout, p, progress, fade)
            WindowEventKind.WINDOW_DROPS -> drawDrops(canvas, layout, p, progress, fade)
            WindowEventKind.BIRD_FLOCK -> drawFlock(canvas, layout, p, progress, fade, sea = false)
            WindowEventKind.SHOOTING_STAR -> drawShootingStar(canvas, layout, p, progress)
            WindowEventKind.SEA_BIRDS -> drawFlock(canvas, layout, p, progress, fade, sea = true)
            WindowEventKind.DARJEELING_SUNBREAK -> drawSunbreak(canvas, layout, progress, fade)
            WindowEventKind.CANYON_DEER -> drawDeer(canvas, layout, p, progress, fade)
            WindowEventKind.PASSING_TRAIN -> drawPassingTrain(canvas, layout, p, progress, fade)
            WindowEventKind.METEOR -> Unit
        }
    }

    private fun drawGlassGlint(canvas: Canvas, layout: SceneLayout, p: Palette, t: Float, fade: Float) {
        val width = layout.w.toFloat()
        val height = layout.h.toFloat()
        reflection.shader = if (p.f[Palette.LIT] > .45f) coolGlint else warmGlint
        reflection.alpha = (fade * 230).toInt()
        val saved = canvas.save()
        canvas.translate(width * (1.25f - 1.5f * t), height * .5f)
        canvas.rotate(16f)
        canvas.scale(width * .18f, height)
        canvas.drawRect(-1f, -1f, 1f, 1f, reflection)
        canvas.restoreToCount(saved)
    }

    private fun drawDrops(canvas: Canvas, layout: SceneLayout, p: Palette, t: Float, fade: Float) {
        val pale = mix(p.c[Palette.BOT], Color.WHITE, .62f)
        val shadow = mix(p.c[Palette.POLE], p.c[Palette.HAZE], .28f)
        val u = layout.u
        for (i in DROP_AT.indices) {
            val age = (t - DROP_AT[i]) / .55f
            if (age <= 0f || age >= 1f) continue
            val alpha = fade * smooth(age / .08f) * smooth((1f - age) / .24f)
            val r = (3.6f + .7f * (2 - i)) * u
            val x = (DROP_X[i] - .05f * age) * layout.w
            val y = (.29f + i * .076f) * layout.h + age * age * layout.g * .26f
            val trail = (5f + 20f * age) * u
            setInk(pale, alpha * .23f)
            canvas.drawOval(x - r * .42f + u, y - trail, x + r * .39f + u, y, ink)
            setInk(shadow, alpha * .33f)
            canvas.drawOval(x - r, y - r * 1.32f, x + r, y + r, ink)
            setInk(pale, alpha * .50f)
            canvas.drawOval(x - r * .63f, y - r * .88f, x + r * .66f, y + r * .69f, ink)
            setInk(Color.WHITE, alpha * .79f)
            canvas.drawCircle(x - r * .33f, y - r * .34f, r * .26f, ink)
        }
    }

    private fun drawFlock(canvas: Canvas, layout: SceneLayout, p: Palette, t: Float, fade: Float, sea: Boolean) {
        val shade = mix(p.c[Palette.POLE], p.c[Palette.HAZE], if (sea) .30f else .21f)
        val light = mix(p.c[Palette.CLOUD], p.c[Palette.BOT], .25f)
        val width = layout.w.toFloat()
        val u = layout.u
        val count = if (sea) 3 else FLOCK_X.size
        for (i in 0 until count) {
            val x = (1.10f - t * 1.22f + FLOCK_X[i]) * width
            val swoop = sin(t * PI + i * .24).toFloat()
            val y = if (sea) {
                layout.yAt(.57f) + (swoop * .105f + FLOCK_Y[i]) * layout.g
            } else {
                (.34f - t * .06f + FLOCK_Y[i] + swoop * .018f) * layout.h
            }
            val size = (if (sea) 11f else 6.5f) * (1f - .055f * i) * u
            val flap = sin((t * 8f + i * .25f) * 2.0 * PI).toFloat()
            val tip = (flap * .40f - .14f) * size
            path.rewind()
            path.moveTo(x - size, y + tip)
            path.quadTo(x - size * .65f, y - size * .50f, x, y)
            path.quadTo(x + size * .62f, y - size * .59f, x + size, y + tip)
            path.quadTo(x + size * .48f, y - size * .16f, x + size * .13f, y + size * .24f)
            path.lineTo(x - size * .16f, y + size * .24f)
            path.quadTo(x - size * .48f, y - size * .12f, x - size, y + tip)
            path.close()
            setInk(shade, fade * .82f)
            canvas.drawPath(path, ink)
            if (sea) {
                setInk(light, fade * .72f)
                ink.strokeWidth = size * .13f
                canvas.drawLine(x - size * .55f, y - size * .04f, x + size * .52f, y - size * .07f, ink)
            }
        }
    }

    private fun drawShootingStar(canvas: Canvas, layout: SceneLayout, p: Palette, t: Float) {
        val streak = (t - .30f) / .37f
        if (streak <= 0f || streak >= 1f) return
        val fade = smooth(streak / .18f) * smooth((1f - streak) / .42f)
        val x = (.34f + .34f * streak) * layout.w
        val y = (.19f + .17f * streak) * layout.h
        val tailX = .12f * layout.w
        val tailY = .06f * layout.h
        val u = layout.u
        val color = mix(p.c[Palette.SUN], Color.WHITE, .57f)
        for (i in 5 downTo 0) {
            val near = i / 6f
            val far = (i + 1) / 6f
            setInk(color, fade * (.77f - .12f * i))
            ink.strokeWidth = (2.2f - .29f * i) * u
            canvas.drawLine(x - tailX * far, y - tailY * far, x - tailX * near, y - tailY * near, ink)
        }
        setInk(color, fade * .90f)
        canvas.drawCircle(x, y, 1.7f * u, ink)
    }

    private fun drawSunbreak(canvas: Canvas, layout: SceneLayout, t: Float, fade: Float) {
        val saved = canvas.save()
        canvas.translate(layout.w * (.05f - .10f * t), layout.yAt(-.40f))
        canvas.scale(layout.w.toFloat(), layout.g * 1.20f)
        sunlight.alpha = (fade * 237).toInt()
        drawBeam(canvas, .59f, .63f, .24f, .39f)
        drawBeam(canvas, .63f, .66f, .45f, .60f)
        drawBeam(canvas, .66f, .675f, .63f, .69f)
        canvas.restoreToCount(saved)
    }

    private fun drawBeam(canvas: Canvas, nearLeft: Float, nearRight: Float, farLeft: Float, farRight: Float) {
        path.rewind()
        path.moveTo(nearLeft, 0f)
        path.lineTo(nearRight, 0f)
        path.lineTo(farRight, 1f)
        path.lineTo(farLeft, 1f)
        path.close()
        canvas.drawPath(path, sunlight)
    }

    private fun drawDeer(canvas: Canvas, layout: SceneLayout, p: Palette, t: Float, fade: Float) {
        val coat = mix(0xFF63432D.toInt(), p.c[Palette.NEAR], .31f)
        val belly = mix(0xFFAE865A.toInt(), p.c[Palette.HAZE], .29f)
        for (i in 0..2) {
            val x = (1.03f + .12f * i - 1.35f * t) * layout.w
            val u = (1f - .12f * i) * layout.u
            val gait = sin((t * 7f + i * .35f) * 2.0 * PI).toFloat()
            val feet = layout.yAt(.90f - .021f * i)
            val body = feet - (11f + abs(gait) * 3f) * u
            setInk(coat, fade * .94f)
            canvas.drawRect(x - 9f * u, body - 5f * u, x + 8f * u, body + 2f * u, ink)
            canvas.drawRect(x - 10f * u, body - 9f * u, x - 6f * u, body - u, ink)
            canvas.drawRect(x - 13f * u, body - 11f * u, x - 7f * u, body - 7f * u, ink)
            canvas.drawRect(x - 8f * u, body - 14f * u, x - 6f * u, body - 10f * u, ink)
            canvas.drawRect(x + 7f * u, body - 6f * u, x + 11f * u, body - 4f * u, ink)
            ink.strokeWidth = 1.7f * u
            for (leg in 0..3) {
                val root = x + ((leg - 1.5f) * 4.8f) * u
                val step = if (leg % 2 == 0) gait else -gait
                canvas.drawLine(root, body, root + step * 6f * u, feet - abs(step) * 2f * u, ink)
            }
            setInk(belly, fade * .78f)
            canvas.drawRect(x - 7f * u, body + .4f * u, x + 7f * u, body + 2.2f * u, ink)
        }
    }

    private fun drawPassingTrain(canvas: Canvas, layout: SceneLayout, p: Palette, t: Float, fade: Float) {
        val u = layout.u
        val carriageWidth = 86f * u
        val spacing = 3f * u
        val base = layout.yAt(.94f)
        val roof = base - 26f * u
        val track = p.c[Palette.POLE]
        val paint = mix(0xFFD5DCE2.toInt(), p.c[Palette.HAZE], .27f + .32f * p.f[Palette.LIT])
        val band = mix(0xFF526B78.toInt(), p.c[Palette.NEAR], .35f)
        val windows = mix(p.c[Palette.TOP], p.c[Palette.POLE], .55f)
        val cabinLight = mix(windows, 0xFFF6D7A1.toInt(), .67f * p.f[Palette.LIT])
        val nose = layout.w * (1.18f - t * 1.36f) - 3f * (carriageWidth + spacing) * t
        for (carriage in 0..2) {
            val x = nose + carriage * (carriageWidth + spacing)
            val right = x + carriageWidth
            if (right < 0 || x > layout.w) continue
            path.rewind()
            val noseLength = if (carriage == 0) 12f * u else 3f * u
            path.moveTo(x, base - 4f * u)
            path.lineTo(x, roof + 13f * u)
            path.lineTo(x + noseLength, roof + 2f * u)
            path.lineTo(right - 3f * u, roof + 2f * u)
            path.lineTo(right, roof + 5f * u)
            path.lineTo(right, base - 4f * u)
            path.close()
            setInk(paint, fade * .90f)
            canvas.drawPath(path, ink)
            setInk(band, fade * .86f)
            canvas.drawRect(x, base - 11f * u, right, base - 7f * u, ink)
            for (window in 0..5) {
                setInk(if ((carriage + window) % 4 == 1) windows else cabinLight, fade * .92f)
                val left = x + (13f + window * 11f) * u
                canvas.drawRect(left, roof + 7f * u, left + 7f * u, roof + 13f * u, ink)
            }
            setInk(track, fade * .82f)
            canvas.drawRect(x + 7f * u, base - 4f * u, right - 6f * u, base, ink)
            if (carriage < 2) canvas.drawRect(right, base - 7f * u, right + spacing, base - 5f * u, ink)
        }
    }

    private fun setInk(color: Int, alpha: Float) {
        ink.color = color
        ink.alpha = (alpha * 255).toInt().coerceIn(0, 255)
    }

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun glintShader(rgb: Int) = LinearGradient(
        -1f, 0f, 1f, 0f,
        intArrayOf(rgb, 0x16000000 or rgb, 0x38000000 or rgb, 0x10000000 or rgb, rgb),
        floatArrayOf(0f, .30f, .49f, .67f, 1f), Shader.TileMode.CLAMP,
    )

    private companion object {
        val DROP_AT = floatArrayOf(.12f, .31f, .44f)
        val DROP_X = floatArrayOf(.27f, .61f, .47f)
        val FLOCK_X = floatArrayOf(-.09f, -.015f, .036f, .072f, .139f)
        val FLOCK_Y = floatArrayOf(.019f, -.021f, .026f, -.054f, .031f)
    }
}
