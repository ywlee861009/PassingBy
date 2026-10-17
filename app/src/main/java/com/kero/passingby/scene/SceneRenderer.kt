package com.kero.passingby.scene

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
    private var layers = Scenery.layers
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "scene-baker").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val spanPainter = SpanPainter { c, l, offU, x0, x1, p -> drawTerrainSpan(c, l, offU, x0, x1, p) }
    private var bakers = Array(layers.size) { ChunkBaker(layers[it], executor, spanPainter) }
    private var appliedTheme: Theme = Themes.KOREA

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
    private val platformPaint = Paint()
    private val housePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val housePath = Path()
    private val waterPaint = Paint()
    private var waterKeyA = 0
    private var waterKeyB = 0
    private var waterTop = 0f
    private val fieldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fieldPts = FloatArray(FIELD_PTS)
    private val trussPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.SQUARE }
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val landmarkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val landmarkPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val signPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val signText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val signSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
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
            // 아래에서 올라온 벽 패널이 창을 가린 만큼 풍경을 위로 올려 땅이 보이게 한다. 옆에서 들어올 때는 그대로 둔다.
            val shift = if (state.seatFromEnd) 0f else (o.bottom - (h - state.seatEdgePx)).coerceIn(0f, o.height())
            canvas.save()
            canvas.translate(dx, dy)
            canvas.save()
            canvas.clipPath(f.glassPath)
            canvas.save()
            canvas.translate(o.left, o.top - shift)
            drawScene(canvas, o.width().toInt(), o.height().toInt(), p)
            canvas.restore()
            f.drawGlass(canvas, p)
            canvas.restore()
            f.drawFrame(canvas, p)
            f.drawSeat(canvas, state.seatFromEnd, state.seatEdgePx)
            canvas.restore()
        } else {
            drawScene(canvas, w, h, p)
        }
        lastDrawNanos = System.nanoTime() - t0
    }

    /** 창밖 풍경. 창틀이 있으면 창 구멍 크기로, 없으면 화면 전체 크기로 그린다. */
    private fun drawScene(canvas: Canvas, w: Int, h: Int, p: Palette) {
        if (state.theme !== appliedTheme) applyTheme()
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
        drawStations(canvas, p)
        drawPoles(canvas, p)
        drawTruss(canvas, p)
        drawFalling(canvas, p)
        drawTunnels(canvas, p)
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

    /** 테마가 바뀌면 지형 레이어가 달라지므로 조각 굽는 쪽을 새로 만든다. */
    private fun applyTheme() {
        val t = state.theme
        appliedTheme = t
        for (b in bakers) b.release()
        layers = t.layers
        bakers = Array(layers.size) { ChunkBaker(layers[it], executor, spanPainter) }
        layerKeyA.fill(0)
        layerKeyB.fill(0)
        tileSet = null
        if (layout.w > 0) applyMode()
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
        val th = state.theme
        val d = state.distanceU
        // 색에 쓰는 값은 64단계로 끊어, 근교를 지나는 동안 그라디언트 셰이더를 매 프레임 새로 만들지 않게 한다.
        val uc = Math.round(state.urban.at(d) * 64) / 64f
        val farA = layers[Scenery.FAR_A]
        val farB = layers[Scenery.FAR_B]
        val mid = layers[Scenery.MID]
        val near = layers[Scenery.NEAR]

        drawTerrain(canvas, Scenery.FAR_A, d * farA.speed, mix(layerColor(farA, p), mix(p.c[Palette.FAR], p.c[Palette.HAZE], .55f), uc), farA.mist, p)
        drawLandmarks(canvas, p, Scenery.FAR_A)
        drawTerrain(canvas, Scenery.FAR_B, d * farB.speed, desaturate(layerColor(farB, p), .25f * uc), farB.mist, p)
        val fb = mix(desaturate(p.c[Palette.FAR], .55f), p.c[Palette.HAZE], .4f)
        drawCityLayer(canvas, th.cityFar, d * th.cityFar.speed, fb, FAR_LEAD, p)
        // 먼 랜드마크(탑 등)는 원경 빌딩 앞에 세워 스카이라인에 묻히지 않게 한다.
        drawLandmarks(canvas, p, Scenery.FAR_B)

        val midOff = d * mid.speed
        drawTerrain(canvas, Scenery.MID, midOff, desaturate(layerColor(mid, p), .25f * uc), mid.mist, p)
        drawLandmarks(canvas, p, Scenery.MID)
        computeTones(spriteColor(mid, p), .6f, p, midTones)
        // 도시 안 언덕에도 나무가 조금 남는다.
        mid.objects?.let { drawObjects(canvas, mid, it, midOff, midTones, p, GATE_RURAL, .8f) }
        drawWater(canvas, p, mid.speed, midOff)
        val mb = mix(desaturate(p.c[Palette.MID], .7f), p.c[Palette.FAR], .15f)
        drawCityLayer(canvas, th.cityMid, d * th.cityMid.speed, mb, 0.0, p)

        val nb = desaturate(p.c[Palette.NEAR], .45f)
        val nearCol = mix(layerColor(near, p), nb, uc)
        val nearOff = d * near.speed
        drawTerrain(canvas, Scenery.NEAR, nearOff, nearCol, 0f, p)
        drawFields(canvas, p, near.speed, nearOff)
        drawCityLayer(canvas, th.cityNear, d * th.cityNear.speed, mix(desaturate(p.c[Palette.MID], .6f), nb, .35f), 0.0, p)
        computeTones(mix(nearCol, p.c[Palette.TREE], .45f), .8f, p, fringeTones)
        drawFringe(canvas, near, near.speed, nearOff, 34.0, 8f, 15f, 303, 0f, fringeTones, GATE_RURAL)
        drawFringe(canvas, near, near.speed, nearOff, 40.0, 7f, 13f, 303, 0f, fringeTones, GATE_URBAN)
        computeTones(spriteColor(near, p), 1f, p, nearTones)
        near.objects?.let { drawObjects(canvas, near, it, nearOff, nearTones, p, GATE_RURAL, 1f) }
        drawObjects(canvas, near, th.streetTrees, nearOff, nearTones, p, GATE_URBAN, 1f)
        drawBed(canvas, p)
        drawRiverCrossing(canvas, p)
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
                if (gate != GATE_NONE) {
                    if (!present(gate, strength, urbanAt(l.speed, placed.wx, 0.0), hash01(placed.key, l.seed + 41))) continue
                    if (submerged(l.speed, placed.wx)) continue
                }
                val s = placed.sprite
                val hPx = placed.heightU * u
                val half = s.base.width * hPx / s.base.height / 2
                val cx = ((placed.wx - offU) * u).toFloat()
                if (cx + half < 0 || cx - half > w) continue
                val yb = terrainY(l, placed.wx) + rule.sink * u
                if (placed.house && gate != GATE_NONE) {
                    drawHouse(canvas, cx, yb, hPx, placed.key, l.spriteHaze, p)
                    continue
                }
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
                if (gate != GATE_NONE && submerged(speed, wx)) continue
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
            val aptH = layer.aptH + hash01(k, s + 7) * layer.aptVar
            val isApt = layer.apt > 0f && valueNoise(k * .08 + .5, s + 11) < layer.apt
            for (j in 0 until n) {
                val key = k * 8 + j
                val r1 = hash01(key, s + 1)
                val r2 = hash01(key, s + 2)
                val bw = if (isApt) 54 + r2 * 36 else layer.minW + r2 * (layer.maxW - layer.minW)
                val urban = urbanAt(layer.speed, (k + r1) * c + bw / 2, lead)
                if (hash01(key, s + 6) >= urban) continue
                if (cityBlocked(layer.speed, (k + r1) * c + bw / 2)) continue
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
                val tone = layer.facades?.let { f -> themed(f[(hash01(key, s + 8) * f.size).toInt()], p, .25f) } ?: base
                val col = mix(tone, if (r5 > .5) sunlight else top, (abs(r5 - .5) * .22).toFloat())
                val side = mix(col, top, .32f)
                val sw = min(bwPx * .2f, 7 * u)

                bldgPaint.color = col
                canvas.drawRect(x, yt, x + bwPx, yb, bldgPaint)
                if (layer.cap == RoofCap.TILE) {
                    // 남유럽 시가지: 낮은 주황 기와 박공.
                    bldgPaint.color = themed(TILE_ROOF, p, .2f)
                    housePath.rewind()
                    housePath.moveTo(x - u, yt)
                    housePath.lineTo(x + bwPx * .5f, yt - min(bwPx * .22f, 9 * u))
                    housePath.lineTo(x + bwPx + u, yt)
                    housePath.close()
                    canvas.drawPath(housePath, bldgPaint)
                    bldgPaint.color = col
                } else if (layer.cap == RoofCap.MANSARD) {
                    // 파리 시가지: 회색 망사르드 지붕과 굴뚝.
                    bldgPaint.color = themed(MANSARD_ROOF, p, .2f)
                    val mh = min(bhPx * .16f, 9 * u)
                    housePath.rewind()
                    housePath.moveTo(x, yt + mh)
                    housePath.lineTo(x + bwPx * .08f, yt)
                    housePath.lineTo(x + bwPx * .92f, yt)
                    housePath.lineTo(x + bwPx, yt + mh)
                    housePath.close()
                    canvas.drawPath(housePath, bldgPaint)
                    canvas.drawRect(x + bwPx * .7f, yt - 3 * u, x + bwPx * .7f + 2.4f * u, yt, bldgPaint)
                    bldgPaint.color = col
                } else if (layer.style != WindowStyle.SHOP) {
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
        when (state.theme.barrier) {
            BarrierStyle.NONE -> return
            BarrierStyle.PARAPET -> {
                drawParapet(canvas, p)
                return
            }
            BarrierStyle.PANEL -> Unit
        }
        val u = layout.u
        val by = layout.yAt(.93f)
        val top = by - 24 * u
        val off = state.distanceU * Scenery.BED_SPEED
        val spacing = 18.0
        val i0 = floor(off / spacing).toInt() - 1
        val count = min(MAX_BARRIER, floor((off + layout.w / u) / spacing).toInt() + 2 - i0)
        var any = false
        for (n in 0 until count) {
            val wx = (i0 + n + .5) * spacing
            val on = urbanAt(Scenery.BED_SPEED, wx, 0.0) >= BARRIER_URBAN && !submerged(Scenery.BED_SPEED, wx)
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


    // ───────────── 코스 테마·구역 ─────────────

    /** 레이어 좌표 [wx]의 사물이 물에 잠기는지. 근경은 다리 아래 강, 중경은 바다. */
    private fun submerged(speed: Double, wx: Double): Boolean {
        val z = state.zones
        if (!z.water) return false
        val x = (wx - halfWU) / speed
        return if (speed >= .5) inRanges(z.bridges, x, BRIDGE_BANK) else z.sea(x) > .5f
    }

    /** 원경 빌딩은 바다 너머에 서지 않고, 근경 상가는 다리 아래 강에 서지 않는다. 중경 아파트는 바닷가에 선다. */
    private fun cityBlocked(speed: Double, wx: Double): Boolean {
        val z = state.zones
        if (!z.water) return false
        val x = (wx - halfWU) / speed
        return when {
            speed >= .5 -> inRanges(z.bridges, x, BRIDGE_BANK)
            speed < .1 -> z.sea(x) > .5f
            else -> false
        }
    }

    private fun inRanges(r: DoubleArray, x: Double, pad: Double): Boolean {
        var i = 0
        while (i < r.size) {
            if (x >= r[i] - pad && x <= r[i + 1] + pad) return true
            i += 2
        }
        return false
    }

    /** 테마 색(벽·지붕·밭)을 하늘 팔레트에 섞는다. 낮에는 안개 쪽으로 조금, 밤에는 어둡게, 멀수록 안개색. */
    private fun themed(color: Int, p: Palette, haze: Float): Int {
        // 노을·새벽에는 불빛(LIT)이 들어오는 만큼 어둑하게 본다.
        val night = max(p.f[Palette.STARS], p.f[Palette.LIT] * .6f)
        val ambient = mix(p.c[Palette.HAZE], p.c[Palette.TREE], night)
        return mix(mix(color, ambient, .22f + .58f * night), p.c[Palette.HAZE], haze)
    }

    /**
     * 마을 집. 테마의 지붕 모양·벽·지붕 색으로 그리고, 빛은 오른쪽 위에서 온다고 보고 왼쪽 벽을 어둡게 한다.
     * 밤에는 창에 불이 켜지고, 눈이 쌓이면 지붕이 하얘진다.
     */
    private fun drawHouse(canvas: Canvas, cx: Float, yb: Float, hPx: Float, key: Int, haze: Float, p: Palette) {
        val style = state.theme.house
        val wall = themed(style.walls[(hash01(key, 61) * style.walls.size).toInt()], p, haze)
        var roof = themed(style.roofs[(hash01(key, 62) * style.roofs.size).toInt()], p, haze)
        val cover = p.f[Palette.SNOW]
        if (cover > .001f) roof = mix(roof, p.c[Palette.SNOW_WHITE], cover * .8f)
        val wide = .95f + hash01(key, 63).toFloat() * .5f
        val bw = hPx * wide
        val roofH = when (style.roof) {
            RoofStyle.GABLE -> hPx * .42f
            RoofStyle.HIP -> hPx * .38f
            RoofStyle.TILE -> hPx * .24f
            RoofStyle.FLAT -> hPx * .08f
        }
        val bodyTop = yb - (hPx - roofH)
        val x0 = cx - bw / 2
        val x1 = cx + bw / 2
        housePaint.color = wall
        canvas.drawRect(x0, bodyTop, x1, yb, housePaint)
        housePaint.color = mix(wall, p.c[Palette.TOP], .28f)
        canvas.drawRect(x0, bodyTop, x0 + bw * .2f, yb, housePaint)
        // 창문
        val lit = p.f[Palette.LIT]
        val wy = bodyTop + (yb - bodyTop) * .3f
        val ws = (yb - bodyTop) * .3f
        for (q in 0..1) {
            val wx = x0 + bw * (.38f + q * .32f)
            val on = lit > .02f && hash01(key * 3 + q, 64) < lit * .85f
            housePaint.color = if (on) WINDOW_COLOR else mix(wall, p.c[Palette.TOP], .55f)
            canvas.drawRect(wx, wy, wx + ws, wy + ws, housePaint)
        }
        // 지붕
        val ov = bw * .07f
        val path = housePath
        path.rewind()
        when (style.roof) {
            RoofStyle.GABLE, RoofStyle.TILE -> {
                path.moveTo(x0 - ov, bodyTop)
                path.lineTo(cx, bodyTop - roofH)
                path.lineTo(x1 + ov, bodyTop)
            }
            RoofStyle.HIP -> {
                path.moveTo(x0 - ov * 2.2f, bodyTop + roofH * .08f)
                path.quadTo(x0, bodyTop - roofH * .1f, x0 + bw * .2f, bodyTop - roofH)
                path.lineTo(x1 - bw * .2f, bodyTop - roofH)
                path.quadTo(x1, bodyTop - roofH * .1f, x1 + ov * 2.2f, bodyTop + roofH * .08f)
            }
            RoofStyle.FLAT -> {
                path.moveTo(x0 - ov * .3f, bodyTop)
                path.lineTo(x0 - ov * .3f, bodyTop - roofH)
                path.lineTo(x1 + ov * .3f, bodyTop - roofH)
                path.lineTo(x1 + ov * .3f, bodyTop)
            }
        }
        path.close()
        housePaint.color = roof
        canvas.drawPath(path, housePaint)
        housePaint.color = mix(roof, p.c[Palette.TOP], .3f)
        canvas.drawRect(x0 - ov, bodyTop - max(1f, hPx * .03f), x1 + ov, bodyTop, housePaint)
    }

    /**
     * 중경 높이의 물. 바다는 수평선(먼 산 아래)까지, 강·호수는 맞은편 언덕 아래부터 채운다.
     * 화면을 세로 띠로 나눠 띠마다 구역 비중을 읽고, 해안선은 비중에 따라 부드럽게 흐려진다.
     */
    private fun drawWater(canvas: Canvas, p: Palette, speed: Double, off: Double) {
        val z = state.zones
        if (!z.water) return
        val u = layout.u
        val w = layout.w.toFloat()
        val seaTop = layout.yAt(.655f)
        val riverTop = layout.yAt(.772f)
        val bottom = layout.yAt(.9f)
        // 바다는 중경 건물 바닥선 위까지만 채워, 해안 도시와 해변이 땅 위에 서게 한다.
        val seaBottom = layout.yAt(.792f)
        val top = mix(p.c[Palette.HAZE], p.c[Palette.BOT], .35f)
        val deep = mix(mix(p.c[Palette.FAR], p.c[Palette.TOP], .3f), p.c[Palette.HAZE], .25f)
        if (top != waterKeyA || deep != waterKeyB || waterTop != seaTop) {
            waterKeyA = top
            waterKeyB = deep
            waterTop = seaTop
            waterPaint.shader = LinearGradient(0f, seaTop, 0f, bottom, top, deep, Shader.TileMode.CLAMP)
        }
        val strip = WATER_STRIP * u
        var x = 0f
        while (x < w) {
            val xw = ((x + strip / 2) / u + off - halfWU) / speed
            val sea = z.sea(xw)
            val river = max(z.river(xw), if (inRanges(z.bridges, xw, BRIDGE_RIVER)) 1f else 0f)
            val amount = max(sea, river)
            if (amount > .02f) {
                waterPaint.alpha = (smooth01((amount - .25f) / .5f) * 255).toInt()
                if (sea >= river) {
                    canvas.drawRect(x, seaTop, x + strip + 1, seaBottom, waterPaint)
                } else {
                    canvas.drawRect(x, riverTop, x + strip + 1, bottom, waterPaint)
                }
            }
            x += strip
        }
        waterPaint.alpha = 255
        drawGlints(canvas, p, speed, off, seaTop, seaBottom, riverTop, bottom)
    }

    /** 물 위 반짝임. 레이어 좌표에 고정된 짧은 선이라 물과 함께 흘러간다. 밤에는 아주 약하다. */
    private fun drawGlints(
        canvas: Canvas, p: Palette, speed: Double, off: Double,
        seaTop: Float, seaBottom: Float, riverTop: Float, bottom: Float,
    ) {
        val z = state.zones
        val u = layout.u
        val w = layout.w.toFloat()
        val strength = .25f + .55f * p.f[Palette.SUN_A] * (1 - p.f[Palette.STARS])
        fieldPaint.color = mix(p.c[Palette.SUN], Color.WHITE, .5f)
        fieldPaint.alpha = (strength * 150).toInt()
        val cell = 22.0
        val k0 = floor(off / cell).toInt()
        val k1 = floor((off + w / u) / cell).toInt() + 1
        for (k in k0..k1) {
            val xw = ((k + .5) * cell - halfWU) / speed
            val sea = z.sea(xw) > .6f
            val river = !sea && (z.river(xw) > .6f || inRanges(z.bridges, xw, BRIDGE_RIVER))
            if (!sea && !river) continue
            val y0 = if (sea) seaTop else riverTop
            val y1 = if (sea) seaBottom else bottom
            for (row in 0..2) {
                if (hash01(k * 5 + row, 913) > .55) continue
                val f = (row + hash01(k * 5 + row, 914).toFloat()) / 3f
                val y = y0 + (y1 - y0) * f * f
                val x = (((k + hash01(k * 5 + row, 915)) * cell - off) * u).toFloat()
                val len = (4 + 8 * f) * u
                canvas.drawRect(x, y, x + len, y + max(1f, .6f * u), fieldPaint)
            }
        }
    }

    /** 근경 땅의 밭 무늬. 밭은 일정한 폭의 칸으로 나뉘고, 칸마다 구역 비중만큼의 확률로 있다. */
    private fun drawFields(canvas: Canvas, p: Palette, speed: Double, off: Double) {
        val z = state.zones
        if (!z.fields) return
        val u = layout.u
        val w = layout.w.toFloat()
        val top = layout.yAt(.868f)
        val bot = layout.yAt(.932f)
        val k0 = floor(off / PLOT_U).toInt() - 1
        val k1 = floor((off + w / u) / PLOT_U).toInt() + 1
        for (k in k0..k1) {
            val xw = ((k + .5) * PLOT_U - halfWU) / speed
            val style = z.fieldStyle(xw)
            if (style == FieldStyle.NONE) continue
            val weight = z.field(xw) * (1 - state.urban.at(xw))
            if (hash01(k, 401) >= weight) continue
            if (inRanges(z.bridges, xw, BRIDGE_BANK)) continue
            val x0 = ((k * PLOT_U - off) * u).toFloat()
            val x1 = (((k + 1) * PLOT_U - off) * u).toFloat()
            drawPlot(canvas, p, style, k, x0, x1, top, bot, u)
        }
    }

    private fun drawPlot(canvas: Canvas, p: Palette, style: FieldStyle, k: Int, x0: Float, x1: Float, top: Float, bot: Float, u: Float) {
        val hgt = bot - top
        val paint = fieldPaint
        paint.alpha = 255
        when (style) {
            FieldStyle.PADDY -> {
                // 논: 자라는 벼의 초록 면, 하늘을 비추는 물빛 줄, 논둑 세 줄.
                paint.color = themed(Color.rgb(118, 158, 84), p, .05f)
                canvas.drawRect(x0, top, x1, bot, paint)
                paint.color = mix(paint.color, p.c[Palette.BOT], .45f)
                for (r in 0..2) {
                    val y = top + hgt * (.1f + r * .3f)
                    canvas.drawRect(x0, y, x1, y + hgt * .07f, paint)
                }
                paint.color = themed(Color.rgb(96, 104, 64), p, 0f)
                for (r in 0..2) {
                    val y = top + hgt * (.26f + r * .3f)
                    canvas.drawRect(x0, y, x1, y + max(1f, 1.1f * u), paint)
                }
            }
            FieldStyle.TEA -> {
                // 차밭: 선로와 나란한 차나무 줄. 줄 윗면이 둥글게 물결지고, 줄 사이 고랑은 그늘.
                paint.color = themed(Color.rgb(34, 64, 36), p, 0f)
                canvas.drawRect(x0, top, x1, bot, paint)
                for (r in 0..2) {
                    val y = top + hgt * (.04f + r * .33f)
                    val rh = hgt * .27f
                    val bump = rh * .32f
                    paint.color = themed(if (r % 2 == 0) Color.rgb(78, 132, 62) else Color.rgb(88, 144, 70), p, 0f)
                    canvas.drawRect(x0, y + bump, x1, y + rh, paint)
                    var x = x0 + bump
                    while (x < x1) {
                        canvas.drawCircle(x, y + bump, bump, paint)
                        x += bump * 1.8f
                    }
                    paint.color = themed(Color.rgb(140, 190, 104), p, 0f)
                    canvas.drawRect(x0, y + bump * .9f, x1, y + bump * .9f + max(1f, .6f * u), paint)
                }
            }
            FieldStyle.VINE -> {
                // 포도밭: 선로와 직각으로 난 줄이 끝에서 보여 세로 줄무늬가 된다.
                paint.color = themed(Color.rgb(128, 100, 70), p, 0f)
                canvas.drawRect(x0, top, x1, bot, paint)
                paint.color = themed(Color.rgb(64, 104, 48), p, 0f)
                var n = 0
                var x = x0 + 2 * u
                while (x < x1 && n < FIELD_PTS - 4) {
                    fieldPts[n++] = x
                    fieldPts[n++] = top + hgt * .08f
                    fieldPts[n++] = x
                    fieldPts[n++] = bot
                    x += 5.2f * u
                }
                paint.strokeWidth = 2.6f * u
                canvas.drawLines(fieldPts, 0, n, paint)
            }
            FieldStyle.LAVENDER -> {
                // 라벤더: 보라 줄과 마른 땅 줄을 번갈아.
                paint.color = themed(Color.rgb(150, 150, 120), p, 0f)
                canvas.drawRect(x0, top, x1, bot, paint)
                paint.color = themed(Color.rgb(132, 108, 186), p, 0f)
                for (r in 0..3) {
                    val y = top + hgt * (.05f + r * .25f)
                    dst.set(x0 + 1 * u, y, x1 - 1 * u, y + hgt * .15f)
                    canvas.drawRoundRect(dst, hgt * .07f, hgt * .07f, paint)
                }
            }
            FieldStyle.WHEAT -> {
                // 밀밭: 금빛 면에 바람 결 몇 줄.
                paint.color = themed(Color.rgb(214, 182, 96), p, 0f)
                canvas.drawRect(x0, top, x1, bot, paint)
                paint.color = themed(Color.rgb(182, 148, 70), p, 0f)
                for (r in 0..2) {
                    val y = top + hgt * (.25f + r * .28f)
                    val x = x0 + (x1 - x0) * hash01(k * 3 + r, 402).toFloat() * .5f
                    canvas.drawRect(x, y, x + (x1 - x0) * .4f, y + max(1f, .8f * u), paint)
                }
            }
            FieldStyle.NONE -> return
        }
        // 밭 경계
        paint.color = themed(Color.rgb(96, 88, 64), p, 0f)
        canvas.drawRect(x0, top, x0 + max(1f, .8f * u), bot, paint)
    }

    /** 다리 구간에서는 근경 땅 대신 강이 보인다. */
    private fun drawRiverCrossing(canvas: Canvas, p: Palette) {
        val br = state.zones.bridges
        if (br.isEmpty()) return
        val u = layout.u
        val w = layout.w.toFloat()
        val d = state.distanceU
        val s = layers[Scenery.NEAR].speed
        val top = layout.yAt(.80f)
        val h = layout.h.toFloat()
        var i = 0
        while (i < br.size) {
            val x0 = (((br[i] - BRIDGE_BANK - d) * s + halfWU) * u).toFloat()
            val x1 = (((br[i + 1] + BRIDGE_BANK - d) * s + halfWU) * u).toFloat()
            i += 2
            if (x1 < 0 || x0 > w) continue
            canvas.drawRect(max(0f, x0), top, min(w, x1), h, waterPaint)
        }
    }

    /** 철교 트러스. 창 바로 앞을 가장 빠르게 지나간다. */
    private fun drawTruss(canvas: Canvas, p: Palette) {
        val br = state.zones.bridges
        if (br.isEmpty()) return
        val u = layout.u
        val w = layout.w.toFloat()
        val d = state.distanceU
        val s = Scenery.POLE_SPEED
        val top = layout.h * .07f
        val bot = layout.yAt(.905f)
        trussPaint.color = mix(p.c[Palette.POLE], p.c[Palette.HAZE], .12f)
        trussPaint.strokeWidth = 3.2f * u
        var i = 0
        while (i < br.size) {
            val l0 = (br[i] - d) * s + halfWU
            val l1 = (br[i + 1] - d) * s + halfWU
            i += 2
            val x0 = (l0 * u).toFloat()
            val x1 = (l1 * u).toFloat()
            if (x1 < 0 || x0 > w) continue
            canvas.drawLine(max(-10f, x0), top, min(w + 10, x1), top, trussPaint)
            canvas.drawLine(max(-10f, x0), bot, min(w + 10, x1), bot, trussPaint)
            var k = 0
            var px = l0
            while (px <= l1) {
                val x = (px * u).toFloat()
                val nx = ((px + TRUSS_PANEL) * u).toFloat()
                if (x > w + 10) break
                if (nx > -10) {
                    canvas.drawLine(x, top, x, bot, trussPaint)
                    if (px + TRUSS_PANEL <= l1) {
                        if (k % 2 == 0) canvas.drawLine(x, top, nx, bot, trussPaint) else canvas.drawLine(x, bot, nx, top, trussPaint)
                    }
                }
                px += TRUSS_PANEL
                k++
            }
        }
    }

    /**
     * 터널. 안은 어둡고 벽 조명이 빠르게 지나간다. 완전히 깜깜하게 두지 않고 실내 반사(유리 효과)가 비친다(명세 7.3).
     */
    private fun drawTunnels(canvas: Canvas, p: Palette) {
        val tn = state.zones.tunnels
        if (tn.isEmpty()) return
        val u = layout.u
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()
        val d = state.distanceU
        val s = Scenery.BED_SPEED
        var i = 0
        while (i < tn.size) {
            val l0 = (tn[i] - d) * s + halfWU
            val l1 = (tn[i + 1] - d) * s + halfWU
            i += 2
            val x0 = (l0 * u).toFloat()
            val x1 = (l1 * u).toFloat()
            if (x1 < 0 || x0 > w) continue
            platformPaint.color = TUNNEL_DARK
            canvas.drawRect(max(0f, x0), 0f, min(w, x1), h, platformPaint)
            platformPaint.color = Color.rgb(255, 210, 150)
            var px = floor(l0 / TUNNEL_LAMP) * TUNNEL_LAMP + TUNNEL_LAMP
            while (px < l1) {
                val x = (px * u).toFloat()
                if (x > w) break
                if (x > -20 * u) canvas.drawRect(x, h * .4f, x + 14 * u, h * .4f + 1.6f * u, platformPaint)
                px += TUNNEL_LAMP
            }
            // 입구·출구 콘크리트 테두리
            platformPaint.color = mix(Color.rgb(150, 146, 138), p.c[Palette.TOP], .35f)
            canvas.drawRect(x0 - 16 * u, 0f, x0, h, platformPaint)
            canvas.drawRect(x1, 0f, x1 + 16 * u, h, platformPaint)
        }
    }

    /** 랜드마크. 대강의 실루엣을 경로로 그린다. 레이어의 색을 따라 먼 것은 흐릿하다. */
    private fun drawLandmarks(canvas: Canvas, p: Palette, layerIndex: Int) {
        val list = state.zones.landmarks
        if (list.isEmpty()) return
        val l = layers[layerIndex]
        val u = layout.u
        val w = layout.w.toFloat()
        val d = state.distanceU
        // 먼 랜드마크는 레이어 색(안개)을 따르고, 중경 탑·다리는 목재·철재처럼 어둡게.
        val col = if (layerIndex == Scenery.MID) {
            themed(Color.rgb(66, 56, 52), p, .2f)
        } else {
            mix(layerColor(l, p), p.c[Palette.TOP], .14f)
        }
        for (lm in list) {
            if (lm.kind.layer != layerIndex) continue
            val lx = (lm.x - d) * l.speed + halfWU
            val cx = (lx * u).toFloat()
            if (cx < -260 * u || cx > w + 260 * u) continue
            val by = if (layerIndex == Scenery.MID) terrainY(l, lx) + 3 * u else layout.yAt(l.base) + 4 * u
            drawLandmark(canvas, p, lm.kind, cx, by, u, col)
        }
    }

    private fun drawLandmark(canvas: Canvas, p: Palette, kind: LandmarkKind, cx: Float, by: Float, u: Float, col: Int) {
        val path = landmarkPath
        path.rewind()
        landmarkPaint.color = col
        val lit = p.f[Palette.LIT]
        when (kind) {
            LandmarkKind.N_TOWER -> {
                // 산 위의 탑: 언덕, 가는 기둥, 전망대, 안테나.
                path.moveTo(cx - 70 * u, by)
                path.quadTo(cx - 30 * u, by - 26 * u, cx, by - 28 * u)
                path.quadTo(cx + 30 * u, by - 26 * u, cx + 70 * u, by)
                path.close()
                canvas.drawPath(path, landmarkPaint)
                val base = by - 26 * u
                canvas.drawRect(cx - 1.8f * u, base - 58 * u, cx + 1.8f * u, base, landmarkPaint)
                dst.set(cx - 5.5f * u, base - 66 * u, cx + 5.5f * u, base - 56 * u)
                canvas.drawRoundRect(dst, 2 * u, 2 * u, landmarkPaint)
                canvas.drawRect(cx - .7f * u, base - 86 * u, cx + .7f * u, base - 66 * u, landmarkPaint)
                if (lit > .05f) {
                    landmarkPaint.color = Color.argb((lit * 220).toInt(), 255, 214, 150)
                    canvas.drawRect(cx - 4.5f * u, base - 62 * u, cx + 4.5f * u, base - 60.5f * u, landmarkPaint)
                }
            }
            LandmarkKind.FUJI -> {
                // 오목한 비탈의 원뿔과 들쭉날쭉한 눈 덮인 꼭대기.
                path.moveTo(cx - 200 * u, by)
                path.quadTo(cx - 70 * u, by - 40 * u, cx - 26 * u, by - 132 * u)
                path.lineTo(cx + 26 * u, by - 132 * u)
                path.quadTo(cx + 70 * u, by - 40 * u, cx + 200 * u, by)
                path.close()
                canvas.drawPath(path, landmarkPaint)
                path.rewind()
                path.moveTo(cx - 26 * u, by - 132 * u)
                path.lineTo(cx + 26 * u, by - 132 * u)
                path.lineTo(cx + 52 * u, by - 92 * u)
                var x = 44f
                var down = true
                while (x > -52) {
                    path.lineTo(cx + x * u, by - (if (down) 80 else 94) * u)
                    x -= 12f
                    down = !down
                }
                path.lineTo(cx - 52 * u, by - 92 * u)
                path.close()
                landmarkPaint.color = mix(mix(Color.WHITE, p.c[Palette.HAZE], .3f), p.c[Palette.TOP], .25f * p.f[Palette.STARS])
                canvas.drawPath(path, landmarkPaint)
            }
            LandmarkKind.PAGODA -> {
                // 오층탑: 위로 갈수록 좁아지는 지붕 다섯 층과 상륜.
                for (i in 0..4) {
                    val y = by - (8 + i * 13) * u
                    val rw = (30 - i * 4.5f) * u
                    canvas.drawRect(cx - rw * .42f, y - 9 * u, cx + rw * .42f, y, landmarkPaint)
                    path.rewind()
                    path.moveTo(cx - rw, y - 8 * u)
                    path.quadTo(cx - rw * .6f, y - 10 * u, cx - rw * .45f, y - 13 * u)
                    path.lineTo(cx + rw * .45f, y - 13 * u)
                    path.quadTo(cx + rw * .6f, y - 10 * u, cx + rw, y - 8 * u)
                    path.close()
                    canvas.drawPath(path, landmarkPaint)
                }
                canvas.drawRect(cx - .8f * u, by - 92 * u, cx + .8f * u, by - 72 * u, landmarkPaint)
            }
            LandmarkKind.PEARL_TOWER -> {
                // 구슬 둘을 꿴 탑: 비스듬한 다리, 큰 구슬, 기둥, 작은 구슬, 첨탑.
                landmarkStroke.color = col
                landmarkStroke.strokeWidth = 3 * u
                canvas.drawLine(cx - 14 * u, by, cx - 3 * u, by - 56 * u, landmarkStroke)
                canvas.drawLine(cx + 14 * u, by, cx + 3 * u, by - 56 * u, landmarkStroke)
                canvas.drawRect(cx - 2.2f * u, by - 150 * u, cx + 2.2f * u, by - 40 * u, landmarkPaint)
                canvas.drawCircle(cx, by - 60 * u, 13 * u, landmarkPaint)
                canvas.drawCircle(cx, by - 116 * u, 8 * u, landmarkPaint)
                canvas.drawCircle(cx, by - 150 * u, 3.5f * u, landmarkPaint)
                canvas.drawRect(cx - .7f * u, by - 178 * u, cx + .7f * u, by - 150 * u, landmarkPaint)
                if (lit > .05f) {
                    landmarkPaint.color = Color.argb((lit * 200).toInt(), 255, 140, 190)
                    canvas.drawCircle(cx, by - 60 * u, 4 * u, landmarkPaint)
                    canvas.drawCircle(cx, by - 116 * u, 2.6f * u, landmarkPaint)
                }
            }
            LandmarkKind.EIFFEL -> {
                // 아래가 벌어진 철탑과 밑의 아치.
                path.moveTo(cx - 34 * u, by)
                path.quadTo(cx - 12 * u, by - 40 * u, cx - 5 * u, by - 86 * u)
                path.lineTo(cx - 2 * u, by - 124 * u)
                path.lineTo(cx + 2 * u, by - 124 * u)
                path.lineTo(cx + 5 * u, by - 86 * u)
                path.quadTo(cx + 12 * u, by - 40 * u, cx + 34 * u, by)
                path.close()
                path.addCircle(cx, by, 15 * u, Path.Direction.CW)
                canvas.drawPath(path, landmarkPaint)
                canvas.drawRect(cx - 15 * u, by - 34 * u, cx + 15 * u, by - 31 * u, landmarkPaint)
                canvas.drawRect(cx - 8 * u, by - 72 * u, cx + 8 * u, by - 70 * u, landmarkPaint)
                canvas.drawRect(cx - .6f * u, by - 140 * u, cx + .6f * u, by - 124 * u, landmarkPaint)
            }
            LandmarkKind.ARCH_BRIDGE -> {
                // 철제 아치교: 강 위 아치와 그 위 상판, 양 끝 교각.
                landmarkStroke.color = col
                landmarkStroke.strokeWidth = 3.4f * u
                path.moveTo(cx - 96 * u, by)
                path.quadTo(cx, by - 86 * u, cx + 96 * u, by)
                canvas.drawPath(path, landmarkStroke)
                canvas.drawRect(cx - 120 * u, by - 46 * u, cx + 120 * u, by - 42 * u, landmarkPaint)
                landmarkStroke.strokeWidth = 1.2f * u
                var x = -72f
                while (x <= 72f) {
                    val ay = by - 86 * u * (1 - (x / 96f) * (x / 96f)) / 2
                    if (ay < by - 44 * u) canvas.drawLine(cx + x * u, ay, cx + x * u, by - 44 * u, landmarkStroke)
                    x += 12f
                }
                canvas.drawRect(cx - 124 * u, by - 46 * u, cx - 114 * u, by, landmarkPaint)
                canvas.drawRect(cx + 114 * u, by - 46 * u, cx + 124 * u, by, landmarkPaint)
            }
        }
    }

    private fun smooth01(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3 - 2 * c)
    }

    /** 고가 선로의 콘크리트 난간. 늘 창 아래쪽을 가로지르고, 이음매가 빠르게 지나간다. */
    private fun drawParapet(canvas: Canvas, p: Palette) {
        val u = layout.u
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()
        // 낮게 둬서 고가 아래로 펼쳐진 밭이 보이게 한다.
        val top = layout.yAt(.955f)
        platformPaint.color = themed(Color.rgb(176, 172, 164), p, 0f)
        canvas.drawRect(0f, top, w, h, platformPaint)
        platformPaint.color = themed(Color.rgb(206, 202, 194), p, 0f)
        canvas.drawRect(0f, top, w, top + 2.2f * u, platformPaint)
        platformPaint.color = themed(Color.rgb(140, 136, 130), p, 0f)
        val off = state.distanceU * Scenery.BED_SPEED
        val spacing = 46.0
        var i = floor(off / spacing).toInt()
        while (true) {
            val x = ((i * spacing - off) * u).toFloat()
            if (x > w) break
            canvas.drawRect(x, top + 2.2f * u, x + .9f * u, h, platformPaint)
            i++
        }
    }

    /**
     * 역 플랫폼. 창 바로 앞(선로 높이 레이어)에 지붕·기둥·바닥·이름판을 그린다.
     * 역의 세계 위치에 카메라가 오면 플랫폼 가운데가 화면 가운데에 온다. 그래서 정차 중에는 이름판이 정면에 보인다.
     */
    private fun drawStations(canvas: Canvas, p: Palette) {
        val xs = state.stationX
        if (xs.isEmpty()) return
        val u = layout.u
        val w = layout.w.toFloat()
        val h = layout.h.toFloat()
        val d = state.distanceU
        val s = Scenery.BED_SPEED
        val half = PLATFORM_HALF * u
        val lit = p.f[Palette.LIT]
        val floorTop = layout.yAt(.885f)
        val roof = h * .16f
        val concrete = mix(mix(p.c[Palette.GROUND], p.c[Palette.HAZE], .45f), Color.WHITE, .22f * (1 - lit * .6f))
        val dark = mix(p.c[Palette.POLE], p.c[Palette.HAZE], .18f)
        for (i in xs.indices) {
            val cx = (((xs[i] - d) * s + halfWU) * u).toFloat()
            if (cx + half < 0 || cx - half > w) continue
            val x0 = cx - half
            val x1 = cx + half
            // 바닥: 먼 쪽 낮은 벽, 콘크리트 면, 선로 쪽 노란 안전선.
            platformPaint.color = concrete
            canvas.drawRect(x0, floorTop, x1, h, platformPaint)
            platformPaint.color = dark
            canvas.drawRect(x0, floorTop - 7 * u, x1, floorTop, platformPaint)
            platformPaint.color = mix(Color.rgb(232, 196, 90), concrete, .25f + .4f * lit)
            val ly = layout.yAt(.972f)
            canvas.drawRect(x0, ly, x1, ly + 2.4f * u, platformPaint)
            // 지붕과 기둥.
            platformPaint.color = dark
            canvas.drawRect(x0, -2f, x1, roof, platformPaint)
            platformPaint.color = mix(dark, Color.WHITE, .18f)
            canvas.drawRect(x0, roof - 2.2f * u, x1, roof, platformPaint)
            var px = x0 + PILLAR_GAP * u / 2
            while (px < x1) {
                platformPaint.color = dark
                canvas.drawRect(px - 2.6f * u, roof, px + 2.6f * u, floorTop, platformPaint)
                if (lit > .05f) {
                    // 밤에는 지붕 아래 조명.
                    platformPaint.color = Color.argb((lit * 230).toInt(), 255, 226, 170)
                    canvas.drawRect(px + 14 * u, roof, px + 44 * u, roof + 1.8f * u, platformPaint)
                }
                px += PILLAR_GAP * u
            }
            drawSign(canvas, i, cx, roof, u, lit)
        }
    }

    /** 역 이름판. 지붕에 매달린 흰 판에 현지 표기를 크게, 로마자를 작게 쓴다. */
    private fun drawSign(canvas: Canvas, i: Int, cx: Float, roof: Float, u: Float, lit: Float) {
        val local = state.stationLocal.getOrNull(i) ?: return
        val roman = state.stationRoman.getOrNull(i) ?: ""
        signText.textSize = 24 * u
        signSub.textSize = 9 * u
        val bw = maxOf(signText.measureText(local), signSub.measureText(roman)) + 40 * u
        val bh = 54 * u
        val top = roof + 10 * u
        platformPaint.color = SIGN_HANGER
        canvas.drawRect(cx - bw * .3f - .6f * u, roof, cx - bw * .3f + .6f * u, top, platformPaint)
        canvas.drawRect(cx + bw * .3f - .6f * u, roof, cx + bw * .3f + .6f * u, top, platformPaint)
        val sign = state.theme.sign
        signPaint.color = mix(sign.board, Color.rgb(255, 236, 205), lit * .25f)
        dst.set(cx - bw / 2, top, cx + bw / 2, top + bh)
        canvas.drawRoundRect(dst, 2.5f * u, 2.5f * u, signPaint)
        signPaint.color = sign.stripe
        canvas.drawRect(dst.left, dst.bottom - 4 * u, dst.right, dst.bottom, signPaint)
        signText.color = sign.ink
        canvas.drawText(local, cx, top + 26.5f * u, signText)
        signSub.color = sign.ink
        canvas.drawText(roman, cx, top + 41f * u, signSub)
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
        const val FIELD_PTS = 2048
        /** 밭 한 칸의 폭(근경 레이어 단위). */
        const val PLOT_U = 64.0
        /** 물을 그리는 세로 띠 폭(단위). */
        const val WATER_STRIP = 8f
        /** 다리 양쪽으로 강둑까지 물이 이어지는 거리(세계 단위). */
        const val BRIDGE_BANK = 240.0
        /** 다리 둘레 중경 강의 폭(세계 단위). */
        const val BRIDGE_RIVER = 2600.0
        const val TRUSS_PANEL = 70.0
        const val TUNNEL_LAMP = 120.0
        val TUNNEL_DARK = Color.rgb(20, 18, 17)
        val TILE_ROOF = Color.rgb(196, 102, 64)
        val MANSARD_ROOF = Color.rgb(96, 104, 116)
        /** 플랫폼 반 길이(장면 단위, 선로 레이어 좌표). */
        const val PLATFORM_HALF = 560f
        const val PILLAR_GAP = 140f
        val SIGN_HANGER = Color.rgb(40, 40, 44)
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
