package com.kero.passingby.content

import com.kero.passingby.experience.train.R
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.LandmarkKind
import com.kero.passingby.scene.Themes

/** 중국 고속철도 노선. */
object ChinaCourses {
    /** 상하이·광저우: 초고층 스카이라인. */
    private val SKYLINE = CityLook(rise = 1.15f)
    /** 지난: 천불산, 난징: 쯔진산이 도시 뒤에. */
    private val JINAN = CityLook(mountains = .5f)
    private val NANJING = CityLook(mountains = .8f)
    /** 구이린: 카르스트 봉우리 사이의 낮은 시가지. */
    private val GUILIN = CityLook(rise = .5f, apt = .2f)
    private val GUIYANG = CityLook(rise = 1.1f, mountains = .4f)

    /** 징후 고속철도(베이징 → 상하이). */
    val NORTH = Course(
        "china-north", 5, R.string.region_china_north, R.string.vehicle_high_speed,
        listOf(
            Station(R.string.station_beijing, "北京", "Běijīng"),
            Station(R.string.station_jinan, "济南", "Jǐnán", JINAN),
            Station(R.string.station_nanjing, "南京", "Nánjīng", NANJING),
            Station(R.string.station_shanghai, "上海", "Shànghǎi", SKYLINE),
        ),
        Course.FAST, "course_china_north", snowy = true, Themes.CHINA_NORTH,
        guide = RouteGuides.CHINA_NORTH,
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
        landmarks = listOf(
            // 베이징 남역 가까이 천단.
            LandmarkSpot.atStation(LandmarkKind.TEMPLE_OF_HEAVEN, 0, 110f),
            // 상하이에 서면 동방명주와 푸둥 초고층 셋.
            LandmarkSpot.atStation(LandmarkKind.PEARL_TOWER, 3, -130f),
            LandmarkSpot.atStation(LandmarkKind.PUDONG, 3, 105f),
        ),
    )

    /** 구이광 고속철도(광저우 → 구이양). */
    val SOUTH = Course(
        "china-south", 6, R.string.region_china_south, R.string.vehicle_high_speed,
        listOf(
            Station(R.string.station_guangzhou, "广州", "Guǎngzhōu", SKYLINE),
            Station(R.string.station_guilin, "桂林", "Guìlín", GUILIN),
            Station(R.string.station_guiyang, "贵阳", "Guìyáng", GUIYANG),
        ),
        Course.FAST, "course_china_south", snowy = false, Themes.CHINA_SOUTH,
        guide = RouteGuides.CHINA_SOUTH,
        features = listOf(
            Feature(Feature.Kind.FIELD, 0, .25f, .45f, FieldStyle.PADDY),
            Feature(Feature.Kind.TUNNEL, 0, .58f, .6f),
            Feature(Feature.Kind.FIELD, 0, .68f, .9f, FieldStyle.PADDY),
            // 구이린을 떠나며 리강.
            Feature(Feature.Kind.RIVER, 1, .06f, .3f),
            Feature(Feature.Kind.FIELD, 1, .4f, .7f, FieldStyle.PADDY),
        ),
        landmarks = listOf(
            LandmarkSpot.atStation(LandmarkKind.CANTON_TOWER, 0, -100f),
            // 구이린 호수 위 일월쌍탑.
            LandmarkSpot.atStation(LandmarkKind.TWIN_PAGODAS, 1, -100f),
        ),
    )

    val ALL = listOf(NORTH, SOUTH)
}
