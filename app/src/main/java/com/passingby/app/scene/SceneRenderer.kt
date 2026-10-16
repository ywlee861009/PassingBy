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
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 창밖 풍경 렌더러. android.graphics.Canvas만 쓰므로 Compose에서도, SurfaceView의
 * 별도 그리기 스레드에서도 같은 코드로 그릴 수 있다.
 *
 * 그리기 루프(render) 안에서는 객체를 만들지 않는다. 예외는 색이 바뀔 때 그라디언트 셰이더를 새로 만드는 것뿐이다
 * (시간대 전환 1.4초 동안, 실제 시각 하늘을 다시 계산하는 1초마다, 근교를 지나며 도시화 단계가 바뀔 때).
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
    private val bldgPaint = Paint()
    private val litPaint = Paint().apply { strokeCap = Paint.Cap.SQUARE }
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

    private val cloudTones = IntArray(4)
    private val midTones = IntArray(4)
    private val nearTones = IntArray(4)
    private val fringeTones = IntArray(4)
    private val bedTones = IntArray(4)
    private val warmPts = FloatArray(MAX_WINDOWS * 2)
    private val coolPts = FloatArray(MAX_WINDOWS * 2)
    private val polesX = FloatArray(MAX_POLES)
    private val barrierOn = BooleanArray(MAX_BARRIER)
    /** 화면 폭의 절반(장면 단위). 사물의 레이어 좌표를 세계 위치로 환산할 때 쓴다. */
    private var halfWU = 0.0
    private val starX = FloatArray(STARS) { hash01(it, 1).toFloat() }
    private val starY = FloatArray(STARS) { Math.pow(hash01(it, 2), 1.4).toFloat() }
    private val starPhase = FloatArray(STARS) { (hash01(it, 3) * 6.28).toFloat() }
    private val starSize = FloatArray(STARS) { if (hash01(it, 4) < .15) 2f else 1.2f }
    // 내리는 비
    private val dropX = FloatArray(MAX_DROPS)
    private val dropY = FloatArray(MAX_DROPS)
    private val dropV = FloatArray(MAX_DROPS) { 900f + random.nextFloat() * 500f }
    private val rainPts = FloatArray(MAX_DROPS * 4)
    // 내리는 눈(원경·중경은 점, 근경은 짧은 줄)
    private val flakeX = FloatArray(MAX_FLAKES)
    private val flakeY = FloatArray(MAX_FLAKES)
    private val flakePhase = FloatArray(MAX_FLAKES) { random.nextFloat() * 6.28f }
    private val flakeFar = FloatArray(MAX_FLAKES * 2)
    private val flakeMid = FloatArray(MAX_FLAKES * 2)
    private val flakeNear = FloatArray(MAX_FLAKES * 4)
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    // 유리의 물방울
    private val gAlive = BooleanArray(GLASS_DROPS)
    private val gSlide = BooleanArray(GLASS_DROPS)
    private val gx = FloatArray(GLASS_DROPS)
    private val gy = FloatArray(GLASS_DROPS)
    private val gr = FloatArray(GLASS_DROPS)
    private val gAge = FloatArray(GLASS_DROPS)
    private val gLife = FloatArray(GLASS_DROPS)
    private val gVy = FloatArray(GLASS_DROPS)
    private val gTx = FloatArray(GLASS_DROPS)
    private val gTy = FloatArray(GLASS_DROPS)
    private var gSpawn = 0f
    // 유리에 붙은 눈송이
    private val sAlive = BooleanArray(GLASS_FLAKES)
    private val sx = FloatArray(GLASS_FLAKES)
    private val sy = FloatArray(GLASS_FLAKES)
    private val sr = FloatArray(GLASS_FLAKES)
    private val sAge = FloatArray(GLASS_FLAKES)
    private val sLife = FloatArray(GLASS_FLAKES)
    private var sSpawn = 0f
    private val glassHi = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val snowGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pilePath = Path()
    private val pilePaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        drawLand(canvas, p)
        drawPoles(canvas, p)
        drawFalling(canvas, p)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignettePaint)
        drawGlassWeather(canvas, p)
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
        halfWU = w / layout.u / 2.0
        vignettePaint.shader = RadialGradient(
            w / 2, h / 2, outer,
            intArrayOf(0, 0, 0x47000000), floatArrayOf(0f, inner / outer, 1f), Shader.TileMode.CLAMP,
        )
        shaderLayoutValid = false
        for (i in 0 until MAX_DROPS) {
            dropX[i] = random.nextFloat() * (w + 300 * layout.u)
            dropY[i] = random.nextFloat() * h
        }
        for (i in 0 until MAX_FLAKES) {
            flakeX[i] = random.nextFloat() * (w + 80 * layout.u)
            flakeY[i] = random.nextFloat() * h
        }
        gAlive.fill(false)
        sAlive.fill(false)
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
        val cb = mix(p.c[Palette.CLOUD], p.c[Palette.HAZE], .15f)
        cloudTones[TONE_BASE] = cb
        cloudTones[TONE_DARK] = mix(cb, p.c[Palette.TOP], .22f)
        cloudTones[TONE_LIGHT] = mix(cb, Color.WHITE, .35f)
        cloudTones[TONE_LIGHT_BOOST] = cloudTones[TONE_LIGHT]
        val alpha = (p.f[Palette.CLOUD_A] * 255).toInt()
        for (k in k0..k1) {
            val n = (if (hash01(k, 901) < .55) 1 else 0) + (if (hash01(k, 902) < .2) 1 else 0)
            for (j in 0 until n) {
                val key = k * 4 + j
                val hPx = (26 + hash01(key, 905) * 30).toFloat() * u
                val y = top + hash01(key, 904).toFloat() * (bot - top)
                val x = (((k + hash01(key, 903)) * c - off) * u).toFloat()
                val s = sprites.clouds[(hash01(key, 906) * sprites.clouds.size).toInt()]
                drawSprite(canvas, s, x, y + hPx / 2, hPx, false, cloudTones, alpha, spritePaint, dst)
                if (state.showBoxes) canvas.drawRect(dst, boxPaint)
            }
        }
    }

    /**
     * 지면 풍경. 도시화 정도에 따라 시골 사물과 도시 건물이 위치마다 섞인다.
     *
     * - 지형 레이어 넷은 늘 그리고, 색만 화면 가운데의 도시화 정도만큼 도시 쪽으로 옮긴다.
     * - 건물은 같은 속도의 지형 앞에 선다(원경 빌딩 ↔ 먼 언덕, 아파트 ↔ 중경 언덕, 상가 ↔ 근경).
     *   각 건물 레이어의 바닥선이 짝이 되는 지형의 가장 낮은 능선보다 아래라 건물이 뜨지 않는다.
     * - 나무·집·건물·풀·방음벽은 각자 자기 위치의 도시화 정도로 있을지 없을지 정한다.
     */
    private fun drawLand(canvas: Canvas, p: Palette) {
        if (appliedMode == RenderMode.TILES) {
            // 비교용 타일 방식은 시골만 지원한다.
            drawCountry(canvas, p)
            return
        }
        val d = state.distanceU
        // 색에 쓰는 값은 64단계로 끊어, 근교를 지나는 동안 그라디언트 셰이더를 매 프레임 새로 만들지 않게 한다.
        val uc = Math.round(state.urban.at(d) * 64) / 64f
        val farA = layers[Scenery.FAR_A]
        val farB = layers[Scenery.FAR_B]
        val mid = layers[Scenery.MID]
        val near = layers[Scenery.NEAR]

        drawTerrain(canvas, Scenery.FAR_A, d * farA.speed, mix(layerColor(farA, p), mix(p.c[Palette.FAR], p.c[Palette.HAZE], .55f), uc), farA.mist, p)
        drawTerrain(canvas, Scenery.FAR_B, d * farB.speed, desaturate(layerColor(farB, p), .25f * uc), farB.mist, p)
        val fb = mix(desaturate(p.c[Palette.FAR], .55f), p.c[Palette.HAZE], .4f)
        drawCityLayer(canvas, CityScenery.far, d * CityScenery.far.speed, fb, FAR_LEAD, p)

        val midOff = d * mid.speed
        drawTerrain(canvas, Scenery.MID, midOff, desaturate(layerColor(mid, p), .25f * uc), mid.mist, p)
        computeTones(spriteColor(mid, p), .6f, p, midTones)
        // 도시 안 언덕에도 나무가 조금 남는다.
        drawObjects(canvas, mid, mid.objects!!, midOff, midTones, p, GATE_RURAL, .8f)
        val mb = mix(desaturate(p.c[Palette.MID], .7f), p.c[Palette.FAR], .15f)
        drawCityLayer(canvas, CityScenery.mid, d * CityScenery.mid.speed, mb, 0.0, p)

        val nb = desaturate(p.c[Palette.NEAR], .45f)
        val nearCol = mix(layerColor(near, p), nb, uc)
        val nearOff = d * near.speed
        drawTerrain(canvas, Scenery.NEAR, nearOff, nearCol, 0f, p)
        drawCityLayer(canvas, CityScenery.near, d * CityScenery.near.speed, mix(desaturate(p.c[Palette.MID], .6f), nb, .35f), 0.0, p)
        computeTones(mix(nearCol, p.c[Palette.TREE], .45f), .8f, p, fringeTones)
        drawFringe(canvas, near, near.speed, nearOff, 34.0, 8f, 15f, 303, 0f, fringeTones, GATE_RURAL)
        drawFringe(canvas, near, near.speed, nearOff, 40.0, 7f, 13f, 303, 0f, fringeTones, GATE_URBAN)
        computeTones(spriteColor(near, p), 1f, p, nearTones)
        drawObjects(canvas, near, near.objects!!, nearOff, nearTones, p, GATE_RURAL, 1f)
        drawObjects(canvas, near, Scenery.cityStreetTrees, nearOff, nearTones, p, GATE_URBAN, 1f)
        drawBed(canvas, p)
        drawBarrier(canvas, p)
    }

    /** 시골만 그린다. 비교용 타일 방식에서 쓴다. */
    private fun drawCountry(canvas: Canvas, p: Palette) {
        val tilesOk = appliedMode == RenderMode.TILES && ensureTiles()
        for (i in layers.indices) {
            val l = layers[i]
            val offU = state.distanceU * l.speed
            if (tilesOk) {
                drawTiles(canvas, i, l)
                continue
            }
            drawTerrain(canvas, i, offU, layerColor(l, p), l.mist, p)
            if (i == Scenery.NEAR) {
                computeTones(mix(p.c[Palette.NEAR], p.c[Palette.TREE], .45f), .8f, p, fringeTones)
                drawFringe(canvas, l, l.speed, offU, 34.0, 8f, 15f, 303, 0f, fringeTones, GATE_NONE)
            }
            val rule = l.objects ?: continue
            val tones = if (i == Scenery.MID) midTones else nearTones
            computeTones(spriteColor(l, p), if (l.spriteHaze > 0f) .6f else 1f, p, tones)
            drawObjects(canvas, l, rule, offU, tones, p, GATE_NONE, 1f)
        }
        drawBed(canvas, p)
    }

    /**
     * 레이어 좌표 [wx]에 있는 사물의 도시화 정도. 그 사물이 화면 가운데를 지날 때의 카메라 위치로 환산해 읽으므로,
     * 먼 레이어일수록 같은 화면 폭에 더 넓은 세계가 담겨 도시가 먼저 보이고 늦게 사라진다.
     * [lead]가 있으면 앞뒤로 그만큼 범위를 넓힌다(멀리서도 보이는 고층 건물).
     */
    private fun urbanAt(speed: Double, wx: Double, lead: Double): Float {
        val x = (wx - halfWU) / speed
        val f = state.urban
        return if (lead <= 0.0) f.at(x) else max(f.at(x - lead), f.at(x + lead))
    }

    /** 사물이 있을지. [h]는 사물마다 고정된 0~1 해시값. */
    private fun present(gate: Int, strength: Float, urban: Float, h: Double): Boolean = when (gate) {
        GATE_RURAL -> h >= urban * strength
        GATE_URBAN -> h < urban
        else -> true
    }

    private fun drawTerrain(canvas: Canvas, i: Int, offU: Double, col: Int, mist: Float, p: Palette) {
        val l = layers[i]
        val paint = layerPaints[i]
        updateLayerPaint(i, l, col, mist, p, paint)
        if (appliedMode == RenderMode.BAKED) {
            bakers[i].draw(canvas, offU, paint, frameCount)
        } else {
            drawTerrainSpan(canvas, l, offU, 0f, layout.w.toFloat(), paint)
        }
    }

    private fun updateLayerPaint(i: Int, l: TerrainLayer, col: Int, mist: Float, p: Palette, paint: Paint) {
        if (mist <= 0f) {
            paint.shader = null
            paint.color = col
            layerKeyA[i] = 0
            layerKeyB[i] = 0
            return
        }
        val end = mix(col, p.c[Palette.HAZE], mist)
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

    private fun drawObjects(
        canvas: Canvas, l: TerrainLayer, rule: ObjectRule, offU: Double, tones: IntArray, p: Palette,
        gate: Int, strength: Float,
    ) {
        val u = layout.u
        val c = rule.chunkUnits
        val k0 = floor(offU / c).toInt() - 1
        val k1 = floor((offU + layout.w / u) / c).toInt() + 1
        val lit = p.f[Palette.LIT]
        val w = layout.w.toFloat()
        for (k in k0..k1) {
            val n = Placement.count(l, rule, k)
            for (j in 0 until n) {
                Placement.place(l, rule, k, j, sprites, placed)
                if (gate != GATE_NONE &&
                    !present(gate, strength, urbanAt(l.speed, placed.wx, 0.0), hash01(placed.key, l.seed + 41))
                ) continue
                val s = placed.sprite
                val hPx = placed.heightU * u
                val half = s.base.width * hPx / s.base.height / 2
                val cx = ((placed.wx - offU) * u).toFloat()
                if (cx + half < 0 || cx - half > w) continue
                val yb = terrainY(l, placed.wx) + rule.sink * u
                drawSprite(canvas, s, cx, yb, hPx, placed.flip, tones, 255, spritePaint, dst)
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

        computeTones(p.c[Palette.GROUND], .7f, p, bedTones)
        drawFringe(canvas, null, Scenery.BED_SPEED, state.distanceU * Scenery.BED_SPEED, 26.0, 9f, 16f, 311, by, bedTones, GATE_NONE)
    }

    /**
     * 풀 다발을 늘어놓아 지형 경계선이나 선로 가장자리의 매끈한 선을 깬다. layer가 null이면 bedY에 놓는다.
     * @param speed 이 줄의 패럴랙스 속도. [gate]로 도시화 정도에 따라 거를 때 위치 환산에 쓴다.
     */
    private fun drawFringe(
        canvas: Canvas, layer: TerrainLayer?, speed: Double, offU: Double, chunkU: Double,
        minH: Float, maxH: Float, seed: Int, bedY: Float, tones: IntArray, gate: Int,
    ) {
        val u = layout.u
        val w = layout.w.toFloat()
        val k0 = floor(offU / chunkU).toInt() - 1
        val k1 = floor((offU + layout.w / u) / chunkU).toInt() + 1
        val tufts = sprites.tufts
        for (k in k0..k1) {
            val n = 1 + (hash01(k, seed) * 3).toInt()
            for (j in 0 until n) {
                val key = k * 8 + j
                val wx = (k + hash01(key, seed + 1)) * chunkU
                val x = ((wx - offU) * u).toFloat()
                if (x < -40 * u || x > w + 40 * u) continue
                if (gate != GATE_NONE && !present(gate, 1f, urbanAt(speed, wx, 0.0), hash01(key, seed + 9))) continue
                val hPx = (minH + hash01(key, seed + 2) * (maxH - minH)).toFloat() * u
                val y = (if (layer != null) terrainY(layer, wx) else bedY) + 2 * u
                drawSprite(canvas, tufts[(hash01(key, seed + 3) * tufts.size).toInt()], x, y, hPx, false, tones, 255, spritePaint, dst)
            }
        }
    }

    /**
     * 도시 건물 레이어. 조각마다 건물 1~3채를 결정적으로 배치하고, 정면·그늘진 옆면·옥상 구조물·
     * 층 줄무늬를 직사각형으로 그린다. 밤에는 창문 불빛을 점(drawPoints) 묶음으로 한 번에 그린다.
     * 건물마다 자기 위치의 도시화 정도만큼의 확률로 서고, 근교일수록 낮아진다(아파트 단지는 높이 유지).
     * @param lead 도시화 정도를 읽을 때 앞뒤로 넓힐 범위(세계 단위). [urbanAt] 참고.
     */
    private fun drawCityLayer(canvas: Canvas, layer: CityLayer, offU: Double, base: Int, lead: Double, p: Palette) {
        val u = layout.u
        val c = layer.chunkUnits
        val k0 = floor(offU / c).toInt() - 1
        val k1 = floor((offU + layout.w / u) / c).toInt() + 1
        val yb = layout.yAt(layer.base) + 2 * u
        val sunlight = mix(p.c[Palette.SUN], p.c[Palette.BOT], .5f)
        val top = p.c[Palette.TOP]
        val lit = p.f[Palette.LIT]
        val w = layout.w.toFloat()
        val s = layer.seed
        val fl = layer.floorU * u
        val cw = layer.colU * u
        for (k in k0..k1) {
            val dense = valueNoise(k * .05 + .2, s + 9)
            val n = 1 + (hash01(k, s) * layer.perChunk).toInt()
            val aptH = 40 + hash01(k, s + 7) * 22
            val isApt = layer.apt > 0f && valueNoise(k * .08 + .5, s + 11) < layer.apt
            for (j in 0 until n) {
                val key = k * 8 + j
                val r1 = hash01(key, s + 1)
                val r2 = hash01(key, s + 2)
                val bw = if (isApt) 54 + r2 * 36 else layer.minW + r2 * (layer.maxW - layer.minW)
                val urban = urbanAt(layer.speed, (k + r1) * c + bw / 2, lead)
                if (hash01(key, s + 6) >= urban) continue
                val r3 = hash01(key, s + 3)
                val r4 = hash01(key, s + 4)
                val r5 = hash01(key, s + 5)
                val bh = if (isApt) {
                    aptH * (.92 + r3 * .16)
                } else {
                    (layer.minH + r3.pow(1.6) * (layer.maxH - layer.minH)) * (.55 + dense * .9) * (.5 + .5 * urban)
                }
                val x = (((k + r1) * c - offU) * u).toFloat()
                val bwPx = (bw * u).toFloat()
                val bhPx = (bh * u).toFloat()
                if (x + bwPx < 0 || x > w) continue
                val yt = yb - bhPx
                val col = mix(base, if (r5 > .5) sunlight else top, (abs(r5 - .5) * .22).toFloat())
                val side = mix(col, top, .32f)
                val sw = min(bwPx * .2f, 7 * u)

                bldgPaint.color = col
                canvas.drawRect(x, yt, x + bwPx, yb, bldgPaint)
                if (layer.style != WindowStyle.SHOP) {
                    if (!isApt && r4 < .16 && bh > 55) {
                        // 첨탑·안테나
                        canvas.drawRect(x + bwPx * .5f - .7f * u, yt - 14 * u, x + bwPx * .5f + .7f * u, yt, bldgPaint)
                    } else if (!isApt && r4 < .42) {
                        // 계단식 옥상
                        val sx = x + bwPx * .18f
                        val swd = bwPx * .62f
                        val sh = min(10 * u, bhPx * .12f)
                        canvas.drawRect(sx, yt - sh, sx + swd, yt, bldgPaint)
                        bldgPaint.color = side
                        canvas.drawRect(sx, yt - sh, sx + min(swd * .2f, 5 * u), yt, bldgPaint)
                    } else {
                        // 옥탑·물탱크
                        val rx = x + bwPx * if (isApt) .08f else .62f
                        canvas.drawRect(rx, yt - 3.5f * u, rx + bwPx * if (isApt) .14f else .2f, yt, bldgPaint)
                    }
                }
                bldgPaint.color = side
                canvas.drawRect(x, yt, x + sw, yb, bldgPaint)
                bldgPaint.color = mix(col, sunlight, .28f)
                canvas.drawRect(x + sw, yt, x + bwPx, yt + max(1f, .8f * u), bldgPaint)

                val floors = ((bhPx - 2 * u) / fl).toInt()
                val cols = max(1, ((bwPx - sw - 2 * u) / cw).toInt())
                val x0 = x + sw + (bwPx - sw - cols * cw) / 2
                if (layer.style != WindowStyle.DOTS && floors > 0) {
                    bldgPaint.color = mix(col, p.c[Palette.BOT], .16f)
                    bldgPaint.alpha = ((1 - min(1f, lit) * .6f) * 255).toInt()
                    for (f in 0 until floors) {
                        val sy = yt + 2 * u + f * fl + fl * .3f
                        canvas.drawRect(x0, sy, x0 + cols * cw, sy + max(1f, fl * .2f), bldgPaint)
                    }
                }
                if (layer.style == WindowStyle.SHOP) {
                    val sg = CityScenery.signs[(r4 * CityScenery.signs.size).toInt()]
                    bldgPaint.color = if (lit > .02f) mix(mix(sg, col, .6f), sg, lit) else mix(sg, col, .62f)
                    canvas.drawRect(x + sw + u, yb - fl * 1.35f, x + bwPx - u, yb - fl * .8f, bldgPaint)
                }
                if (lit > .02f && floors > 0) drawLitWindows(canvas, layer.style, key, floors, cols, x0, yt, fl, cw, isApt, lit)
            }
        }
    }

    /** 창문 불빛. 건물마다 따뜻한 색·흰색 두 묶음의 점으로 그려 그리기 호출 수를 줄인다. */
    private fun drawLitWindows(
        canvas: Canvas, style: WindowStyle, key: Int, floors: Int, cols: Int,
        x0: Float, yt: Float, fl: Float, cw: Float, isApt: Boolean, lit: Float,
    ) {
        val u = layout.u
        val threshold = lit * if (isApt) .5f else .4f
        var nWarm = 0
        var nCool = 0
        for (f in 0 until floors) {
            if (hash01(key * 31 + f, 4) > .86) continue // 통째로 꺼진 층
            val cy = yt + 2 * u + f * fl + fl / 2
            for (c in 0 until cols) {
                val hv = hash01(key * 131 + f * 17 + c, 3)
                if (hv >= threshold) continue
                val cx = x0 + c * cw + cw / 2
                if ((hv * 97) % 1 < .7) {
                    if (nWarm < MAX_WINDOWS) {
                        warmPts[nWarm * 2] = cx
                        warmPts[nWarm * 2 + 1] = cy
                        nWarm++
                    }
                } else if (nCool < MAX_WINDOWS) {
                    coolPts[nCool * 2] = cx
                    coolPts[nCool * 2 + 1] = cy
                    nCool++
                }
            }
        }
        litPaint.strokeWidth = when (style) {
            WindowStyle.DOTS -> max(1f, .9f * u)
            WindowStyle.GRID -> 1.7f * u
            WindowStyle.SHOP -> 2.2f * u
        }
        val alpha = (min(1f, lit * 1.1f) * 255).toInt()
        if (nWarm > 0) {
            litPaint.color = WINDOW_COLOR
            litPaint.alpha = alpha
            canvas.drawPoints(warmPts, 0, nWarm * 2, litPaint)
        }
        if (nCool > 0) {
            litPaint.color = WINDOW_COOL
            litPaint.alpha = alpha
            canvas.drawPoints(coolPts, 0, nCool * 2, litPaint)
        }
    }

    /**
     * 선로 옆 방음벽: 반투명 판, 위아래 레일, 빠르게 지나가는 기둥.
     * 기둥 사이 칸 단위로 서며, 도시화가 [BARRIER_URBAN] 이상인 칸만 세운다. 그래서 근교에서 기둥 하나를 경계로 시작하고 끝난다.
     */
    private fun drawBarrier(canvas: Canvas, p: Palette) {
        val u = layout.u
        val by = layout.yAt(.93f)
        val top = by - 24 * u
        val off = state.distanceU * Scenery.BED_SPEED
        val spacing = 18.0
        val i0 = floor(off / spacing).toInt() - 1
        val count = min(MAX_BARRIER, floor((off + layout.w / u) / spacing).toInt() + 2 - i0)
        var any = false
        for (n in 0 until count) {
            val on = urbanAt(Scenery.BED_SPEED, (i0 + n + .5) * spacing, 0.0) >= BARRIER_URBAN
            barrierOn[n] = on
            any = any || on
        }
        if (!any) return
        // 판을 먼저 칠하고 레일·기둥을 위에 그린다. 판은 이어진 칸끼리 한 번에 칠한다.
        bldgPaint.color = Color.argb(((.10f + .06f * (1 - p.f[Palette.LIT])) * 255).toInt(), 220, 232, 240)
        var runStart = -1
        for (n in 0..count) {
            val on = n < count && barrierOn[n]
            if (on && runStart < 0) runStart = n
            if (!on && runStart >= 0) {
                val x0 = (((i0 + runStart) * spacing - off) * u).toFloat()
                val x1 = (((i0 + n) * spacing - off) * u).toFloat()
                canvas.drawRect(x0, top, x1, by, bldgPaint)
                runStart = -1
            }
        }
        bldgPaint.color = mix(p.c[Palette.POLE], p.c[Palette.HAZE], .25f)
        for (n in 0 until count) {
            if (!barrierOn[n]) continue
            val x0 = (((i0 + n) * spacing - off) * u).toFloat()
            val x1 = (((i0 + n + 1) * spacing - off) * u).toFloat()
            canvas.drawRect(x0, top, x1, top + 1.6f * u, bldgPaint)
            canvas.drawRect(x0, by - 3 * u, x1, by, bldgPaint)
            canvas.drawRect(x0 - .8f * u, top, x0 + .8f * u, by, bldgPaint)
            if (n == count - 1 || !barrierOn[n + 1]) canvas.drawRect(x1 - .8f * u, top, x1 + .8f * u, by, bldgPaint)
        }
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

    /** 창밖에 내리는 비·눈. 세기에 따라 개수·길이·굵기가 달라진다. */
    private fun drawFalling(canvas: Canvas, p: Palette) {
        val dt = state.lastDt
        val u = layout.u
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()

        val rain = state.rainAmount
        if (rain > .01f) {
            val n = (MAX_DROPS * rain).toInt()
            val vx = -(220 + state.speed * .9).toFloat() * u
            val k = .012f + .012f * rain
            val fade = min(1f, rain * 3f)
            rainPaint.color = Color.argb(((.2f + .25f * rain) * fade * 255).toInt(), 225, 232, 245)
            rainPaint.strokeWidth = (.8f + .5f * rain) * u
            for (i in 0 until n) {
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
            canvas.drawLines(rainPts, 0, n * 4, rainPaint)
        }

        val snow = state.snowAmount
        if (snow > .01f) {
            val n = (MAX_FLAKES * snow).toInt()
            val t = state.elapsedSec
            var nf = 0
            var nm = 0
            var nn = 0
            for (i in 0 until n) {
                // 다섯 개 중 둘은 원경, 둘은 중경, 하나는 근경. 가까울수록 크고 빠르게 스쳐 간다.
                val d = when (i % 5) {
                    0, 1 -> 0
                    2, 3 -> 1
                    else -> 2
                }
                val vx = (-(state.speed * FLAKE_PAR[d] + 12) * u + sin(t * 1.3 + flakePhase[i]) * 10 * u).toFloat()
                val vy = FLAKE_FALL[d] * u
                flakeX[i] += vx * dt
                flakeY[i] += vy * dt
                if (flakeX[i] < -30 * u) {
                    flakeX[i] = w + random.nextFloat() * 80 * u
                    flakeY[i] = random.nextFloat() * h
                }
                if (flakeY[i] > h + 5 * u) {
                    flakeY[i] = -5 * u
                    flakeX[i] = random.nextFloat() * (w + 80 * u)
                }
                when (d) {
                    0 -> {
                        flakeFar[nf * 2] = flakeX[i]
                        flakeFar[nf * 2 + 1] = flakeY[i]
                        nf++
                    }
                    1 -> {
                        flakeMid[nm * 2] = flakeX[i]
                        flakeMid[nm * 2 + 1] = flakeY[i]
                        nm++
                    }
                    else -> {
                        val o = nn * 4
                        flakeNear[o] = flakeX[i]
                        flakeNear[o + 1] = flakeY[i]
                        flakeNear[o + 2] = flakeX[i] - vx * NEAR_BLUR
                        flakeNear[o + 3] = flakeY[i] - vy * NEAR_BLUR
                        nn++
                    }
                }
            }
            val fade = min(1f, snow * 3f)
            val col = mix(p.c[Palette.SNOW_WHITE], Color.WHITE, .55f)
            flakePaint.color = col
            flakePaint.alpha = (.5f * fade * 255).toInt()
            flakePaint.strokeWidth = 1.3f * u
            canvas.drawPoints(flakeFar, 0, nf * 2, flakePaint)
            flakePaint.alpha = (.72f * fade * 255).toInt()
            flakePaint.strokeWidth = 2.2f * u
            canvas.drawPoints(flakeMid, 0, nm * 2, flakePaint)
            flakePaint.alpha = (.85f * fade * 255).toInt()
            flakePaint.strokeWidth = 3f * u
            canvas.drawLines(flakeNear, 0, nn * 4, flakePaint)
        }
    }

    /**
     * 유리 위의 날씨. 비: 물방울이 생겼다 마르고, 소나기 때는 큰 물방울이 맞바람에 밀려 비스듬히 흘러내린다.
     * 눈: 눈송이가 붙었다 녹고, 창 아래쪽에 눈이 살짝 쌓인다(가장자리일수록 조금 더).
     */
    private fun drawGlassWeather(canvas: Canvas, p: Palette) {
        val dt = state.lastDt
        val u = layout.u
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()

        val wet = state.glassWet
        val rain = state.rainAmount
        gSpawn += dt * wet * (6 + 28 * rain)
        while (gSpawn >= 1f) {
            gSpawn -= 1f
            spawnGlassDrop(rain, w, h, u)
        }
        val drift = ((state.speed * .06 + 8) * u * dt).toFloat()
        for (i in 0 until GLASS_DROPS) {
            if (!gAlive[i]) continue
            gAge[i] += dt
            if (!gSlide[i] && gr[i] > 2.2f * u && rain > .55f && random.nextFloat() < dt * .35f) {
                gSlide[i] = true
                gTx[i] = gx[i]
                gTy[i] = gy[i]
                gVy[i] = 0f
            }
            val a: Float
            if (gSlide[i]) {
                gVy[i] = min(gVy[i] + 70 * u * dt, 160 * u)
                gy[i] += gVy[i] * dt
                gx[i] -= drift
                gr[i] -= .25f * u * dt
                if (gy[i] > h + 10 * u || gr[i] < .5f * u) {
                    gAlive[i] = false
                    continue
                }
                a = 1f
                glassTrail.color = Color.argb((.13f * a * 255).toInt(), 255, 255, 255)
                glassTrail.strokeWidth = gr[i] * .55f
                canvas.drawLine(gTx[i], gTy[i], gx[i], gy[i] - gr[i], glassTrail)
            } else {
                if (gAge[i] > gLife[i]) {
                    gAlive[i] = false
                    continue
                }
                a = min(1f, gAge[i] * 2f) * min(1f, (gLife[i] - gAge[i]) / 1.5f) * min(1f, wet * 2f)
            }
            val r = gr[i]
            glassFill.color = Color.argb((.10f * a * 255).toInt(), 255, 255, 255)
            glassStroke.color = Color.argb((.24f * a * 255).toInt(), 255, 255, 255)
            glassStroke.strokeWidth = .5f * u
            glassHi.color = Color.argb((.5f * a * 255).toInt(), 255, 255, 255)
            canvas.drawCircle(gx[i], gy[i], r, glassFill)
            canvas.drawCircle(gx[i], gy[i], r, glassStroke)
            canvas.drawCircle(gx[i] - r * .35f, gy[i] - r * .35f, r * .3f, glassHi)
        }

        val gs = state.glassSnow
        sSpawn += dt * gs * (4 + 22 * state.snowAmount)
        while (sSpawn >= 1f) {
            sSpawn -= 1f
            spawnGlassFlake(w, h, u)
        }
        val flakeCol = mix(p.c[Palette.SNOW_WHITE], Color.WHITE, .6f)
        for (i in 0 until GLASS_FLAKES) {
            if (!sAlive[i]) continue
            sAge[i] += dt
            if (sAge[i] > sLife[i]) {
                sAlive[i] = false
                continue
            }
            // 붙자마자 선명했다가 마지막 6초 동안 녹아 사라진다.
            val a = min(1f, sAge[i] * 3f) * min(1f, (sLife[i] - sAge[i]) / 6f)
            snowGlassPaint.color = flakeCol
            snowGlassPaint.alpha = (.28f * a * 255).toInt()
            canvas.drawCircle(sx[i], sy[i], sr[i] * 1.5f, snowGlassPaint)
            snowGlassPaint.alpha = (.75f * a * 255).toInt()
            canvas.drawCircle(sx[i], sy[i], sr[i] * .7f, snowGlassPaint)
        }
        if (gs > .02f) {
            val pileH = gs * .05f * layout.g
            val path = pilePath
            path.rewind()
            path.moveTo(0f, h)
            val edge = w * .12f
            var x = 0f
            while (true) {
                val corner = max(0f, 1 - x / edge) + max(0f, 1 - (w - x) / edge)
                val y = h - pileH * (.55f + .45f * valueNoise(x / (45.0 * u), 97).toFloat()) * (1 + .6f * corner)
                path.lineTo(x, y)
                if (x >= w) break
                x = min(x + 10 * u, w)
            }
            path.lineTo(w, h)
            path.close()
            pilePaint.color = flakeCol
            pilePaint.alpha = 235
            canvas.drawPath(path, pilePaint)
        }
    }

    private fun spawnGlassDrop(rain: Float, w: Float, h: Float, u: Float) {
        for (i in 0 until GLASS_DROPS) {
            if (gAlive[i]) continue
            gAlive[i] = true
            gSlide[i] = false
            gx[i] = random.nextFloat() * w
            gy[i] = random.nextFloat() * h * .95f
            gr[i] = (.7f + random.nextFloat() * (1.2f + 2.4f * rain)) * u
            gAge[i] = 0f
            gLife[i] = 5f + random.nextFloat() * 12f
            return
        }
    }

    private fun spawnGlassFlake(w: Float, h: Float, u: Float) {
        for (i in 0 until GLASS_FLAKES) {
            if (sAlive[i]) continue
            sAlive[i] = true
            sx[i] = random.nextFloat() * w
            sy[i] = random.nextFloat() * h * .92f
            sr[i] = (.7f + random.nextFloat() * 2f) * u
            sAge[i] = 0f
            sLife[i] = 14f + random.nextFloat() * 26f
            return
        }
    }

    private companion object {
        const val STEP_PX = 3f
        const val STARS = 110
        const val MAX_DROPS = 420
        const val MAX_FLAKES = 600
        const val GLASS_DROPS = 70
        const val GLASS_FLAKES = 90
        /** 근경 눈송이를 속도 방향으로 늘려 그리는 길이(초). */
        const val NEAR_BLUR = .018f
        val FLAKE_PAR = doubleArrayOf(.12, .45, 1.1)
        val FLAKE_FALL = floatArrayOf(22f, 34f, 52f)
        const val MAX_WINDOWS = 1024
        const val MAX_POLES = 16
        const val MAX_BARRIER = 256
        /** 이 도시화 정도부터 방음벽이 선다. */
        const val BARRIER_URBAN = .6f
        /** 원경 빌딩이 도시 밖으로 더 보이는 범위(세계 단위). 고속 순항으로 약 40초. */
        const val FAR_LEAD = 12000.0
        const val GATE_NONE = 0
        /** 도시화 정도가 높을수록 드물어진다(나무·집·풀). */
        const val GATE_RURAL = 1
        /** 도시화 정도가 높을수록 많아진다(가로수·도시 풀). */
        const val GATE_URBAN = 2
        /** 창틀 이동 각속도(rad/s). 주기 약 170초, 230초. */
        const val DRIFT_X = 2 * PI / 170
        const val DRIFT_Y = 2 * PI / 230
        val WIRE_DX = floatArrayOf(-15f, 15f, -10f, 10f)
        val WIRE_DY = floatArrayOf(0f, 0f, 22f, 22f)
    }
}
