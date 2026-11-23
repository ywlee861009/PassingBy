package com.kero.passingby.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs

/** Trackside elevations of the Fray Marcos complex and the log depot in Grand Canyon Village. */
internal class ArizonaDepotArt(width: Int, height: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint()
    private val dark = 0x403B38
    private val wood = 0x79553D
    private val endGrain = 0xBB8C5D
    private val glass = 0x657D79
    private val cream = 0xEFDEB5
    private val stucco = 0xD7BE90
    private val shadow = 0xAA916F
    private val trim = 0xF6E9CC
    private val pine = 0x425A4F

    private fun rect(x: Int, y: Int, r: Int, b: Int, color: Int) {
        paint.color = color or 0xFF000000.toInt()
        canvas.drawRect(x.toFloat(), y.toFloat(), r.toFloat(), b.toFloat(), paint)
    }

    private fun sash(x: Int, y: Int, w: Int, h: Int, frame: Int) {
        rect(x - 1, y - 1, x + w + 1, y + h + 1, frame)
        rect(x, y, x + w, y + h, dark)
        rect(x + 1, y + 1, x + w - 1, y + h - 2, glass)
        rect(x + w / 2, y, x + w / 2 + 1, y + h, frame)
        rect(x, y + h / 2, x + w, y + h / 2 + 1, frame)
    }

    private fun column(x: Int, y: Int) {
        rect(x - 1, y, x + 5, y + 2, trim)
        rect(x, y + 2, x + 4, 52, stucco)
        rect(x, y + 3, x + 1, 51, shadow)
        rect(x + 2, y + 3, x + 3, 51, trim)
        rect(x - 1, 51, x + 5, 54, cream)
    }

    fun williams() {
        // Two-storey Harvey House behind the lower flat-roofed platform colonnade.
        rect(4, 9, 82, 54, stucco)
        rect(4, 9, 10, 54, shadow)
        rect(13, 5, 75, 28, cream)
        rect(11, 4, 77, 6, trim)
        rect(3, 8, 84, 10, trim)
        rect(9, 9, 81, 11, shadow)
        for (x in 15..69 step 11) {
            sash(x, 14, 6, 10, shadow)
            rect(x - 2, 12, x + 8, 14, trim)
            rect(x - 2, 24, x + 8, 26, trim)
        }
        rect(7, 27, 80, 30, trim)
        for (x in 16..64 step 15) sash(x, 33, 9, 16, shadow)
        // Slightly recessed ticket hall and warm brick paving are visible through the columns.
        rect(78, 32, 161, 53, shadow)
        for (x in 87..148 step 12) sash(x, 37, 7, 13, wood)
        rect(74, 28, 171, 32, cream)
        rect(74, 32, 171, 34, shadow)
        rect(72, 26, 173, 28, trim)
        rect(75, 24, 169, 26, stucco)
        for (x in intArrayOf(76, 81, 97, 114, 131, 149, 161, 166)) column(x, 34)
        rect(97, 19, 132, 26, cream)
        label("WILLIAMS", 99, 20, dark)
        rect(1, 54, 172, 57, 0x9A6C54)
        for (x in 2..168 step 7) rect(x, 54, x + 4, 55, 0xC29B75)
    }

    private fun logs(x: Int, y: Int, r: Int, b: Int) {
        rect(x, y, r, b, wood)
        for (row in y until b - 1 step 3) {
            val end = if ((row - y) % 6 == 0) 2 else 0
            rect(x - end, row, r + end, row + 1, 0x9C7251)
            rect(x - end, row + 2, r + end, row + 3, 0x573D32)
            rect(x - end, row, x + 2, row + 2, endGrain)
            rect(r - 2, row, r + end, row + 2, endGrain)
        }
    }

    private fun gable(cx: Int, peak: Int, half: Int, pitch: Int, base: Int) {
        for (dx in -half..half) {
            val x = cx + dx
            val y = peak + abs(dx) * pitch / half
            rect(x, y + 2, x + 1, base, if (dx < -half + 4) dark else wood)
            if ((x % 4 == 0) && y + 4 < base) rect(x, y + 4, x + 1, base, 0x866349)
            rect(x, y, x + 1, y + 2, pine)
            rect(x, y + 2, x + 1, y + 3, endGrain)
        }
    }

    fun grandCanyon() {
        // The wings have east-west green shingle ridges. The taller centre gable faces the tracks.
        logs(7, 41, 160, 63)
        for (y in 31..43) {
            val inset = (43 - y) / 2
            rect(1 + inset, y, 168 - inset, y + 1, if (y % 3 == 0) 0x597064 else pine)
            if (y % 3 == 0) for (x in 9 + y % 7..155 step 8) rect(x, y, x + 1, y + 1, pine)
        }
        rect(1, 44, 168, 46, dark)
        for (x in intArrayOf(18, 34, 123, 140)) sash(x, 49, 10, 10, 0x9F7856)
        logs(50, 39, 117, 63)
        gable(83, 2, 37, 26, 42)
        for (y in 26..39 step 4) rect(55, y, 112, y + 1, 0x654A39)
        rect(80, 12, 87, 17, pine)
        rect(82, 11, 85, 18, pine)
        rect(81, 14, 86, 15, cream)
        rect(83, 12, 84, 17, cream)
        rect(60, 23, 106, 30, 0x543E32)
        label("GRAND CANYON", 60, 24, 0xB3BFA2)
        sash(62, 33, 10, 8, endGrain)
        sash(95, 33, 10, 8, endGrain)
        // Projecting operator's bay, with a smaller cross-gable and windows on three faces.
        logs(68, 48, 99, 63)
        gable(83, 35, 19, 12, 49)
        rect(82, 41, 85, 44, cream)
        sash(70, 50, 6, 8, wood)
        sash(79, 49, 9, 9, endGrain)
        sash(91, 50, 6, 8, wood)
        rect(4, 63, 164, 66, 0x827C6B)
        rect(4, 63, 164, 64, 0xB0A48D)
    }

    /** Three-pixel letterforms keep both historic name boards legible when the view is enlarged. */
    private fun label(text: String, left: Int, top: Int, color: Int) {
        val glyphs = mapOf(
            'A' to "25755", 'C' to "34443", 'D' to "65556", 'G' to "34753",
            'I' to "72227", 'L' to "44447", 'M' to "57755", 'N' to "57775",
            'O' to "25552", 'R' to "65655", 'S' to "34216", 'W' to "55572", 'Y' to "55222",
        )
        for ((i, letter) in text.withIndex()) {
            val rows = glyphs[letter] ?: continue
            for ((row, bits) in rows.withIndex()) for (col in 0..2) {
                if (bits.digitToInt() and (4 shr col) != 0) {
                    val x = left + i * 4 + col
                    rect(x, top + row, x + 1, top + row + 1, color)
                }
            }
        }
    }
}
