package com.kero.passingby.content

import com.kero.passingby.experience.train.R
import android.graphics.Color
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.Hill
import com.kero.passingby.scene.LandmarkKind
import com.kero.passingby.scene.RoofCap
import com.kero.passingby.scene.Themes

/**
 * 한국 고속열차 노선. 정차역은 실제 고속열차가 서는 역에서 고르고, 화면과 이름판에는 역 이름 대신 도시 이름을 쓴다
 * (용산역 → 서울, 동대구역 → 대구, 여수엑스포역 → 여수).
 */
object KoreaCourses {
    /** 서울: 북한산·관악산이 둘러싼 고층 도시. */
    private val SEOUL = CityLook(rise = 1.15f, mountains = .9f)
    /** 대구: 팔공산이 감싼 분지의 아파트 도시. */
    private val DAEGU = CityLook(mountains = .8f)
    /** 부산: 바다 앞 초고층과 바로 뒤의 가파른 산. */
    private val BUSAN = CityLook(rise = 1.25f, mountains = 1.3f)
    /** 전주: 검은 기와 한옥 지붕이 낮게 깔린 마을과 뒤의 산. */
    private val JEONJU = CityLook(
        rise = .35f, apt = 0f, cap = RoofCap.HANOK, mountains = .6f,
        facades = intArrayOf(Color.rgb(238, 234, 224), Color.rgb(226, 218, 200), Color.rgb(176, 140, 104)),
    )
    private val SUNCHEON = CityLook(rise = .75f, apt = .35f, mountains = .5f)
    private val YEOSU = CityLook(rise = .7f, apt = .3f, mountains = .6f)
    /** 양평: 강가의 낮은 읍내와 둘레 산. */
    private val YANGPYEONG = CityLook(rise = .45f, apt = .25f, mountains = .7f)
    /** 평창: 높은 산 아래 작은 마을. */
    private val PYEONGCHANG = CityLook(rise = .3f, apt = 0f, mountains = 1.1f, hills = listOf(Hill(110f, 120f, 58f)))
    /** 강릉: 중간 높이 시가지 뒤로 태백산맥. */
    private val GANGNEUNG_CITY = CityLook(rise = .7f, apt = .4f, mountains = 1f)

    private val seoul = Station(R.string.station_seoul, "서울", "Seoul", SEOUL)

    /** 경부선. 무료. */
    val GYEONGBU = Course(
        "korea", 1, R.string.region_korea_gyeongbu, R.string.vehicle_high_speed,
        listOf(
            seoul,
            Station(R.string.station_daejeon, "대전", "Daejeon", CityLook(mountains = .4f)),
            Station(R.string.station_daegu, "대구", "Daegu", DAEGU),
            Station(R.string.station_busan, "부산", "Busan", BUSAN),
        ),
        Course.FAST, null, snowy = true, Themes.KOREA,
        guide = RouteGuides.GYEONGBU,
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
        landmarks = listOf(
            // 서울에 서면 남산 탑, 대전에 서면 한빛탑, 부산에 서면 바다 위 광안대교.
            LandmarkSpot.atStation(LandmarkKind.N_TOWER, 0, -110f),
            LandmarkSpot.atStation(LandmarkKind.HANBIT_TOWER, 1, -90f),
            LandmarkSpot.atStation(LandmarkKind.SUSPENSION_BRIDGE, 3, -60f),
        ),
    )

    /** 전라선. 호남평야, 섬진강, 여수 바다. */
    val JEOLLA = Course(
        "korea-jeolla", 3, R.string.region_korea_jeolla, R.string.vehicle_high_speed,
        listOf(
            seoul,
            Station(R.string.station_jeonju, "전주", "Jeonju", JEONJU),
            Station(R.string.station_suncheon, "순천", "Suncheon", SUNCHEON),
            Station(R.string.station_yeosu, "여수", "Yeosu", YEOSU),
        ),
        Course.FAST, "course_korea_jeolla", snowy = true, Themes.KOREA_JEOLLA,
        guide = RouteGuides.JEOLLA,
        features = listOf(
            // 서울(용산역)을 떠나 바로 한강 철교.
            Feature(Feature.Kind.BRIDGE, 0, .1f, .125f),
            Feature(Feature.Kind.FIELD, 0, .3f, .5f, FieldStyle.PADDY),
            // 익산에서 전주까지 호남평야.
            Feature(Feature.Kind.FIELD, 0, .6f, .9f, FieldStyle.PADDY),
            Feature(Feature.Kind.TUNNEL, 1, .2f, .215f),
            // 남원·곡성·구례구를 지나며 섬진강을 따라간다.
            Feature(Feature.Kind.RIVER, 1, .45f, .78f),
            Feature(Feature.Kind.FIELD, 2, .2f, .45f, FieldStyle.PADDY),
            Feature(Feature.Kind.SEA, 2, .72f, 1.2f),
        ),
        landmarks = listOf(
            // 출발하는 용산역은 남산 바로 아래. 여수에 서면 바다 위 돌산대교.
            LandmarkSpot.atStation(LandmarkKind.N_TOWER, 0, -110f),
            LandmarkSpot.atStation(LandmarkKind.CABLE_BRIDGE, 3, -70f),
            // 여수 앞바다의 섬들.
            LandmarkSpot.atStation(LandmarkKind.ISLANDS, 3, 130f),
        ),
    )

    /** 강릉선. 남한강, 높은 산과 풍력발전기, 대관령 터널. */
    val GANGNEUNG = Course(
        "korea-gangneung", 4, R.string.region_korea_gangneung, R.string.vehicle_high_speed,
        listOf(
            seoul,
            Station(R.string.station_yangpyeong, "양평", "Yangpyeong", YANGPYEONG),
            Station(R.string.station_pyeongchang, "평창", "Pyeongchang", PYEONGCHANG),
            Station(R.string.station_gangneung, "강릉", "Gangneung", GANGNEUNG_CITY),
        ),
        Course.FAST, "course_korea_gangneung", snowy = true, Themes.KOREA_GANGNEUNG,
        guide = RouteGuides.GANGNEUNG,
        features = listOf(
            Feature(Feature.Kind.FIELD, 0, .22f, .42f, FieldStyle.PADDY),
            // 팔당부터 양평까지 한강을 따라가고, 북한강 철교를 건넌다.
            Feature(Feature.Kind.RIVER, 0, .55f, .95f),
            Feature(Feature.Kind.BRIDGE, 0, .8f, .83f),
            Feature(Feature.Kind.TUNNEL, 1, .25f, .262f),
            Feature(Feature.Kind.FIELD, 1, .35f, .52f, FieldStyle.PADDY),
            Feature(Feature.Kind.TUNNEL, 1, .62f, .635f),
            // 진부를 지나 대관령 터널(실제로는 21.7km라 짧게 줄였다).
            Feature(Feature.Kind.TUNNEL, 2, .5f, .58f),
        ),
        landmarks = listOf(
            LandmarkSpot.atStation(LandmarkKind.N_TOWER, 0, -110f),
            // 평창에 서면 산비탈의 스키점프대, 진부를 지나 대관령 능선의 풍력발전기.
            LandmarkSpot.atStation(LandmarkKind.SKI_JUMP, 2, 110f),
            LandmarkSpot.along(LandmarkKind.WIND_FARM, 2, .3f),
        ),
    )

    val ALL = listOf(GYEONGBU, JEOLLA, GANGNEUNG)
}
