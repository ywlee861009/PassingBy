package com.kero.passingby.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

/** Bake at one scene unit per pixel and preserve the hard tile, timber and stucco edges at any density. */
internal class CaliforniaLandmarks {
    private val sprites = java.util.EnumMap<LandmarkKind, Bitmap>(LandmarkKind::class.java)
    var bitmapBytes = 0L
        private set

    fun release() {
        // Recorded hardware canvas frames may still reference the pixels.
        sprites.clear()
        bitmapBytes = 0L
    }

    private val paint = Paint().apply { isFilterBitmap = false }
    private val bounds = RectF()
    private var previousMultiply = 0
    private var previousAdd = 0

    fun draw(canvas: Canvas, palette: Palette, kind: LandmarkKind, x: Float, base: Float, unit: Float) {
        val sprite = sprites[kind] ?: run {
            val bitmap = when (kind) {
                LandmarkKind.LA_UNION_STATION -> Sprite(139, 81).apply { unionStation() }.bitmap
                LandmarkKind.SANTA_FE_DEPOT -> Sprite(151, 60).apply { santaFeDepot() }.bitmap
                LandmarkKind.SAN_CLEMENTE_PIER -> Sprite(239, 90).apply { timberPier() }.bitmap
                LandmarkKind.ANAHEIM_ARTIC -> anaheimArticBitmap()
                else -> return
            }
            sprites[kind] = bitmap
            bitmapBytes += bitmap.allocationByteCount.toLong()
            bitmap
        }
        val darkness = max(palette.f[Palette.STARS], palette.f[Palette.LIT] * .6f)
        val haze = .16f + .52f * darkness
        val ambient = mix(palette.c[Palette.HAZE], palette.c[Palette.TREE], darkness)
        val multiply = mix(Color.WHITE, Color.BLACK, haze)
        val add = mix(Color.BLACK, ambient, haze)
        if (multiply != previousMultiply || add != previousAdd) {
            paint.colorFilter = LightingColorFilter(multiply, add)
            previousMultiply = multiply
            previousAdd = add
        }
        val half = sprite.width * unit / 2
        bounds.set(x - half, base - sprite.height * unit, x + half, base)
        canvas.drawBitmap(sprite, null, bounds, paint)
    }

