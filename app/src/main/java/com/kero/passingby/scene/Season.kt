package com.kero.passingby.scene

import androidx.annotation.StringRes
import com.kero.passingby.R
import kotlin.math.max

/** 저장 ID는 UI 순서·enum 이름과 독립적이다. 시스템 날짜로 자동 변경하지 않는다. */
enum class Season(val id: String, @param:StringRes val label: Int) {
    DEFAULT("default", R.string.season_default), AUTUMN("autumn", R.string.season_autumn);

    companion object {
        fun fromId(id: String?): Season = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** 원래 명도와 밤의 청색을 유지하면서 색상만 바꾼다. 매번 원본 팔레트로 계산한다. */
internal fun autumnColor(base: Int, tint: Int, night: Float): Int {
    fun luminance(c: Int) = .3f * ((c shr 16) and 255) + .59f * ((c shr 8) and 255) + .11f * (c and 255)
    val scale = luminance(base) / max(1f, luminance(tint))
    val r = (((tint shr 16) and 255) * scale).toInt().coerceIn(0, 255)
    val g = (((tint shr 8) and 255) * scale).toInt().coerceIn(0, 255)
    val b = ((tint and 255) * scale).toInt().coerceIn(0, 255)
    return mix(-0x1000000 or (r shl 16) or (g shl 8) or b, base, night * .7f)
}

/** 합성 순서: 원본 시간대 → 계절 → 비·눈 안개 → 지형 적설 → 식생 명암·윗면 적설. */
internal fun seasonInto(out: Palette, src: Palette, season: Season) {
    out.copyFrom(src)
    if (season == Season.DEFAULT) return
    val night = max(src.f[Palette.STARS], src.f[Palette.LIT])
    out.c[Palette.AUTUMN_RED] = autumnColor(src.c[Palette.TREE], 0xffa84931.toInt(), night)
    out.c[Palette.AUTUMN_AMBER] = autumnColor(src.c[Palette.TREE], 0xffb97a34.toInt(), night)
    out.c[Palette.AUTUMN_GOLD] = autumnColor(src.c[Palette.TREE], 0xffb69a42.toInt(), night)
    out.c[Palette.MID] = mix(src.c[Palette.MID], autumnColor(src.c[Palette.MID], 0xff977d50.toInt(), night), .22f)
    out.c[Palette.NEAR] = mix(src.c[Palette.NEAR], autumnColor(src.c[Palette.NEAR], 0xff977d50.toInt(), night), .35f)
    out.c[Palette.GROUND] = mix(src.c[Palette.GROUND], autumnColor(src.c[Palette.GROUND], 0xff977d50.toInt(), night), .4f)
}

/** 레이어별 재사용 색 버퍼. 집·상록수는 원래 색, 낙엽수는 세 단풍색, 억새는 금색을 쓴다. */
internal class FoliageTones {
    private val original = IntArray(4)
    private val red = IntArray(4)
    private val amber = IntArray(4)
    private val gold = IntArray(4)
    private var autumn = false

    fun update(base: Int, haze: Float, strength: Float, p: Palette, season: Season) {
        computeTones(base, strength, p, original)
        autumn = season == Season.AUTUMN
        if (!autumn) return
        computeTones(mix(p.c[Palette.AUTUMN_RED], p.c[Palette.HAZE], haze), strength, p, red)
        computeTones(mix(p.c[Palette.AUTUMN_AMBER], p.c[Palette.HAZE], haze), strength, p, amber)
        computeTones(mix(p.c[Palette.AUTUMN_GOLD], p.c[Palette.HAZE], haze), strength, p, gold)
    }

    fun forObject(kind: SpriteKind?, key: Int): IntArray {
        if (!autumn) return original
        return when (kind) {
            SpriteKind.ROUND, SpriteKind.POPLAR, SpriteKind.BUSH -> when ((key and Int.MAX_VALUE) % 3) {
                0 -> red
                1 -> amber
                else -> gold
            }
            SpriteKind.REED -> gold
            else -> original
        }
    }
}

/** 색을 굽는 비교용 타일만 계절·날씨를 캐시 키에 넣는다. 연속 날씨는 4단계로 묶는다. */
internal data class TileStyle(val time: TimeOfDay, val season: Season, val rain: Int, val snow: Int, val cover: Int) {
    fun matches(state: SceneState): Boolean = time == state.timeOfDay && season == state.season &&
        rain == step(state.rainAmount) && snow == step(state.snowAmount) && cover == step(state.snowCover)

    companion object {
        private fun step(value: Float) = (value * 4 + .5f).toInt()
        fun of(state: SceneState) = TileStyle(state.timeOfDay, state.season, step(state.rainAmount), step(state.snowAmount), step(state.snowCover))
    }
}
