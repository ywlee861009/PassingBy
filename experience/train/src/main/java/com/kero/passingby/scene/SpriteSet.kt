package com.kero.passingby.scene

import com.kero.passingby.experience.Season

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class SpriteKind { PINE, ROUND, POPLAR, BUSH, REED, BAMBOO, CYPRESS }

/**
 * 단색 알파 마스크(ALPHA_8) 스프라이트.
 * 명암이 있는 스프라이트는 기본·그늘·빛 마스크 3장으로 이뤄지고, 그릴 때 팔레트에서 뽑은 3가지 색을
 * 차례로 입힌다. 그래서 시간대·안개 색이 바뀌어도 이미지를 다시 만들 필요가 없다.
 * 빛은 항상 오른쪽 위에서 온다고 가정하므로 명암 스프라이트는 좌우 반전하지 않는다.
 *
 * 실제 제품에서는 디자이너가 그린 실루엣을 같은 3장 구조로 불러와 이 자리를 대신할 수 있다.
 *
 * @param windows 창문 사각형(x, y, w, h 반복, 마스크 픽셀 좌표). 밤에 불이 켜진다.
 * @param lightBoost 1보다 크면 빛 마스크를 더 밝게 칠한다(억새 이삭처럼 반짝이는 부분).
 */
class Sprite(
    val base: Bitmap,
    val dark: Bitmap? = null,
    val light: Bitmap? = null,
    val windows: FloatArray? = null,
    val lightBoost: Float = 1f,
) {
    val shaded: Boolean get() = dark != null
}

class SpriteSet {
    private val bareRounds = Array(6) { bareTree(1201 + it, 170, 210) }
    private val barePoplars = Array(4) { bareTree(1301 + it, 64, 240) }
    private val bareBushes = Array(4) { bareTree(1401 + it, 140, 64, bush = true) }
    val pines = Array(5) { pine(101 + it) }
    val rounds = Array(6) { deciduous(201 + it) }
    val poplars = Array(4) { poplar(301 + it) }
    val bushes = Array(4) { bush(401 + it) }
    val clouds = Array(4) { cloud(501 + it) }
    val reeds = Array(4) { tuft(601 + it, tall = true) }
    val tufts = Array(6) { tuft(701 + it, tall = false) }
    val bamboos = Array(4) { bamboo(801 + it) }
    val cypresses = Array(4) { cypress(901 + it) }
    val houses = arrayOf(
        house(floatArrayOf(26f, 46f, 10f, 10f, 64f, 46f, 10f, 10f)) { c ->
            c.drawRect(14f, 34f, 86f, 80f, fill)
            poly(c, 8f, 36f, 50f, 8f, 92f, 36f)
        },
        house(floatArrayOf(16f, 42f, 12f, 10f, 44f, 42f, 12f, 10f, 72f, 42f, 12f, 10f)) { c ->
            c.drawRect(6f, 30f, 94f, 80f, fill)
            c.drawRect(2f, 24f, 98f, 32f, fill)
            c.drawRect(70f, 8f, 78f, 26f, fill)
        },
        house(floatArrayOf(34f, 26f, 10f, 10f, 56f, 26f, 10f, 10f, 34f, 50f, 10f, 10f, 56f, 50f, 10f, 10f)) { c ->
            c.drawRect(26f, 14f, 74f, 80f, fill)
            poly(c, 20f, 16f, 50f, 0f, 80f, 16f)
        },
    )

    fun of(kind: SpriteKind, season: Season): Array<Sprite> = when (kind) {
        SpriteKind.PINE -> pines
        SpriteKind.ROUND -> if (season == Season.WINTER) bareRounds else rounds
        SpriteKind.POPLAR -> if (season == Season.WINTER) barePoplars else poplars
        SpriteKind.BUSH -> if (season == Season.WINTER) bareBushes else bushes
        SpriteKind.REED -> reeds
        SpriteKind.BAMBOO -> bamboos
        SpriteKind.CYPRESS -> cypresses
    }

