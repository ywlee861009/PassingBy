package com.kero.passingby.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** A repeating daylight shore and a transparent breaking-wave sheet. Both are baked once on course entry. */
internal class PacificShore {
    private val water: Bitmap
    private val breakers: Bitmap
    private val paint = Paint()
    private val target = RectF()
    private var lastMultiply = 0
    private var lastAdd = 0
    private var runup = 0f
    private var foamOpacity = 0f
    val bytes: Long get() = water.allocationByteCount.toLong() + breakers.allocationByteCount

    init {
        val base = IntArray(W * H)
        val foam = IntArray(W * H)
        val far = Color.rgb(76, 128, 153)
        val shelf = Color.rgb(56, 133, 152)
        val shallows = Color.rgb(111, 169, 163)
        val beach = Color.rgb(220, 193, 149)
        val damp = Color.rgb(161, 151, 125)
        val spume = Color.rgb(229, 234, 216)
        for (x in 0 until W) {
            val t = x * (2 * PI / W)
            val bay = 5.2 * sin(t + .3) + 3.2 * sin(3 * t - .9) + 1.1 * sin(7 * t)
            val beachY = (96 + bay + 1.6 * sin(11 * t)).roundToInt()
            val wetWidth = (6 + 2.3 * sin(4 * t + 1.1) + sin(9 * t)).roundToInt()
            for (y in 0 until H) {
                val grit = hash01(x + y * W, 51).toFloat()
                base[y * W + x] = when {
                    y < 24 -> mix(far, shelf, y / 24f)
                    y < beachY -> mix(shelf, shallows, (y - 24f) / (beachY - 24))
                    y < beachY + wetWidth -> mix(damp, beach, .15f + .35f * (y - beachY) / wetWidth)
                    grit > .985f -> mix(beach, damp, .27f)
                    grit < .007f -> mix(beach, spume, .34f)
                    else -> beach
                }
            }

            // Far rollers compress in both dimensions. Near rollers have a dark face, a lit lip,
            // and intermittently spill into white water before reaching the beach.
            for (row in 0 until ROWS) {
                val depth = (row + 1.0) / ROWS
                val reach = depth * depth
                val center = 2 + 84 * reach + bay * reach +
                    (0.18 + 1.7 * reach) * sin((3 + row % 4) * t + row * 1.8) +
                    (.25 + reach) * sin((9 + row % 6) * t + row)
                val crest = center.roundToInt()
                val group = sin((5 + (ROWS - row) / 2) * t + row * 2.1) +
                    .55 * sin((13 + row) * t - row * .9) + .26 * sin((31 + row) * t + row * .7)
                if (group < -.30) continue
                val strength = ((group + .30) / 1.8).toFloat().coerceIn(0f, 1f)
                val face = crest * W + x
                base[face] = mix(base[face], Color.rgb(41, 104, 126), .14f + strength * .34f)
                if (reach > .42 && group > .35) {
                    base[face + W] = mix(base[face + W], Color.rgb(54, 122, 132), .4f)
                }
                val lip = face - W
                base[lip] = mix(base[lip], Color.rgb(174, 209, 200), strength * .70f)
                if (row >= ROWS - 3 && group > .35) {
                    foam[lip] = mix(shallows, spume, .5f + strength * .4f)
                    if (group > .90 && hash01(x, row + 712) > .24) foam[face] = spume
                    if (group > 1.10 && hash01(x, row + 293) > .62) foam[face + W] = spume
                }
            }

            // A scalloped swash edge over darker wet sand; backwash leaves broken lace behind it.
            val edge = beachY * W + x
            val patch = sin(6 * t - .8) + .75 * sin(17 * t) + .35 * sin(39 * t)
            base[edge - 2 * W] = mix(shallows, spume, .34f)
            if (patch > -.65) foam[edge - W] = spume
            if (patch > -.15) foam[edge] = mix(shallows, spume, .80f)
            if (patch > .38 && hash01(x, 551) > .3) foam[edge + W] = spume
            if (patch > -.30 && hash01(x, 630) > .55) {
                val tail = (3 + 1.3 * sin(19 * t)).roundToInt()
                foam[edge - tail * W] = mix(shallows, spume, .63f)
            }
        }
        water = Bitmap.createBitmap(base, W, H, Bitmap.Config.ARGB_8888)
        breakers = Bitmap.createBitmap(foam, W, H, Bitmap.Config.ARGB_8888)
    }

    fun update(p: Palette, seconds: Double) {
        val night = max(p.f[Palette.STARS], p.f[Palette.LIT] * .65f)
        val haze = .10f + .72f * night
        val ambient = mix(p.c[Palette.HAZE], p.c[Palette.TREE], night)
        val mult = mix(Color.WHITE, Color.BLACK, haze)
        val add = mix(Color.BLACK, ambient, haze)
        if (mult != lastMultiply || add != lastAdd) {
            paint.colorFilter = LightingColorFilter(mult, add)
            lastMultiply = mult
            lastAdd = add
        }
        val phase = sin(seconds * .84).toFloat()
        runup = 1.65f * phase
        foamOpacity = .72f + .20f * phase
    }

    fun draw(canvas: Canvas, start: Float, end: Float, top: Float, bottom: Float, off: Double, u: Float, alpha: Int) {
        canvas.save()
        canvas.clipRect(start, top, end, bottom)
        val tileW = W * u
        val firstTile = floor((off + start / u) / W)
        var x = ((firstTile * W - off) * u).toFloat()
        val wash = runup * (bottom - top) / H
        while (x < end) {
            target.set(x, top, x + tileW, bottom)
            paint.alpha = alpha
            canvas.drawBitmap(water, null, target, paint)
            target.offset(0f, wash)
            paint.alpha = (alpha * foamOpacity).toInt()
            canvas.drawBitmap(breakers, null, target, paint)
            x += tileW
        }
        canvas.restore()
    }

    private companion object {
        const val W = 640
        const val H = 150
        const val ROWS = 14
    }
}
