package com.kero.passingby.scene

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.withTranslation
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** 별과 해·달. 단위 크기의 경로와 광륜을 옮겨 쓰므로 프레임마다 객체를 만들지 않는다. */
internal class CelestialPainter {
    private val starX = FloatArray(STARS) { hash01(it, 1).toFloat() }
    private val starY = FloatArray(STARS) { hash01(it, 2).pow(1.4).toFloat() }
    private val starPhase = FloatArray(STARS) { (hash01(it, 3) * Math.PI * 2).toFloat() }
    private val starSize = FloatArray(STARS) { if (hash01(it, 4) < .15) 2f else 1.2f }
    private val starSpeed = FloatArray(STARS) { .65f + hash01(it, 5).toFloat() * 1.5f }
    private val starBright = BooleanArray(STARS) { hash01(it, 6) < .12 }
    private val starTone = IntArray(STARS) { if (hash01(it, 7) < .2) 1 else 0 }
    private val starGlintPhase = FloatArray(STARS) { (hash01(it, 8) * Math.PI * 2).toFloat() }
    private val starColors = intArrayOf(Color.rgb(234, 243, 255), Color.rgb(255, 233, 199))
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starGlow = Array(starColors.size) { glowPaint(starColors[it]) }
    private val glintPath = Path().apply {
        moveTo(0f, -1f)
        quadTo(.10f, -.12f, .66f, 0f)
        quadTo(.10f, .12f, 0f, 1f)
        quadTo(-.10f, .12f, -.66f, 0f)
        quadTo(-.10f, -.12f, 0f, -1f)
        close()
    }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sunColor = 0
    private var sunWarmth = 0f

    fun draw(canvas: Canvas, layout: SceneLayout, p: Palette, elapsedSec: Double) {
        val skyH = layout.h - layout.g * .45f
        drawStars(canvas, layout.w.toFloat(), skyH, layout.u, p.f[Palette.STARS], elapsedSec)
        drawSun(canvas, layout.w.toFloat(), skyH, layout.u, p)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float, u: Float, amount: Float, t: Double) {
        if (amount <= .01f) return
        for (i in 0 until STARS) {
            val breath = .5f + .5f * sin(t * starSpeed[i] + starPhase[i]).toFloat()
            // 밝은 별 일부만 잠깐 빛을 뻗는다. 나머지는 사라지지 않고 은은하게 밝기만 바뀐다.
            val glint = if (starBright[i]) {
                max(0f, sin(t * starSpeed[i] * .64 + starGlintPhase[i]).toFloat()).pow(18)
            } else 0f
            val x = starX[i] * w
            val y = starY[i] * h
            val tone = starTone[i]
            starPaint.color = starColors[tone]
            if (starBright[i]) {
                drawGlow(canvas, x, y, (4f + glint * 4f) * u,
                    amount * (.12f + glint * .24f), starGlow[tone])
                starPaint.alpha = (amount * (.11f + glint * .58f) * 255).toInt().coerceIn(0, 255)
                val reach = (2.5f + glint * 4.8f) * u
                canvas.withTranslation(x, y) {
                    scale(reach, reach)
                    drawPath(glintPath, starPaint)
                }
            }
            starPaint.alpha = (amount * (.32f + breath * .35f + glint * .3f) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, (starSize[i] * .49f + breath * .15f) * u, starPaint)
        }
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float, u: Float, p: Palette) {
        val alpha = p.f[Palette.SUN_A]
        if (alpha <= .01f) return
        val color = p.c[Palette.SUN]
        if (color != sunColor) {
            // 푸른 달과 따뜻한 해를 팔레트 색으로 구분해 시간대·날씨의 색 보간을 따른다.
            sunWarmth = ((Color.red(color) - Color.blue(color)) / 22f).coerceIn(0f, 1f)
            val sunset = ((Color.red(color) - Color.blue(color) - 22) / 75f).coerceIn(0f, 1f)
            val core = mix(color, Color.WHITE, .78f)
            val rim = mix(
                mix(color, Color.rgb(255, 193, 95), .55f * sunWarmth),
                Color.rgb(255, 136, 85), .45f * sunset,
            )
            sunPaint.shader = RadialGradient(
                -.18f, -.24f, 1.2f,
                intArrayOf(core, color, rim), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP,
            )
            val glow = mix(
                mix(color, Color.rgb(173, 201, 255), .25f * (1 - sunWarmth)),
                Color.rgb(255, 193, 120), .5f * sunWarmth,
            )
            sunGlow.shader = glowShader(glow)
            sunColor = color
        }
        val x = p.f[Palette.SUN_X] * w
        val y = p.f[Palette.SUN_Y] * h
        val r = p.f[Palette.SUN_R] * u
        // 넓게 퍼지는 빛과 윤곽 가까운 빛을 겹친다. 비·눈의 투명도는 둘 다에 적용한다.
        drawGlow(canvas, x, y, r * (3.4f + 2.4f * sunWarmth),
            alpha * (.088f + .136f * sunWarmth), sunGlow)
        drawGlow(canvas, x, y, r * (1.9f + .8f * sunWarmth),
            alpha * (.072f + .12f * sunWarmth), sunGlow)
        sunPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.withTranslation(x, y) {
            scale(r, r)
            drawCircle(0f, 0f, 1f, sunPaint)
        }
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, alpha: Float, paint: Paint) {
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.withTranslation(x, y) {
            scale(radius, radius)
            drawCircle(0f, 0f, 1f, paint)
        }
    }

    private fun glowPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glowShader(color) }

    private fun glowShader(color: Int) = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(color, withAlpha(color, .62f), withAlpha(color, .19f), withAlpha(color, 0f)),
        floatArrayOf(0f, .24f, .58f, 1f), Shader.TileMode.CLAMP,
    )

    private fun withAlpha(color: Int, alpha: Float) = (color and 0x00ffffff) or ((alpha * 255).toInt() shl 24)

    private companion object {
        const val STARS = 110
    }
}
