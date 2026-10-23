package com.kero.passingby.content

import com.kero.passingby.experience.train.R
import android.graphics.Color
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.Hill
import com.kero.passingby.scene.LandmarkKind
import com.kero.passingby.scene.RoofCap
import com.kero.passingby.scene.Span
import com.kero.passingby.scene.Themes

/** 프랑스 노선. */
object FranceCourses {
    /** 아비뇽: 론강가의 바위 언덕에 교황청, 그 아래 낮은 돌집, 강에서 끊긴 다리. */
    private val AVIGNON = CityLook(
        rise = .45f, apt = 0f, cap = RoofCap.TILE,
        facades = intArrayOf(Color.rgb(226, 210, 176), Color.rgb(214, 196, 160), Color.rgb(236, 224, 200)),
        hills = listOf(Hill(80f, 150f, 34f)), river = Span(-175f, 60f), hillside = true, near = false,
    )

    /** 마르세유: 크림·황토색 낮은 시가지와 뒤의 흰 석회암 산. */
    private val MARSEILLE = CityLook(
        rise = .7f, apt = .1f, cap = RoofCap.TILE, mountains = .8f,
        facades = intArrayOf(Color.rgb(236, 224, 200), Color.rgb(220, 190, 140), Color.rgb(240, 236, 226), Color.rgb(210, 170, 140)),
    )

    /** 남동·지중해 고속선(파리 → 마르세유). */
    val SUD_EST = Course(
        "france", 8, R.string.region_france, R.string.vehicle_high_speed,
        listOf(
            Station(R.string.station_paris, "Paris", "Gare de Lyon"),
            Station(R.string.station_lyon, "Lyon", "Part-Dieu", CityLook(mountains = .3f)),
            // 고속역의 실제 이름에는 상표가 들어 있어 지역명을 쓴다.
            Station(R.string.station_avignon, "Avignon", "Provence", AVIGNON),
            Station(R.string.station_marseille, "Marseille", "Saint-Charles", MARSEILLE),
        ),
        Course.FAST, "course_france", snowy = false, Themes.FRANCE,
        guide = RouteGuides.FRANCE,
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
        landmarks = listOf(
            // 파리 리옹역 시계탑과 멀리 에펠탑.
            LandmarkSpot.atStation(LandmarkKind.EIFFEL, 0, -130f),
            LandmarkSpot.atStation(LandmarkKind.CLOCK_TOWER, 0, 110f),
            // 리옹 파르디외 역 앞 고층들.
            LandmarkSpot.atStation(LandmarkKind.PART_DIEU, 1, -40f),
            // 아비뇽 바위 언덕의 교황청과 론강의 끊긴 다리.
            LandmarkSpot.atStation(LandmarkKind.PAPAL_PALACE, 2, 80f),
            LandmarkSpot.atStation(LandmarkKind.BROKEN_BRIDGE, 2, -175f),
            // 마르세유에 서면 언덕 위 노트르담 드 라 가르드.
            LandmarkSpot.atStation(LandmarkKind.HILL_BASILICA, 3, -60f),
        ),
    )

    val ALL = listOf(SUD_EST)
}
