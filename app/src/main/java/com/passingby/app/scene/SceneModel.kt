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
                105.0, 3.2, 50f, 92f, town = false, sink = 6f,
                kinds = arrayOf(SpriteKind.ROUND, SpriteKind.PINE, SpriteKind.POPLAR, SpriteKind.BUSH, SpriteKind.BUSH, SpriteKind.REED),
            ),
        ),
    )

    const val FAR_A = 0
    const val FAR_B = 1
    const val MID = 2
    const val NEAR = 3

    /** 도시 근경의 가로수. 근경 지형 레이어 위에 이 규칙으로 배치한다. */
    val cityStreetTrees = ObjectRule(
        105.0, 1.6, 50f, 92f, town = false, sink = 6f,
        kinds = arrayOf(SpriteKind.ROUND, SpriteKind.ROUND, SpriteKind.BUSH),
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
            out.heightU = when (kind) {
                SpriteKind.BUSH -> 20 + r3 * 12
                SpriteKind.REED -> 30 + r3 * 18
                else -> r.minH + r3 * (r.maxH - r.minH)
            }
        }
    }
}

enum class SceneKind(val label: String) { COUNTRY("시골"), CITY("도시") }

/** 도시 건물 레이어의 창문 모양. */
enum class WindowStyle { DOTS, GRID, SHOP }

/**
 * 도시 건물 레이어. 건물은 조각(chunk) 단위로 결정적으로 배치되며 직사각형으로 매 프레임 그린다.
 *
 * @param apt 이 비율만큼의 구간은 높이가 고른 아파트 단지가 된다.
 * @param floorU 층 높이(단위), [colU] 창 간격(단위).
 */
class CityLayer(
    val speed: Double,
    val base: Float,
    val chunkUnits: Double,
    val perChunk: Int,
    val minW: Float,
    val maxW: Float,
    val minH: Float,
    val maxH: Float,
    val seed: Int,
    val style: WindowStyle,
    val floorU: Float,
    val colU: Float,
    val apt: Float = 0f,
)

object CityScenery {
    val far = CityLayer(0.075, .70f, 30.0, 3, 12f, 28f, 26f, 120f, 61, WindowStyle.DOTS, 4.5f, 3.6f)
    val mid = CityLayer(0.2, .80f, 76.0, 2, 30f, 70f, 22f, 60f, 71, WindowStyle.GRID, 3.4f, 3.8f, apt = .6f)
    val near = CityLayer(0.55, .885f, 64.0, 2, 22f, 44f, 10f, 26f, 81, WindowStyle.SHOP, 4.6f, 4.4f)

    /** 상가 간판 색. */
    val signs = intArrayOf(-0x8586, -0x852f01, -0x2c92, -0x4d7301, -0x871956)
}

internal fun desaturate(c: Int, k: Float): Int {
    val l = (0.3f * ((c shr 16) and 0xff) + 0.59f * ((c shr 8) and 0xff) + 0.11f * (c and 0xff)).toInt()
    return mix(c, -0x1000000 or (l shl 16) or (l shl 8) or l, k)
}

/**
 * 날씨. [rain]·[snow]는 목표 세기(0~1)이고, 실제 세기는 [SceneState]에서 서서히 따라간다.
 */
enum class Weather(val label: String, val rain: Float, val snow: Float) {
    CLEAR("맑음", 0f, 0f),
    DRIZZLE("부슬비", .4f, 0f),
    SHOWER("소나기", 1f, 0f),
    LIGHT_SNOW("적은 눈", 0f, .35f),
    HEAVY_SNOW("함박눈", 0f, 1f),
}
