package com.passingby.app.scene

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 장면 좌표계.
 * 세로 기준 길이 G를 360 "단위"로 보고, 모든 크기·속도·주기를 단위로 정의한다.
 * 그래서 해상도·화면 방향이 바뀌어도 같은 풍경이 같은 비율로 이어진다.
 */
class SceneLayout {
    var w = 0
    var h = 0
    /** 풍경이 차지하는 세로 길이(px). 세로 화면에서는 아래쪽 일부만 쓰고 나머지는 하늘이다. */
    var g = 0f
    /** 1단위의 px 크기. */
    var u = 0f

    fun update(width: Int, height: Int): Boolean {
        if (width == w && height == h) return false
        w = width
        h = height
        g = min(height.toFloat(), max(width * .56f, height * .5f))
        u = g / 360f
        return true
    }

    fun yAt(f: Float) = h - (1 - f) * g

    fun copy() = SceneLayout().also {
        it.w = w
        it.h = h
        it.g = g
        it.u = u
    }
}

/** 사물(나무·집) 배치 규칙. 밀도 노이즈로 숲·들판·마을 구간이 생긴다. */
class ObjectRule(
    val chunkUnits: Double,
    val density: Double,
    val minH: Float,
    val maxH: Float,
    val town: Boolean,
    val sink: Float,
    val kinds: Array<SpriteKind>,
)

/**
 * 지형 레이어 하나. 높이는 노이즈 곡선으로 매번 계산되므로 반복이 없다.
 *
 * @param speed 카메라 이동 대비 이동 배율(패럴랙스). 멀수록 작다.
 * @param haze 레이어 색을 안개색으로 당기는 정도. 멀수록 크다.
 * @param mist 레이어 아래쪽을 안개색으로 흐리게 하는 그라디언트 정도.
 */
class TerrainLayer(
    val speed: Double,
    val base: Float,
    val amp: Float,
    val freq: Double,
    val ridge: Double,
    val seed: Int,
    val colorKey: Int,
    val haze: Float,
    val mist: Float,
    val spriteHaze: Float = 0f,
    val objects: ObjectRule? = null,
)

object Scenery {
    val layers = arrayOf(
        TerrainLayer(0.035, .60f, .26f, 1.0 / 300, 0.8, 11, Palette.FAR, .5f, .4f),
        TerrainLayer(0.075, .67f, .16f, 1.0 / 210, 0.45, 23, Palette.FAR, .22f, .35f),
        TerrainLayer(
            0.2, .77f, .09f, 1.0 / 260, 0.0, 37, Palette.MID, .08f, .2f, spriteHaze = .35f,
            objects = ObjectRule(
                64.0, 5.2, 17f, 30f, town = true, sink = 2f,
                kinds = arrayOf(SpriteKind.PINE, SpriteKind.ROUND, SpriteKind.ROUND, SpriteKind.POPLAR),
            ),
        ),
        TerrainLayer(
            0.55, .875f, .035f, 1.0 / 340, 0.0, 51, Palette.NEAR, 0f, 0f,
            objects = ObjectRule(
                105.0, 3.0, 46f, 86f, town = false, sink = 6f,
                kinds = arrayOf(SpriteKind.ROUND, SpriteKind.PINE, SpriteKind.POPLAR, SpriteKind.BUSH, SpriteKind.BUSH),
            ),
        ),
    )

    const val CLOUD_SPEED = 0.025
    const val BED_SPEED = 1.15
    const val POLE_SPEED = 1.7
}

/** 0(낮음)~1(높음) 정규화 높이. */
internal fun heightNorm(l: TerrainLayer, wx: Double): Double {
    var n = fbm(wx * l.freq, l.seed)
    if (l.ridge > 0) {
        val r = 1 - abs(2 * fbm(wx * l.freq * 0.8, l.seed + 5) - 1)
        n = n * (1 - l.ridge) + r * l.ridge
    }
    return n
}

internal fun layerColor(l: TerrainLayer, p: Palette) = mix(p.c[l.colorKey], p.c[Palette.HAZE], l.haze)

internal fun spriteColor(l: TerrainLayer, p: Palette) = mix(p.c[Palette.TREE], p.c[Palette.HAZE], l.spriteHaze)

/** 배치 결과를 담는 재사용 객체. 그리기 루프에서 할당하지 않기 위해 호출 측이 하나씩 들고 있는다. */
internal class Placed {
    var wx = 0.0
    var heightU = 0f
    var flip = false
    var key = 0
    lateinit var sprite: Sprite
}

/** 조각(chunk) 단위의 결정적 사물 배치. 같은 조각은 언제 계산해도 같은 결과가 나온다. */
internal object Placement {
    fun count(l: TerrainLayer, r: ObjectRule, k: Int): Int {
        val d = valueNoise(k * .11 + .3, l.seed + 70)
        return floor(d.pow(1.3) * r.density + hash01(k, l.seed + 3) * .9).toInt()
    }

    fun place(l: TerrainLayer, r: ObjectRule, k: Int, j: Int, sprites: SpriteSet, out: Placed) {
        val key = k * 16 + j
        val s = l.seed
        val r1 = hash01(key, s + 11)
        val r3 = hash01(key, s + 13).toFloat()
        val r4 = hash01(key, s + 14)
        val r5 = hash01(key, s + 15)
        out.wx = (k + r1) * r.chunkUnits
        out.flip = hash01(key, s + 16) > .5
        out.key = key
        val town = if (r.town) valueNoise(k * .05 + .7, s + 90) else 0.0
        if (town > .58 && r5 < .7) {
            out.sprite = sprites.houses[(r4 * sprites.houses.size).toInt()]
            out.heightU = 15 + r3 * 9
        } else {
            val kind = r.kinds[(r4 * r.kinds.size).toInt()]
            val arr = sprites.of(kind)
            out.sprite = arr[(r5 * arr.size).toInt()]
            out.heightU = if (kind == SpriteKind.BUSH) 18 + r3 * 12 else r.minH + r3 * (r.maxH - r.minH)
        }
    }
}
