package com.kero.passingby.scene

import androidx.annotation.StringRes
import com.kero.passingby.R
import kotlin.math.max

/** 저장 ID는 UI 순서와 독립적이다. 이전 기본 풍경(default)은 여름으로 복원한다. */
enum class Season(val id: String, @param:StringRes val label: Int) {
    SPRING("spring", R.string.season_spring), SUMMER("summer", R.string.season_summer),
    AUTUMN("autumn", R.string.season_autumn), WINTER("winter", R.string.season_winter);

    fun baseSnow(snowy: Boolean): Float = if (this == WINTER && snowy) .55f else 0f

    companion object {
        fun fromId(id: String?): Season = entries.firstOrNull { it.id == id } ?: SUMMER
    }
}

/** 설정의 선택과 실제 그리는 계절을 구분한다. 무작위는 여행 시드로 정해 복원 시에도 유지한다. */
enum class SeasonChoice(val season: Season?) {
    RANDOM(null), SPRING(Season.SPRING), SUMMER(Season.SUMMER), AUTUMN(Season.AUTUMN), WINTER(Season.WINTER);

    val id: String get() = season?.id ?: "random"
    val label: Int get() = season?.label ?: R.string.random

    fun resolve(seed: Int): Season = season ?: Season.entries[
        (seed xor (seed ushr 16)).ushr(1) % Season.entries.size
    ]

    companion object {
        fun fromId(id: String?): SeasonChoice = entries.firstOrNull { it.id == id } ?: SUMMER
        fun fromSeason(season: Season): SeasonChoice = entries.first { it.season == season }
    }
}

/** 원래 명도와 밤의 청색을 유지하면서 색상만 바꾼다. 매번 원본 팔레트로 계산한다. */
internal fun seasonColor(base: Int, tint: Int, night: Float): Int {
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
    if (season == Season.SUMMER) return
    val night = max(src.f[Palette.STARS], src.f[Palette.LIT])
    val a: Int
    val b: Int
    val c: Int
    val ground: Int
    val strength: Float
    when (season) {
        Season.SPRING -> {
            a = 0xffd3a5ac.toInt(); b = 0xff83b45b.toInt(); c = 0xff66a66a.toInt()
            ground = 0xff91ab67.toInt(); strength = .22f
        }
        Season.AUTUMN -> {
            a = 0xffa84931.toInt(); b = 0xffb97a34.toInt(); c = 0xffb69a42.toInt()
            ground = 0xff977d50.toInt(); strength = .4f
        }
        Season.WINTER -> {
            a = 0xff847967.toInt(); b = 0xff7b776c.toInt(); c = 0xffa79970.toInt()
            ground = 0xff8a8371.toInt(); strength = .45f
        }
        Season.SUMMER -> return
    }
    out.c[Palette.FOLIAGE_A] = seasonColor(src.c[Palette.TREE], a, night)
    out.c[Palette.FOLIAGE_B] = seasonColor(src.c[Palette.TREE], b, night)
    out.c[Palette.FOLIAGE_C] = seasonColor(src.c[Palette.TREE], c, night)
    out.c[Palette.MID] = mix(src.c[Palette.MID], seasonColor(src.c[Palette.MID], ground, night), strength * .55f)
    out.c[Palette.NEAR] = mix(src.c[Palette.NEAR], seasonColor(src.c[Palette.NEAR], ground, night), strength * .875f)
    out.c[Palette.GROUND] = mix(src.c[Palette.GROUND], seasonColor(src.c[Palette.GROUND], ground, night), strength)
}

/** 레이어별 재사용 톤. 상록수와 집은 유지하고, 낙엽수·억새에만 계절색을 쓴다. */
internal class FoliageTones {
    private val original = IntArray(4)
    private val a = IntArray(4)
    private val b = IntArray(4)
    private val c = IntArray(4)
    private var season = Season.SUMMER

    fun update(base: Int, haze: Float, strength: Float, p: Palette, season: Season) {
        computeTones(base, strength, p, original)
        this.season = season
        if (season == Season.SUMMER) return
        computeTones(mix(p.c[Palette.FOLIAGE_A], p.c[Palette.HAZE], haze), strength, p, a)
        computeTones(mix(p.c[Palette.FOLIAGE_B], p.c[Palette.HAZE], haze), strength, p, b)
        computeTones(mix(p.c[Palette.FOLIAGE_C], p.c[Palette.HAZE], haze), strength, p, c)
    }

    fun forObject(kind: SpriteKind?, key: Int): IntArray {
        if (season == Season.SUMMER) return original
        val variant = key and Int.MAX_VALUE
        return when (kind) {
            SpriteKind.ROUND, SpriteKind.POPLAR, SpriteKind.BUSH -> when (season) {
                Season.SPRING -> if (kind == SpriteKind.ROUND && variant % 5 == 0) a else if (variant % 2 == 0) b else c
                Season.AUTUMN -> when (variant % 3) { 0 -> a; 1 -> b; else -> c }
                Season.WINTER -> b
                Season.SUMMER -> original
            }
            SpriteKind.REED -> if (season == Season.SPRING) original else c
            else -> original
        }
    }
}

/** 색을 굽는 비교용 타일만 계절·날씨를 캐시 키에 넣는다. 연속 날씨는 4단계로 묶는다. */
internal data class TileStyle(val time: TimeOfDay, val season: Season, val rain: Int, val snow: Int, val cover: Int) {
    fun matches(state: SceneState): Boolean = time == state.timeOfDay && season == state.season &&
        rain == step(state.rainAmount) && snow == step(state.snowAmount) && cover == step(state.surfaceSnowCover)

    companion object {
        private fun step(value: Float) = (value * 4 + .5f).toInt()
        fun of(state: SceneState) = TileStyle(state.timeOfDay, state.season, step(state.rainAmount), step(state.snowAmount), step(state.surfaceSnowCover))
    }
}
