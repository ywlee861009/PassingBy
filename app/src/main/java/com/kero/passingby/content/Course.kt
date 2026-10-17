package com.kero.passingby.content

import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.LandmarkKind

/**
 * 역. [name]은 화면 문구에 쓰는 한국어 이름, [local]은 역 이름판에 크게 쓰는 현지 표기, [roman]은 그 아래 작은 글자.
 */
class Station(val name: String, val local: String, val roman: String)

/**
 * 코스. 세션 하나가 코스 전체이고, 중간 역마다 잠깐 정차한다(명세 7.2).
 * 실제 상표명은 쓰지 않는다("고속열차" 같은 일반 명칭).
 *
 * @param number 1부터. 화면에 "1코스"처럼 쓴다.
 * @param cruise 시골 구간 순항 속도(장면 단위/초).
 * @param productId Play 결제 상품 ID. 무료 코스는 null.
 */
class Course(
    val id: String,
    val number: Int,
    val region: String,
    val vehicle: String,
    val stations: List<Station>,
    val cruise: Double,
    val productId: String?,
    /** 이 코스 날씨에 눈이 올 수 있는지. 남쪽 코스는 눈이 오지 않는다. */
    val snowy: Boolean,
    val features: List<Feature> = emptyList(),
    val landmarks: List<LandmarkSpot> = emptyList(),
) {
    val free: Boolean get() = productId == null

    /** [direction]이 −1이면 역방향(부산 → 서울). */
    fun ordered(direction: Int): List<Station> = if (direction < 0) stations.asReversed() else stations

    fun title(direction: Int): String {
        val s = ordered(direction)
        return "${s.first().name} → ${s.last().name}"
    }

    /** 중간 정차역 문구. "대전 · 대구 정차" */
    fun stopsLine(direction: Int): String {
        val mid = ordered(direction).drop(1).dropLast(1)
        return if (mid.isEmpty()) "직통" else mid.joinToString(" · ") { it.name } + " 정차"
    }

    companion object {
        const val FAST = 300.0
        const val REGIONAL = 170.0

        val KOREA = Course(
            "korea", 1, "한국", "고속열차",
            listOf(
                Station("서울", "서울", "Seoul"),
                Station("대전", "대전", "Daejeon"),
                Station("대구", "대구", "Daegu"),
                Station("부산", "부산", "Busan"),
            ),
            FAST, null, snowy = true,
            features = listOf(
                // 서울을 떠나 바로 한강 철교.
                Feature(Feature.Kind.BRIDGE, 0, .125f, .15f),
                Feature(Feature.Kind.FIELD, 0, .3f, .72f, FieldStyle.PADDY),
                Feature(Feature.Kind.FIELD, 1, .26f, .74f, FieldStyle.PADDY),
                Feature(Feature.Kind.TUNNEL, 2, .55f, .565f),
                Feature(Feature.Kind.FIELD, 2, .25f, .45f, FieldStyle.PADDY),
                // 부산에 다가가면 도시 너머로 바다.
                Feature(Feature.Kind.SEA, 2, .9f, 1.2f),
            ),
            landmarks = listOf(LandmarkSpot(LandmarkKind.N_TOWER, 0, -.012f)),
        )
        val JAPAN = Course(
            "japan", 2, "일본", "고속열차",
            listOf(
                Station("도쿄", "東京", "Tōkyō"),
                Station("시즈오카", "静岡", "Shizuoka"),
                Station("나고야", "名古屋", "Nagoya"),
                Station("교토", "京都", "Kyōto"),
                Station("오사카", "大阪", "Ōsaka"),
            ),
            FAST, null, snowy = true,
            features = listOf(
                Feature(Feature.Kind.TUNNEL, 0, .3f, .318f),
                // 사가미 만 해안, 후지산을 지나 시즈오카 차밭.
                Feature(Feature.Kind.SEA, 0, .38f, .52f),
                Feature(Feature.Kind.FIELD, 0, .7f, .94f, FieldStyle.TEA),
                Feature(Feature.Kind.FIELD, 1, .08f, .3f, FieldStyle.TEA),
                // 하마나 호수.
                Feature(Feature.Kind.RIVER, 1, .5f, .62f),
                Feature(Feature.Kind.FIELD, 1, .7f, .86f, FieldStyle.PADDY),
                Feature(Feature.Kind.FIELD, 2, .25f, .72f, FieldStyle.PADDY),
                Feature(Feature.Kind.FIELD, 3, .28f, .6f, FieldStyle.PADDY),
            ),
            landmarks = listOf(
                LandmarkSpot(LandmarkKind.FUJI, 0, .62f),
                // 교토에 서면 왼쪽에 오층탑.
                LandmarkSpot(LandmarkKind.PAGODA, 2, .994f),
            ),
        )
        val CHINA_NORTH = Course(
            "china-north", 3, "중국 북부", "고속열차",
            listOf(
                Station("베이징", "北京", "Běijīng"),
                Station("지난", "济南", "Jǐnán"),
                Station("난징", "南京", "Nánjīng"),
                Station("상하이", "上海", "Shànghǎi"),
            ),
            FAST, "course_china_north", snowy = true,
            features = listOf(
                Feature(Feature.Kind.FIELD, 0, .2f, .8f, FieldStyle.WHEAT),
                Feature(Feature.Kind.FIELD, 1, .2f, .55f, FieldStyle.WHEAT),
                Feature(Feature.Kind.FIELD, 1, .6f, .8f, FieldStyle.PADDY),
                // 난징 앞 양쯔강 대교.
                Feature(Feature.Kind.BRIDGE, 1, .86f, .9f),
                Feature(Feature.Kind.FIELD, 2, .2f, .38f, FieldStyle.PADDY),
                // 타이후 호수 근처의 물길.
                Feature(Feature.Kind.RIVER, 2, .42f, .54f),
                Feature(Feature.Kind.FIELD, 2, .58f, .78f, FieldStyle.PADDY),
            ),
            landmarks = listOf(LandmarkSpot(LandmarkKind.PEARL_TOWER, 2, .985f)),
        )
        val CHINA_SOUTH = Course(
            "china-south", 4, "중국 남부", "고속열차",
            listOf(
                Station("광저우", "广州", "Guǎngzhōu"),
                Station("구이린", "桂林", "Guìlín"),
                Station("구이양", "贵阳", "Guìyáng"),
            ),
            FAST, "course_china_south", snowy = false,
            features = listOf(
                Feature(Feature.Kind.FIELD, 0, .25f, .45f, FieldStyle.PADDY),
                Feature(Feature.Kind.TUNNEL, 0, .58f, .6f),
                Feature(Feature.Kind.FIELD, 0, .68f, .9f, FieldStyle.PADDY),
                // 구이린을 떠나며 리강.
                Feature(Feature.Kind.RIVER, 1, .06f, .3f),
                Feature(Feature.Kind.FIELD, 1, .4f, .7f, FieldStyle.PADDY),
            ),
            landmarks = listOf(LandmarkSpot(LandmarkKind.PAGODA, 0, .99f)),
        )
        val PORTUGAL = Course(
            "portugal", 5, "포르투갈", "급행열차",
            listOf(
                Station("리스본", "Lisboa", "Santa Apolónia"),
                Station("코임브라", "Coimbra", "Coimbra-B"),
                Station("포르투", "Porto", "Campanhã"),
                Station("레구아", "Régua", "Peso da Régua"),
            ),
            REGIONAL, "course_portugal", snowy = false,
            features = listOf(
                // 리스본을 떠나며 테주강 하구.
                Feature(Feature.Kind.RIVER, 0, .05f, .22f),
                Feature(Feature.Kind.FIELD, 0, .58f, .8f, FieldStyle.VINE),
                // 아베이루 석호와 대서양.
                Feature(Feature.Kind.SEA, 1, .42f, .6f),
                // 포르투에서 도루강을 따라 포도밭 계곡으로.
                Feature(Feature.Kind.RIVER, 2, .1f, .96f),
                Feature(Feature.Kind.FIELD, 2, .14f, .95f, FieldStyle.VINE),
            ),
            landmarks = listOf(LandmarkSpot(LandmarkKind.ARCH_BRIDGE, 1, .992f)),
        )
        val FRANCE = Course(
            "france", 6, "프랑스", "고속열차",
            listOf(
                Station("파리", "Paris", "Gare de Lyon"),
                Station("리옹", "Lyon", "Part-Dieu"),
                // 고속역의 실제 이름에는 상표가 들어 있어 지역명을 쓴다.
                Station("아비뇽", "Avignon", "Provence"),
                Station("마르세유", "Marseille", "Saint-Charles"),
            ),
            FAST, "course_france", snowy = false,
            features = listOf(
                Feature(Feature.Kind.FIELD, 0, .18f, .5f, FieldStyle.WHEAT),
                // 부르고뉴 포도밭.
                Feature(Feature.Kind.FIELD, 0, .6f, .84f, FieldStyle.VINE),
                // 론강 계곡.
                Feature(Feature.Kind.RIVER, 1, .2f, .6f),
                Feature(Feature.Kind.FIELD, 1, .65f, .86f, FieldStyle.VINE),
                // 프로방스 라벤더.
                Feature(Feature.Kind.FIELD, 2, .2f, .5f, FieldStyle.LAVENDER),
                Feature(Feature.Kind.FIELD, 2, .55f, .72f, FieldStyle.VINE),
                // 마르세유에 다가가면 지중해.
                Feature(Feature.Kind.SEA, 2, .88f, 1.2f),
            ),
            landmarks = listOf(LandmarkSpot(LandmarkKind.EIFFEL, 0, -.014f)),
        )

        val ALL = listOf(KOREA, JAPAN, CHINA_NORTH, CHINA_SOUTH, PORTUGAL, FRANCE)

        fun byId(id: String?): Course = ALL.firstOrNull { it.id == id } ?: KOREA
    }
}

/**
 * 코스 안 위치마다 달라지는 구역. 정방향 기준으로 [seg]번 구간의 거리 비율 [from]~[to]에 둔다.
 * 역방향으로 달릴 때는 여정이 위치를 뒤집어 쓴다.
 */
class Feature(
    val kind: Kind,
    val seg: Int,
    val from: Float,
    val to: Float,
    val style: FieldStyle = FieldStyle.NONE,
) {
    enum class Kind { SEA, RIVER, FIELD, TUNNEL, BRIDGE }
}

/** 랜드마크 위치. 정방향 기준 [seg]번 구간의 거리 비율 [at]. */
class LandmarkSpot(val kind: LandmarkKind, val seg: Int, val at: Float)
