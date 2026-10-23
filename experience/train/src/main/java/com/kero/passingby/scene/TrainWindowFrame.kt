package com.kero.passingby.scene


import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * 기차 창틀. 화면 전체를 실내 벽으로 덮고, 둥근 창(유리) 안에만 풍경이 보이게 한다.
 * 이미지 없이 코드로 그리므로 화면 비율·시간대가 바뀌어도 바로 맞춰진다.
 *
 * 그리는 순서: [glassPath]로 잘라 풍경 → [drawGlass] → 잘라내기 해제 → [drawFrame].
 * 크기는 모두 화면 짧은 변(m)에 비례한다.
 */
class TrainWindowFrame {
    /** 풍경이 보이는 창 구멍(화면 좌표). */
    val opening = RectF()
    val glassPath = Path()

    private val recessOuter = RectF()
    private val panelPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val recessPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val ledge = RectF()
    private val ledgeShadow = RectF()

    private var w = 0
    private var h = 0
    private var m = 0f
    private var radius = 0f
    private var outerRadius = 0f
    private var keyPanel = 0
    private var keyShade = 0

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recessPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(22, 255, 255, 255)
    }
    private val gasketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(235, 14, 14, 16)
    }
    /** 벽 패널과 유리가 만나는 틈. */
    private val seamPaint = Paint().apply { color = Color.argb(235, 14, 14, 16) }
    /** 옆에서 들어온 벽 패널이 창 쪽으로 드리우는 그림자. 패널 가장자리를 원점으로 그린다. */
    private val sideShadowPaint = Paint()
    private var sideShadowW = 0f
    private val ledgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledgeLinePaint = Paint().apply { color = Color.argb(34, 255, 255, 255) }
    private val ledgeShadowPaint = Paint()
    private val glassShadePaint = Paint()
    private val glassStreakPaint = Paint()
    private val nightPaint = Paint()

    /** 번인 완화용으로 창틀을 움직일 수 있는 최대 거리. 벽은 이만큼 화면 밖까지 그린다. */
    val driftMax: Float
        get() = m * .003f

    /** @return 크기가 바뀌어 다시 계산했으면 true */
    fun layout(width: Int, height: Int): Boolean {
        if (width == w && height == h) return false
        w = width
        h = height
        m = min(width, height).toFloat()
        val side = (m * .075f).toInt().toFloat()
        val top = (m * .085f).toInt().toFloat()
        val bottom = (m * .16f).toInt().toFloat()
        opening.set(side, top, width - side, height - bottom)
        radius = m * .07f
        val bevel = m * .022f
        outerRadius = radius + bevel
        recessOuter.set(opening)
        recessOuter.inset(-bevel, -bevel)

        // rewind()는 채우기 방식을 WINDING으로 되돌리므로, 구멍을 내는 경로는 reset()을 쓴다.
        glassPath.rewind()
        glassPath.addRoundRect(opening, radius, radius, Path.Direction.CW)
        recessPath.reset()
        recessPath.addRoundRect(recessOuter, outerRadius, outerRadius, Path.Direction.CW)
        recessPath.addRoundRect(opening, radius, radius, Path.Direction.CW)
        val margin = m * .01f
        panelPath.reset()
        panelPath.addRect(-margin, -margin, width + margin, height + margin, Path.Direction.CW)
        panelPath.addRoundRect(recessOuter, outerRadius, outerRadius, Path.Direction.CW)

        edgePaint.strokeWidth = max(1f, m * .004f)
        gasketPaint.strokeWidth = m * .014f

        // 창 아래 선반(팔걸이 턱)과 그 아래 그림자.
        val ledgeTop = recessOuter.bottom + m * .025f
        ledge.set(-margin, ledgeTop, width + margin, ledgeTop + m * .03f)
        ledgeShadow.set(-margin, ledge.bottom, width + margin, ledge.bottom + m * .05f)
        ledgeShadowPaint.shader = LinearGradient(
            0f, ledgeShadow.top, 0f, ledgeShadow.bottom,
            Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )

        sideShadowW = m * .05f
        sideShadowPaint.shader = LinearGradient(
            -sideShadowW, 0f, 0f, 0f,
            Color.TRANSPARENT, Color.argb(110, 0, 0, 0), Shader.TileMode.CLAMP,
        )

        // 유리: 위쪽 창틀이 드리운 그림자와 비스듬한 반사광. 둘 다 크기에만 의존한다.
        glassShadePaint.shader = LinearGradient(
            0f, opening.top, 0f, opening.top + m * .08f,
            Color.argb(64, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        glassStreakPaint.shader = LinearGradient(
            opening.left, opening.top, opening.left + opening.width() * .5f, opening.bottom,
            intArrayOf(0, 0, Color.argb(18, 255, 255, 255), 0, 0, Color.argb(11, 255, 255, 255), 0),
            floatArrayOf(0f, .30f, .34f, .38f, .42f, .44f, .47f),
            Shader.TileMode.CLAMP,
        )
        keyPanel = 0
        keyShade = 0
        return true
    }

    /** 유리 효과. [glassPath]로 잘라 둔 상태에서 풍경 위에 그린다. */
    fun drawGlass(canvas: Canvas, p: Palette) {
        canvas.drawRect(opening, glassShadePaint)
        canvas.drawRect(opening, glassStreakPaint)
        // 밤에는 불 켜진 실내가 유리에 비친다.
        val lit = p.f[Palette.LIT]
        if (lit > .02f) {
            nightPaint.color = Color.argb((lit * 20).toInt(), 255, 214, 160)
            canvas.drawRect(opening, nightPaint)
        }
    }

    fun drawFrame(canvas: Canvas, p: Palette) {
        val panel = p.c[Palette.PANEL]
        val shade = p.c[Palette.PANEL_SHADE]
        if (panel != keyPanel || shade != keyShade) {
            keyPanel = panel
            keyShade = shade
            // 창 근처는 창밖 빛을 받아 밝고, 화면 가장자리로 갈수록 어두워진다.
            panelPaint.shader = RadialGradient(
                w / 2f, h * .45f, max(w, h) * .75f,
                intArrayOf(mix(panel, Color.WHITE, .06f), panel, shade),
                floatArrayOf(0f, .55f, 1f),
                Shader.TileMode.CLAMP,
            )
            // 창 둘레 홈: 위는 그늘지고 아래는 빛을 받는다.
            recessPaint.shader = LinearGradient(
                0f, recessOuter.top, 0f, recessOuter.bottom,
                mix(shade, Color.BLACK, .35f), mix(panel, Color.WHITE, .1f), Shader.TileMode.CLAMP,
            )
            ledgePaint.color = mix(panel, Color.WHITE, .12f)
        }
        canvas.drawPath(panelPath, panelPaint)
        canvas.drawPath(recessPath, recessPaint)
        canvas.drawRoundRect(recessOuter, outerRadius, outerRadius, edgePaint)
        canvas.drawRoundRect(opening, radius, radius, gasketPaint)
        drawLedge(canvas)
    }

    /**
     * 정차 중 창을 가리는 벽 패널. 벽과 같은 칠이라 이어진 벽처럼 보인다.
     * 아래에서 올라올 때는 윗면이 창턱이 되고, 옆에서 들어올 때는 창 쪽 모서리에 그늘진 홈을 둔다.
     * [drawFrame] 다음에 불러야 한다(벽 칠을 거기서 정한다).
     * @param edgePx 화면 아래(또는 끝) 가장자리에서 패널 가장자리까지의 거리.
     */
    fun drawSeat(canvas: Canvas, fromEnd: Boolean, edgePx: Float) {
        val margin = m * .01f
        if (fromEnd) {
            val left = w - edgePx
            if (left >= recessOuter.right) return
            canvas.drawRect(left, -margin, w + margin, h + margin, panelPaint)
            // 틈선을 위아래 벽까지 그으면 전봇대처럼 보여서, 창 높이 안에서만 부드러운 그림자로 둔다.
            canvas.save()
            canvas.translate(left, 0f)
            canvas.drawRect(-sideShadowW, recessOuter.top, 0f, recessOuter.bottom, sideShadowPaint)
            canvas.restore()
            canvas.drawRect(left, -margin, left + max(1f, m * .003f), h + margin, ledgeLinePaint)
            drawLedge(canvas)
        } else {
            val top = h - edgePx
            if (top >= recessOuter.bottom) return
            canvas.drawRect(-margin, top, w + margin, h + margin, panelPaint)
            canvas.drawRect(-margin, top - m * .014f, w + margin, top, seamPaint)
            val sill = m * .03f
            canvas.save()
            canvas.translate(0f, top + sill - ledgeShadow.top)
            canvas.drawRect(ledgeShadow, ledgeShadowPaint)
            canvas.restore()
            canvas.drawRect(-margin, top, w + margin, top + sill, ledgePaint)
            canvas.drawRect(-margin, top, w + margin, top + max(1f, m * .002f), ledgeLinePaint)
        }
    }

    private fun drawLedge(canvas: Canvas) {
        canvas.drawRect(ledgeShadow, ledgeShadowPaint)
        canvas.drawRect(ledge, ledgePaint)
        canvas.drawRect(ledge.left, ledge.top, ledge.right, ledge.top + max(1f, m * .002f), ledgeLinePaint)
    }
}
