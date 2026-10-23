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

/**
 * 랜드마크와 그것이 서는 레이어. 그림은 [LandmarkPainter]에 있다.
 * [water]가 true인 다리는 물을 그린 다음에 물 위(바다면 수평선 가까이, 강이면 강물 위)에 세운다.
 * 물을 중경 위에 덮어 그리므로, 중경에 그냥 세우면 바다에 가려진다.
 * [top]이 true면 중경 도시 건물 다음에 그린다. 언덕 마을 꼭대기의 성·종탑이 쌓인 집들에 묻히지 않게 한다.
 * [tone]은 재질 색. 원경은 레이어 색(안개)을 따르고, 중경·물 위는 재질대로 칠한다.
 */
enum class LandmarkKind(
    val layer: Int,
    val water: Boolean = false,
    val top: Boolean = false,
    val tone: Tone = if (water) Tone.CONCRETE else Tone.LAYER,
) {
    /** 서울 남산의 탑. */
    N_TOWER(Scenery.FAR_B),
    FUJI(Scenery.FAR_A),
    PAGODA(Scenery.MID),
    PEARL_TOWER(Scenery.FAR_B),
    EIFFEL(Scenery.FAR_B),
    /** 포르투 동 루이스 1세 다리: 양쪽 언덕 꼭대기를 잇는 위 상판, 강가에서 솟은 철제 아치, 강가를 잇는 아래 상판. */
    ARCH_BRIDGE(Scenery.MID, water = true, tone = Tone.IRON),
    /** 대전 엑스포의 탑: 원기둥 몸체, 원반 전망대, 긴 원뿔. */
    HANBIT_TOWER(Scenery.FAR_B),
    /** 현수교(부산 광안대교). */
    SUSPENSION_BRIDGE(Scenery.MID, water = true),
    /** A자 주탑 둘의 사장교(여수 돌산대교, 리스본 바스쿠 다 가마 다리). */
    CABLE_BRIDGE(Scenery.MID, water = true),
    /** 능선을 따라 선 풍력발전기들(대관령). */
    WIND_FARM(Scenery.FAR_B),
    TOKYO_TOWER(Scenery.FAR_B),
    /** 돌 축대 위 여러 층 천수각(오사카성). */
    CASTLE(Scenery.FAR_B),
    /** 기단 위 삼층 원형 지붕(베이징 천단). */
    TEMPLE_OF_HEAVEN(Scenery.FAR_B),
    /** 허리가 잘록한 격자탑과 안테나(광저우). */
    CANTON_TOWER(Scenery.FAR_B),
    /** 언덕 위 성당과 종탑의 성모상(마르세유 노트르담 드 라 가르드). */
    HILL_BASILICA(Scenery.FAR_B),
    /** 언덕 꼭대기 성벽과 네모난 탑들(리스본 상조르즈 성). */
    CASTLE_WALLS(Scenery.MID, top = true, tone = Tone.STONE),
    /** 언덕 꼭대기 대학 건물과 종탑(코임브라). */
    UNIVERSITY_TOWER(Scenery.MID, top = true, tone = Tone.STONE),
    /** 바다 위 낮은 섬들(여수). */
    ISLANDS(Scenery.MID, water = true, tone = Tone.FOLIAGE),
    /** 언덕 꼭대기 스키점프대: 높은 탑, 탑에서 내려오는 활강로, 언덕을 따라 내려가는 흰 착지 비탈(평창). */
    SKI_JUMP(Scenery.MID, tone = Tone.CONCRETE),
    /** 건물 위에 선 흰 촛대 모양 탑(교토 타워). */
    KYOTO_TOWER(Scenery.FAR_B),
    /** 한 기단 위 둥근 탑과 네모난 탑(나고야역 쌍둥이 타워). */
    TWIN_TOWERS(Scenery.FAR_B),
    /** 푸둥의 초고층 셋: 탑 모양 진마오, 병따개 모양 세계금융센터, 비틀린 상하이 타워. */
    PUDONG(Scenery.FAR_B),
    /** 호수 위 가늘고 높은 쌍탑(구이린 일월쌍탑). 밤에는 금빛·은빛. */
    TWIN_PAGODAS(Scenery.MID),
    /** 역 시계탑(파리 리옹역). */
    CLOCK_TOWER(Scenery.MID, tone = Tone.STONE),
    /** 연필 모양 원통 탑과 유리 고층(리옹 파르디외). */
    PART_DIEU(Scenery.FAR_B),
    /** 성벽과 탑이 겹친 고딕 궁전과 금빛 성모상 탑(아비뇽 교황청). */
    PAPAL_PALACE(Scenery.MID, top = true, tone = Tone.STONE),
    /** 강 가운데서 끊긴 돌다리와 작은 예배당(아비뇽 생베네제 다리). */
    BROKEN_BRIDGE(Scenery.MID, water = true, tone = Tone.STONE),
}

