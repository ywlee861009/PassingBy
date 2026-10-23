package com.kero.passingby.scene

import android.graphics.Color
import androidx.annotation.StringRes
import com.kero.passingby.R

enum class TimeOfDay(@param:StringRes val label: Int) {
    DAWN(R.string.time_dawn), DAY(R.string.time_day), DUSK(R.string.time_dusk), NIGHT(R.string.time_night);

    companion object {
        /** 이전 새벽/노을 설정을 현재 낮/밤 선택으로 옮긴다. */
        fun fromSetting(name: String?): TimeOfDay? = when (name) {
            "DAY", "DAWN" -> DAY
            "NIGHT", "DUSK" -> NIGHT
            else -> null
        }
    }
}

/**
 * 시간대 팔레트. 모든 레이어 색은 이 팔레트와 "멀수록 안개색(HAZE)에 가깝게" 규칙으로 정해진다.
 * 매 프레임 보간하므로 객체를 새로 만들지 않도록 배열을 재사용한다.
 */
class Palette {
    val c = IntArray(C_COUNT)
    val f = FloatArray(F_COUNT)

    fun copyFrom(o: Palette) {
        o.c.copyInto(c)
        o.f.copyInto(f)
    }

    companion object {
        const val TOP = 0
        const val BOT = 1
        const val HAZE = 2
        const val SUN = 3
        const val FAR = 4
        const val MID = 5
        const val NEAR = 6
        const val TREE = 7
        const val GROUND = 8
        const val POLE = 9
        const val CLOUD = 10
        /** 여기까지가 창밖 색. 비 효과는 창밖 색에만 적용한다. */
        const val EXTERIOR_COUNT = 11
        /** 실내 벽(창틀) 색. 창밖보다 어둡게 둬서 눈부심과 OLED 번인을 줄인다. */
        const val PANEL = 11
        const val PANEL_SHADE = 12
        /** 눈 덮인 땅·잎 색. 프리셋에는 없고 [snowInto]가 매 프레임 계산한다. */
        const val SNOW_WHITE = 13
        const val FOLIAGE_A = 14
        const val FOLIAGE_B = 15
        const val FOLIAGE_C = 16
        const val C_COUNT = 17

        const val SUN_X = 0
        const val SUN_Y = 1
        const val SUN_R = 2
        const val SUN_A = 3
        const val CLOUD_A = 4
        const val STARS = 5
        const val LIT = 6
        /** 눈 덮임 정도(0~1). [snowInto]가 채운다. */
        const val SNOW = 7
        const val F_COUNT = 8

        fun of(t: TimeOfDay): Palette = PRESETS[t.ordinal]

        // 색 순서: TOP, BOT, HAZE, SUN, FAR, MID, NEAR, TREE, GROUND, POLE, CLOUD, PANEL, PANEL_SHADE
        // 수치 순서: SUN_X, SUN_Y, SUN_R, SUN_A, CLOUD_A, STARS, LIT, SNOW
        private val PRESETS = arrayOf(
            preset(
                intArrayOf(0x2f3a66, 0xf0b8a2, 0xe6b6aa, 0xfff1dc, 0x8a83aa, 0x5d6c87, 0x3f5466, 0x2b3b4b, 0x26303c, 0x182029, 0xf7d4c8, 0x57545e, 0x2c2a31),
                floatArrayOf(.78f, .66f, 15f, 1f, .8f, .2f, .35f, 0f),
            ),
            preset(
                intArrayOf(0x4b97d6, 0xd3eaf5, 0xcde3ee, 0xfffbe9, 0x86a8c4, 0x79a476, 0x548a55, 0x2d5e3f, 0x4d5e3f, 0x262b2f, 0xffffff, 0x74716a, 0x3b3936),
                floatArrayOf(.8f, .22f, 17f, 1f, .95f, 0f, 0f, 0f),
            ),
            preset(
                intArrayOf(0x2a2c5a, 0xf49b5d, 0xee9c76, 0xffdc9e, 0x93627f, 0x5c4568, 0x3a2f4e, 0x281f3c, 0x241c33, 0x150f20, 0xf6b08e, 0x5e4f47, 0x2d2420),
                floatArrayOf(.7f, .74f, 22f, 1f, .88f, .08f, .65f, 0f),
            ),
            preset(
                intArrayOf(0x060a1c, 0x1d2848, 0x26325a, 0xe6ecf8, 0x2e3a60, 0x1b2443, 0x121a32, 0x0b1126, 0x0c1226, 0x05080e, 0x2c3760, 0x4a4038, 0x221d19),
                floatArrayOf(.24f, .24f, 12f, 1f, .65f, 1f, 1f, 0f),
            ),
        )

        private fun preset(rgb: IntArray, scalars: FloatArray) = Palette().apply {
            for (i in rgb.indices) c[i] = OPAQUE or rgb[i]
            scalars.copyInto(f)
        }
    }
}

