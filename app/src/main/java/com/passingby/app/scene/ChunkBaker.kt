package com.passingby.app.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.util.concurrent.Executor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 조각이 아직 준비되지 않았을 때 그 구간을 직접 그리는 함수. */
internal fun interface SpanPainter {
    fun paint(canvas: Canvas, layer: TerrainLayer, offU: Double, x0: Float, x1: Float, paint: Paint)
}

/**
 * 지형 레이어를 화면 폭보다 작은 조각으로 나눠 ALPHA_8 비트맵에 한 번만 그려 둔다.
 * 매 프레임은 조각 비트맵을 옮겨 그리기만 하고, 색은 그릴 때 Paint(색 또는 그라디언트)로 입힌다.
 *
 * - 조각은 고정 개수의 슬롯을 돌려 쓰므로 메모리가 재생 시간에 비례해 늘지 않는다.
 * - 새 조각은 백그라운드 스레드에서 그리고, 준비될 때까지는 [fallback]으로 그 구간을 직접 그린다.
 * - 능선이 있는 띠 부분만 굽고, 그 아래의 꽉 찬 부분은 사각형으로 채운다.
 */
internal class ChunkBaker(
    private val layer: TerrainLayer,
    private val executor: Executor,
    private val fallback: SpanPainter,
) {
    private class Slot(val bitmap: Bitmap) {
        @Volatile var index = Int.MIN_VALUE
        @Volatile var ready = false
        @Volatile var pending = false
        var lastDrawnFrame = Long.MIN_VALUE / 2
    }

    private var slots: Array<Slot> = emptyArray()
    private var top = 0
    private var bottom = 0
    private var u = 1f
    private var g = 0f
    private var yBase = 0f
    private var w = 0
    private var h = 0

    // 아래 셋은 굽는 스레드에서만 쓴다.
    private val bakeCanvas = Canvas()
    private val bakePath = Path()
    private val bakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    val bytes: Long
        get() {
            var sum = 0L
            for (s in slots) sum += s.bitmap.allocationByteCount
            return sum
        }

    fun resize(layout: SceneLayout) {
        u = layout.u
        g = layout.g
        w = layout.w
        h = layout.h
        yBase = layout.yAt(layer.base)
        top = max(0, floor(yBase - layer.amp * g).toInt() - 2)
        bottom = min(h, ceil(yBase).toInt() + 2)
        val chunkPx = ceil(CHUNK_UNITS * u).toInt() + 1
        val visible = ceil(w / (CHUNK_UNITS * u)).toInt() + 2
        slots = Array(visible + 3) { Slot(Bitmap.createBitmap(chunkPx, bottom - top, Bitmap.Config.ALPHA_8)) }
    }

    fun release() {
        slots = emptyArray()
    }

    fun draw(canvas: Canvas, offU: Double, paint: Paint, frame: Long) {
        if (slots.isEmpty()) return
        val k0 = floor(offU / CHUNK_UNITS).toInt()
        val k1 = floor((offU + w / u) / CHUNK_UNITS).toInt()
        val chunkW = (CHUNK_UNITS * u).toFloat()
        for (k in k0..k1) {
            val x = ((k * CHUNK_UNITS - offU) * u).toFloat()
            val slot = find(k)
            if (slot != null && slot.ready) {
                canvas.drawBitmap(slot.bitmap, x, top.toFloat(), paint)
                slot.lastDrawnFrame = frame
            } else {
                if (slot == null) request(k, k0, k1, frame)
                fallback.paint(canvas, layer, offU, max(0f, x - 1f), min(w.toFloat(), x + chunkW + 1f), paint)
            }
        }
        // 진행 방향으로 한 조각 미리 굽는다.
        if (find(k1 + 1) == null) request(k1 + 1, k0, k1, frame)
        canvas.drawRect(0f, bottom - 1f, w.toFloat(), h.toFloat(), paint)
    }

    private fun find(k: Int): Slot? {
        for (s in slots) if (s.index == k) return s
        return null
    }

    private fun request(k: Int, k0: Int, k1: Int, frame: Long) {
        var target: Slot? = null
        for (s in slots) {
            // 화면 밖이고, 최근 2프레임 동안 그리지 않은 슬롯만 재사용한다(RenderThread가 아직 쓰고 있을 수 있음).
            if (!s.pending && (s.index < k0 - 1 || s.index > k1 + 1) && frame - s.lastDrawnFrame > 2) {
                target = s
                break
            }
        }
        val slot = target ?: return
        slot.ready = false
        slot.index = k
        slot.pending = true
        val top = top
        val u = u
        val g = g
        val yBase = yBase
        executor.execute {
            bake(slot.bitmap, k, top, u, g, yBase)
            if (slot.index == k) slot.ready = true
            slot.pending = false
        }
    }

    private fun bake(bmp: Bitmap, k: Int, top: Int, u: Float, g: Float, yBase: Float) {
        bmp.eraseColor(Color.TRANSPARENT)
        bakeCanvas.setBitmap(bmp)
        val p = bakePath
        p.rewind()
        val hb = bmp.height.toFloat()
        val wb = bmp.width.toFloat()
        p.moveTo(0f, hb)
        var x = 0f
        while (true) {
            val wx = k * CHUNK_UNITS + x / u
            p.lineTo(x, (yBase - heightNorm(layer, wx) * g * layer.amp).toFloat() - top)
            if (x >= wb) break
            x = min(x + 2f, wb)
        }
        p.lineTo(wb, hb)
        p.close()
        bakeCanvas.drawPath(p, bakePaint)
        bakeCanvas.setBitmap(null)
    }

    companion object {
        /** 조각 폭(단위). 가로 화면 기준 화면의 약 1/3. */
        const val CHUNK_UNITS = 256.0
    }
}
