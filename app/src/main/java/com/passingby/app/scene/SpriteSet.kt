package com.passingby.app.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class SpriteKind { PINE, ROUND, POPLAR, BUSH }

/**
 * 단색 알파 마스크(ALPHA_8) 스프라이트. 그릴 때 Paint 색으로 칠해지므로
 * 시간대·안개 색이 바뀌어도 이미지를 다시 만들 필요가 없다.
 * 실제 제품에서는 디자이너가 그린 실루엣을 ALPHA_8로 불러와 이 자리를 대신한다.
 *
 * @param windows 창문 사각형(x, y, w, h 반복, 마스크 픽셀 좌표). 밤에 불이 켜진다.
 */
class Sprite(val mask: Bitmap, val windows: FloatArray? = null)

class SpriteSet {
    val pines = arrayOf(pine(1), pine(2), pine(3))
    val rounds = arrayOf(round(4), round(5), round(6))
    val poplars = arrayOf(poplar(7), poplar(8))
    val bushes = arrayOf(bush(9), bush(10))
    val clouds = arrayOf(cloud(11), cloud(12), cloud(13), cloud(14))
    val houses = arrayOf(
        house(floatArrayOf(26f, 46f, 10f, 10f, 64f, 46f, 10f, 10f)) { c, p ->
            c.drawRect(14f, 34f, 86f, 80f, p)
            poly(c, p, 8f, 36f, 50f, 8f, 92f, 36f)
        },
        house(floatArrayOf(16f, 42f, 12f, 10f, 44f, 42f, 12f, 10f, 72f, 42f, 12f, 10f)) { c, p ->
            c.drawRect(6f, 30f, 94f, 80f, p)
            c.drawRect(2f, 24f, 98f, 32f, p)
            c.drawRect(70f, 8f, 78f, 26f, p)
        },
        house(floatArrayOf(34f, 26f, 10f, 10f, 56f, 26f, 10f, 10f, 34f, 50f, 10f, 10f, 56f, 50f, 10f, 10f)) { c, p ->
            c.drawRect(26f, 14f, 74f, 80f, p)
            poly(c, p, 20f, 16f, 50f, 0f, 80f, 16f)
        },
    )

    fun of(kind: SpriteKind): Array<Sprite> = when (kind) {
        SpriteKind.PINE -> pines
        SpriteKind.ROUND -> rounds
        SpriteKind.POPLAR -> poplars
        SpriteKind.BUSH -> bushes
    }

    val bytes: Long = listOf(pines, rounds, poplars, bushes, clouds, houses)
        .sumOf { arr -> arr.sumOf { it.mask.allocationByteCount.toLong() } }
}

/** 데모(HTML) 좌표 대비 마스크 해상도 배율. */
private const val RES = 2f
private const val TAU = (2 * PI).toFloat()

private fun mask(w: Int, h: Int, draw: (Canvas, Paint, Float, Float) -> Unit): Bitmap {
    val bmp = Bitmap.createBitmap((w * RES).toInt(), (h * RES).toInt(), Bitmap.Config.ALPHA_8)
    val c = Canvas(bmp)
    c.scale(RES, RES)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    draw(c, p, w.toFloat(), h.toFloat())
    return bmp
}

private fun poly(c: Canvas, p: Paint, vararg pts: Float) {
    val path = Path()
    path.moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) {
        path.lineTo(pts[i], pts[i + 1])
        i += 2
    }
    path.close()
    c.drawPath(path, p)
}

private fun pine(seed: Int) = Sprite(mask(72, 140) { c, p, w, h ->
    val r = SeqRandom(seed)
    c.drawRect(w / 2 - 3, h - 16, w / 2 + 3, h, p)
    val n = 4 + (r.next() * 2).toInt()
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val top = 4 + t * (h - 60) * .92f
        val hw = 8 + t * 22 + r.next() * 4
        val th = 30 + r.next() * 8
        poly(c, p, w / 2 + (r.next() - .5f) * 2, top, w / 2 + hw, top + th, w / 2 - hw, top + th)
    }
})

private fun round(seed: Int) = Sprite(mask(100, 120) { c, p, w, h ->
    val r = SeqRandom(seed)
    c.drawRect(w / 2 - 4, h - 40, w / 2 + 4, h, p)
    val n = 4 + (r.next() * 3).toInt()
    for (i in 0 until n) {
        val a = i.toFloat() / n * TAU + r.next()
        val d = 10 + r.next() * 12
        c.drawCircle(w / 2 + cos(a) * d, h - 68 + sin(a) * d * .7f, 16 + r.next() * 10, p)
    }
    c.drawCircle(w / 2, h - 70, 24f, p)
})

private fun poplar(seed: Int) = Sprite(mask(44, 140) { c, p, w, h ->
    val r = SeqRandom(seed)
    c.drawRect(w / 2 - 2.5f, h - 14, w / 2 + 2.5f, h, p)
    val rx = 12 + r.next() * 5
    val ry = (h - 14) / 2 - 2
    val cy = (h - 14) / 2 + 2
    c.drawOval(w / 2 - rx, cy - ry, w / 2 + rx, cy + ry, p)
})

private fun bush(seed: Int) = Sprite(mask(104, 44) { c, p, _, h ->
    val r = SeqRandom(seed)
    for (i in 0 until 4) {
        val cx = 16 + i * 20 + r.next() * 6
        val rx = 12 + r.next() * 7
        val ry = 10 + r.next() * 10
        c.drawOval(cx - rx, h - 12 - ry, cx + rx, h - 12 + ry, p)
    }
    c.drawRect(6f, h - 12, 96f, h, p)
})

private fun cloud(seed: Int) = Sprite(mask(220, 90) { c, p, w, h ->
    val r = SeqRandom(seed)
    val n = 5 + (r.next() * 3).toInt()
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val x = 30 + t * 160 + (r.next() - .5f) * 14
        val rad = 16 + sin(t * PI.toFloat()) * 22 + r.next() * 8
        c.drawCircle(x, h - 18 - rad * .35f, rad, p)
    }
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    c.drawRect(0f, h - 18, w, h, p)
    p.xfermode = null
})

private fun house(windows: FloatArray, draw: (Canvas, Paint) -> Unit) =
    Sprite(mask(100, 80) { c, p, _, _ -> draw(c, p) }, FloatArray(windows.size) { windows[it] * RES })

/** 스프라이트 마스크를 (cx, yb)를 바닥 중심으로 높이 hPx만큼 그린다. */
internal fun drawSprite(canvas: Canvas, s: Sprite, cx: Float, yb: Float, hPx: Float, flip: Boolean, paint: Paint, dst: RectF) {
    val m = s.mask
    val wPx = m.width * hPx / m.height
    dst.set(cx - wPx / 2, yb - hPx, cx + wPx / 2, yb)
    if (flip) {
        canvas.save()
        canvas.scale(-1f, 1f, cx, 0f)
        canvas.drawBitmap(m, null, dst, paint)
        canvas.restore()
    } else {
        canvas.drawBitmap(m, null, dst, paint)
    }
}

/** 집 스프라이트의 창문에 불을 켠다. 어느 창이 켜질지는 key로 고정된다. */
internal fun drawWindows(canvas: Canvas, s: Sprite, key: Int, cx: Float, yb: Float, hPx: Float, flip: Boolean, lit: Float, paint: Paint) {
    val win = s.windows ?: return
    val m = s.mask
    val sc = hPx / m.height
    val x0 = cx - m.width * sc / 2
    val y0 = yb - hPx
    paint.alpha = (minOf(1f, lit * 1.2f) * 255).toInt()
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
