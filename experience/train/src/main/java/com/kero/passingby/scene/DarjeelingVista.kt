package com.kero.passingby.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Two depths of the Batasia view. The mountain faces and garden are painted once off the render thread. */
internal class DarjeelingVista {
    private val massif = bakeMassif()
    private val loop = bakeLoop()
    val bytes: Long get() = massif.allocationByteCount.toLong() + loop.allocationByteCount

    private val massifPaint = Paint().apply { isFilterBitmap = false }
    private val loopPaint = Paint().apply { isFilterBitmap = false }
    private val target = RectF()
    private var lastSkyWash = 0
    private var lastGardenWash = 0
    private var lastDimming = 0

    /** [offset] is the horizontal displacement in scene units relative to the center of the viewpoint. */
    fun drawMassif(canvas: Canvas, layout: SceneLayout, p: Palette, offset: Double, visibility: Float) {
        if (visibility <= 0f) return
        updateLight(p)
        drawArtwork(canvas, layout, massif, massifPaint, offset.toFloat(), .66f, visibility)
    }

    fun drawLoop(canvas: Canvas, layout: SceneLayout, p: Palette, distance: Double, center: Double, visibility: Float) {
        if (visibility <= 0f) return
        updateLight(p)
        val offset = ((distance - center) * GARDEN_PARALLAX).toFloat()
        drawArtwork(canvas, layout, loop, loopPaint, offset, .99f, visibility)
    }

