package com.kero.passingby.content

import com.kero.passingby.experience.train.R
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.LandmarkKind
import com.kero.passingby.scene.Themes

/** 일본 노선. */
object JapanCourses {
    /** 도쿄·오사카: 유리 초고층이 빽빽한 도심. */
    private val TOWERS = CityLook(rise = 1.3f, apt = 0f)
    /** 시즈오카: 중간 높이 시가지 뒤로 후지산. */
    private val SHIZUOKA = CityLook(rise = .7f, apt = 0f, mountains = .5f)
    private val NAGOYA = CityLook(rise = 1.1f, apt = 0f)
    /** 교토: 높이 제한으로 낮게 깔린 시가지와 세 방향의 산. */
    private val KYOTO = CityLook(rise = .45f, apt = 0f, mountains = .8f)

    /** 도카이도선. 무료. */
    val TOKAIDO = Course(
        "japan", 2, R.string.region_japan, R.string.vehicle_high_speed,
        listOf(
            Station(R.string.station_tokyo, "東京", "Tōkyō", TOWERS),
            Station(R.string.station_shizuoka, "静岡", "Shizuoka", SHIZUOKA),
            Station(R.string.station_nagoya, "名古屋", "Nagoya", NAGOYA),
            Station(R.string.station_kyoto, "京都", "Kyōto", KYOTO),
            Station(R.string.station_osaka, "大阪", "Ōsaka", TOWERS),
        ),
        Course.FAST, null, snowy = true, Themes.JAPAN,
        guide = RouteGuides.TOKAIDO,
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
            // 도쿄를 떠나면 곧 오른쪽에 도쿄 타워.
            LandmarkSpot.atStation(LandmarkKind.TOKYO_TOWER, 0, 150f),
            LandmarkSpot.along(LandmarkKind.FUJI, 0, .62f),
            // 시즈오카에 서면 시가지 너머 후지산.
            LandmarkSpot.atStation(LandmarkKind.FUJI, 1, 150f),
            // 나고야역 위 쌍둥이 타워.
            LandmarkSpot.atStation(LandmarkKind.TWIN_TOWERS, 2, 100f),
            // 교토에 서면 왼쪽에 오층탑, 오른쪽에 교토 타워.
            LandmarkSpot.atStation(LandmarkKind.PAGODA, 3, -100f),
            LandmarkSpot.atStation(LandmarkKind.KYOTO_TOWER, 3, 95f),
            LandmarkSpot.atStation(LandmarkKind.CASTLE, 4, -120f),
        ),
    )

    val ALL = listOf(TOKAIDO)
}
