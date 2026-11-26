package com.kero.passingby.scene

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.roundToInt

/** The rare meteor takes out the scenery for a moment, within the train window. */
internal class MeteorEventPainter {
    private val paint = Paint()
    private val path = Path()
    private val dustPaint = Paint()
    private val dustMatrix = Matrix()
    private var dustColor = 0

    fun terrainAlpha(progress: Float): Int = ((1f - destruction(progress)) * 255).roundToInt()

    fun draw(canvas: Canvas, layout: SceneLayout, p: Palette, progress: Float) {
        val impactX = layout.w * .38f
        val impactY = layout.yAt(.55f)
        if (progress < IMPACT) {
            drawMeteor(canvas, layout, impactX, impactY, progress / IMPACT)
            return
        }

        val damage = destruction(progress)
        val dust = smooth(IMPACT, .52f, progress) * (1f - smooth(.66f, .97f, progress))
        if (dust <= 0f) return
        val dusk = p.f[Palette.STARS]
        val soot = mix(0xFF9C7E68.toInt(), 0xFF433B47.toInt(), dusk)
        drawRubble(canvas, layout, p, impactX, impactY, progress, damage)
        drawDust(canvas, layout, soot, dust)
        val afterImpact = ((progress - IMPACT) / .44f).coerceIn(0f, 1f)
        drawDebris(canvas, layout, p, impactX, impactY, afterImpact)
    }

    private fun drawMeteor(canvas: Canvas, layout: SceneLayout, impactX: Float, impactY: Float, progress: Float) {
        val travel = progress * progress
        val startX = layout.w * 1.05f
        val startY = layout.h * .08f
        val x = startX + (impactX - startX) * travel
        val y = startY + (impactY - startY) * travel
        val u = layout.u
        val radius = (3.5f + 6f * travel) * u
        val tail = .18f + .09f * progress
        val tailX = x + layout.w * tail
        val tailY = y - (impactY - startY) * tail

        paint.color = 0xFFC1704C.toInt()
        paint.alpha = (130 * smooth(0f, .36f, progress)).roundToInt()
        path.rewind()
        path.moveTo(tailX, tailY)
        path.lineTo(x + radius * .6f, y + radius * .4f)
        path.lineTo(x - radius, y + radius * .2f)
        path.lineTo(x - radius * .3f, y - radius * .9f)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = 0xFFD98658.toInt()
        canvas.drawRect(x - radius, y - radius * .62f, x + radius * .63f, y + radius * .63f, paint)
        paint.color = 0xFFF0B675.toInt()
        canvas.drawRect(x - radius * .65f, y - radius * .37f, x + radius * .15f, y + radius * .33f, paint)
    }

    private fun drawRubble(
        canvas: Canvas, layout: SceneLayout, p: Palette, impactX: Float, impactY: Float,
        progress: Float, damage: Float,
    ) {
        if (damage <= 0f) return
        val width = layout.w.toFloat()
        val bottom = layout.h.toFloat()
        val span = width / 31f
        val wave = smooth(IMPACT, .62f, progress) * width
        val stones = mix(p.c[Palette.GROUND], 0xFF585051.toInt(), .58f)
        for (column in 0 until 31) {
            val x = column * span
            if (abs(x + span * .5f - impactX) > wave) continue
            val ledge = hash01(column, 781).toFloat()
            val top = impactY + layout.g * (.09f + .23f * ledge)
            val seam = layout.g * (.018f + .04f * hash01(column, 787).toFloat())
            paint.color = mix(stones, p.c[Palette.NEAR], .17f * ledge)
            paint.alpha = (245 * damage).roundToInt()
            path.rewind()
            path.moveTo(x, bottom)
            path.lineTo(x, top + seam)
            path.lineTo(x + span * .16f, top + seam * .9f)
            path.lineTo(x + span * .28f, top)
            path.lineTo(x + span * .59f, top + seam * .2f)
            path.lineTo(x + span * .68f, top + seam * 1.35f)
            path.lineTo(x + span + 1f, top + seam * 1.54f)
            path.lineTo(x + span + 1f, bottom)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawDust(canvas: Canvas, layout: SceneLayout, soot: Int, dust: Float) {
        val dustTop = layout.yAt(.30f - .16f * dust)
        if (soot != dustColor) {
            dustColor = soot
            val transparent = soot and 0x00FFFFFF
            dustPaint.shader = LinearGradient(0f, 0f, 0f, 1f, transparent, soot, Shader.TileMode.CLAMP)
        }
        val bottom = layout.h.toFloat()
        dustMatrix.setScale(1f, bottom - dustTop)
        dustMatrix.postTranslate(0f, dustTop)
        dustPaint.shader?.setLocalMatrix(dustMatrix)
        dustPaint.alpha = (203 * dust).roundToInt()
        canvas.drawRect(0f, dustTop, layout.w.toFloat(), bottom, dustPaint)
    }

    private fun drawDebris(
        canvas: Canvas, layout: SceneLayout, p: Palette, impactX: Float, impactY: Float, age: Float,
    ) {
        val envelope = smooth(0f, .14f, age) * (1f - smooth(.62f, 1f, age))
        if (envelope <= 0f) return
        val u = layout.u
        for (i in 0 until 112) {
            val spread = (hash01(i, 821).toFloat() - .5f) * 2f
            val lift = hash01(i, 829).toFloat()
            val size = (2.3f + 9f * hash01(i, 839).toFloat()) * u
            val x = impactX + spread * layout.w * (.16f + .64f * age)
            val y = impactY + layout.g * (.26f * (1 - lift) - age * (1.22f * lift + .31f) + age * age * 1.5f)
            paint.color = mix(p.c[Palette.GROUND], 0xFFD79C69.toInt(), .2f + .45f * lift)
            paint.alpha = (205 * envelope * (.4f + .6f * lift)).roundToInt()
            canvas.drawRect(x, y, x + size, y + size * (.5f + .45f * lift), paint)
        }

        paint.color = mix(0xFFD79865.toInt(), Color.GRAY, .25f)
        paint.alpha = (69 * envelope * (1f - age)).roundToInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * u
        val spread = layout.w * (.06f + .8f * age)
        val rise = layout.g * (.019f + .19f * age)
        canvas.drawOval(impactX - spread, impactY - rise, impactX + spread, impactY + rise, paint)
        paint.style = Paint.Style.FILL
    }

    private fun destruction(progress: Float): Float =
        smooth(IMPACT, .51f, progress) * (1f - smooth(.66f, .92f, progress))

    private fun smooth(start: Float, end: Float, progress: Float): Float {
        val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object { const val IMPACT = .42f }
}