    private fun drawArtwork(
        canvas: Canvas, layout: SceneLayout, bitmap: Bitmap, paint: Paint,
        offset: Float, baseline: Float, visibility: Float,
    ) {
        val u = layout.u
        val left = layout.w * .5f - (bitmap.width * .5f + offset) * u
        val right = left + bitmap.width * u
        if (right < 0f || left > layout.w) return
        val bottom = layout.yAt(baseline)
        target.set(left, bottom - bitmap.height * u, right, bottom)
        paint.alpha = (visibility * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(bitmap, null, target, paint)
    }

    private fun updateLight(p: Palette) {
        val darkness = max(p.f[Palette.STARS], .63f * p.f[Palette.LIT])
        val dimming = mix(Color.WHITE, Color.BLACK, .16f + .53f * darkness)
        val skyWash = mix(Color.BLACK, p.c[Palette.HAZE], .13f + .38f * darkness)
        val gardenWash = mix(Color.BLACK, mix(p.c[Palette.HAZE], p.c[Palette.TREE], .4f), .10f + .45f * darkness)
        if (dimming == lastDimming && skyWash == lastSkyWash && gardenWash == lastGardenWash) return
        massifPaint.colorFilter = LightingColorFilter(dimming, skyWash)
        loopPaint.colorFilter = LightingColorFilter(dimming, gardenWash)
        lastDimming = dimming
        lastSkyWash = skyWash
        lastGardenWash = gardenWash
    }

    private fun bakeMassif(): Bitmap {
        val bitmap = Bitmap.createBitmap(820, 238, Bitmap.Config.ARGB_8888)
        val art = PixelArt(bitmap)
        // Broad Kanchenjunga profile, with the highest ridge set off from its neighboring summits.
        art.facet(0x788CAD, 0, 231, 34, 210, 79, 190, 111, 158, 143, 157, 177, 113,
            193, 114, 218, 86, 243, 98, 265, 75, 293, 59, 320, 76, 349, 56, 373, 68,
            398, 38, 412, 37, 429, 19, 446, 31, 456, 32, 482, 64, 507, 81, 519, 76,
            549, 100, 568, 90, 585, 109, 607, 112, 639, 144, 662, 143, 694, 169,
            710, 165, 755, 199, 780, 203, 820, 231, 820, 238, 0, 238)
        art.facet(0xA8BCCD, 35, 210, 111, 158, 143, 157, 177, 113, 193, 114, 218, 86,
            231, 118, 208, 132, 205, 161, 131, 197, 145, 211, 68, 229)
        art.facet(0xE2ECEA, 140, 160, 177, 113, 193, 114, 218, 86, 244, 98, 232, 114,
            216, 117, 211, 132, 189, 135, 179, 153, 161, 143)
        art.facet(0xC9DCE3, 207, 153, 231, 118, 243, 98, 265, 75, 293, 59, 314, 71,
            304, 91, 280, 101, 260, 140, 271, 159, 251, 182, 212, 196, 180, 208)
        art.facet(0xECF2ED, 247, 103, 265, 75, 293, 59, 314, 71, 300, 83, 272, 91, 266, 105, 253, 96)
        art.facet(0x9AB0CA, 267, 187, 293, 145, 287, 122, 314, 112, 320, 76, 349, 56,
            373, 68, 398, 38, 412, 37, 429, 19, 446, 31, 449, 87, 409, 132, 421, 169,
            395, 223, 303, 232)
        art.facet(0xCFDFE6, 288, 175, 319, 115, 320, 76, 349, 56, 373, 68, 355, 96,
            336, 100, 334, 141, 320, 157, 325, 170, 303, 170)
        art.facet(0xF3F4E9, 331, 88, 349, 56, 374, 68, 399, 38, 412, 37, 429, 19, 447, 31,
            440, 55, 418, 67, 411, 99, 382, 120, 371, 148, 362, 124, 368, 105, 355, 111, 354, 83)
        art.facet(0xD4E3E8, 376, 184, 397, 124, 421, 103, 432, 70, 446, 31, 456, 32,
            483, 64, 507, 81, 519, 76, 549, 100, 553, 126, 523, 147, 538, 183,
            488, 211, 471, 234, 401, 234)
        art.facet(0x8DA7C3, 409, 192, 430, 140, 458, 117, 451, 91, 475, 77, 468, 48,
            485, 65, 507, 81, 502, 102, 471, 143, 484, 168, 452, 185, 442, 221)
        art.facet(0xF1F3EA, 444, 55, 446, 31, 456, 32, 482, 64, 467, 59, 462, 77, 450, 83)
        art.facet(0x96ADC6, 518, 176, 526, 143, 553, 125, 549, 100, 568, 90, 585, 109,
            607, 112, 639, 144, 662, 143, 694, 169, 710, 165, 755, 199, 789, 221, 685, 233, 526, 233)
        art.facet(0xDBE6E6, 546, 123, 549, 100, 568, 90, 585, 109, 607, 112, 621, 135,
            597, 129, 585, 142, 570, 126, 551, 141)
        art.facet(0x6883A5, 8, 230, 75, 216, 127, 208, 202, 223, 278, 216, 325, 191,
            394, 193, 467, 221, 539, 204, 604, 197, 689, 221, 752, 215, 820, 230, 820, 238, 8, 238)
        return art.weathered(281, .11f)
    }

    private fun bakeLoop(): Bitmap {
        val bitmap = Bitmap.createBitmap(520, 178, Bitmap.Config.ARGB_8888)
        val art = PixelArt(bitmap)
        // A garden on the hillside, with the return curve tucked below its far entrance.
        art.facet(0x405B40, 16, 150, 58, 130, 98, 100, 165, 84, 226, 80, 328, 76,
            412, 88, 483, 116, 517, 148, 497, 165, 436, 174, 86, 177, 30, 167)
        art.facet(0x68814C, 29, 149, 59, 130, 98, 106, 167, 86, 239, 80, 329, 78,
            411, 90, 475, 115, 498, 140, 472, 154, 418, 155, 344, 162, 162, 160, 76, 157)
        art.facet(0x809855, 78, 132, 135, 103, 194, 91, 270, 85, 347, 87, 418, 95,
            463, 114, 468, 127, 427, 143, 341, 148, 252, 145, 190, 154, 110, 148)

        val track = Path().apply {
            moveTo(58f, 134f)
            cubicTo(105f, 112f, 135f, 100f, 216f, 96f)
            cubicTo(316f, 87f, 410f, 94f, 447f, 110f)
            cubicTo(497f, 139f, 401f, 157f, 316f, 156f)
            cubicTo(213f, 155f, 146f, 143f, 74f, 159f)
        }
        art.stroke(track, 0x78816A, 11f)
        art.stroke(track, 0xAA9B83, 8f)
        val pos = FloatArray(2)
        val tangent = FloatArray(2)
        val measure = PathMeasure(track, false)
        var at = 0f
        while (at < measure.length) {
            measure.getPosTan(at, pos, tangent)
            val x = pos[0]
            val y = pos[1]
            val nx = -tangent[1]
            val ny = tangent[0]
            art.line(x - nx * 4.2f, y - ny * 4.2f, x + nx * 4.2f, y + ny * 4.2f, 0x4D4845, 1.7f)
            val next = (at + 4f).coerceAtMost(measure.length)
            for (rail in -1..1 step 2) {
                measure.getPosTan(next, pos, tangent)
                art.line(x + nx * 2.55f * rail, y + ny * 2.55f * rail,
                    pos[0] - tangent[1] * 2.55f * rail, pos[1] + tangent[0] * 2.55f * rail, 0xD5CAC2, 1.25f)
            }
            at = next
        }

        // Curved flower beds inside the oval and the broad steps below the memorial obelisk.
        art.oval(130f, 116f, 229f, 138f, 0x496E44)
        art.oval(299f, 110f, 420f, 138f, 0x4D7448)
        val flowers = intArrayOf(0xE7C665, 0xD4868A, 0xF0DAC0, 0xC86C6B)
        val random = SeqRandom(530)
        for (i in 0 until 172) {
            val x = 138f + random.next() * 270f
            val y = 115f + random.next() * 21f
            val leftBed = abs(x - 178f) / 48f + abs(y - 126f) / 16f < 1f
            val rightBed = abs(x - 358f) / 64f + abs(y - 123f) / 17f < 1f
            if (leftBed || rightBed) art.rect(x, y, x + 2f, y + 1.5f, flowers[i % flowers.size])
        }
        art.facet(0x667461, 176, 130, 209, 111, 306, 111, 338, 129, 316, 140, 197, 140)
        art.facet(0xAFACA1, 191, 128, 213, 109, 304, 109, 322, 128, 304, 135, 213, 135)
        art.rect(198f, 127f, 318f, 130f, 0xD6D1B7)
        art.rect(205f, 122f, 310f, 125f, 0xBEB89F)
        art.rect(211f, 117f, 306f, 119f, 0xDDD6BE)
        art.rect(227f, 110f, 284f, 116f, 0x908778)
        art.rect(232f, 103f, 279f, 111f, 0xCBC0A2)
        art.rect(238f, 97f, 272f, 103f, 0xA4967E)
        art.facet(0xBBAD91, 242, 96, 248, 43, 256, 34, 265, 43, 270, 96)
        art.facet(0xE7DABC, 247, 95, 251, 45, 256, 34, 259, 44, 261, 95)
        art.facet(0x9A8B77, 261, 95, 259, 44, 256, 34, 265, 43, 270, 96)
        art.rect(242f, 93f, 270f, 99f, 0xCFC09F)
        art.rect(251f, 78f, 260f, 87f, 0x77716A)

        val hedgeXs = intArrayOf(96, 113, 144, 165, 328, 350, 385, 408, 440)
        for (i in hedgeXs.indices) {
            val x = hedgeXs[i].toFloat()
            val y = 100f + abs(x - 260f) * .075f
            art.oval(x - 9f, y - 5f, x + 9f, y + 2f, 0x394F3A)
            art.facet(0x426744, x.toInt() - 6, y.toInt(), x.toInt() - 4, y.toInt() - 10,
                x.toInt() + 1, y.toInt() - 17, x.toInt() + 5, y.toInt() - 8, x.toInt() + 7, y.toInt())
        }
        return art.weathered(619, .09f)
    }

    private class PixelArt(private val bitmap: Bitmap) {
        private val canvas = Canvas(bitmap)
        private val paint = Paint()
        private val path = Path()

        fun facet(color: Int, vararg coordinates: Int) {
            path.rewind()
            path.moveTo(coordinates[0].toFloat(), coordinates[1].toFloat())
            for (i in 2 until coordinates.size step 2) {
                path.lineTo(coordinates[i].toFloat(), coordinates[i + 1].toFloat())
            }
            path.close()
            paint.color = solid(color)
            canvas.drawPath(path, paint)
        }

        fun stroke(curve: Path, color: Int, width: Float) {
            paint.color = solid(color)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width
            canvas.drawPath(curve, paint)
            paint.style = Paint.Style.FILL
        }

        fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float) {
            paint.color = solid(color)
            paint.strokeWidth = width
            canvas.drawLine(x0, y0, x1, y1, paint)
        }

        fun oval(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
            paint.color = solid(color)
            canvas.drawOval(x0, y0, x1, y1, paint)
        }

        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
            paint.color = solid(color)
            canvas.drawRect(x0, y0, x1, y1, paint)
        }

        fun weathered(seed: Int, grain: Float): Bitmap {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val color = pixels[i]
                if (color == Color.TRANSPARENT) continue
                val noise = hash01(i, seed).toFloat()
                val highlight = if (noise < .5f) Color.BLACK else Color.WHITE
                val toned = mix(color, highlight, abs(noise - .5f) * grain)
                val x = i % w
                val edge = minOf(x, w - x - 1).coerceAtMost(36) / 36f
                pixels[i] = (toned and 0x00FFFFFF) or ((edge * 255f).roundToInt() shl 24)
            }
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            return bitmap
        }

        private fun solid(color: Int): Int = color or Color.BLACK
    }

    private companion object {
        const val GARDEN_PARALLAX = .046
    }
}
