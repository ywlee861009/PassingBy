package com.passingby.app.content

import com.passingby.app.scene.FieldStyle
import com.passingby.app.scene.LandmarkKind

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
        )
        val CHINA_SOUTH = Course(
            "china-south", 4, "중국 남부", "고속열차",
            listOf(
                Station("광저우", "广州", "Guǎngzhōu"),
                Station("구이린", "桂林", "Guìlín"),
                Station("구이양", "贵阳", "Guìyáng"),
            ),
            FAST, "course_china_south", snowy = false,
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
