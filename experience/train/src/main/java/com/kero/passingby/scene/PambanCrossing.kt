package com.kero.passingby.scene

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Open water and the lowered lift span of the New Pamban railway bridge. */
internal class PambanCrossing {
    private val waterPaint = Paint()
    private val ink = Paint()
    private val path = Path()
    private var lastHorizon = 0f
    private var lastBottom = 0f
    private val waterColors = IntArray(3)

    fun draw(
        canvas: Canvas, layout: SceneLayout, p: Palette, distance: Double, seconds: Double,
        start: Double, end: Double, liftProgress: Float,
    ) {
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()
        val u = layout.u
        val horizon = h * .46f
        val progress = ((distance - start) / (end - start)).coerceIn(0.0, 1.0).toFloat()
        val night = max(p.f[Palette.STARS], p.f[Palette.LIT] * .62f)

        drawWater(canvas, w, h, u, horizon, p, night, distance, seconds)
        drawRoadBridge(canvas, w, horizon, u, p, night, progress)
        drawFishingBoats(canvas, w, h, u, horizon, p, night, distance, seconds)
        drawRailBridge(canvas, w, h, u, p, night, distance, progress, liftProgress)
    }

    private fun drawWater(
        canvas: Canvas, w: Float, h: Float, u: Float, horizon: Float, p: Palette, night: Float,
        distance: Double, seconds: Double,
    ) {
        val farWater = tone(0x8FC9CC, p, night, .32f)
        val shoals = tone(0x389D9F, p, night, .07f)
        val deepWater = tone(0x297984, p, night)
        if (horizon != lastHorizon || h != lastBottom ||
            farWater != waterColors[0] || shoals != waterColors[1] || deepWater != waterColors[2]
        ) {
            lastHorizon = horizon
            lastBottom = h
            waterColors[0] = farWater
            waterColors[1] = shoals
            waterColors[2] = deepWater
            waterPaint.shader = LinearGradient(0f, horizon, 0f, h, waterColors, WATER_STOPS, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, horizon, w, h, waterPaint)

        val waterHeight = h - horizon
        val waveDark = tone(0x286F83, p, night)
        val waveLight = tone(0xA7DCDA, p, night, .1f)
        val glint = mix(p.c[Palette.SUN], Color.WHITE, .34f)
        val sunlight = p.f[Palette.SUN_A] * (1f - night)
        val reflectionX = p.f[Palette.SUN_X] * w
        for (row in 0 until WAVE_ROWS) {
            val depth = (row + 1f) / WAVE_ROWS
            val spacing = 23.0 + depth * 27.0
            val y = horizon + (waterHeight - u) * depth * depth
            val off = distance * (.025 + .095 * depth) + seconds * (1.8 + 4.0 * depth)
            val first = floor(off / spacing).toInt()
            val last = floor((off + w / u) / spacing).toInt()
            for (cell in first..last) {
                val key = cell * 29 + row * 79
                val seed = hash01(key, 1823).toFloat()
                if (seed < .23f) continue
                val x = ((cell * spacing - off) * u).toFloat() + seed * 11f * u
                val crest = y + (hash01(key, 1829).toFloat() - .5f) * 8f * depth * u
                val length = (3 + (5 + 16 * depth) * seed) * u
                val thickness = max(1f, (.55f + depth * .55f) * u)
                val sparkling = seed > .79f && abs(x - reflectionX) < w * (.07f + .15f * depth)
                ink.color = when {
                    sparkling -> mix(waveLight, glint, sunlight * (.34f + .34f * seed))
                    seed > .65f -> waveLight
                    else -> waveDark
                }
                ink.alpha = if (sparkling) 194 else (70 + 80 * depth).toInt()
                canvas.drawRect(x, crest, x + length, crest + thickness, ink)
            }
        }
        ink.alpha = 255
    }

    /** The separate road crossing reads as a thin silhouette behind the railway. */
    private fun drawRoadBridge(
        canvas: Canvas, w: Float, horizon: Float, u: Float, p: Palette, night: Float, progress: Float,
    ) {
        val center = w * (.5f + (.5f - progress) * 1.25f)
        val base = horizon + 22f * u
        val waterline = base + 6f * u
        val step = 26f * u
        val archWidth = 155f * u
        val archHeight = 13f * u
        val edge = tone(0x7D9696, p, night, .4f)
        val columns = tone(0x537C82, p, night, .36f)
        var x = (center % step) - step
        var y = base - archHeight * roadArch((x - center) / archWidth)
        while (x < w) {
            val next = x + step
            val nextY = base - archHeight * roadArch((next - center) / archWidth)
            ink.color = columns
            canvas.drawRect(x - u, y, x + u, waterline, ink)
            ink.color = edge
            ink.strokeWidth = 2f * u
            canvas.drawLine(x, y, next, nextY, ink)
            x = next
            y = nextY
        }
    }

    private fun roadArch(x: Float): Float = (1f - x * x).coerceAtLeast(0f)

    private fun drawFishingBoats(
        canvas: Canvas, w: Float, h: Float, u: Float, horizon: Float, p: Palette, night: Float,
        distance: Double, seconds: Double,
    ) {
        val spacing = 235.0
        val off = distance * .048 - seconds * .58
        val first = floor(off / spacing).toInt()
        val last = floor((off + w / u) / spacing).toInt() + 1
        for (cell in first..last) {
            val pos = hash01(cell, 1831).toFloat()
            val x = ((cell * spacing - off + 31 + 155 * pos) * u).toFloat()
            val y = horizon + h * (.085f + .19f * pos)
            val size = (9f + 15f * pos) * u
            ink.color = tone(0xA7D9D6, p, night)
            ink.alpha = 150
            canvas.drawRect(x - size * 1.1f, y + 2f * u, x + size * .58f, y + 3f * u, ink)
            canvas.drawRect(x - size * .9f, y + 4f * u, x - size * .1f, y + 5f * u, ink)

            ink.color = tone(if (cell % 2 == 0) 0xA64932 else 0x315F73, p, night)
            path.rewind()
            path.moveTo(x - size * .55f, y - size * .13f)
            path.lineTo(x + size * .55f, y - size * .19f)
            path.lineTo(x + size * .32f, y + size * .13f)
            path.lineTo(x - size * .37f, y + size * .13f)
            path.close()
            canvas.drawPath(path, ink)

            ink.color = tone(0xE3D5A8, p, night)
            canvas.drawRect(x - size * .23f, y - size * .31f, x + size * .1f, y - size * .12f, ink)
            ink.color = tone(0x365E69, p, night)
            ink.strokeWidth = max(1f, .74f * u)
            canvas.drawLine(x + size * .06f, y - size * .31f, x + size * .06f, y - size * .72f, ink)
        }
    }

    private fun drawRailBridge(
        canvas: Canvas, w: Float, h: Float, u: Float, p: Palette, night: Float,
        distance: Double, progress: Float, liftProgress: Float,
    ) {
        val deck = h * .76f
        val towerHeight = h * .31f
        val middle = w * (.5f + (liftProgress - progress) * 2.6f)
        val liftHalf = min(w * .18f, 74f * u)
        val left = middle - liftHalf
        val right = middle + liftHalf
        val pierLight = tone(0xC9BEAC, p, night, .04f)
        val pierShade = tone(0x7A8C8D, p, night)
        val steel = tone(0xC2CED1, p, night)
        val girder = tone(0x596E77, p, night)
        val foot = deck + 22f * u
        val pierSpacing = 54.0 * u
        var x = ((-distance * .32 * u) % pierSpacing).toFloat() - pierSpacing.toFloat()
        while (x < w) {
            if (x < left - 9f * u || x > right + 9f * u) {
                drawPier(canvas, x, deck, foot, u * 4f, pierLight, pierShade)
            }
            x += pierSpacing.toFloat()
        }
        ink.color = girder
        canvas.drawRect(0f, deck - u, w, deck + 3f * u, ink)
        ink.color = steel
        canvas.drawRect(0f, deck - 2f * u, w, deck - .7f * u, ink)

        if (right < -26f * u || left > w + 26f * u) return
        // The movable span stays down under the train. The rising towers and their bracing frame it.
        val spanTop = deck - 14f * u
        ink.color = girder
        ink.strokeWidth = 2.1f * u
        canvas.drawLine(left, spanTop, right, spanTop, ink)
        val panel = (right - left) / 8f
        for (bay in 0 until 8) {
            val start = left + panel * bay
            val end = start + panel
            if (bay % 2 == 0) canvas.drawLine(start, deck, end, spanTop, ink)
            else canvas.drawLine(start, spanTop, end, deck, ink)
        }
        for (tower in 0..1) {
            val axis = if (tower == 0) left else right
            if (axis < -20f * u || axis > w + 20f * u) continue
            drawPier(canvas, axis, deck, foot + 3f * u, 12f * u, pierLight, pierShade)
            drawLiftTower(canvas, axis, deck - towerHeight, deck, u, steel, girder)
        }
    }

    private fun drawPier(
        canvas: Canvas, x: Float, top: Float, foot: Float, half: Float, light: Int, shade: Int,
    ) {
        ink.color = shade
        canvas.drawRect(x - half * 1.65f, foot - half * .3f, x + half * 1.65f, foot + half * .4f, ink)
        canvas.drawRect(x - half, top, x + half, foot, ink)
        ink.color = light
        canvas.drawRect(x - half, top, x + half * .18f, foot, ink)
        canvas.drawRect(x - half * 1.25f, top, x + half * 1.25f, top + half * .45f, ink)
    }

    private fun drawLiftTower(
        canvas: Canvas, axis: Float, top: Float, deck: Float, u: Float, steel: Int, girder: Int,
    ) {
        val left = axis - 8f * u
        val right = axis + 8f * u
        val beam = 2.1f * u
        val height = deck - top
        val tier = height / 7f
        ink.color = girder
        ink.strokeWidth = 1.55f * u
        for (ring in 0 until 7) {
            val y = top + tier * ring
            val next = y + tier
            canvas.drawLine(left, y, right, y, ink)
            if (ring % 2 == 0) canvas.drawLine(left, y, right, next, ink)
            else canvas.drawLine(right, y, left, next, ink)
        }
        canvas.drawRect(left - beam * .55f, top, left + beam * 1.45f, deck, ink)
        canvas.drawRect(right - beam * .55f, top, right + beam * 1.45f, deck, ink)
        ink.color = steel
        canvas.drawRect(left - beam, top, left + beam * .45f, deck, ink)
        canvas.drawRect(right - beam, top, right + beam * .45f, deck, ink)
        canvas.drawRect(left - 2.5f * u, top - 3f * u, right + 2.5f * u, top + 4f * u, ink)
        ink.color = girder
        canvas.drawRect(left - 2.5f * u, top + 2.5f * u, right + 2.5f * u, top + 4.5f * u, ink)
    }

    private fun tone(rgb: Int, p: Palette, night: Float, haze: Float = 0f): Int {
        val daylight = mix(0xFF000000.toInt() or rgb, p.c[Palette.HAZE], .13f + haze)
        return mix(daylight, p.c[Palette.MID], .84f * night)
    }

    private companion object {
        const val WAVE_ROWS = 18
        val WATER_STOPS = floatArrayOf(0f, .42f, 1f)
    }
}
