package com.kero.passingby.scene


import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 랜드마크 실루엣. 대강의 모양을 경로로 그린다(명세 7.1).
 * 크기는 장면 단위 [u]로 쓰고, 바닥 가운데([cx], [by])에서 위(−y)로 쌓는다.
 *
 * 명암은 일반 건물과 같다: 빛은 오른쪽 위에서 오고, 부재마다 왼쪽 옆면은 그늘, 정면은 오른쪽으로 갈수록 볕을 받는다.
 * 부재를 그리기 전에 [facet](각진 것)이나 [round](둥근 것)로 그 폭을 알려 주면 된다. 불빛·장식은 [flat].
 *
 * 새 랜드마크는 [LandmarkKind]에 이름과 레이어를 더하고 [draw]에 가지 하나를 더한 뒤, 코스의 landmarks에 놓으면 된다.
 * 개발 빌드에서 `--ef pos <구간 위치>` 실행 옵션으로 그 자리로 바로 가서 확인한다(MainActivity.applyDebugExtras).
 */
internal class LandmarkPainter {
    /** 랜드마크 가운데에서 [dx]px 떨어진 곳의 지형 높이(px). 능선을 따라 서는 것(풍력발전기)이 쓴다. */
    fun interface Ground {
        fun y(dx: Float): Float
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val blade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val rect = RectF()

    // ── 명암 ──
    /** 그늘에 섞는 하늘 위쪽 색, 볕 색, 명암 세기(먼 원경은 안개로 약하다), 밤 정도. */
    private var sky = 0
    private var sun = 0
    private var relief = 1f
    private var night = 0f
    /** 지금 부재의 바탕색과 그 색으로 만든 그러데이션 자리. */
    private var base = 0
    private var slot = 0
    private val slotBase = IntArray(SHADE_SLOTS)
    private val slotSky = IntArray(SHADE_SLOTS)
    private val slotSun = IntArray(SHADE_SLOTS)
    private val slotRelief = FloatArray(SHADE_SLOTS) { -1f }
    private val slotFacet = arrayOfNulls<LinearGradient>(SHADE_SLOTS)
    private val slotRound = arrayOfNulls<LinearGradient>(SHADE_SLOTS)
    private var nextSlot = 0
    private val shadeMatrix = Matrix()

    /**
     * @param time 장면이 흐른 초. 풍력발전기 날개처럼 저절로 움직이는 것에 쓴다.
     * @param relief 명암 세기. 중경은 1, 안개 낀 원경은 더 약하게.
     */
    fun draw(
        canvas: Canvas, p: Palette, kind: LandmarkKind,
        cx: Float, by: Float, u: Float, col: Int, time: Double, ground: Ground, relief: Float,
    ) {
        path.rewind()
        sky = p.c[Palette.TOP]
        sun = mix(p.c[Palette.SUN], p.c[Palette.BOT], .5f)
        this.relief = relief
        night = p.f[Palette.STARS]
        tone(col)
        flat(col)
        val lit = p.f[Palette.LIT]
        when (kind) {
            LandmarkKind.N_TOWER -> nTower(canvas, cx, by, u, lit)
            LandmarkKind.FUJI -> fuji(canvas, p, cx, by, u)
            LandmarkKind.PAGODA -> pagoda(canvas, cx, by, u)
            LandmarkKind.PEARL_TOWER -> pearlTower(canvas, cx, by, u, col, lit)
            LandmarkKind.EIFFEL -> eiffel(canvas, cx, by, u)
            LandmarkKind.ARCH_BRIDGE -> archBridge(canvas, cx, by, u, col, lit, ground)
            LandmarkKind.HANBIT_TOWER -> hanbitTower(canvas, cx, by, u, lit)
            LandmarkKind.SUSPENSION_BRIDGE -> suspensionBridge(canvas, cx, by, u, col, lit)
            LandmarkKind.CABLE_BRIDGE -> cableBridge(canvas, cx, by, u, col, lit)
            LandmarkKind.WIND_FARM -> windFarm(canvas, p, cx, u, col, lit, time, ground)
            LandmarkKind.TOKYO_TOWER -> tokyoTower(canvas, cx, by, u, col, lit)
            LandmarkKind.CASTLE -> castle(canvas, cx, by, u, col, lit)
            LandmarkKind.TEMPLE_OF_HEAVEN -> templeOfHeaven(canvas, cx, by, u, col)
            LandmarkKind.CANTON_TOWER -> cantonTower(canvas, cx, by, u, col, lit)
            LandmarkKind.HILL_BASILICA -> hillBasilica(canvas, cx, by, u, col, lit)
            LandmarkKind.CASTLE_WALLS -> castleWalls(canvas, cx, by, u, col, lit)
            LandmarkKind.UNIVERSITY_TOWER -> universityTower(canvas, cx, by, u, col, lit)
            LandmarkKind.ISLANDS -> islands(canvas, cx, by, u)
            LandmarkKind.SKI_JUMP -> skiJump(canvas, p, cx, by, u, col, lit, ground)
            LandmarkKind.KYOTO_TOWER -> kyotoTower(canvas, cx, by, u, col, lit)
            LandmarkKind.TWIN_TOWERS -> twinTowers(canvas, cx, by, u, lit)
            LandmarkKind.PUDONG -> pudong(canvas, cx, by, u, lit)
            LandmarkKind.TWIN_PAGODAS -> twinPagodas(canvas, cx, by, u, col, lit)
            LandmarkKind.CLOCK_TOWER -> clockTower(canvas, cx, by, u, col, lit)
            LandmarkKind.PART_DIEU -> partDieu(canvas, cx, by, u, col)
            LandmarkKind.PAPAL_PALACE -> papalPalace(canvas, cx, by, u, col, lit)
            LandmarkKind.BROKEN_BRIDGE -> brokenBridge(canvas, cx, by, u)
        }
        fill.shader = null
    }

    /**
     * 이후 부재의 바탕색. 그늘·볕 그러데이션은 색이 바뀔 때만 새로 만든다(몇 칸을 돌려 써서
     * 한 랜드마크 안에서 벽·지붕처럼 색을 오가도 매 프레임 만들지 않는다).
     */
    private fun tone(c: Int) {
        base = c
        for (i in 0 until SHADE_SLOTS) {
            if (slotFacet[i] != null && slotBase[i] == c && slotSky[i] == sky && slotSun[i] == sun && slotRelief[i] == relief) {
                slot = i
                return
            }
        }
        slot = nextSlot
        nextSlot = (nextSlot + 1) % SHADE_SLOTS
        val dark = mix(c, sky, .34f * relief)
        val light = mix(c, sun, .26f * relief)
        slotBase[slot] = c
        slotSky[slot] = sky
        slotSun[slot] = sun
        slotRelief[slot] = relief
        slotFacet[slot] = LinearGradient(
            0f, 0f, 1f, 0f, intArrayOf(dark, dark, c, light), floatArrayOf(0f, .24f, .25f, 1f), Shader.TileMode.CLAMP,
        )
        slotRound[slot] = LinearGradient(
            0f, 0f, 1f, 0f, intArrayOf(dark, c, light, c), floatArrayOf(0f, .42f, .78f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /** 각진 부재(탑 몸통·벽·교각) [x0]~[x1]: 왼쪽 4분의 1은 옆면 그늘, 정면은 오른쪽으로 갈수록 볕. */
    private fun facet(x0: Float, x1: Float) = shade(slotFacet[slot]!!, x0, x1)

    /** 둥근 부재(구슬·원통·돔·언덕) [x0]~[x1]: 왼쪽 그늘에서 오른쪽 볕으로 부드럽게, 끝에서 조금 돌아간다. */
    private fun round(x0: Float, x1: Float) = shade(slotRound[slot]!!, x0, x1)

    private fun shade(g: LinearGradient, x0: Float, x1: Float) {
        shadeMatrix.setScale(max(1f, x1 - x0), 1f)
        shadeMatrix.postTranslate(x0, 0f)
        g.setLocalMatrix(shadeMatrix)
        fill.shader = g
        fill.color = Color.BLACK // 셰이더를 쓸 때는 불투명 알파만 의미가 있다.
    }

    /** 명암 없이 한 색(불빛·장식). */
    private fun flat(c: Int) {
        fill.shader = null
        fill.color = c
    }

    /** 볕 받는 윗모서리 띠(일반 건물 옥상 가장자리처럼). */
    private fun rim(canvas: Canvas, x0: Float, y: Float, x1: Float, u: Float) {
        flat(mix(base, sun, .45f * relief))
        canvas.drawRect(x0, y, x1, y + max(1f, .8f * u), fill)
    }

    /** 상판·처마 밑 그늘 띠. */
    private fun underside(canvas: Canvas, x0: Float, y: Float, x1: Float, h: Float) {
        flat(mix(base, sky, .42f * relief))
        canvas.drawRect(x0, y, x1, y + h, fill)
    }

    /** 바탕색을 낮에만 [c] 쪽으로 [k]만큼 물들인다(흰 벽·푸른 지붕 같은 재질). 밤에는 어둠에 묻힌다. */
    private fun dye(col: Int, c: Int, k: Float) = mix(col, c, k * (1 - night))

    /** 산 위의 탑: 언덕, 가는 기둥, 전망대, 안테나. */
    private fun nTower(canvas: Canvas, cx: Float, by: Float, u: Float, lit: Float) {
        round(cx - 70 * u, cx + 70 * u)
        path.moveTo(cx - 70 * u, by)
        path.quadTo(cx - 30 * u, by - 26 * u, cx, by - 28 * u)
        path.quadTo(cx + 30 * u, by - 26 * u, cx + 70 * u, by)
        path.close()
        canvas.drawPath(path, fill)
        val foot = by - 26 * u
        facet(cx - 1.8f * u, cx + 1.8f * u)
        canvas.drawRect(cx - 1.8f * u, foot - 58 * u, cx + 1.8f * u, foot, fill)
        round(cx - 5.5f * u, cx + 5.5f * u)
        rect.set(cx - 5.5f * u, foot - 66 * u, cx + 5.5f * u, foot - 56 * u)
        canvas.drawRoundRect(rect, 2 * u, 2 * u, fill)
        underside(canvas, cx - 5 * u, foot - 57.6f * u, cx + 5 * u, 1.6f * u)
        facet(cx - .7f * u, cx + .7f * u)
        canvas.drawRect(cx - .7f * u, foot - 86 * u, cx + .7f * u, foot - 66 * u, fill)
        if (lit > .05f) {
            flat(Color.argb((lit * 220).toInt(), 255, 214, 150))
            canvas.drawRect(cx - 4.5f * u, foot - 62 * u, cx + 4.5f * u, foot - 60.5f * u, fill)
        }
    }

    /** 오목한 비탈의 원뿔과 들쭉날쭉한 눈 덮인 꼭대기. 왼쪽 비탈은 그늘, 오른쪽은 볕. */
    private fun fuji(canvas: Canvas, p: Palette, cx: Float, by: Float, u: Float) {
        round(cx - 200 * u, cx + 200 * u)
        path.moveTo(cx - 200 * u, by)
        path.quadTo(cx - 70 * u, by - 40 * u, cx - 26 * u, by - 132 * u)
        path.lineTo(cx + 26 * u, by - 132 * u)
        path.quadTo(cx + 70 * u, by - 40 * u, cx + 200 * u, by)
        path.close()
        canvas.drawPath(path, fill)
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
        tone(mix(mix(Color.WHITE, p.c[Palette.HAZE], .3f), p.c[Palette.TOP], .25f * night))
        round(cx - 52 * u, cx + 52 * u)
        canvas.drawPath(path, fill)
    }

    /** 오층탑: 위로 갈수록 좁아지는 지붕 다섯 층과 상륜. 처마 밑 벽에는 그늘. */
    private fun pagoda(canvas: Canvas, cx: Float, by: Float, u: Float) {
        for (i in 0..4) {
            val y = by - (8 + i * 13) * u
            val rw = (30 - i * 4.5f) * u
            facet(cx - rw * .42f, cx + rw * .42f)
            canvas.drawRect(cx - rw * .42f, y - 9 * u, cx + rw * .42f, y, fill)
            facet(cx - rw, cx + rw)
            eaveRoof(canvas, cx, y, rw, rw * .45f, u)
            underside(canvas, cx - rw * .42f, y - 8 * u, cx + rw * .42f, 1.6f * u)
        }
        facet(cx - .8f * u, cx + .8f * u)
        canvas.drawRect(cx - .8f * u, by - 92 * u, cx + .8f * u, by - 72 * u, fill)
    }

    /** 끝이 살짝 들린 기와지붕 한 층. 처마 반폭 [rw], 용마루 반폭 [top]. 처마는 [y]보다 8단위, 용마루는 13단위 위. */
    private fun eaveRoof(canvas: Canvas, cx: Float, y: Float, rw: Float, top: Float, u: Float) {
        path.rewind()
        path.moveTo(cx - rw, y - 8 * u)
        path.quadTo(cx - rw * .6f, y - 10 * u, cx - top, y - 13 * u)
        path.lineTo(cx + top, y - 13 * u)
        path.quadTo(cx + rw * .6f, y - 10 * u, cx + rw, y - 8 * u)
        path.close()
        canvas.drawPath(path, fill)
    }

    /** 구슬 둘을 꿴 탑: 비스듬한 다리, 큰 구슬, 기둥, 작은 구슬, 첨탑. 구슬은 둥글게 비친다. */
    private fun pearlTower(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        stroke.color = mix(col, sky, .18f * relief)
        stroke.strokeWidth = 3 * u
        canvas.drawLine(cx - 14 * u, by, cx - 3 * u, by - 56 * u, stroke)
        stroke.color = mix(col, sun, .2f * relief)
        canvas.drawLine(cx + 14 * u, by, cx + 3 * u, by - 56 * u, stroke)
        facet(cx - 2.2f * u, cx + 2.2f * u)
        canvas.drawRect(cx - 2.2f * u, by - 150 * u, cx + 2.2f * u, by - 40 * u, fill)
        round(cx - 13 * u, cx + 13 * u)
        canvas.drawCircle(cx, by - 60 * u, 13 * u, fill)
        round(cx - 8 * u, cx + 8 * u)
        canvas.drawCircle(cx, by - 116 * u, 8 * u, fill)
        round(cx - 3.5f * u, cx + 3.5f * u)
        canvas.drawCircle(cx, by - 150 * u, 3.5f * u, fill)
        facet(cx - .7f * u, cx + .7f * u)
        canvas.drawRect(cx - .7f * u, by - 178 * u, cx + .7f * u, by - 150 * u, fill)
        if (lit > .05f) {
            flat(Color.argb((lit * 200).toInt(), 255, 140, 190))
            canvas.drawCircle(cx, by - 60 * u, 4 * u, fill)
            canvas.drawCircle(cx, by - 116 * u, 2.6f * u, fill)
        }
    }

    /** 아래가 벌어진 철탑과 밑의 아치. */
    private fun eiffel(canvas: Canvas, cx: Float, by: Float, u: Float) {
        facet(cx - 34 * u, cx + 34 * u)
        path.moveTo(cx - 34 * u, by)
        path.quadTo(cx - 12 * u, by - 40 * u, cx - 5 * u, by - 86 * u)
        path.lineTo(cx - 2 * u, by - 124 * u)
        path.lineTo(cx + 2 * u, by - 124 * u)
        path.lineTo(cx + 5 * u, by - 86 * u)
        path.quadTo(cx + 12 * u, by - 40 * u, cx + 34 * u, by)
        path.close()
        path.addCircle(cx, by, 15 * u, Path.Direction.CW)
        canvas.drawPath(path, fill)
        facet(cx - 15 * u, cx + 15 * u)
        canvas.drawRect(cx - 15 * u, by - 34 * u, cx + 15 * u, by - 31 * u, fill)
        facet(cx - 8 * u, cx + 8 * u)
        canvas.drawRect(cx - 8 * u, by - 72 * u, cx + 8 * u, by - 70 * u, fill)
        facet(cx - .6f * u, cx + .6f * u)
        canvas.drawRect(cx - .6f * u, by - 140 * u, cx + .6f * u, by - 124 * u, fill)
    }

    /**
     * 동 루이스 1세 다리(포르투): 강가에서 솟은 철제 아치가 양쪽 언덕 꼭대기를 잇는 위 상판을 받치고,
     * 아치 아래로 강가를 잇는 아래 상판이 지난다. 위 상판은 양쪽 언덕 높이([ground])에 맞춰 언덕 속으로 이어진다.
     */
    private fun archBridge(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float, ground: Ground) {
        val a = 64f
        // 위 상판 높이: 아치 양 끝 바깥 언덕 중 높은 쪽 꼭대기. 언덕이 낮아도 강에서 40단위는 띄운다.
        val yl = ground.y(-(a + 20) * u)
        val yr = ground.y((a + 20) * u)
        val deck = min(min(yl, yr) - u, by - 40 * u)
        // 상판 양 끝은 언덕 비탈에 닿을 때까지.
        var left = a + 20
        while (left < 230 && ground.y(-left * u) > deck + 2 * u) left += 4f
        var right = a + 20
        while (right < 230 && ground.y(right * u) > deck + 2 * u) right += 4f
        flat(col)
        canvas.drawRect(cx - left * u, deck - 3.4f * u, cx + right * u, deck, fill)
        underside(canvas, cx - left * u, deck - 1.2f * u, cx + right * u, 1.2f * u)
        rim(canvas, cx - left * u, deck - 3.4f * u, cx + right * u, u)
        // 아치: 바깥 굵은 테와 안쪽 가는 테, 그 사이 사선 격자. 바깥 테는 볕 쪽이 조금 밝다.
        val apex = deck + 1.5f * u
        val ctrl = 2 * apex - by
        stroke.color = col
        stroke.strokeWidth = 4.2f * u
        path.moveTo(cx - a * u, by)
        path.quadTo(cx, ctrl, cx + a * u, by)
        canvas.drawPath(path, stroke)
        stroke.color = mix(col, sun, .3f * relief)
        stroke.strokeWidth = 1.1f * u
        canvas.drawPath(path, stroke)
        val inner = 7 * u
        stroke.color = mix(col, sky, .2f * relief)
        stroke.strokeWidth = 1.4f * u
        path.rewind()
        path.moveTo(cx - (a - 6) * u, by)
        path.quadTo(cx, ctrl + inner * 2, cx + (a - 6) * u, by)
        canvas.drawPath(path, stroke)
        stroke.color = col
        stroke.strokeWidth = .7f * u
        var x = -a + 8
        var up = true
        while (x < a - 8) {
            val outer = archY(x, a, by, ctrl)
            val innerY = archY(x + 5, a - 6, by, ctrl + inner * 2)
            canvas.drawLine(cx + x * u, if (up) innerY else outer, cx + (x + 5) * u, if (up) outer else innerY, stroke)
            x += 5f
            up = !up
        }
        // 아치에서 위 상판으로 서는 수직재.
        stroke.strokeWidth = 1.1f * u
        x = -a + 10
        while (x <= a - 10) {
            val ay = archY(x, a, by, ctrl)
            if (ay > deck + 2 * u) canvas.drawLine(cx + x * u, deck, cx + x * u, ay, stroke)
            x += 10f
        }
        // 아치 양 끝의 높은 교각(위 상판까지).
        for (sgn in intArrayOf(-1, 1)) {
            val px = cx + sgn * (a + 6) * u
            facet(px - 3 * u, px + 3 * u)
            canvas.drawRect(px - 3 * u, deck, px + 3 * u, by + 4 * u, fill)
            facet(px - 4 * u, px + 4 * u)
            canvas.drawRect(px - 4 * u, deck, px + 4 * u, deck + 3 * u, fill)
        }
        // 아래 상판: 강가에서 강가까지, 아치에 매달려 지난다.
        val low = by - 16 * u
        flat(col)
        canvas.drawRect(cx - (a + 12) * u, low - 2.6f * u, cx + (a + 12) * u, low, fill)
        underside(canvas, cx - (a + 12) * u, low - 1 * u, cx + (a + 12) * u, 1 * u)
        stroke.color = col
        stroke.strokeWidth = .8f * u
        x = -a + 16
        while (x <= a - 16) {
            val ay = archY(x, a, by, ctrl)
            if (ay < low - 3 * u) canvas.drawLine(cx + x * u, ay, cx + x * u, low - 2.6f * u, stroke)
            x += 8f
        }
        if (lit > .05f) {
            flat(Color.argb((lit * 220).toInt(), 255, 214, 150))
            canvas.drawRect(cx - left * u, deck - 1.6f * u, cx + right * u, deck - .8f * u, fill)
            canvas.drawRect(cx - (a + 12) * u, low - 1.4f * u, cx + (a + 12) * u, low - .7f * u, fill)
        }
    }

    /** 양 끝이 (±[a], [by])이고 조절점 높이가 [ctrl]인 2차 곡선 아치의 x 위치 높이(px). */
    private fun archY(x: Float, a: Float, by: Float, ctrl: Float): Float {
        val t = ((x + a) / (2 * a)).coerceIn(0f, 1f)
        return by + 2 * t * (1 - t) * (ctrl - by)
    }

    /** 한빛탑: 아래가 조금 넓은 몸체, 그 위 넓적한 원반 전망대(밑면은 그늘), 띠를 두른 긴 원뿔. */
    private fun hanbitTower(canvas: Canvas, cx: Float, by: Float, u: Float, lit: Float) {
        // 둘레 아파트와 키가 비슷해 묻히지 않게 실제 비율보다 조금 크게.
        val k = 1.2f * u
        facet(cx - 12 * k, cx + 12 * k)
        path.moveTo(cx - 12 * k, by)
        path.lineTo(cx - 9 * k, by - 50 * k)
        path.lineTo(cx + 9 * k, by - 50 * k)
        path.lineTo(cx + 12 * k, by)
        path.close()
        canvas.drawPath(path, fill)
        facet(cx - 6 * k, cx + 6 * k)
        path.rewind()
        path.moveTo(cx - 6 * k, by - 56 * k)
        path.lineTo(cx - 4.4f * k, by - 82 * k)
        path.lineTo(cx - 3.8f * k, by - 85 * k)
        path.lineTo(cx - .3f * k, by - 124 * k)
        path.lineTo(cx + .3f * k, by - 124 * k)
        path.lineTo(cx + 3.8f * k, by - 85 * k)
        path.lineTo(cx + 4.4f * k, by - 82 * k)
        path.lineTo(cx + 6 * k, by - 56 * k)
        path.close()
        canvas.drawPath(path, fill)
        round(cx - 22 * k, cx + 22 * k)
        rect.set(cx - 22 * k, by - 59 * k, cx + 22 * k, by - 49 * k)
        canvas.drawOval(rect, fill)
        flat(mix(base, sky, .4f * relief))
        rect.set(cx - 21 * k, by - 56 * k, cx + 21 * k, by - 49.5f * k)
        canvas.drawArc(rect, 0f, 180f, false, fill)
        if (lit > .05f) {
            flat(Color.argb((lit * 220).toInt(), 255, 220, 160))
            canvas.drawRect(cx - 18 * k, by - 55 * k, cx + 18 * k, by - 53.6f * k, fill)
            canvas.drawCircle(cx, by - 124 * k, 1.2f * k, fill)
        }
    }

    /**
     * 현수교(광안대교): 높은 주탑 둘, 주탑 사이로 처진 주케이블과 행어, 긴 상판.
     * 밤에는 케이블을 따라 조명이 켜진다.
     */
    private fun suspensionBridge(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        val deck = by - 16 * u
        val top = by - 78 * u
        val sag = by - 24 * u
        val tower = 64f
        val end = 176f
        // 교각
        var x = -150f
        while (x <= 150f) {
            if (x < -tower - 8 || x > tower + 8) {
                facet(cx + (x - 1.4f) * u, cx + (x + 1.4f) * u)
                canvas.drawRect(cx + (x - 1.4f) * u, deck, cx + (x + 1.4f) * u, by + 4 * u, fill)
            }
            x += 30f
        }
        // 이층 상판: 위 상판 밑은 그늘, 윗모서리는 볕.
        flat(col)
        canvas.drawRect(cx - end * u, deck - 3 * u, cx + end * u, deck, fill)
        canvas.drawRect(cx - end * u, deck + 2 * u, cx + end * u, deck + 3.4f * u, fill)
        underside(canvas, cx - end * u, deck - 1 * u, cx + end * u, 1 * u)
        rim(canvas, cx - end * u, deck - 3 * u, cx + end * u, u)
        // 주탑: 기둥과 가로보 둘
        for (s in intArrayOf(-1, 1)) {
            val tx = cx + s * tower * u
            facet(tx - 2.2f * u, tx + 2.2f * u)
            canvas.drawRect(tx - 2.2f * u, top, tx + 2.2f * u, by + 4 * u, fill)
            facet(tx - 3.4f * u, tx + 3.4f * u)
            canvas.drawRect(tx - 3.4f * u, top, tx + 3.4f * u, top + 3 * u, fill)
            canvas.drawRect(tx - 3.4f * u, by - 50 * u, tx + 3.4f * u, by - 47 * u, fill)
        }
        // 케이블: 가운데 경간은 처진 곡선, 옆 경간은 주탑에서 상판 끝으로.
        stroke.color = col
        stroke.strokeWidth = 1.3f * u
        path.rewind()
        path.moveTo(cx - end * u, deck - 3 * u)
        path.quadTo(cx - (end + tower) / 2 * u, (top + deck) / 2 + 8 * u, cx - tower * u, top + 2 * u)
        path.quadTo(cx, 2 * sag - top, cx + tower * u, top + 2 * u)
        path.quadTo(cx + (end + tower) / 2 * u, (top + deck) / 2 + 8 * u, cx + end * u, deck - 3 * u)
        canvas.drawPath(path, stroke)
        // 행어
        stroke.strokeWidth = .6f * u
        x = -tower + 8
        while (x < tower) {
            val t = (x + tower) / (2 * tower)
            val cy = top + 2 * u + 2 * t * (1 - t) * (2 * sag - top - (top + 2 * u))
            canvas.drawLine(cx + x * u, cy, cx + x * u, deck - 3 * u, stroke)
            x += 8f
        }
        if (lit > .05f) {
            stroke.color = Color.argb((lit * 210).toInt(), 150, 170, 255)
            stroke.strokeWidth = 1.1f * u
            canvas.drawPath(path, stroke)
            flat(Color.argb((lit * 200).toInt(), 255, 226, 170))
            canvas.drawRect(cx - end * u, deck - 1.2f * u, cx + end * u, deck - .4f * u, fill)
        }
    }

    /**
     * 사장교(돌산대교): A자 주탑 둘에서 부채꼴로 내려오는 케이블과 상판.
     * 밤에는 케이블이 색색으로 빛난다.
     */
    private fun cableBridge(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        val deck = by - 14 * u
        val apex = by - 86 * u
        val pylon = 50f
        val end = 150f
        var x = -135f
        while (x <= 135f) {
            if (x < -pylon - 10 || x > pylon + 10 || x == 0f) {
                facet(cx + (x - 1.3f) * u, cx + (x + 1.3f) * u)
                canvas.drawRect(cx + (x - 1.3f) * u, deck, cx + (x + 1.3f) * u, by + 4 * u, fill)
            }
            x += 45f
        }
        flat(col)
        canvas.drawRect(cx - end * u, deck - 3 * u, cx + end * u, deck, fill)
        underside(canvas, cx - end * u, deck - 1 * u, cx + end * u, 1 * u)
        rim(canvas, cx - end * u, deck - 3 * u, cx + end * u, u)
        for (s in intArrayOf(-1, 1)) {
            val px = cx + s * pylon * u
            // A자 다리 둘과 꼭대기
            facet(px - 7 * u, px + 7 * u)
            path.rewind()
            path.moveTo(px - 7 * u, by + 4 * u)
            path.lineTo(px - 1.2f * u, apex)
            path.lineTo(px + 1.2f * u, apex)
            path.lineTo(px + 7 * u, by + 4 * u)
            path.lineTo(px + 4.6f * u, by + 4 * u)
            path.lineTo(px, apex + 14 * u)
            path.lineTo(px - 4.6f * u, by + 4 * u)
            path.close()
            canvas.drawPath(path, fill)
            facet(px - 5.6f * u, px + 5.6f * u)
            canvas.drawRect(px - 5.6f * u, deck - 1 * u, px + 5.6f * u, deck + 2 * u, fill)
            facet(px - .9f * u, px + .9f * u)
            canvas.drawRect(px - .9f * u, apex - 6 * u, px + .9f * u, apex, fill)
        }
        for (pass in 0..1) {
            if (pass == 1 && lit <= .05f) break
            stroke.strokeWidth = (if (pass == 0) .7f else .9f) * u
            for (s in intArrayOf(-1, 1)) {
                val px = cx + s * pylon * u
                for (k in 0..4) {
                    if (pass == 1) {
                        // 밤 조명: 주탑마다 다른 색.
                        stroke.color = if (s < 0) Color.argb((lit * 200).toInt(), 110, 210, 255) else Color.argb((lit * 200).toInt(), 230, 130, 255)
                    } else {
                        stroke.color = col
                    }
                    val cy = apex + (6 + k * 3.5f) * u
                    val reach = (12 + k * 9) * u
                    canvas.drawLine(px, cy, px - reach, deck - 3 * u, stroke)
                    canvas.drawLine(px, cy, px + reach, deck - 3 * u, stroke)
                }
            }
        }
        if (lit > .05f) {
            flat(Color.argb((lit * 200).toInt(), 255, 226, 170))
            canvas.drawRect(cx - end * u, deck - 1.2f * u, cx + end * u, deck - .4f * u, fill)
        }
    }

    /** 능선을 따라 선 풍력발전기들. 날개가 천천히 돌고, 밤에는 꼭대기에 빨간 불이 깜박인다. */
    private fun windFarm(
        canvas: Canvas, p: Palette, cx: Float, u: Float, col: Int, lit: Float, time: Double, ground: Ground,
    ) {
        // 낮에는 흰 기둥이 산보다 밝게 보인다.
        val white = mix(col, mix(Color.WHITE, p.c[Palette.HAZE], .35f), .55f * (1 - night))
        tone(white)
        blade.color = white
        blade.strokeWidth = 1.2f * u
        for (i in TURBINE_X.indices) {
            val dx = TURBINE_X[i] * u
            val x = cx + dx
            val gy = ground.y(dx) + 2 * u
            val top = gy - TURBINE_H[i] * u
            facet(x - 1.1f * u, x + 1.1f * u)
            path.rewind()
            path.moveTo(x - 1.1f * u, gy)
            path.lineTo(x - .5f * u, top)
            path.lineTo(x + .5f * u, top)
            path.lineTo(x + 1.1f * u, gy)
            path.close()
            canvas.drawPath(path, fill)
            facet(x - 1.4f * u, x + 2.2f * u)
            canvas.drawRect(x - 1.4f * u, top - 1.2f * u, x + 2.2f * u, top + 1f * u, fill)
            val a = time * .9 + i * 1.7
            for (k in 0..2) {
                val th = a + k * 2 * PI / 3
                canvas.drawLine(x, top, x + (cos(th) * 15 * u).toFloat(), top + (sin(th) * 15 * u).toFloat(), blade)
            }
            if (lit > .05f && sin(time * 2.4 + i) > .55) {
                flat(Color.argb((lit * 255).toInt(), 255, 60, 50))
                canvas.drawCircle(x, top - 1.6f * u, 1f * u, fill)
            }
        }
    }

    /** 도쿄 타워: 벌어진 다리와 아치, 큰 전망대, 작은 전망대, 안테나. 낮에는 주황빛에 흰 띠, 밤에는 주황 조명. */
    private fun tokyoTower(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        val warm = dye(col, Color.rgb(206, 92, 60), .3f)
        tone(mix(warm, Color.rgb(255, 150, 70), .75f * lit))
        facet(cx - 30 * u, cx + 30 * u)
        path.moveTo(cx - 30 * u, by)
        path.quadTo(cx - 13 * u, by - 30 * u, cx - 10 * u, by - 56 * u)
        path.lineTo(cx + 10 * u, by - 56 * u)
        path.quadTo(cx + 13 * u, by - 30 * u, cx + 30 * u, by)
        path.close()
        path.addCircle(cx, by, 13 * u, Path.Direction.CW)
        canvas.drawPath(path, fill)
        facet(cx - 13 * u, cx + 13 * u)
        canvas.drawRect(cx - 13 * u, by - 64 * u, cx + 13 * u, by - 55 * u, fill)
        underside(canvas, cx - 13 * u, by - 56 * u, cx + 13 * u, 1.2f * u)
        facet(cx - 8 * u, cx + 8 * u)
        path.rewind()
        path.moveTo(cx - 8 * u, by - 64 * u)
        path.lineTo(cx - 4 * u, by - 92 * u)
        path.lineTo(cx + 4 * u, by - 92 * u)
        path.lineTo(cx + 8 * u, by - 64 * u)
        path.close()
        canvas.drawPath(path, fill)
        facet(cx - 5.5f * u, cx + 5.5f * u)
        canvas.drawRect(cx - 5.5f * u, by - 97 * u, cx + 5.5f * u, by - 91 * u, fill)
        facet(cx - 3 * u, cx + 3 * u)
        path.rewind()
        path.moveTo(cx - 3 * u, by - 97 * u)
        path.lineTo(cx - 1.2f * u, by - 112 * u)
        path.lineTo(cx + 1.2f * u, by - 112 * u)
        path.lineTo(cx + 3 * u, by - 97 * u)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawRect(cx - .6f * u, by - 136 * u, cx + .6f * u, by - 112 * u, fill)
        // 흰 띠(낮에만 보인다).
        if (night < .5f && lit < .3f) {
            flat(dye(base, Color.WHITE, .35f))
            canvas.drawRect(cx - 11.6f * u, by - 46 * u, cx + 11.6f * u, by - 44 * u, fill)
            canvas.drawRect(cx - 5.4f * u, by - 80 * u, cx + 5.4f * u, by - 78 * u, fill)
        }
    }

    /** 오사카성: 돌 축대 위 다섯 층 천수각. 흰 벽과 짙은 청동 지붕, 층마다 들린 처마, 둘째·넷째 층에 박공. */
    private fun castle(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        val stone = dye(col, Color.rgb(120, 116, 104), .2f)
        val wall = dye(col, Color.WHITE, .3f)
        val roof = dye(col, Color.rgb(60, 82, 84), .32f)
        tone(stone)
        facet(cx - 38 * u, cx + 38 * u)
        path.moveTo(cx - 38 * u, by)
        path.lineTo(cx - 31 * u, by - 18 * u)
        path.lineTo(cx + 31 * u, by - 18 * u)
        path.lineTo(cx + 38 * u, by)
        path.close()
        canvas.drawPath(path, fill)
        var top = 0f
        for (i in 0..4) {
            val y = by - (18 + i * 12) * u
            val bw = (24 - i * 3.8f) * u
            tone(wall)
            facet(cx - bw, cx + bw)
            canvas.drawRect(cx - bw, y - 9 * u, cx + bw, y, fill)
            if (lit > .05f && i < 4) {
                flat(Color.argb((lit * 170).toInt(), 255, 214, 150))
                canvas.drawRect(cx - bw * .7f, y - 5.5f * u, cx + bw * .7f, y - 4.5f * u, fill)
            }
            underside(canvas, cx - bw, y - 8 * u, cx + bw, 1.6f * u)
            tone(roof)
            facet(cx - bw - 6 * u, cx + bw + 6 * u)
            eaveRoof(canvas, cx, y, bw + 6 * u, bw * .7f, u)
            if (i == 1 || i == 3) {
                path.rewind()
                path.moveTo(cx - 7 * u, y - 12 * u)
                path.lineTo(cx, y - 19 * u)
                path.lineTo(cx + 7 * u, y - 12 * u)
                path.close()
                canvas.drawPath(path, fill)
            }
            top = y - 13 * u
        }
        // 용마루 양 끝의 금빛 장식.
        flat(dye(col, Color.rgb(214, 180, 90), .45f))
        val tw = (24 - 4 * 3.8f) * .7f * u
        for (s in intArrayOf(-1, 1)) {
            path.rewind()
            path.moveTo(cx + s * tw, top)
            path.lineTo(cx + s * (tw + 1.6f * u), top - 4 * u)
            path.lineTo(cx + s * (tw - 1.2f * u), top)
            path.close()
            canvas.drawPath(path, fill)
        }
    }

    /** 천단 기년전: 흰 대리석 세 단 기단, 붉은 원형 몸체, 위로 갈수록 좁아지는 푸른 둥근 지붕 셋, 금빛 꼭대기. */
    private fun templeOfHeaven(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int) {
        // 넓고 낮아서 역 둘레 아파트 위로 지붕 셋이 보이도록 크게.
        val k = 1.7f * u
        tone(dye(col, Color.WHITE, .28f))
        for (i in 0..2) {
            val hw = (42 - i * 7) * k
            val y = by - (4 + i * 4) * k
            facet(cx - hw, cx + hw)
            canvas.drawRect(cx - hw, y, cx + hw, y + 4 * k, fill)
            rim(canvas, cx - hw, y, cx + hw, u)
        }
        var y = by - 12 * k
        val red = dye(col, Color.rgb(150, 64, 52), .3f)
        val blue = dye(col, Color.rgb(48, 70, 128), .34f)
        for (i in 0..2) {
            val bw = (15 - i * 3) * k
            val bh = (if (i == 0) 10 else 7) * k
            val rw = (25 - i * 5.5f) * k
            val rise = (8 + i * 1.5f) * k
            val tip = if (i == 2) 1.6f * k else bw * .8f
            tone(red)
            round(cx - bw, cx + bw)
            canvas.drawRect(cx - bw, y - bh, cx + bw, y, fill)
            y -= bh
            tone(blue)
            round(cx - rw, cx + rw)
            path.rewind()
            path.moveTo(cx - rw, y)
            path.quadTo(cx - rw * .45f, y - rise * .35f, cx - tip, y - rise)
            path.lineTo(cx + tip, y - rise)
            path.quadTo(cx + rw * .45f, y - rise * .35f, cx + rw, y)
            path.close()
            canvas.drawPath(path, fill)
            y -= rise
        }
        tone(dye(col, Color.rgb(214, 180, 90), .45f))
        facet(cx - .7f * k, cx + .7f * k)
        canvas.drawRect(cx - .7f * k, y - 4 * k, cx + .7f * k, y, fill)
        round(cx - 2.2f * k, cx + 2.2f * k)
        canvas.drawCircle(cx, y - 5 * k, 2.2f * k, fill)
    }

    /**
     * 광저우 타워: 허리가 잘록한 격자 몸체(쌍곡면)와 긴 안테나.
     * 몸체는 반투명으로 채우고 세로 갈비·가로 고리를 그어 격자처럼 보이게 한다. 밤에는 보랏빛 조명.
     */
    private fun cantonTower(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        val h = 150f
        path.moveTo(cx - CANTON_HW[0] * u, by)
        for (i in 1 until CANTON_HW.size) path.lineTo(cx - CANTON_HW[i] * u, by - h * i / (CANTON_HW.size - 1) * u)
        for (i in CANTON_HW.indices.reversed()) path.lineTo(cx + CANTON_HW[i] * u, by - h * i / (CANTON_HW.size - 1) * u)
        path.close()
        round(cx - CANTON_HW[0] * u, cx + CANTON_HW[0] * u)
        fill.alpha = 150
        canvas.drawPath(path, fill)
        stroke.color = col
        stroke.strokeWidth = 1.1f * u
        canvas.drawPath(path, stroke)
        stroke.strokeWidth = .6f * u
        for (f in CANTON_RIBS) {
            // 볕 쪽 갈비는 밝게, 그늘 쪽은 어둡게.
            stroke.color = if (f > 0) mix(col, sun, .25f * relief) else mix(col, sky, .2f * relief)
            var px = cx + f * CANTON_HW[0] * u
            var py = by
            for (i in 1 until CANTON_HW.size) {
                val nx = cx + f * CANTON_HW[i] * u
                val ny = by - h * i / (CANTON_HW.size - 1) * u
                canvas.drawLine(px, py, nx, ny, stroke)
                px = nx
                py = ny
            }
        }
        stroke.color = col
        for (i in 2 until CANTON_HW.size step 2) {
            val y = by - h * i / (CANTON_HW.size - 1) * u
            canvas.drawLine(cx - CANTON_HW[i] * u, y, cx + CANTON_HW[i] * u, y, stroke)
        }
        facet(cx - 11 * u, cx + 11 * u)
        canvas.drawRect(cx - 11 * u, by - (h + 3) * u, cx + 11 * u, by - (h - 1) * u, fill)
        facet(cx - 3.4f * u, cx + 3.4f * u)
        path.rewind()
        path.moveTo(cx - 3.4f * u, by - h * u)
        path.lineTo(cx - .6f * u, by - 206 * u)
        path.lineTo(cx + .6f * u, by - 206 * u)
        path.lineTo(cx + 3.4f * u, by - h * u)
        path.close()
        canvas.drawPath(path, fill)
        if (lit > .05f) {
            path.rewind()
            path.moveTo(cx - CANTON_HW[0] * u, by)
            for (i in 1 until CANTON_HW.size) path.lineTo(cx - CANTON_HW[i] * u, by - h * i / (CANTON_HW.size - 1) * u)
            for (i in CANTON_HW.indices.reversed()) path.lineTo(cx + CANTON_HW[i] * u, by - h * i / (CANTON_HW.size - 1) * u)
            path.close()
            flat(Color.argb((lit * 120).toInt(), 214, 96, 230))
            canvas.drawPath(path, fill)
            stroke.color = Color.argb((lit * 220).toInt(), 255, 150, 240)
            stroke.strokeWidth = 1f * u
            canvas.drawPath(path, stroke)
        }
    }

    /** 노트르담 드 라 가르드: 언덕, 돔을 얹은 성당, 높은 종탑과 그 위 금빛 성모상. */
    private fun hillBasilica(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        // 바다에 아래가 잠기므로 언덕 위 성당이 또렷하게 보이도록 크게.
        val k = 1.4f * u
        round(cx - 92 * k, cx + 92 * k)
        path.moveTo(cx - 92 * k, by)
        path.quadTo(cx - 40 * k, by - 34 * k, cx, by - 36 * k)
        path.quadTo(cx + 40 * k, by - 34 * k, cx + 92 * k, by)
        path.close()
        canvas.drawPath(path, fill)
        val g = by - 34 * k
        tone(dye(col, Color.rgb(226, 214, 196), .2f))
        // 본당과 지붕
        facet(cx - 22 * k, cx + 6 * k)
        canvas.drawRect(cx - 22 * k, g - 12 * k, cx + 6 * k, g + 2 * k, fill)
        facet(cx - 23 * k, cx + 7 * k)
        path.rewind()
        path.moveTo(cx - 23 * k, g - 12 * k)
        path.lineTo(cx - 8 * k, g - 16 * k)
        path.lineTo(cx + 7 * k, g - 12 * k)
        path.close()
        canvas.drawPath(path, fill)
        // 돔
        facet(cx - 15 * k, cx - 5 * k)
        canvas.drawRect(cx - 15 * k, g - 19 * k, cx - 5 * k, g - 12 * k, fill)
        round(cx - 15 * k, cx - 5 * k)
        canvas.drawCircle(cx - 10 * k, g - 19 * k, 5 * k, fill)
        canvas.drawRect(cx - 10.6f * k, g - 28 * k, cx - 9.4f * k, g - 23 * k, fill)
        // 종탑, 종루, 받침
        facet(cx + 5 * k, cx + 13 * k)
        canvas.drawRect(cx + 5 * k, g - 38 * k, cx + 13 * k, g + 2 * k, fill)
        facet(cx + 4 * k, cx + 14 * k)
        canvas.drawRect(cx + 4 * k, g - 46 * k, cx + 14 * k, g - 38 * k, fill)
        facet(cx + 7.4f * k, cx + 10.6f * k)
        canvas.drawRect(cx + 7.4f * k, g - 53 * k, cx + 10.6f * k, g - 46 * k, fill)
        // 성모상: 낮에도 금빛, 밤에는 비친다.
        flat(if (lit > .05f) Color.argb((150 + lit * 100).toInt(), 255, 210, 120) else dye(col, Color.rgb(214, 180, 90), .45f))
        path.rewind()
        path.moveTo(cx + 7.4f * k, g - 53 * k)
        path.lineTo(cx + 9 * k, g - 63 * k)
        path.lineTo(cx + 10.6f * k, g - 53 * k)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawCircle(cx + 9 * k, g - 63.5f * k, 1.3f * k, fill)
    }

    /** 상조르즈 성: 언덕 꼭대기를 두른 톱니 성벽과 그 앞에 튀어나온 네모난 탑들. 밤에는 성벽이 노랗게 비친다. */
    private fun castleWalls(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        // 성벽은 긴 면이라 한 색으로 두고 윗모서리만 밝게, 탑마다 옆면 그늘로 튀어나와 보이게 한다.
        flat(mix(col, sky, .1f * relief))
        canvas.drawRect(cx - 56 * u, by - 18 * u, cx + 56 * u, by + 6 * u, fill)
        battlements(canvas, cx - 56 * u, cx + 56 * u, by - 18 * u, u)
        rim(canvas, cx - 56 * u, by - 18 * u, cx + 56 * u, u)
        for (i in TOWER_X.indices) {
            val tx = cx + TOWER_X[i] * u
            val th = TOWER_H[i] * u
            tone(col)
            facet(tx - 6 * u, tx + 6 * u)
            canvas.drawRect(tx - 6 * u, by - th, tx + 6 * u, by, fill)
            battlements(canvas, tx - 6 * u, tx + 6 * u, by - th, u)
            // 총안 창.
            flat(mix(col, sky, .5f * relief))
            canvas.drawRect(tx - .8f * u, by - th + 7 * u, tx + .8f * u, by - th + 11 * u, fill)
        }
        if (lit > .05f) {
            flat(Color.argb((lit * 120).toInt(), 255, 210, 140))
            canvas.drawRect(cx - 56 * u, by - 18 * u, cx + 56 * u, by, fill)
        }
    }

    /** 성벽 위 톱니(총안). 지금 칠(명암 포함)을 그대로 쓴다. */
    private fun battlements(canvas: Canvas, x0: Float, x1: Float, top: Float, u: Float) {
        var x = x0
        while (x < x1 - u) {
            canvas.drawRect(x, top - 3 * u, min(x + 2.6f * u, x1), top, fill)
            x += 5 * u
        }
    }

    /** 코임브라 대학: 언덕 꼭대기의 긴 대학 건물(주황 기와)과 그 옆에 높이 솟은 종탑(시계와 작은 돔). */
    private fun universityTower(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        flat(mix(col, sky, .08f * relief))
        canvas.drawRect(cx - 52 * u, by - 20 * u, cx + 6 * u, by + 6 * u, fill)
        tone(col)
        // 창 줄.
        flat(mix(col, sky, .32f * relief))
        var wx = cx - 48 * u
        while (wx < cx + 2 * u) {
            canvas.drawRect(wx, by - 14 * u, wx + 2 * u, by - 9 * u, fill)
            wx += 6 * u
        }
        flat(dye(col, Color.rgb(184, 100, 70), .45f))
        path.moveTo(cx - 54 * u, by - 20 * u)
        path.lineTo(cx - 23 * u, by - 27 * u)
        path.lineTo(cx + 8 * u, by - 20 * u)
        path.close()
        canvas.drawPath(path, fill)
        // 종탑: 네모난 몸통, 종루, 작은 돔과 꼭대기 장식.
        tone(col)
        facet(cx + 6 * u, cx + 18 * u)
        canvas.drawRect(cx + 6 * u, by - 58 * u, cx + 18 * u, by + 6 * u, fill)
        facet(cx + 5 * u, cx + 19 * u)
        canvas.drawRect(cx + 5 * u, by - 62 * u, cx + 19 * u, by - 58 * u, fill)
        facet(cx + 7.5f * u, cx + 16.5f * u)
        canvas.drawRect(cx + 7.5f * u, by - 70 * u, cx + 16.5f * u, by - 62 * u, fill)
        round(cx + 7.5f * u, cx + 16.5f * u)
        canvas.drawCircle(cx + 12 * u, by - 70 * u, 4.5f * u, fill)
        facet(cx + 11.4f * u, cx + 12.6f * u)
        canvas.drawRect(cx + 11.4f * u, by - 80 * u, cx + 12.6f * u, by - 74 * u, fill)
        // 시계 자리.
        flat(Color.argb(70, 0, 0, 0))
        canvas.drawCircle(cx + 12 * u, by - 50 * u, 2.6f * u, fill)
        if (lit > .05f) {
            flat(Color.argb((lit * 200).toInt(), 255, 220, 160))
            canvas.drawCircle(cx + 12 * u, by - 50 * u, 2.2f * u, fill)
            canvas.drawRect(cx + 8.5f * u, by - 68 * u, cx + 15.5f * u, by - 64 * u, fill)
        }
    }

    /** 바다 위 낮은 섬들. 꼭대기가 둥근 언덕 모양이고, 섬마다 왼쪽 비탈은 그늘. */
    private fun islands(canvas: Canvas, cx: Float, by: Float, u: Float) {
        for (i in ISLE_X.indices) {
            val x = cx + ISLE_X[i] * u
            val half = ISLE_HALF[i] * u
            val h = ISLE_H[i] * u
            round(x - half, x + half)
            path.rewind()
            path.moveTo(x - half, by)
            path.quadTo(x - half * .45f, by - h * 1.15f, x, by - h)
            path.quadTo(x + half * .5f, by - h * 1.1f, x + half, by)
            path.close()
            canvas.drawPath(path, fill)
        }
    }

    /**
     * 평창 스키점프대: 언덕 꼭대기([by])의 높은 탑과 넓적한 꼭대기, 탑에서 휘어 내려오는 활강로,
     * 도약대 아래로 언덕 비탈([ground])을 따라 내려가는 흰 착지 비탈.
     */
    private fun skiJump(canvas: Canvas, p: Palette, cx: Float, by: Float, u: Float, col: Int, lit: Float, ground: Ground) {
        val snow = mix(mix(Color.WHITE, p.c[Palette.HAZE], .25f), p.c[Palette.TOP], .3f * night)
        // 착지 비탈: 도약대 아래부터 언덕을 따라 내려가는 흰 띠.
        path.moveTo(cx - 44 * u, ground.y(-44 * u) - 3 * u)
        var x = -48f
        while (x >= -140f) {
            path.lineTo(cx + x * u, ground.y(x * u) - 3 * u)
            x -= 6f
        }
        x = -140f
        while (x <= -44f) {
            path.lineTo(cx + x * u, ground.y(x * u) + 3 * u)
            x += 6f
        }
        path.close()
        flat(snow)
        canvas.drawPath(path, fill)
        // 활강로: 탑 중간에서 가파르게 내려와 도약대에서 눕는다. 아래에 받침대.
        val tx = cx - 40 * u
        val ty = ground.y(-40 * u) - 9 * u
        stroke.color = col
        stroke.strokeWidth = .9f * u
        var k = 0
        while (k < 4) {
            val t = (k + 1) / 5f
            val rx = cx - 4 * u + (tx - cx + 4 * u) * t
            val ry = by - 52 * u + (ty - by + 52 * u) * t * t
            canvas.drawLine(rx, ry, rx, ground.y(rx - cx), stroke)
            k++
        }
        stroke.color = snow
        stroke.strokeWidth = 3.4f * u
        path.rewind()
        path.moveTo(cx - 4 * u, by - 52 * u)
        path.quadTo(cx - 16 * u, ty, tx, ty)
        canvas.drawPath(path, stroke)
        // 탑
        tone(col)
        facet(cx - 3 * u, cx + 3 * u)
        canvas.drawRect(cx - 3 * u, by - 64 * u, cx + 3 * u, by, fill)
        facet(cx - 10 * u, cx + 10 * u)
        path.rewind()
        path.moveTo(cx - 3.5f * u, by - 64 * u)
        path.lineTo(cx - 10 * u, by - 74 * u)
        path.lineTo(cx + 10 * u, by - 74 * u)
        path.lineTo(cx + 3.5f * u, by - 64 * u)
        path.close()
        canvas.drawPath(path, fill)
        facet(cx - 8 * u, cx + 8 * u)
        canvas.drawRect(cx - 8 * u, by - 78 * u, cx + 8 * u, by - 74 * u, fill)
        canvas.drawRect(cx - .6f * u, by - 88 * u, cx + .6f * u, by - 78 * u, fill)
        if (lit > .05f) {
            flat(Color.argb((lit * 220).toInt(), 255, 120, 110))
            canvas.drawRect(cx - 8 * u, by - 77 * u, cx + 8 * u, by - 75.5f * u, fill)
            flat(Color.argb((lit * 160).toInt(), 200, 150, 255))
            canvas.drawRect(cx - 1.6f * u, by - 62 * u, cx + 1.6f * u, by - 10 * u, fill)
        }
    }

    /** 교토 타워: 층 줄무늬가 있는 넓은 건물 위에 선 흰 촛대 모양 탑, 둥근 전망대, 붉은 띠, 짧은 첨탑. */
    private fun kyotoTower(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        tone(mix(dye(col, Color.WHITE, .15f), Color.rgb(255, 214, 160), .6f * lit))
        facet(cx - 32 * u, cx + 32 * u)
        canvas.drawRect(cx - 32 * u, by - 34 * u, cx + 32 * u, by, fill)
        stripes(canvas, cx - 24 * u, cx + 31 * u, by - 31 * u, by - 2 * u, 3.4f * u, u)
        rim(canvas, cx - 32 * u, by - 34 * u, cx + 32 * u, u)
        tone(mix(dye(col, Color.WHITE, .15f), Color.rgb(255, 214, 160), .6f * lit))
        facet(cx - 6 * u, cx + 6 * u)
        path.moveTo(cx - 6 * u, by - 34 * u)
        path.lineTo(cx - 3.4f * u, by - 92 * u)
        path.lineTo(cx + 3.4f * u, by - 92 * u)
        path.lineTo(cx + 6 * u, by - 34 * u)
        path.close()
        canvas.drawPath(path, fill)
        round(cx - 7 * u, cx + 7 * u)
        rect.set(cx - 7 * u, by - 103 * u, cx + 7 * u, by - 90 * u)
        canvas.drawRoundRect(rect, 3 * u, 3 * u, fill)
        underside(canvas, cx - 6.5f * u, by - 91.5f * u, cx + 6.5f * u, 1.5f * u)
        tone(base)
        facet(cx - 3 * u, cx + 3 * u)
        canvas.drawRect(cx - 3 * u, by - 110 * u, cx + 3 * u, by - 103 * u, fill)
        canvas.drawRect(cx - .7f * u, by - 126 * u, cx + .7f * u, by - 110 * u, fill)
        // 전망대 위 붉은 띠.
        flat(mix(Color.rgb(196, 70, 60), col, .4f))
        canvas.drawRect(cx - 3 * u, by - 111 * u, cx + 3 * u, by - 108 * u, fill)
        if (lit > .05f) {
            flat(Color.argb((lit * 220).toInt(), 255, 226, 170))
            canvas.drawRect(cx - 6 * u, by - 98 * u, cx + 6 * u, by - 96.5f * u, fill)
        }
    }

    /** 층 줄무늬: 유리·창 띠가 층마다 가로로 지나간다(일반 건물의 층 줄과 같은 느낌). */
    private fun stripes(canvas: Canvas, x0: Float, x1: Float, y0: Float, y1: Float, step: Float, u: Float) {
        flat(mix(base, sky, .2f * relief))
        var y = y0
        while (y < y1) {
            canvas.drawRect(x0, y, x1, y + max(1f, .6f * u), fill)
            y += step
        }
    }

    /** 나고야역 위 쌍둥이 타워: 넓은 기단, 둥근 호텔 탑, 조금 더 높은 네모난 사무 탑. 두 탑 다 층 줄무늬. */
    private fun twinTowers(canvas: Canvas, cx: Float, by: Float, u: Float, lit: Float) {
        val col = base
        facet(cx - 44 * u, cx + 40 * u)
        canvas.drawRect(cx - 44 * u, by - 26 * u, cx + 40 * u, by, fill)
        rim(canvas, cx - 44 * u, by - 26 * u, cx + 40 * u, u)
        tone(col)
        round(cx - 32 * u, cx - 8 * u)
        rect.set(cx - 32 * u, by - 152 * u, cx - 8 * u, by - 20 * u)
        canvas.drawRoundRect(rect, 12 * u, 12 * u, fill)
        canvas.drawRect(cx - 32 * u, by - 140 * u, cx - 8 * u, by - 26 * u, fill)
        stripes(canvas, cx - 29 * u, cx - 10 * u, by - 140 * u, by - 28 * u, 5 * u, u)
        tone(col)
        facet(cx + 2 * u, cx + 28 * u)
        path.moveTo(cx + 2 * u, by - 26 * u)
        path.lineTo(cx + 2 * u, by - 164 * u)
        path.lineTo(cx + 7 * u, by - 170 * u)
        path.lineTo(cx + 23 * u, by - 170 * u)
        path.lineTo(cx + 28 * u, by - 164 * u)
        path.lineTo(cx + 28 * u, by - 26 * u)
        path.close()
        canvas.drawPath(path, fill)
        stripes(canvas, cx + 9 * u, cx + 27 * u, by - 160 * u, by - 28 * u, 5 * u, u)
        if (lit > .05f) {
            flat(Color.argb((lit * 150).toInt(), 255, 224, 170))
            var y = by - 40 * u
            while (y > by - 150 * u) {
                canvas.drawRect(cx - 29 * u, y, cx - 11 * u, y + .9f * u, fill)
                canvas.drawRect(cx + 5 * u, y - 6 * u, cx + 25 * u, y - 5.1f * u, fill)
                y -= 12 * u
            }
        }
    }

    /** 상하이 푸둥: 층층이 좁아지는 진마오, 꼭대기에 사다리꼴 구멍이 뚫린 세계금융센터, 가장 높은 상하이 타워. */
    private fun pudong(canvas: Canvas, cx: Float, by: Float, u: Float, lit: Float) {
        // 진마오
        val jx = cx - 46 * u
        facet(jx - 11 * u, jx + 11 * u)
        path.moveTo(jx - 11 * u, by)
        for (i in JINMAO_W.indices) {
            path.lineTo(jx - JINMAO_W[i] * u, by - JINMAO_Y[i] * u)
            if (i + 1 < JINMAO_W.size) path.lineTo(jx - JINMAO_W[i + 1] * u, by - JINMAO_Y[i] * u)
        }
        path.lineTo(jx, by - 166 * u)
        for (i in JINMAO_W.indices.reversed()) {
            if (i + 1 < JINMAO_W.size) path.lineTo(jx + JINMAO_W[i + 1] * u, by - JINMAO_Y[i] * u)
            path.lineTo(jx + JINMAO_W[i] * u, by - JINMAO_Y[i] * u)
        }
        path.lineTo(jx + 11 * u, by)
        path.close()
        canvas.drawPath(path, fill)
        // 세계금융센터: 꼭대기 구멍은 짝수-홀수 채우기로 뚫는다.
        facet(cx - 13 * u, cx + 13 * u)
        path.rewind()
        path.moveTo(cx - 13 * u, by)
        path.lineTo(cx - 5 * u, by - 182 * u)
        path.lineTo(cx + 5 * u, by - 182 * u)
        path.lineTo(cx + 13 * u, by)
        path.close()
        path.moveTo(cx - 4 * u, by - 176 * u)
        path.lineTo(cx + 4 * u, by - 176 * u)
        path.lineTo(cx + 3.4f * u, by - 164 * u)
        path.lineTo(cx - 3.4f * u, by - 164 * u)
        path.close()
        canvas.drawPath(path, fill)
        // 상하이 타워: 위로 갈수록 가늘어지며 살짝 부푼 둥근 몸통과 비스듬한 꼭대기.
        val sx = cx + 46 * u
        round(sx - 15 * u, sx + 14 * u)
        path.rewind()
        path.moveTo(sx - 15 * u, by)
        path.quadTo(sx - 15 * u, by - 120 * u, sx - 5 * u, by - 210 * u)
        path.lineTo(sx + 4 * u, by - 218 * u)
        path.quadTo(sx + 14 * u, by - 120 * u, sx + 14 * u, by)
        path.close()
        canvas.drawPath(path, fill)
        if (lit > .05f) {
            stroke.color = Color.argb((lit * 200).toInt(), 140, 200, 255)
            stroke.strokeWidth = .9f * u
            canvas.drawLine(sx - 5 * u, by - 210 * u, sx + 4 * u, by - 218 * u, stroke)
            canvas.drawRect(cx - 4 * u, by - 176 * u, cx + 4 * u, by - 164 * u, stroke)
            flat(Color.argb((lit * 200).toInt(), 255, 220, 150))
            canvas.drawRect(jx - .8f * u, by - 166 * u, jx + .8f * u, by - 140 * u, fill)
        }
    }

    /** 구이린 일월쌍탑: 아홉 층 해 탑과 일곱 층 달 탑. 층마다 처마가 살짝 들린다. 밤에는 금빛·은빛. */
    private fun twinPagodas(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        slimPagoda(canvas, cx - 16 * u, by, u, 9, 6.4f, 9.2f, col, lit, Color.rgb(255, 196, 90))
        slimPagoda(canvas, cx + 15 * u, by, u, 7, 5.4f, 8.6f, col, lit, Color.rgb(226, 236, 255))
    }

    private fun slimPagoda(
        canvas: Canvas, x: Float, by: Float, u: Float, tiers: Int, half0: Float, tierH: Float, col: Int, lit: Float, glow: Int,
    ) {
        for (i in 0 until tiers) {
            val y = by - i * tierH * u
            val hw = (half0 - i * .35f) * u
            tone(col)
            facet(x - hw, x + hw)
            canvas.drawRect(x - hw, y - tierH * u, x + hw, y, fill)
            if (lit > .05f) {
                flat((glow and 0x00ffffff) or ((lit * 200).toInt() shl 24))
                canvas.drawRect(x - hw * .7f, y - tierH * u * .7f, x + hw * .7f, y - tierH * u * .3f, fill)
            }
            underside(canvas, x - hw, y - tierH * u + .5f * u, x + hw, 1.2f * u)
            tone(col)
            facet(x - hw - 4 * u, x + hw + 4 * u)
            path.rewind()
            path.moveTo(x - hw - 4 * u, y - tierH * u + .5f * u)
            path.quadTo(x - hw, y - tierH * u + 1.6f * u, x - hw * .6f, y - tierH * u - 1.8f * u)
            path.lineTo(x + hw * .6f, y - tierH * u - 1.8f * u)
            path.quadTo(x + hw, y - tierH * u + 1.6f * u, x + hw + 4 * u, y - tierH * u + .5f * u)
            path.close()
            canvas.drawPath(path, fill)
        }
        val top = by - tiers * tierH * u - 1.8f * u
        facet(x - .8f * u, x + .8f * u)
        canvas.drawRect(x - .8f * u, top - 14 * u, x + .8f * u, top, fill)
    }

    /** 파리 리옹역 시계탑: 역사 지붕 옆 네모난 탑, 네 면 시계, 종 모양 돔과 작은 랜턴. */
    private fun clockTower(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        flat(mix(col, sky, .08f * relief))
        canvas.drawRect(cx - 76 * u, by - 24 * u, cx - 8 * u, by, fill)
        tone(col)
        stripes(canvas, cx - 74 * u, cx - 10 * u, by - 17 * u, by - 2 * u, 7 * u, u)
        tone(col)
        rim(canvas, cx - 76 * u, by - 24 * u, cx - 8 * u, u)
        tone(dye(col, Color.rgb(120, 130, 140), .2f))
        round(cx - 70 * u, cx - 14 * u)
        rect.set(cx - 70 * u, by - 34 * u, cx - 14 * u, by - 18 * u)
        canvas.drawArc(rect, 180f, 180f, true, fill)
        tone(col)
        facet(cx - 7 * u, cx + 7 * u)
        canvas.drawRect(cx - 7 * u, by - 62 * u, cx + 7 * u, by, fill)
        facet(cx - 8.5f * u, cx + 8.5f * u)
        canvas.drawRect(cx - 8.5f * u, by - 78 * u, cx + 8.5f * u, by - 62 * u, fill)
        facet(cx - 9.5f * u, cx + 9.5f * u)
        canvas.drawRect(cx - 9.5f * u, by - 80 * u, cx + 9.5f * u, by - 77 * u, fill)
        tone(dye(col, Color.rgb(110, 118, 128), .25f))
        round(cx - 8 * u, cx + 8 * u)
        path.moveTo(cx - 8 * u, by - 80 * u)
        path.quadTo(cx - 8 * u, by - 92 * u, cx, by - 94 * u)
        path.quadTo(cx + 8 * u, by - 92 * u, cx + 8 * u, by - 80 * u)
        path.close()
        canvas.drawPath(path, fill)
        facet(cx - 2 * u, cx + 2 * u)
        canvas.drawRect(cx - 2 * u, by - 100 * u, cx + 2 * u, by - 93 * u, fill)
        canvas.drawRect(cx - .5f * u, by - 107 * u, cx + .5f * u, by - 100 * u, fill)
        // 시계 판: 테두리는 어둡게, 판은 밝게(밤에는 불이 켜진다).
        flat(mix(col, Color.BLACK, .35f))
        canvas.drawCircle(cx, by - 70 * u, 6 * u, fill)
        flat(if (lit > .05f) mix(Color.rgb(240, 236, 220), Color.rgb(255, 232, 170), lit) else mix(col, Color.WHITE, .5f))
        canvas.drawCircle(cx, by - 70 * u, 5 * u, fill)
        flat(mix(col, Color.BLACK, .5f))
        canvas.drawRect(cx - .4f * u, by - 74 * u, cx + .4f * u, by - 70 * u, fill)
        canvas.drawRect(cx, by - 70.4f * u, cx + 3 * u, by - 69.6f * u, fill)
    }

    /** 리옹 파르디외: 꼭대기가 피라미드인 붉은 원통 탑(연필), 비스듬한 지붕의 가는 유리 탑, 낮은 둥근 탑. */
    private fun partDieu(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int) {
        tone(dye(col, Color.rgb(170, 96, 74), .35f))
        round(cx - 27 * u, cx - 13 * u)
        canvas.drawRect(cx - 27 * u, by - 108 * u, cx - 13 * u, by, fill)
        stripes(canvas, cx - 27 * u, cx - 13 * u, by - 104 * u, by - 2 * u, 4 * u, u)
        tone(dye(col, Color.rgb(170, 96, 74), .35f))
        facet(cx - 27 * u, cx - 13 * u)
        path.moveTo(cx - 27 * u, by - 108 * u)
        path.lineTo(cx - 20 * u, by - 128 * u)
        path.lineTo(cx - 13 * u, by - 108 * u)
        path.close()
        canvas.drawPath(path, fill)
        tone(col)
        facet(cx + 10 * u, cx + 25 * u)
        path.rewind()
        path.moveTo(cx + 10 * u, by)
        path.lineTo(cx + 10 * u, by - 152 * u)
        path.lineTo(cx + 25 * u, by - 140 * u)
        path.lineTo(cx + 25 * u, by)
        path.close()
        canvas.drawPath(path, fill)
        stripes(canvas, cx + 14 * u, cx + 25 * u, by - 136 * u, by - 2 * u, 5 * u, u)
        tone(col)
        round(cx + 36 * u, cx + 54 * u)
        rect.set(cx + 36 * u, by - 86 * u, cx + 54 * u, by)
        canvas.drawRoundRect(rect, 9 * u, 9 * u, fill)
        canvas.drawRect(cx + 36 * u, by - 70 * u, cx + 54 * u, by, fill)
    }

    /** 아비뇽 교황청: 높이가 다른 네모 탑들과 톱니 성벽, 벽면의 뾰족 아치, 옆 성당 탑 꼭대기의 금빛 성모상. */
    private fun papalPalace(canvas: Canvas, cx: Float, by: Float, u: Float, col: Int, lit: Float) {
        flat(mix(col, sky, .1f * relief))
        canvas.drawRect(cx - 58 * u, by - 34 * u, cx + 42 * u, by + 6 * u, fill)
        battlements(canvas, cx - 58 * u, cx + 42 * u, by - 34 * u, u)
        tone(col)
        rim(canvas, cx - 58 * u, by - 34 * u, cx + 42 * u, u)
        for (i in PALACE_X.indices) {
            val tx = cx + PALACE_X[i] * u
            val hw = PALACE_W[i] * u / 2
            tone(col)
            facet(tx - hw, tx + hw)
            canvas.drawRect(tx - hw, by - PALACE_H[i] * u, tx + hw, by, fill)
            battlements(canvas, tx - hw, tx + hw, by - PALACE_H[i] * u, u)
        }
        // 벽면·탑의 뾰족 아치(그늘). 탑 앞에 그려야 보인다.
        flat(mix(col, sky, .4f * relief))
        var x = cx - 50 * u
        while (x < cx + 36 * u) {
            path.rewind()
            path.moveTo(x, by - 4 * u)
            path.lineTo(x, by - 22 * u)
            path.lineTo(x + 2.5f * u, by - 27 * u)
            path.lineTo(x + 5 * u, by - 22 * u)
            path.lineTo(x + 5 * u, by - 4 * u)
            path.close()
            canvas.drawPath(path, fill)
            x += 13 * u
        }
        // 성당 탑과 금빛 성모상.
        tone(col)
        facet(cx + 48 * u, cx + 60 * u)
        canvas.drawRect(cx + 48 * u, by - 44 * u, cx + 60 * u, by + 6 * u, fill)
        facet(cx + 51 * u, cx + 57 * u)
        canvas.drawRect(cx + 51 * u, by - 50 * u, cx + 57 * u, by - 44 * u, fill)
        flat(dye(col, Color.rgb(222, 180, 80), .6f))
        path.rewind()
        path.moveTo(cx + 52.4f * u, by - 50 * u)
        path.lineTo(cx + 54 * u, by - 60 * u)
        path.lineTo(cx + 55.6f * u, by - 50 * u)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawCircle(cx + 54 * u, by - 61 * u, 1.3f * u, fill)
        if (lit > .05f) {
            flat(Color.argb((lit * 110).toInt(), 255, 206, 140))
            canvas.drawRect(cx - 58 * u, by - 34 * u, cx + 42 * u, by, fill)
        }
    }

    /** 생베네제 다리: 왼쪽 강가에서 시작해 강 가운데서 끊긴 돌 아치 넷과 둘째 교각 위 작은 예배당. */
    private fun brokenBridge(canvas: Canvas, cx: Float, by: Float, u: Float) {
        val deck = by - 22 * u
        path.moveTo(cx - 112 * u, by + 4 * u)
        path.lineTo(cx - 112 * u, deck)
        path.lineTo(cx + 4 * u, deck)
        path.lineTo(cx + 9 * u, deck + 3 * u)
        path.lineTo(cx + 6 * u, deck + 7 * u)
        path.lineTo(cx + 10 * u, by + 4 * u)
        path.close()
        // 아치 구멍: 교각 사이마다 반원.
        var x = -104f
        while (x < 0f) {
            rect.set(cx + x * u, by - 16 * u, cx + (x + 22) * u, by + 16 * u)
            path.addOval(rect, Path.Direction.CW)
            x += 28f
        }
        canvas.drawPath(path, fill)
        // 아치 안쪽 그늘: 구멍 테두리 위쪽을 어둡게.
        stroke.color = mix(base, sky, .4f * relief)
        stroke.strokeWidth = 1.4f * u
        x = -104f
        while (x < 0f) {
            rect.set(cx + (x + .7f) * u, by - 15.3f * u, cx + (x + 21.3f) * u, by + 15.3f * u)
            canvas.drawArc(rect, 180f, 180f, false, stroke)
            x += 28f
        }
        rim(canvas, cx - 112 * u, deck, cx + 4 * u, u)
        // 예배당
        tone(base)
        facet(cx - 86 * u, cx - 70 * u)
        canvas.drawRect(cx - 86 * u, deck - 12 * u, cx - 70 * u, deck, fill)
        facet(cx - 88 * u, cx - 68 * u)
        path.rewind()
        path.moveTo(cx - 88 * u, deck - 12 * u)
        path.lineTo(cx - 78 * u, deck - 18 * u)
        path.lineTo(cx - 68 * u, deck - 12 * u)
        path.close()
        canvas.drawPath(path, fill)
        facet(cx - 73 * u, cx - 70 * u)
        canvas.drawRect(cx - 73 * u, deck - 22 * u, cx - 70 * u, deck - 14 * u, fill)
    }

    private companion object {
        /** 명암 그러데이션을 돌려 쓰는 칸 수. 한 랜드마크 안에서 오가는 재질 색 수보다 넉넉하게. */
        const val SHADE_SLOTS = 8

        val ISLE_X = floatArrayOf(-130f, -52f, 40f, 140f)
        val ISLE_HALF = floatArrayOf(46f, 26f, 70f, 34f)
        val ISLE_H = floatArrayOf(16f, 10f, 23f, 13f)

        /** 진마오 층별 반폭과 그 층 꼭대기 높이(단위). */
        val JINMAO_W = floatArrayOf(11f, 9.6f, 8.2f, 7f, 5.8f, 4.6f, 3.4f)
        val JINMAO_Y = floatArrayOf(40f, 72f, 98f, 118f, 134f, 146f, 154f)

        /** 교황청 탑의 가운데로부터 거리, 폭, 높이(단위). */
        val PALACE_X = floatArrayOf(-54f, -26f, 4f, 30f)
        val PALACE_W = floatArrayOf(13f, 16f, 11f, 12f)
        val PALACE_H = floatArrayOf(48f, 54f, 44f, 42f)

        /** 상조르즈 성 탑의 가운데로부터 거리와 높이(단위). */
        val TOWER_X = floatArrayOf(-50f, -22f, 4f, 30f, 50f)
        val TOWER_H = floatArrayOf(26f, 30f, 34f, 28f, 25f)

        /** 풍력발전기의 가운데로부터 거리와 높이(단위). */
        val TURBINE_X = floatArrayOf(-132f, -86f, -40f, 6f, 52f, 98f, 144f)
        val TURBINE_H = floatArrayOf(36f, 40f, 37f, 41f, 38f, 35f, 39f)

        /** 광저우 타워 몸체의 높이별 반폭(아래부터 위로 같은 간격): 허리가 높이의 62%인 쌍곡선. */
        val CANTON_HW = FloatArray(13) { i ->
            val y = 150f * i / 12
            sqrt(49f + .02775f * (y - 93f) * (y - 93f))
        }

        /** 격자 세로 갈비의 위치(반폭 대비). */
        val CANTON_RIBS = floatArrayOf(-.55f, -.15f, .25f, .65f)
    }
}
