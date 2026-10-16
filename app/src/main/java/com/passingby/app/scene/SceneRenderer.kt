package com.passingby.app.scene

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 창밖 풍경 렌더러. android.graphics.Canvas만 쓰므로 Compose에서도, SurfaceView의
 * 별도 그리기 스레드에서도 같은 코드로 그릴 수 있다.
 *
 * 그리기 루프(render) 안에서는 객체를 만들지 않는다. 예외는 팔레트가 바뀌는 1.4초 동안
 * 그라디언트 셰이더를 새로 만드는 것뿐이다.
 */
class SceneRenderer(private val state: SceneState) {
    private val layout = SceneLayout()
    private val sprites = SpriteSet()
    private val layers = Scenery.layers
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "scene-baker").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val spanPainter = SpanPainter { c, l, offU, x0, x1, p -> drawTerrainSpan(c, l, offU, x0, x1, p) }
    private val bakers = Array(layers.size) { ChunkBaker(layers[it], executor, spanPainter) }

    @Volatile private var appliedMode: RenderMode? = null
    @Volatile private var tileSet: TileSet? = null
    @Volatile private var tileBakePending = false

    private val windowFrame = TrainWindowFrame()

    private var frameCount = 0L
    /** 마지막 render()가 그리기 명령을 기록하는 데 걸린 시간. GPU 시간은 포함하지 않는다. */
    var lastDrawNanos = 0L
        private set

    val bitmapBytes: Long
        get() = sprites.bytes + bakers.sumOf { it.bytes } + (tileSet?.bytes ?: 0L)

    // 재사용 객체
    private val terrainPath = Path()
    private val wirePath = Path()
    private val dst = RectF()
    private val placed = Placed()
    private val random = Random(7)

    private val layerPaints = Array(layers.size) { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    private val layerKeyA = IntArray(layers.size)
    private val layerKeyB = IntArray(layers.size)
    private val skyPaint = Paint()
    private var skyKeyTop = 0
    private var skyKeyBot = 0
    private var shaderLayoutValid = false
    private val starPaint = Paint().apply { color = Color.WHITE }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val windowPaint = Paint().apply { color = WINDOW_COLOR }
    private val groundPaint = Paint()
    private val tuftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val polePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glassFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val vignettePaint = Paint()
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = -0xa05e // 0xffff5fa2
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val tuftPts = FloatArray(4 * MAX_TUFTS)
    private val polesX = FloatArray(MAX_POLES)
    private val starX = FloatArray(STARS) { hash01(it, 1).toFloat() }
    private val starY = FloatArray(STARS) { Math.pow(hash01(it, 2), 1.4).toFloat() }
    private val starPhase = FloatArray(STARS) { (hash01(it, 3) * 6.28).toFloat() }
    private val starSize = FloatArray(STARS) { if (hash01(it, 4) < .15) 2f else 1.2f }
    private val dropX = FloatArray(DROPS)
    private val dropY = FloatArray(DROPS)
    private val dropV = FloatArray(DROPS) { 900f + random.nextFloat() * 500f }
    private val rainPts = FloatArray(DROPS * 4)
    private val glassX = FloatArray(GLASS)
    private val glassY = FloatArray(GLASS)
    private val glassR = FloatArray(GLASS) { 1.2f + random.nextFloat() * 3f }
    private val glassVx = FloatArray(GLASS) { -(6f + random.nextFloat() * 14f) }
    private val glassVy = FloatArray(GLASS) { 3f + random.nextFloat() * 10f }

    fun render(canvas: Canvas, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val t0 = System.nanoTime()
        frameCount++
        val p = state.effective
        if (state.windowFrame) {
            val f = windowFrame
            f.layout(w, h)
            // 번인 완화: 창틀 전체를 몇 픽셀 안에서 아주 천천히 움직인다(주기 수 분).
            val t = state.elapsedSec
            val dx = (f.driftMax * sin(t * DRIFT_X)).toFloat()
            val dy = (f.driftMax * sin(t * DRIFT_Y + 1.0)).toFloat()
            val o = f.opening
            canvas.save()
            canvas.translate(dx, dy)
            canvas.save()
            canvas.clipPath(f.glassPath)
            canvas.save()
            canvas.translate(o.left, o.top)
            drawScene(canvas, o.width().toInt(), o.height().toInt(), p)
            canvas.restore()
            f.drawGlass(canvas, p)
            canvas.restore()
            f.drawFrame(canvas, p)
            canvas.restore()
        } else {
            drawScene(canvas, w, h, p)
        }
        lastDrawNanos = System.nanoTime() - t0
    }

    /** 창밖 풍경. 창틀이 있으면 창 구멍 크기로, 없으면 화면 전체 크기로 그린다. */
    private fun drawScene(canvas: Canvas, w: Int, h: Int, p: Palette) {
        if (layout.update(w, h)) {
            onResize()
            applyMode()
        } else if (state.mode != appliedMode) {
            applyMode()
        }
        drawSky(canvas, p)
        drawStars(canvas, p)
        drawSun(canvas, p)
        drawClouds(canvas, p)
        val tilesOk = appliedMode == RenderMode.TILES && ensureTiles()
        for (i in layers.indices) drawLayer(canvas, i, p, tilesOk)
        drawBed(canvas, p)
        drawPoles(canvas, p)
        drawRain(canvas)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignettePaint)
    }

    fun release() {
        executor.shutdownNow()
        for (b in bakers) b.release()
        tileSet = null
    }

    private fun onResize() {
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()
        val outer = max(w, h) * .75f
        val inner = min(w, h) * .35f
        vignettePaint.shader = RadialGradient(
            w / 2, h / 2, outer,
            intArrayOf(0, 0, 0x47000000), floatArrayOf(0f, inner / outer, 1f), Shader.TileMode.CLAMP,
        )
        shaderLayoutValid = false
        for (i in 0 until DROPS) {
            dropX[i] = random.nextFloat() * (w + 300 * layout.u)
            dropY[i] = random.nextFloat() * h
        }
        for (i in 0 until GLASS) {
            glassX[i] = random.nextFloat() * w
            glassY[i] = random.nextFloat() * h
        }
    }

    /** 방식별로 필요한 메모리만 들고 있는다. 바꾸면 다른 방식의 비트맵은 놓아준다. */
    private fun applyMode() {
        val mode = state.mode
        appliedMode = mode
        if (mode == RenderMode.BAKED) {
            for (b in bakers) b.resize(layout)
        } else {
            for (b in bakers) b.release()
        }
        if (mode != RenderMode.TILES) tileSet = null
    }

    private fun ensureTiles(): Boolean {
        val ts = tileSet
        val sizeOk = ts != null && ts.w == layout.w && ts.h == layout.h
        if (sizeOk && ts!!.key == state.timeOfDay) return true
        if (!tileBakePending) {
            tileBakePending = true
            val snap = layout.copy()
            val pal = Palette().apply { copyFrom(state.target) }
            val key = state.timeOfDay
            executor.execute {
                try {
                    val baked = TileBaker.bake(snap, pal, key, sprites, layers)
                    if (appliedMode == RenderMode.TILES) tileSet = baked
                } finally {
                    tileBakePending = false
                }
            }
        }
        // 크기가 맞으면 새 타일이 준비될 때까지 이전 시간대 타일을 그린다.
        return sizeOk
    }

    private fun drawSky(canvas: Canvas, p: Palette) {
        val top = p.c[Palette.TOP]
        val bot = p.c[Palette.BOT]
        if (!shaderLayoutValid || top != skyKeyTop || bot != skyKeyBot) {
            skyPaint.shader = LinearGradient(0f, 0f, 0f, layout.yAt(.78f), top, bot, Shader.TileMode.CLAMP)
            skyKeyTop = top
            skyKeyBot = bot
            if (!shaderLayoutValid) {
                layerKeyA.fill(0)
                layerKeyB.fill(0)
                shaderLayoutValid = true
            }
        }
        canvas.drawRect(0f, 0f, layout.w.toFloat(), layout.h.toFloat(), skyPaint)
    }

    private fun drawStars(canvas: Canvas, p: Palette) {
        val amount = p.f[Palette.STARS]
        if (amount <= .01f) return
        val skyH = layout.h - layout.g * .45f
        val t = state.elapsedSec
        for (i in 0 until STARS) {
            val a = amount * (.35f + .4f * (.5f + .5f * sin(t * 1.7 + starPhase[i]).toFloat()))
            starPaint.alpha = (a * 255).toInt().coerceIn(0, 255)
            val x = starX[i] * layout.w
            val y = starY[i] * skyH
            val s = starSize[i] * layout.u
            canvas.drawRect(x, y, x + s, y + s, starPaint)
        }
    }

    private fun drawSun(canvas: Canvas, p: Palette) {
        val a = p.f[Palette.SUN_A]
        if (a <= .01f) return
        val skyH = layout.h - layout.g * .45f
        val sx = p.f[Palette.SUN_X] * layout.w
        val sy = p.f[Palette.SUN_Y] * skyH
        val sr = p.f[Palette.SUN_R] * layout.u
        sunPaint.color = p.c[Palette.SUN]
        sunPaint.alpha = (.16f * a * 255).toInt()
        canvas.drawCircle(sx, sy, sr * 2.4f, sunPaint)
        sunPaint.alpha = (a * 255).toInt()
        canvas.drawCircle(sx, sy, sr, sunPaint)
    }

    private fun drawClouds(canvas: Canvas, p: Palette) {
        val u = layout.u
        val c = 230.0
        val off = state.distanceU * Scenery.CLOUD_SPEED
        val k0 = floor(off / c).toInt() - 1
        val k1 = floor((off + layout.w / u) / c).toInt() + 1
        val top = layout.h * .06f
        val bot = layout.h - layout.g * .62f
        spritePaint.color = mix(p.c[Palette.CLOUD], p.c[Palette.HAZE], .15f)
        spritePaint.alpha = (p.f[Palette.CLOUD_A] * 255).toInt()
        for (k in k0..k1) {
            val n = (if (hash01(k, 901) < .55) 1 else 0) + (if (hash01(k, 902) < .2) 1 else 0)
            for (j in 0 until n) {
                val key = k * 4 + j
                val hPx = (26 + hash01(key, 905) * 30).toFloat() * u
                val y = top + hash01(key, 904).toFloat() * (bot - top)
                val x = (((k + hash01(key, 903)) * c - off) * u).toFloat()
                val s = sprites.clouds[(hash01(key, 906) * sprites.clouds.size).toInt()]
                drawSprite(canvas, s, x, y + hPx / 2, hPx, hash01(key, 907) > .5, spritePaint, dst)
                if (state.showBoxes) canvas.drawRect(dst, boxPaint)
            }
        }
    }

    private fun drawLayer(canvas: Canvas, i: Int, p: Palette, tilesOk: Boolean) {
        val l = layers[i]
        val offU = state.distanceU * l.speed
        if (tilesOk) {
            drawTiles(canvas, i, l)
            return
        }
        val paint = layerPaints[i]
        updateLayerPaint(i, l, p, paint)
        if (appliedMode == RenderMode.BAKED) {
            bakers[i].draw(canvas, offU, paint, frameCount)
        } else {
            drawTerrainSpan(canvas, l, offU, 0f, layout.w.toFloat(), paint)
        }
        l.objects?.let { drawObjects(canvas, l, it, offU, p) }
    }

    private fun updateLayerPaint(i: Int, l: TerrainLayer, p: Palette, paint: Paint) {
        val col = layerColor(l, p)
        if (l.mist <= 0f) {
            paint.shader = null
            paint.color = col
            return
        }
        val end = mix(col, p.c[Palette.HAZE], l.mist)
        if (col == layerKeyA[i] && end == layerKeyB[i]) return
        layerKeyA[i] = col
        layerKeyB[i] = end
        val yBase = layout.yAt(l.base)
        paint.color = Color.BLACK // 셰이더를 쓸 때는 불투명 알파만 의미가 있다.
        paint.shader = LinearGradient(0f, yBase - l.amp * layout.g, 0f, yBase + 8 * layout.u, col, end, Shader.TileMode.CLAMP)
    }

    private fun drawTiles(canvas: Canvas, i: Int, l: TerrainLayer) {
        val ts = tileSet ?: return
        val tileW = ts.tileW.toDouble()
        val offPx = state.distanceU * l.speed * layout.u
        var idx = floor(offPx / tileW).toLong()
        var x = (idx * tileW - offPx).toFloat()
        val top = ts.tops[i].toFloat()
        while (x < layout.w) {
            val v = if (hash01(idx.toInt(), 77 + i) < .5) 0 else 1
            canvas.drawBitmap(ts.tiles[i][v], x, top, tilePaint)
            idx++
            x += ts.tileW
        }
    }

    private fun terrainY(l: TerrainLayer, wx: Double): Float =
        (layout.yAt(l.base) - heightNorm(l, wx) * layout.g * l.amp).toFloat()

    private fun drawTerrainSpan(canvas: Canvas, l: TerrainLayer, offU: Double, x0: Float, x1: Float, paint: Paint) {
        val u = layout.u
        val path = terrainPath
        path.rewind()
        val bottom = layout.h.toFloat()
        path.moveTo(x0, bottom)
        var x = x0
        while (true) {
            path.lineTo(x, terrainY(l, offU + x / u))
            if (x >= x1) break
            x = min(x + STEP_PX, x1)
        }
        path.lineTo(x1, bottom)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawObjects(canvas: Canvas, l: TerrainLayer, rule: ObjectRule, offU: Double, p: Palette) {
        val u = layout.u
        val c = rule.chunkUnits
        val k0 = floor(offU / c).toInt() - 1
        val k1 = floor((offU + layout.w / u) / c).toInt() + 1
        spritePaint.color = spriteColor(l, p)
        val lit = p.f[Palette.LIT]
        val w = layout.w.toFloat()
        for (k in k0..k1) {
            val n = Placement.count(l, rule, k)
            for (j in 0 until n) {
                Placement.place(l, rule, k, j, sprites, placed)
                val s = placed.sprite
                val hPx = placed.heightU * u
                val half = s.mask.width * hPx / s.mask.height / 2
                val cx = ((placed.wx - offU) * u).toFloat()
                if (cx + half < 0 || cx - half > w) continue
                val yb = terrainY(l, placed.wx) + rule.sink * u
                drawSprite(canvas, s, cx, yb, hPx, placed.flip, spritePaint, dst)
                if (state.showBoxes) canvas.drawRect(dst, boxPaint)
                if (lit > .02f && s.windows != null) {
                    drawWindows(canvas, s, placed.key, cx, yb, hPx, placed.flip, lit, windowPaint)
                }
            }
        }
    }

    private fun drawBed(canvas: Canvas, p: Palette) {
        val u = layout.u
        val by = layout.yAt(.93f)
        groundPaint.color = p.c[Palette.GROUND]
        canvas.drawRect(0f, by, layout.w.toFloat(), layout.h + 1f, groundPaint)

        tuftPaint.color = p.c[Palette.GROUND]
        tuftPaint.strokeWidth = 1.3f * u
        val off = state.distanceU * Scenery.BED_SPEED
        val c = 22.0
        val k0 = floor(off / c).toInt() - 1
        val k1 = floor((off + layout.w / u) / c).toInt() + 1
        var count = 0
        loop@ for (k in k0..k1) {
            val n = (hash01(k, 301) * 4).toInt()
            for (j in 0 until n) {
                if (count >= MAX_TUFTS) break@loop
                val key = k * 8 + j
                val x = (((k + hash01(key, 302)) * c - off) * u).toFloat()
                val hh = (4 + hash01(key, 303) * 10).toFloat() * u
                val lean = ((hash01(key, 304) - .5) * 5).toFloat() * u
                val o = count * 4
                tuftPts[o] = x
                tuftPts[o + 1] = by + u
                tuftPts[o + 2] = x + lean
                tuftPts[o + 3] = by - hh
                count++
            }
        }
        canvas.drawLines(tuftPts, 0, count * 4, tuftPaint)
    }

    private fun drawPoles(canvas: Canvas, p: Palette) {
        val u = layout.u
        val spacing = 360.0
        val off = state.distanceU * Scenery.POLE_SPEED
        val i0 = floor(off / spacing).toInt() - 1
        val i1 = floor((off + layout.w / u) / spacing).toInt() + 1
        var count = 0
        for (i in i0..i1) {
            if (count >= MAX_POLES) break
            polesX[count++] = ((i * spacing - off) * u).toFloat()
        }
        val top = layout.yAt(.16f)
        val arm = top + 10 * u
        val h = layout.h.toFloat()
        polePaint.color = p.c[Palette.POLE]
        for (n in 0 until count) {
            val x = polesX[n]
            canvas.drawRect(x - 2.5f * u, top, x + 2.5f * u, h, polePaint)
            canvas.drawRect(x - 17 * u, arm, x + 17 * u, arm + 3 * u, polePaint)
            canvas.drawRect(x - 12 * u, arm + 22 * u, x + 12 * u, arm + 24.5f * u, polePaint)
        }
        wirePaint.color = p.c[Palette.POLE]
        wirePaint.alpha = 230
        wirePaint.strokeWidth = 1.1f * u
        val path = wirePath
        path.rewind()
        for (n in 0 until count - 1) {
            val a = polesX[n]
            val b = polesX[n + 1]
            for (q in 0 until 4) {
                val dx = WIRE_DX[q] * u
                val dy = WIRE_DY[q]
                val y = arm + dy * u
                val sag = (16 + dy * .3f) * u
                path.moveTo(a + dx, y)
                path.quadTo((a + b) / 2 + dx, y + sag * 2, b + dx, y)
            }
        }
        canvas.drawPath(path, wirePaint)
    }

    private fun drawRain(canvas: Canvas) {
        val amount = state.rainAmount
        if (amount <= .01f) return
        val dt = state.lastDt
        val u = layout.u
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()
        val vx = -(220 + state.speed * .9).toFloat() * u
        val k = .022f
        rainPaint.color = Color.argb((.42f * amount * 255).toInt(), 225, 232, 245)
        rainPaint.strokeWidth = 1.2f * u
        for (i in 0 until DROPS) {
            val f = dropV[i] / 1100f
            dropY[i] += dropV[i] * u * dt
            dropX[i] += vx * dt * f
            if (dropY[i] > h || dropX[i] < -40 * u) {
                dropY[i] = -20 * u - random.nextFloat() * h * .3f
                dropX[i] = random.nextFloat() * (w + 300 * u)
            }
            val o = i * 4
            rainPts[o] = dropX[i]
            rainPts[o + 1] = dropY[i]
            rainPts[o + 2] = dropX[i] - vx * k * f
            rainPts[o + 3] = dropY[i] - dropV[i] * u * k
        }
        canvas.drawLines(rainPts, rainPaint)

        glassFill.color = Color.argb((.10f * amount * 255).toInt(), 255, 255, 255)
        glassStroke.color = Color.argb((.22f * amount * 255).toInt(), 255, 255, 255)
        glassStroke.strokeWidth = u * .6f
        for (i in 0 until GLASS) {
            glassX[i] += glassVx[i] * u * dt
            glassY[i] += glassVy[i] * u * dt
            if (glassX[i] < -5 * u) glassX[i] = w + 5 * u
            if (glassY[i] > h + 5 * u) glassY[i] = -5 * u
            val r = glassR[i] * u
            canvas.drawCircle(glassX[i], glassY[i], r, glassFill)
            canvas.drawCircle(glassX[i], glassY[i], r, glassStroke)
        }
    }

    private companion object {
        const val STEP_PX = 3f
        const val STARS = 110
        const val DROPS = 240
        const val GLASS = 28
        const val MAX_TUFTS = 600
        const val MAX_POLES = 16
        /** 창틀 이동 각속도(rad/s). 주기 약 170초, 230초. */
        const val DRIFT_X = 2 * PI / 170
        const val DRIFT_Y = 2 * PI / 230
        val WIRE_DX = floatArrayOf(-15f, 15f, -10f, 10f)
        val WIRE_DY = floatArrayOf(0f, 0f, 22f, 22f)
    }
}
