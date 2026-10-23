package com.kero.passingby.scene


import kotlin.math.min

/** 톤 배열 인덱스. */
internal const val TONE_BASE = 0
internal const val TONE_DARK = 1
internal const val TONE_LIGHT = 2
internal const val TONE_LIGHT_BOOST = 3

/** 기본색에서 그늘(하늘 위쪽 색 쪽)·빛(햇빛 쪽) 색을 뽑는다. 밤에는 빛이 약해진다. */
internal fun computeTones(base: Int, strength: Float, p: Palette, out: IntArray) {
    val sunlight = mix(p.c[Palette.SUN], p.c[Palette.BOT], .5f)
    val la = (.12f + .22f * (1 - p.f[Palette.STARS])) * strength
    out[TONE_BASE] = base
    out[TONE_DARK] = mix(base, p.c[Palette.TOP], .4f * strength)
    out[TONE_LIGHT] = mix(base, sunlight, la)
    out[TONE_LIGHT_BOOST] = mix(base, sunlight, min(.85f, la * 2.4f))
    // 눈이 쌓이면 빛 받는 윗면(잎·가지 위)이 하얗게 덮인다.
    val cover = p.f[Palette.SNOW]
    if (cover > .001f) {
        val sw = p.c[Palette.SNOW_WHITE]
        out[TONE_BASE] = mix(out[TONE_BASE], sw, cover * .2f)
        out[TONE_LIGHT] = mix(out[TONE_LIGHT], sw, cover * .75f)
        out[TONE_LIGHT_BOOST] = mix(out[TONE_LIGHT_BOOST], sw, cover * .75f)
    }
}
