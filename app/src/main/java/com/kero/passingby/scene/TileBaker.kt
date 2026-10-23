package com.kero.passingby.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 비교용: 원래 명세(8.3)의 "레이어 이미지 반복" 방식을 흉내 낸다.
 * 레이어마다 화면 긴 변 2배 폭의 ARGB 이미지(나무·집 포함)를 변형 2장씩 만들어 이어 붙인다.
 * 디자이너가 그린 PNG를 디코딩해 들고 있는 것과 같은 메모리를 쓴다.
 */
internal class TileSet(
    val key: TileStyle,
    val generation: Long,
    val w: Int,
    val h: Int,
    val tileW: Int,
    val tops: IntArray,
    val tiles: Array<Array<Bitmap>>,
) {
    val bytes: Long = tiles.sumOf { row -> row.sumOf { it.allocationByteCount.toLong() } }
    fun recycle() { for (row in tiles) for (bitmap in row) bitmap.recycle() }
}

internal object TileBaker {
    private const val VARIANTS = 2
    /** 변형마다 다른 노이즈 구간을 쓰기 위한 오프셋(단위). */
    private const val VARIANT_OFFSET_U = 1_000_000.0
    /** 모든 변형의 좌우 끝 높이를 이 값에 맞춰 이음새를 없앤다. */
    private const val EDGE = 0.5
    /** Placement에서 집 높이의 최댓값(15 + 9). */
    private const val HOUSE_MAX_H = 24f

    fun bake(layout: SceneLayout, pal: Palette, key: TileStyle, generation: Long, sprites: SpriteSet, layers: Array<TerrainLayer>): TileSet {
        val tileW = ceil(2f * max(layout.w, layout.h)).toInt()
        val periodU = tileW / layout.u.toDouble()
        val canvas = Canvas()
        val path = Path()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val windowPaint = Paint().apply { color = WINDOW_COLOR }
        val dst = RectF()
        val placed = Placed()
        val tops = IntArray(layers.size)
        val tiles = Array(layers.size) { i ->
            val l = layers[i]
            val yBase = layout.yAt(l.base)
            val ridgeTop = yBase - l.amp * layout.g
            // 능선 위로 솟는 나무·집까지 이미지에 담아야 하므로 가장 큰 사물 높이만큼 위로 늘린다.
            val headroom = (l.objects?.let { max(it.maxH, HOUSE_MAX_H) } ?: 0f) * layout.u
            val top = max(0, floor(ridgeTop - headroom).toInt() - 2)
            tops[i] = top
            Array(VARIANTS) { v ->
                val bmp = Bitmap.createBitmap(tileW, layout.h - top, Bitmap.Config.ARGB_8888)
                canvas.setBitmap(bmp)
                val col = layerColor(l, pal)
                fill.shader = if (l.mist > 0f) {
                    LinearGradient(
                        0f, ridgeTop - top, 0f, yBase + 8 * layout.u - top,
                        col, mix(col, pal.c[Palette.HAZE], l.mist), Shader.TileMode.CLAMP,
                    )
                } else null
                fill.color = col

                path.rewind()
                val hb = bmp.height.toFloat()
                path.moveTo(0f, hb)
                var x = 0f
                while (true) {
                    path.lineTo(x, periodicY(l, layout, v, x / layout.u.toDouble(), periodU) - top)
                    if (x >= tileW) break
                    x = min(x + 2f, tileW.toFloat())
                }
                path.lineTo(tileW.toFloat(), hb)
                path.close()
                canvas.drawPath(path, fill)

                l.objects?.let { rule ->
                    val tones = FoliageTones()
                    tones.update(spriteColor(l, pal), l.spriteHaze, if (l.spriteHaze > 0f) .6f else 1f, pal, key.season)
                    val lit = pal.f[Palette.LIT]
                    val base = v * VARIANT_OFFSET_U
                    val k0 = floor(base / rule.chunkUnits).toInt()
                    val k1 = floor((base + periodU) / rule.chunkUnits).toInt()
                    for (k in k0..k1) {
                        val n = Placement.count(l, rule, k)
                        for (j in 0 until n) {
                            Placement.place(l, rule, k, j, sprites, placed, key.season)
                            val local = placed.wx - base
                            val hPx = placed.heightU * layout.u
                            val m = placed.sprite.base
                            val halfU = m.width * hPx / m.height / 2 / layout.u
                            // 타일 끝에 걸치는 사물은 잘리므로 뺀다(실제 아트 작업에서도 같은 제약이 생긴다).
                            if (local - halfU < 0 || local + halfU > periodU) continue
                            val cx = (local * layout.u).toFloat()
                            val yb = periodicY(l, layout, v, local, periodU) + rule.sink * layout.u - top
                            drawSprite(canvas, placed.sprite, cx, yb, hPx, placed.flip, tones.forObject(placed.kind, placed.key), 255, spritePaint, dst)
                            if (lit > .02f && placed.sprite.windows != null) {
                                drawWindows(canvas, placed.sprite, placed.key, cx, yb, hPx, placed.flip, lit, windowPaint)
                            }
                        }
                    }
                }
                canvas.setBitmap(null)
                bmp
            }
        }
        return TileSet(key, generation, layout.w, layout.h, tileW, tops, tiles)
    }

    /** 좌우 끝 높이가 EDGE로 맞춰진 주기 함수. 어떤 변형끼리 이어 붙여도 이음새가 없다. */
    private fun periodicY(l: TerrainLayer, layout: SceneLayout, v: Int, xU: Double, periodU: Double): Float {
        val o = v * VARIANT_OFFSET_U
        val t = xU / periodU
        val f0 = heightNorm(l, o)
        val fT = heightNorm(l, o + periodU)
        val n = (heightNorm(l, o + xU) + (EDGE - f0) * (1 - t) + (EDGE - fT) * t).coerceIn(0.0, 1.0)
        return (layout.yAt(l.base) - n * layout.g * l.amp).toFloat()
    }
}
