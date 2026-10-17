package com.kero.passingby.scene

import android.graphics.Color

/** 마을 집 지붕 모양. */
enum class RoofStyle {
    /** 박공(삼각) 지붕. */
    GABLE,
    /** 처마가 넓은 모임지붕(일본·중국 기와집). */
    HIP,
    /** 평지붕. */
    FLAT,
    /** 낮은 박공에 주황 기와(남유럽). */
    TILE,
}

/** 근경 땅에 까는 밭 무늬. */
enum class FieldStyle { NONE, PADDY, TEA, VINE, LAVENDER, WHEAT }

/** 선로 옆 구조물. */
enum class BarrierStyle {
    NONE,
    /** 반투명 방음벽(도시 구간만). */
    PANEL,
    /** 고가 선로의 콘크리트 난간(늘). */
    PARAPET,
}

/** 랜드마크와 그것이 서는 레이어. */
enum class LandmarkKind(val layer: Int) {
    N_TOWER(Scenery.FAR_B),
    FUJI(Scenery.FAR_A),
    PAGODA(Scenery.MID),
    PEARL_TOWER(Scenery.FAR_B),
    EIFFEL(Scenery.FAR_B),
    ARCH_BRIDGE(Scenery.MID),
}

/** 세계 위치(카메라 이동 거리)에 놓인 랜드마크. */
class Landmark(val kind: LandmarkKind, val x: Double)

/** 집 양식. 벽·지붕 색은 집마다 이 중에서 고르고, 그릴 때 하늘 팔레트에 섞어 시간대를 따른다. */
class HouseStyle(val roof: RoofStyle, val walls: IntArray, val roofs: IntArray)

/** 역 이름판 색. */
class SignStyle(val board: Int, val ink: Int, val stripe: Int)

/**
 * 코스 테마. 지형·식생·마을·도시·선로 구조물·이름판처럼 코스 전체에 걸쳐 같은 것만 담는다.
 * 코스 안에서 위치마다 달라지는 것(바다·밭·터널·다리·랜드마크)은 [Zones]가 맡는다.
 */
class Theme(
    val id: String,
    /** 원경 둘, 중경, 근경 순서. 각 레이어의 식생 규칙([ObjectRule])도 여기 들어 있다. */
    val layers: Array<TerrainLayer>,
    val cityFar: CityLayer,
    val cityMid: CityLayer,
    val cityNear: CityLayer,
    val streetTrees: ObjectRule,
    val house: HouseStyle,
    val barrier: BarrierStyle,
    val sign: SignStyle,
)

/**
 * 코스 안에서 위치마다 달라지는 것. 여정이 코스 정의와 계획으로 만들어 [SceneState.zones]에 넣는다.
 * 그리기 루프에서 여러 번 부르므로 가볍게 구현해야 한다.
 */
interface Zones {
    /** 수평선까지 이어지는 바다(0~1). */
    fun sea(x: Double): Float
    /** 중경 높이에서 맞은편 언덕이 보이는 강·호수(0~1). */
    fun river(x: Double): Float
    /** 근경 밭 무늬와 그 비중. */
    fun fieldStyle(x: Double): FieldStyle
    fun field(x: Double): Float
    /** 터널·다리 구간(세계 위치 시작·끝 쌍). */
    val tunnels: DoubleArray
    val bridges: DoubleArray
    val landmarks: List<Landmark>
    /** 물(바다·강·다리) 구역이 하나라도 있으면 true. 없으면 물 그리기를 통째로 건너뛴다. */
    val water: Boolean
    /** 밭 구역이 하나라도 있으면 true. */
    val fields: Boolean

    companion object {
        val NONE = object : Zones {
            override fun sea(x: Double) = 0f
            override fun river(x: Double) = 0f
            override fun fieldStyle(x: Double) = FieldStyle.NONE
            override fun field(x: Double) = 0f
            override val tunnels = DoubleArray(0)
            override val bridges = DoubleArray(0)
            override val landmarks = emptyList<Landmark>()
            override val water = false
            override val fields = false
        }
    }
}
