package com.kero.passingby.experience.voyage

import android.graphics.*
import com.kero.passingby.experience.ExperienceRenderer
import kotlin.math.min

/** Responsive window/cabin surface shared by non-rail experiences. World drawing is module-owned. */
abstract class VoyageRenderer(protected val scene: VoyageScene) : ExperienceRenderer {
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val path = Path()
    private val opening = RectF()
    private val clip = Path()
    final override var lastDrawNanos = 0L
        private set
    override val bitmapBytes = 0L
    protected abstract fun drawWorld(canvas: Canvas, width: Float, height: Float)
    protected open val roundness = .16f

    final override fun render(canvas: Canvas, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val start = System.nanoTime()
        val unit = min(w, h).toFloat()
        val availableW = if (scene.viewportFromEnd) (w - scene.viewportEdge).coerceAtLeast(w * .42f) else w.toFloat()
        val availableH = if (!scene.viewportFromEnd) (h - scene.viewportEdge).coerceAtLeast(h * .3f) else h.toFloat()
        canvas.drawColor(scene.panelColor)
        val margin = unit * .055f
        opening.set(margin, margin, availableW - margin, availableH - margin)
        val radius = min(opening.width(), opening.height()) * roundness
        val count = canvas.save()
        if (scene.windowFrame) {
            clip.rewind(); clip.addRoundRect(opening, radius, radius, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.translate(opening.left, opening.top)
            drawWorld(canvas, opening.width(), opening.height())
        } else drawWorld(canvas, w.toFloat(), h.toFloat())
        canvas.restoreToCount(count)
        if (scene.windowFrame) {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = unit * .026f
            paint.color = 0xff0b111d.toInt()
            canvas.drawRoundRect(opening, radius, radius, paint)
            paint.strokeWidth = unit * .005f
            paint.color = 0xff7b8998.toInt()
            canvas.drawRoundRect(opening, radius, radius, paint)
            paint.style = Paint.Style.FILL
            // Tiny cabin status lights, outside the landscape.
            paint.color = 0xff8ebfc8.toInt()
            repeat(3) { canvas.drawCircle(opening.left + unit * (.04f + it * .025f), opening.bottom + margin * .56f, unit * .0035f, paint) }
        }
        lastDrawNanos = System.nanoTime() - start
    }
    override fun release() { paint.shader = null }
    protected fun fill(canvas: Canvas, w: Float, h: Float, top: Int, bottom: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }
}