    val bytes: Long = listOf(pines, rounds, poplars, bushes, clouds, reeds, tufts, houses, bamboos, cypresses, bareRounds, barePoplars, bareBushes).sumOf { arr ->
        arr.sumOf { s ->
            s.base.allocationByteCount.toLong() + (s.dark?.allocationByteCount ?: 0) + (s.light?.allocationByteCount ?: 0)
        }
    }
}

// ───────────── 마스크 생성 (앱 시작 시 한 번, 메인 스레드에서만 실행) ─────────────

/** 설계 좌표 대비 마스크 해상도 배율. */
private const val RES = 1.6f
private const val TAU = (2 * PI).toFloat()

private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
private val fillOut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
}
private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
}
private val plain = Paint(Paint.FILTER_BITMAP_FLAG)
private val cutOut = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
private val tmpRect = RectF()

private class Mask(w: Int, h: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(ceil(w * RES).toInt(), ceil(h * RES).toInt(), Bitmap.Config.ALPHA_8)
    val canvas = Canvas(bitmap).apply { scale(RES, RES) }
}

/** 잎 뭉치 등에 쓰는 임시 마스크. 가장 큰 스프라이트(220×240)를 덮는다. */
private val TMP = Mask(260, 260)

private inline fun shaded(w: Int, h: Int, lightBoost: Float = 1f, draw: (b: Canvas, l: Canvas, d: Canvas, base: Bitmap, w: Float, h: Float) -> Unit): Sprite {
    val b = Mask(w, h)
    val l = Mask(w, h)
    val d = Mask(w, h)
    draw(b.canvas, l.canvas, d.canvas, b.bitmap, w.toFloat(), h.toFloat())
    return Sprite(b.bitmap, d.bitmap, l.bitmap, lightBoost = lightBoost)
}

private fun house(windows: FloatArray, draw: (Canvas) -> Unit): Sprite {
    val m = Mask(100, 80)
    draw(m.canvas)
    return Sprite(m.bitmap, windows = FloatArray(windows.size) { windows[it] * RES })
}

/**
 * 마스크를 기본 모양 안쪽으로만 남긴다.
 * ALPHA_8 비트맵은 "덮는 정도"로 취급되어 0인 곳은 아예 건드리지 않으므로 DST_IN으로는 바깥이 지워지지 않는다.
 * 그래서 기본 모양을 뒤집은 마스크를 만들어 DST_OUT으로 바깥을 지운다.
 */
private fun restrict(g: Canvas, base: Bitmap) {
    val inverse = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ALPHA_8)
    inverse.eraseColor(Color.WHITE)
    Canvas(inverse).drawBitmap(base, 0f, 0f, cutOut)
    tmpRect.set(0f, 0f, base.width / RES, base.height / RES)
    g.drawBitmap(inverse, null, tmpRect, cutOut)
    inverse.recycle()
}

/** 기본 모양에서 (dx, dy)만큼 밀린 자신을 빼서 가장자리 띠만 남긴다. */
private fun rim(g: Canvas, base: Bitmap, dx: Float, dy: Float) {
    tmpRect.set(0f, 0f, base.width / RES, base.height / RES)
    g.drawBitmap(base, null, tmpRect, plain)
    tmpRect.offset(dx, dy)
    g.drawBitmap(base, null, tmpRect, cutOut)
}

/** 가장자리가 울퉁불퉁한 둥근 덩어리. 점을 이은 뒤 중점으로 부드럽게 잇는다. */
private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, rnd: SeqRandom, rough: Float, p: Paint = fill) {
    val n = 18
    val ph = rnd.next() * TAU
    val ph2 = rnd.next() * TAU
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    for (i in 0 until n) {
        val a = i.toFloat() / n * TAU
        val k = 1 + rough * (.55f * sin(a * 3 + ph) + .35f * sin(a * 5 + ph2) + .45f * (rnd.next() - .5f))
        xs[i] = cx + cos(a) * r * k
        ys[i] = cy + sin(a) * r * k * .9f
    }
    val path = Path()
    path.moveTo((xs[n - 1] + xs[0]) / 2, (ys[n - 1] + ys[0]) / 2)
    for (i in 0 until n) {
        val j = (i + 1) % n
        path.quadTo(xs[i], ys[i], (xs[i] + xs[j]) / 2, (ys[i] + ys[j]) / 2)
    }
    path.close()
    c.drawPath(path, p)
}