/** 랜드마크 재질 색. LAYER는 원경이면 레이어 색(안개), 중경이면 철재색. */
enum class Tone { LAYER, IRON, STONE, CONCRETE, FOLIAGE }

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

    /** 세계 위치 [x]에서 가장 가까운 역의 풍경. 역 사이 시골은 도시화가 0이라 바뀌는 자리가 보이지 않는다. */
    fun look(x: Double): CityLook

    /** [layer]번 지형 레이어의 세계 위치 [x]에서 지형 높이 [h](단위)를 역 둘레 지형(뒷산·언덕·골짜기)에 맞춰 바꾼다. */
    fun shape(layer: Int, x: Double, h: Float): Float

    /** [layer]번 지형이 [shape]로 더 솟을 수 있는 최대 높이(단위). 지형 조각을 굽는 비트맵 높이를 잡는 데 쓴다. */
    fun maxLift(layer: Int): Float

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
            override fun look(x: Double) = CityLook.DEFAULT
            override fun shape(layer: Int, x: Double, h: Float) = h
            override fun maxLift(layer: Int) = 0f
        }
    }
}

/**
 * 역 둘레 풍경. 같은 나라 테마 안에서 도시마다 다른 모습(저층 한옥 마을, 언덕에 쌓인 집, 강 골짜기, 높은 뒷산)을 낸다.
 * 위치·폭·높이는 정방향으로 그 역에 섰을 때 중경 화면 기준 단위다(가운데 0, 오른쪽 +). 역방향이면 좌우가 뒤집힌다.
 *
 * @param rise 건물 높이 배율. 저층 도시 0.3~0.5, 보통 1, 초고층 1.3.
 * @param apt 아파트 단지 비율. null이면 테마 값.
 * @param cap 원경·중경 건물 지붕. null이면 테마 값.
 * @param facades 원경·중경 건물 외벽색. null이면 테마 값.
 * @param mountains 원경 두 겹(뒷산)의 높이를 이만큼 더 키운다. 1이면 두 배.
 * @param hills 중경 언덕.
 * @param river 중경 강(골짜기 바닥). 언덕은 강가에서 끊기고 건물은 강 위에 서지 않는다.
 * @param hillside 중경 건물이 지형을 따라 계단처럼 쌓인다(언덕 마을).
 * @param near 근경 상가를 세울지. 언덕 마을은 강과 언덕이 보이도록 끈다.
 * @param terraces 중경 언덕에 포도밭 계단 줄을 긋는다.
 * @param townTop 0보다 크면 언덕 마을 집이 비탈 아래(강가)에서 이 높이(단위)까지만 선다. 그 위는 밭·숲.
 */
class CityLook(
    val rise: Float = 1f,
    val apt: Float? = null,
    val cap: RoofCap? = null,
    val facades: IntArray? = null,
    val mountains: Float = 0f,
    val hills: List<Hill> = emptyList(),
    val river: Span? = null,
    val hillside: Boolean = false,
    val near: Boolean = true,
    val terraces: Boolean = false,
    val townTop: Float = 0f,
) {
    companion object {
        val DEFAULT = CityLook()
    }
}

/** 중경 언덕: 가운데 [center], 반폭 [half], 높이 [height](단위). 꼭대기는 넓고 평평하며 가장자리에서 내려간다. */
class Hill(val center: Float, val half: Float, val height: Float)

/** 중경 강: 가운데 [center], 반폭 [half](단위). */
class Span(val center: Float, val half: Float)