private const val OPAQUE = -0x1000000 // 0xff000000

internal fun mix(a: Int, b: Int, t: Float): Int {
    val ar = (a shr 16) and 0xff
    val ag = (a shr 8) and 0xff
    val ab = a and 0xff
    val r = (ar + (((b shr 16) and 0xff) - ar) * t).toInt().coerceIn(0, 255)
    val g = (ag + (((b shr 8) and 0xff) - ag) * t).toInt().coerceIn(0, 255)
    val bl = (ab + ((b and 0xff) - ab) * t).toInt().coerceIn(0, 255)
    return OPAQUE or (r shl 16) or (g shl 8) or bl
}

internal fun lerpInto(out: Palette, a: Palette, b: Palette, t: Float) {
    for (i in 0 until Palette.C_COUNT) out.c[i] = mix(a.c[i], b.c[i], t)
    for (i in 0 until Palette.F_COUNT) out.f[i] = a.f[i] + (b.f[i] - a.f[i]) * t
}

/** 비가 오면 창밖 색을 채도 낮은 회청색 쪽으로 당기고 해·별을 가린다. 실내 벽 색은 그대로 둔다. */
internal fun wetInto(out: Palette, src: Palette, amount: Float) {
    out.copyFrom(src)
    if (amount <= 0.001f) return
    for (i in 0 until Palette.C_COUNT) {
        if (i in Palette.PANEL..Palette.SNOW_WHITE) continue
        val c = src.c[i]
        val l =0.3f * ((c shr 16) and 0xff) + 0.59f * ((c shr 8) and 0xff) + 0.11f * (c and 0xff)
        val r = (l * .78f + 18).toInt().coerceIn(0, 255)
        val g = (l * .8f + 22).toInt().coerceIn(0, 255)
        val b = (l * .86f + 30).toInt().coerceIn(0, 255)
        out.c[i] = mix(c, OPAQUE or (r shl 16) or (g shl 8) or b, amount * .55f)
    }
    out.f[Palette.SUN_A] = src.f[Palette.SUN_A] * (1 - amount)
    out.f[Palette.STARS] = src.f[Palette.STARS] * (1 - amount)
}

/**
 * 눈: 하늘과 먼 풍경을 회백색 안개 쪽으로 당겨 시야를 줄이고, 덮임 정도만큼 땅·언덕을 하얗게 만든다.
 * [p]를 제자리에서 바꾼다. 밤에는 눈 색도 어두운 청회색이 된다.
 */
internal fun snowInto(p: Palette, amount: Float, cover: Float) {
    val night = p.f[Palette.STARS]
    if (amount > .001f) {
        val fog = mix(desaturate(p.c[Palette.HAZE], .7f), p.c[Palette.BOT], .3f)
        p.c[Palette.TOP] = mix(p.c[Palette.TOP], fog, amount * .55f)
        p.c[Palette.BOT] = mix(p.c[Palette.BOT], fog, amount * .55f)
        p.c[Palette.HAZE] = mix(p.c[Palette.HAZE], fog, amount * .5f)
        p.c[Palette.FAR] = mix(p.c[Palette.FAR], fog, amount * .6f)
        p.c[Palette.MID] = mix(p.c[Palette.MID], fog, amount * .35f)
        p.c[Palette.NEAR] = mix(p.c[Palette.NEAR], fog, amount * .15f)
        p.c[Palette.TREE] = mix(p.c[Palette.TREE], fog, amount * .1f)
        p.c[Palette.CLOUD] = mix(p.c[Palette.CLOUD], fog, amount * .5f)
        for (i in Palette.FOLIAGE_A..Palette.FOLIAGE_C) p.c[i] = mix(p.c[i], fog, amount * .1f)
        p.f[Palette.SUN_A] *= 1 - amount
        p.f[Palette.STARS] *= 1 - amount
    }
    val snowWhite = mix(p.c[Palette.BOT], Color.WHITE, .62f * (1 - .5f * night))
    p.c[Palette.SNOW_WHITE] = snowWhite
    p.f[Palette.SNOW] = cover
    if (cover > .001f) {
        p.c[Palette.GROUND] = mix(p.c[Palette.GROUND], snowWhite, cover * .85f)
        p.c[Palette.NEAR] = mix(p.c[Palette.NEAR], snowWhite, cover * .75f)
        p.c[Palette.MID] = mix(p.c[Palette.MID], snowWhite, cover * .55f)
        p.c[Palette.FAR] = mix(p.c[Palette.FAR], snowWhite, cover * .3f)
    }
}