/** 잎 뭉치 하나의 그늘(빛 반대쪽 초승달 모양)을 d에 더한다. */
private fun crescent(d: Canvas, cx: Float, cy: Float, r: Float, dx: Float, dy: Float, rnd: SeqRandom) {
    TMP.bitmap.eraseColor(Color.TRANSPARENT)
    val seed = (rnd.next() * 1e9f).toInt()
    blob(TMP.canvas, cx, cy, r, SeqRandom(seed), .15f)
    blob(TMP.canvas, cx + dx, cy + dy, r * 1.02f, SeqRandom(seed), .15f, fillOut)
    tmpRect.set(0f, 0f, 260f, 260f)
    d.drawBitmap(TMP.bitmap, null, tmpRect, plain)
}

/** 세로 중심선 왼쪽을 물결 모양으로 칠한다(빛 반대편 그늘). */
private fun leftSide(g: Canvas, cx: Float, h: Float, rnd: SeqRandom, amp: Float) {
    val p = Path()
    p.moveTo(-5f, -5f)
    p.lineTo(cx, -5f)
    var y = 0f
    while (y <= h + 5) {
        p.lineTo(cx + amp * sin(y * .13f + rnd.next() * .4f) + (rnd.next() - .5f) * 2, y)
        y += 6
    }
    p.lineTo(-5f, h + 5)
    p.close()
    g.drawPath(p, fill)
}

private fun poly(c: Canvas, vararg pts: Float) {
    val path = Path()
    path.moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) {
        path.lineTo(pts[i], pts[i + 1])
        i += 2
    }
    path.close()
    c.drawPath(path, fill)
}

/** 끝이 가늘어지며 휘는 풀잎 하나. */
private fun blade(g: Canvas, x: Float, base: Float, bh: Float, lean: Float, bw: Float) {
    val p = Path()
    p.moveTo(x - bw, base)
    p.quadTo(x + lean * .35f - bw * .4f, base - bh * .55f, x + lean, base - bh)
    p.quadTo(x + lean * .35f + bw * .4f, base - bh * .55f, x + bw, base)
    p.close()
    g.drawPath(p, fill)
}

/** 활엽수: 가지 + 잎 뭉치 10~15개 + 가장자리 잎 다발, 뭉치마다 그늘과 빛. */
private fun deciduous(seed: Int) = shaded(170, 210) { b, l, d, base, w, h ->
    val r = SeqRandom(seed)
    val cx = w / 2
    val tt = h * .62f
    val trunk = Path().apply {
        moveTo(cx - 6, h)
        quadTo(cx - 3, h * .8f, cx - 2.5f, tt)
        lineTo(cx + 2.5f, tt)
        quadTo(cx + 3, h * .8f, cx + 6, h)
        close()
    }
    b.drawPath(trunk, fill)
    val ccx = cx + (r.next() - .5f) * 10
    val ccy = h * .4f
    val crx = 56 + r.next() * 16
    val cry = 50 + r.next() * 12
    val nb = 2 + (r.next() * 2).toInt()
    for (i in 0 until nb) {
        val a = -PI.toFloat() / 2 + (i - (nb - 1) / 2f) * .8f + (r.next() - .5f) * .3f
        stroke.strokeWidth = 4 - i * .7f
        val br = Path().apply {
            moveTo(cx, tt + 8)
            quadTo(cx + cos(a) * 12, tt - 8, ccx + cos(a) * crx * .45f, ccy + sin(a) * cry * .25f + 14)
        }
        b.drawPath(br, stroke)
    }
    val n = 10 + (r.next() * 6).toInt()
    val cl = Array(n) {
        val a = r.next() * TAU
        val dd = sqrt(r.next())
        floatArrayOf(ccx + cos(a) * crx * .62f * dd, ccy + sin(a) * cry * .62f * dd, (21 + r.next() * 14) * (1 - .3f * dd))
    }
    for (c in cl) blob(b, c[0], c[1], c[2], r, .18f)
    for (k in 0 until 16) {
        val c = cl[(r.next() * n).toInt()]
        val a = r.next() * TAU
        blob(b, c[0] + cos(a) * c[2] * .9f, c[1] + sin(a) * c[2] * .82f, c[2] * (.24f + r.next() * .14f), r, .3f)
    }
    for (c in cl) crescent(d, c[0], c[1], c[2], c[2] * .3f, -c[2] * .34f, r)
    d.drawRect(cx - 7, tt - 4, cx, h, fill)
    restrict(d, base)
    for (c in cl) blob(l, c[0] + c[2] * .3f, c[1] - c[2] * .34f, c[2] * .5f, r, .2f)
    restrict(l, base)
}