    private class Sprite(width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private val canvas = Canvas(bitmap)
        private val brush = Paint()
        private val cream = 0xE8CD9F
        private val sun = 0xFAE7BC
        private val shadow = 0xAD886B
        private val recess = 0x4C4A4B
        private val sill = 0x735447
        private val clay = 0xAC6146
        private val tile = 0xD58A5E
        private val glass = 0x789394
        private val timber = 0x795642

        // Integer half-open bounds keep adjacent colour planes seamless.
        private fun box(x: Int, y: Int, right: Int, bottom: Int, rgb: Int) {
            brush.color = rgb or 0xFF000000.toInt()
            canvas.drawRect(x.toFloat(), y.toFloat(), right.toFloat(), bottom.toFloat(), brush)
        }

        private fun arch(x: Int, y: Int, w: Int, h: Int, color: Int) {
            box(x + 2, y, x + w - 2, y + 1, color)
            box(x + 1, y + 1, x + w - 1, y + 3, color)
            box(x, y + 3, x + w, y + h, color)
        }

        private fun roof(x: Int, y: Int, w: Int, h: Int) {
            for (row in 0 until h) {
                val inset = h - row - 1
                box(x + inset, y + row, x + w - inset, y + row + 1, if (row == 0) tile else clay)
                if (row % 2 == 1) {
                    for (i in x + inset + 2 until x + w - inset - 1 step 5)
                        box(i, y + row, i + 2, y + row + 1, tile)
                }
            }
            box(x, y + h, x + w, y + h + 2, sill)
        }

        fun unionStation() {
            // Waiting hall recedes to the left of the clock tower.
            box(1, 55, 99, 81, cream)
            roof(0, 49, 101, 8)
            for (x in 5..85 step 11) {
                arch(x, 63, 7, 16, shadow)
                arch(x + 1, 64, 5, 15, recess)
                box(x + 2, 67, x + 5, 72, glass)
            }
            box(1, 78, 99, 81, shadow)
            box(91, 43, 135, 81, cream)
            box(98, 39, 128, 44, cream)
            box(103, 36, 123, 40, sun)
            for (x in 95..126 step 10) arch(x, 58, 7, 23, recess)
            // Stepped shoulders, louvered belfry and shallow pyramidal terracotta cap.
            box(71, 34, 95, 81, cream)
            box(71, 34, 77, 81, shadow)
            box(74, 18, 92, 36, cream)
            box(74, 18, 78, 36, shadow)
            box(73, 16, 93, 19, sun)
            box(76, 8, 90, 16, cream)
            for (x in 78..87 step 4) arch(x, 9, 3, 7, recess)
            roof(73, 2, 20, 6)
            box(82, 0, 84, 3, sill)
            arch(78, 21, 11, 11, sill)
            box(80, 22, 87, 31, sun)
            box(79, 24, 88, 29, sun)
            box(83, 23, 84, 27, recess)
            box(83, 26, 87, 27, recess)
            box(80, 35, 82, 62, sun)
            box(89, 35, 91, 62, sun)
            arch(79, 64, 12, 17, recess)
        }

        fun santaFeDepot() {
            box(1, 35, 150, 60, cream)
            roof(0, 30, 151, 7)
            for (x in 5..141 step 12) {
                arch(x, 42, 8, 16, shadow)
                arch(x + 1, 43, 6, 15, recess)
                box(x + 2, 46, x + 6, 50, glass)
            }
            box(59, 31, 98, 60, sun)
            box(64, 28, 93, 32, sun)
            box(69, 25, 88, 29, cream)
            arch(69, 34, 19, 26, shadow)
            arch(71, 36, 15, 24, recess)
            box(73, 40, 84, 45, glass)
            box(78, 38, 79, 60, shadow)
            depotTower(40)
            depotTower(97)
        }

        private fun depotTower(x: Int) {
            box(x, 21, x + 21, 60, cream)
            box(x, 21, x + 4, 60, shadow)
            box(x + 17, 22, x + 20, 59, sun)
            arch(x + 6, 25, 9, 16, recess)
            box(x + 10, 26, x + 11, 41, cream)
            box(x + 3, 42, x + 18, 44, shadow)
            box(x - 1, 20, x + 22, 23, sun)
            val blue = 0x376A78
            val gold = 0xE6B15B
            for (row in 0 until 13) {
                val inset = when (row) { 0 -> 8; 1, 2 -> 5; 3, 4 -> 3; 5, 6 -> 2; else -> 0 }
                for (i in inset until 21 - inset) {
                    val diamond = (i + row) % 6 < 3 == ((i - row + 18) % 6 < 3)
                    val color = if (diamond) gold else blue
                    box(x + i, 7 + row, x + i + 1, 8 + row, if (i < 6) mix(color, sill, .38f) else color)
                }
            }
            box(x + 8, 4, x + 13, 8, cream)
            box(x + 9, 1, x + 12, 4, blue)
            box(x + 10, 0, x + 11, 2, gold)
        }

        fun timberPier() {
            // Oblique view looking out to sea, with perspective-compressed bays at the head.
            fun deckY(x: Int) = 23 + x * 43 / 238 + max(0, x - 201) / 4
            fun width(x: Int) = 3 + x / 25
            fun postHeight(x: Int) = 2 + x / 49
            val bays = intArrayOf(3, 11, 20, 30, 41, 54, 69, 86, 105, 127, 152, 180, 210)
            val wetWood = 0x594F4A
            val planks = 0xC39A72
            val bleached = 0xE1C198
            for (i in 0 until bays.lastIndex) {
                val x = bays[i]
                val next = bays[i + 1]
                val top = deckY(x) + width(x)
                val length = 4 + x / 18
                val thick = 1 + x / 74
                box(x + 4, deckY(x), x + 5 + thick, top + length - 2, wetWood)
                box(x, top, x + thick + 1, top + length, timber)
                box(x + thick, top, x + thick + 1, top + length - 1, shadow)
                for (xx in x + 1 until next) {
                    val span = next - x
                    val brace = deckY(xx) + width(xx) + 2
                    val down = brace + (xx - x) * (length - 2) / span
                    val up = brace + (next - xx) * (length - 2) / span
                    box(xx, down, xx + 1, down + thick, timber)
                    box(xx, up, xx + 1, up + thick, wetWood)
                }
            }
            for (x in 0..238) {
                val back = deckY(x)
                val front = back + width(x)
                val rail = postHeight(x)
                box(x, back, x + 1, front, planks)
                if (x % (3 + x / 70) == 0) box(x, back, x + 1, front, shadow)
                box(x, front, x + 1, front + 1, bleached)
                box(x, front + 1, x + 1, front + 3, timber)
                box(x, back - rail, x + 1, back - rail + 1, bleached)
                box(x, front - rail, x + 1, front - rail + 1, bleached)
                if (x > 93) box(x, front - rail / 2, x + 1, front - rail / 2 + 1, shadow)
            }
            for (i in bays.indices) {
                val x = bays[i]
                val back = deckY(x)
                val rail = postHeight(x)
                val jump = if (i == bays.lastIndex) 238 - x else bays[i + 1] - x
                for (xx in intArrayOf(x, x + jump / 2)) {
                    val y = deckY(xx)
                    box(xx, y - rail, xx + 1, y, timber)
                    val front = y + width(xx)
                    box(xx, front - rail, xx + 1 + xx / 150, front + 1, timber)
                }
                if (i in 3..11 && i % 2 == 1) {
                    val top = back - 5 - x / 28
                    box(x, top, x + 1 + x / 135, back, wetWood)
                    box(x - 1, top, x + 2, top + 2, sun)
                    box(x - 1, top - 1, x + 2, top, wetWood)
                }
            }
            // Low fishing pavilion and its shaded veranda at the seaward head.
            box(5, 15, 26, 24, cream)
            box(5, 15, 9, 23, shadow)
            for (x in 10..23 step 5) box(x, 17, x + 3, 21, recess)
            roof(2, 10, 28, 5)
            box(25, 19, 32, 20, clay)
            box(30, 20, 31, 28, timber)
        }
    }
}