/** 겨울 낙엽수: 줄기와 잔가지만 남긴 재사용 마스크. 상록수는 기존 마스크를 쓴다. */
private fun bareTree(seed: Int, width: Int, height: Int, bush: Boolean = false) = shaded(width, height) { b, l, d, base, w, h ->
    val random = SeqRandom(seed)
    val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val path = Path()
    val spread = if (w / h < .4f) .23f else .6f
    fun branch(x: Float, y: Float, length: Float, angle: Float, thickness: Float, depth: Int) {
        val nx = x + cos(angle) * length
        val ny = y + sin(angle) * length
        path.rewind()
        path.moveTo(x, y)
        path.quadTo(x + (nx - x) * .35f + (random.next() - .5f) * 3, y + (ny - y) * .5f, nx, ny)
        pen.strokeWidth = thickness
        b.drawPath(path, pen)
        if (depth == 0) return
        branch(nx, ny, length * .6f, angle - spread * (.8f + random.next() * .3f), thickness * .6f, depth - 1)
        branch(nx, ny, length * .63f, angle + spread * (.8f + random.next() * .3f), thickness * .6f, depth - 1)
    }
    if (bush) {
        repeat(7) { i -> branch(w * (.14f + i * .12f), h - 2, h * .43f, -PI.toFloat() / 2 + (random.next() - .5f) * .5f, 2f, 2) }
    } else {
        branch(w / 2, h - 2, h * .38f, -PI.toFloat() / 2 + (random.next() - .5f) * .08f, max(3f, w * .035f), 3)
    }
    leftSide(d, w / 2, h, random, 1f)
    restrict(d, base)
    rim(l, base, -2f, 2f)
}

/** 침엽수: 톱니처럼 삐죽하고 끝이 처지는 가지층, 층마다 아래 그림자. */
private fun pine(seed: Int) = shaded(120, 240) { b, l, d, base, w, h ->
    val r = SeqRandom(seed)
    val cx = w / 2
    b.drawRect(cx - 3, h - 36, cx + 3, h, fill)
    val n = 7 + (r.next() * 3).toInt()
    val top = 4f
    val span = h - 30 - 34 - top
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val y0 = top + t * span
        val th = 24 + t * 16
        val hw = (5 + t * 46) * (.92f + r.next() * .16f)
        val ax = cx + (r.next() - .5f) * 3
        val p = Path()
        p.moveTo(ax, y0)
        p.quadTo(cx + hw * .45f, y0 + th * .5f, cx + hw, y0 + th + r.next() * 3)
        val k = 4 + (t * 5).toInt()
        for (j in 1..k * 2) {
            val f = j / (k * 2f)
            val x = cx + hw - f * 2 * hw
            val sag = -sin(f * PI.toFloat()) * (4 + t * 4)
            val y = y0 + th + sag + if (j % 2 == 0) r.next() * 3 else -(5 + r.next() * 5)
            p.lineTo(x, y)
        }
        p.quadTo(cx - hw * .45f, y0 + th * .5f, ax, y0)
        p.close()
        b.drawPath(p, fill)
        if (i > 0) d.drawOval(cx - hw * .75f, y0, cx + hw * .75f, y0 + 12, fill)
    }
    leftSide(d, cx, h, r, 3f)
    restrict(d, base)
    rim(l, base, -5f, 2f)
}

/** 포플러: 위아래로 긴 잎 뭉치를 쌓는다. */
private fun poplar(seed: Int) = shaded(64, 240) { b, l, d, base, w, h ->
    val r = SeqRandom(seed)
    val cx = w / 2
    b.drawRect(cx - 2.5f, h - 24, cx + 2.5f, h, fill)
    for (i in 0 until 13) {
        val t = i / 12f
        val y = 10 + t * (h - 50)
        val prof = sin(t.pow(.75f) * PI.toFloat() * .92f + .12f)
        blob(b, cx + (r.next() - .5f) * 5, y, (5 + prof * 15) * (.88f + r.next() * .24f), r, .22f)
    }
    leftSide(d, cx, h, r, 3f)
    restrict(d, base)
    rim(l, base, -4f, 2f)
}

/** 대나무 덤불: 가늘게 휘는 줄기 여러 대와 위쪽에 몰린 길쭉한 잎. */
private fun bamboo(seed: Int) = shaded(110, 240) { b, l, d, base, w, h ->
    val r = SeqRandom(seed)
    val n = 7 + (r.next() * 4).toInt()
    for (i in 0 until n) {
        val x = w / 2 + (r.next() - .5f) * w * .45f
        val top = h * (.04f + r.next() * .2f)
        val lean = (r.next() - .5f) * 26
        stroke.strokeWidth = 2.2f + r.next() * 1.4f
        val culm = Path().apply {
            moveTo(x, h)
            quadTo(x + lean * .2f, (h + top) / 2, x + lean, top)
        }
        b.drawPath(culm, stroke)
        val leaves = 7 + (r.next() * 5).toInt()
        for (j in 0 until leaves) {
            val t = r.next() * .62f
            val ly = top + t * (h - top)
            val lx = x + lean * (1 - t) + (r.next() - .5f) * 30
            val g = if (r.next() < .4f) d else b
            g.save()
            g.rotate((r.next() - .5f) * 70, lx, ly)
            g.drawOval(lx - 11, ly - 3.2f, lx + 11, ly + 3.2f, fill)
            g.restore()
        }
    }
    restrict(d, base)
    rim(l, base, -4f, 3f)
}

/** 사이프러스: 위가 뾰족한 촛불 모양으로 좁게 쌓은 잎 뭉치. */
private fun cypress(seed: Int) = shaded(56, 240) { b, l, d, base, w, h ->
    val r = SeqRandom(seed)
    val cx = w / 2
    b.drawRect(cx - 2f, h - 16, cx + 2f, h, fill)
    for (i in 0 until 16) {
        val t = i / 15f
        val y = 6 + t * (h - 30)
        val prof = sin(t.pow(.55f) * PI.toFloat() * .96f)
        blob(b, cx + (r.next() - .5f) * 3, y, (2.5f + prof * 13) * (.9f + r.next() * .2f), r, .2f)
    }
    leftSide(d, cx, h, r, 2.5f)
    restrict(d, base)
    rim(l, base, -3f, 2f)
}

private fun bush(seed: Int) = shaded(130, 60) { b, l, d, base, w, h ->
    val r = SeqRandom(seed)
    val n = 6 + (r.next() * 4).toInt()
    val cl = Array(n) { i ->
        val t = i / (n - 1f)
        val s = sin(t * PI.toFloat())
        floatArrayOf(14 + t * (w - 28) + (r.next() - .5f) * 8, h - 14 - s * 12 - r.next() * 5, 9 + s * 8 + r.next() * 5)
    }
    for (c in cl) blob(b, c[0], c[1], c[2], r, .2f)
    b.drawRect(8f, h - 14, w - 8, h, fill)
    for (c in cl) crescent(d, c[0], c[1], c[2], c[2] * .3f, -c[2] * .34f, r)
    d.drawRect(0f, h - 9, w, h, fill)
    restrict(d, base)
    for (c in cl) blob(l, c[0] + c[2] * .3f, c[1] - c[2] * .36f, c[2] * .45f, r, .2f)
    restrict(l, base)
}

/** 풀 다발. tall이면 이삭이 달린 억새가 되고, 이삭은 빛 마스크에 들어가 반짝인다. */
private fun tuft(seed: Int, tall: Boolean) = shaded(70, if (tall) 100 else 40, if (tall) 2.4f else 1f) { b, l, d, base, w, h ->
    val r = SeqRandom(seed)
    val n = if (tall) 10 else 8
    for (i in 0 until n) {
        val x = w / 2 + (r.next() - .5f) * w * .5f
        val bh = h * if (tall) .4f + r.next() * .45f else .45f + r.next() * .5f
        val lean = (r.next() - .5f) * h * .55f
        val bw = 1.6f + r.next() * 1.6f
        blade(b, x, h, bh, lean, bw)
        if (i < n / 2) blade(d, x, h, bh, lean, bw)
        if (tall && r.next() < .6f) {
            val tx = x + lean * 1.04f
            val ty = h - bh - 5
            val deg = lean * .012f * 180f / PI.toFloat()
            for (g in arrayOf(b, l)) {
                g.save()
                g.rotate(deg, tx, ty)
                g.drawOval(tx - 3.2f, ty - 9, tx + 3.2f, ty + 9, fill)
                g.restore()
            }
        }
    }
    restrict(d, base)
}

/** 구름: 윗면은 빛, 아랫면은 그늘. */
private fun cloud(seed: Int) = shaded(220, 90) { b, l, d, base, w, h ->
    val r = SeqRandom(seed)
    val n = 5 + (r.next() * 3).toInt()
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val x = 30 + t * 160 + (r.next() - .5f) * 14
        val rad = 16 + sin(t * PI.toFloat()) * 22 + r.next() * 8
        blob(b, x, h - 18 - rad * .35f, rad, r, .08f)
    }
    b.drawRect(0f, h - 18, w, h, fillOut)
    rim(l, base, 0f, 7f)
    rim(d, base, 0f, -9f)
}

// ───────────── 그리기 ─────────────

/**
 * 스프라이트를 (cx, yb)를 바닥 중심으로 높이 hPx만큼 그린다.
 * 명암 스프라이트는 기본 → 그늘 → 빛 순서로 3번 칠한다. 좌우 반전은 명암 없는 스프라이트에만 적용한다.
 */
internal fun drawSprite(
    canvas: Canvas, s: Sprite, cx: Float, yb: Float, hPx: Float, flip: Boolean,
    tones: IntArray, alpha: Int, paint: Paint, dst: RectF,
) {
    val m = s.base
    val wPx = m.width * hPx / m.height
    dst.set(cx - wPx / 2, yb - hPx, cx + wPx / 2, yb)
    val mirror = flip && !s.shaded
    if (mirror) {
        canvas.save()
        canvas.scale(-1f, 1f, cx, 0f)
    }
    paint.color = tones[TONE_BASE]
    paint.alpha = alpha
    canvas.drawBitmap(m, null, dst, paint)
    s.dark?.let {
        paint.color = tones[TONE_DARK]
        paint.alpha = alpha
        canvas.drawBitmap(it, null, dst, paint)
    }
    s.light?.let {
        paint.color = if (s.lightBoost > 1f) tones[TONE_LIGHT_BOOST] else tones[TONE_LIGHT]
        paint.alpha = alpha
        canvas.drawBitmap(it, null, dst, paint)
    }
    if (mirror) canvas.restore()
}

/** 집 스프라이트의 창문에 불을 켠다. 어느 창이 켜질지는 key로 고정된다. */
internal fun drawWindows(canvas: Canvas, s: Sprite, key: Int, cx: Float, yb: Float, hPx: Float, flip: Boolean, lit: Float, paint: Paint) {
    val win = s.windows ?: return
    val m = s.base
    val sc = hPx / m.height
    val x0 = cx - m.width * sc / 2
    val y0 = yb - hPx
    paint.alpha = (min(1f, lit * 1.2f) * 255).toInt()
    var q = 0
    while (q < win.size / 4) {
        if (hash01(key * 7 + q, 55) < lit * .8f) {
            val wx = win[q * 4]
            val wy = win[q * 4 + 1]
            val ww = win[q * 4 + 2]
            val wh = win[q * 4 + 3]
            val rx = if (flip) m.width - wx - ww else wx
            val left = x0 + rx * sc
            val top = y0 + wy * sc
            canvas.drawRect(left, top, left + max(1f, ww * sc), top + max(1f, wh * sc), paint)
        }
        q++
    }
}

internal const val WINDOW_COLOR = -0x2d81 // 0xffffd27f
internal const val WINDOW_COOL = -0x201601 // 0xffdfe9ff
