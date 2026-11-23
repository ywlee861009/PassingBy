package com.kero.passingby.content

import com.kero.passingby.experience.train.R
import com.kero.passingby.experience.CoursePurchasePreview
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.Hill
import com.kero.passingby.scene.LandmarkKind
import com.kero.passingby.scene.RoofCap
import com.kero.passingby.scene.Themes

object UsaCourses {
    private val LOS_ANGELES = CityLook(rise = 1.45f, mountains = .5f, near = false)
    private val ANAHEIM = CityLook(rise = .7f, apt = .18f, mountains = .25f, near = false)
    private val CAPISTRANO = CityLook(
        rise = .48f, apt = 0f, cap = RoofCap.TILE, hills = listOf(Hill(150f, 190f, 52f)),
    )
    private val OCEANSIDE = CityLook(rise = .8f, apt = .2f)
    private val SOLANA_BEACH = CityLook(rise = .5f, apt = .08f, hills = listOf(Hill(-160f, 180f, 28f)))
    private val SAN_DIEGO = CityLook(rise = 1.5f, near = false)

    val PACIFIC_COAST = Course(
        "usa-pacific-coast", 9, R.string.region_usa_pacific_coast, R.string.vehicle_intercity,
        listOf(
            Station(R.string.station_los_angeles, "Los Angeles", "Union Station", LOS_ANGELES),
            Station(R.string.station_anaheim, "Anaheim", "ARTIC", ANAHEIM),
            Station(R.string.station_san_juan_capistrano, "San Juan Capistrano", "California", CAPISTRANO),
            Station(R.string.station_oceanside, "Oceanside", "Transit Center", OCEANSIDE),
            Station(R.string.station_solana_beach, "Solana Beach", "California", SOLANA_BEACH),
            Station(R.string.station_san_diego, "San Diego", "Santa Fe Depot", SAN_DIEGO),
        ),
        Course.REGIONAL, "course_usa_pacific_coast", snowy = false, Themes.CALIFORNIA,
        guide = RouteGuides.USA_PACIFIC_COAST,
        purchasePreview = CoursePurchasePreview(
            R.drawable.california_purchase_preview,
            R.string.california_purchase_preview_caption,
        ),
        features = listOf(
            // Inland from Los Angeles through Orange County; the tracks meet the ocean at Dana Point.
            Feature(Feature.Kind.RIVER, 0, .04f, .11f),
            Feature(Feature.Kind.RIVER, 1, .02f, .07f),
            Feature(Feature.Kind.SEA, 2, .17f, .43f),
            Feature(Feature.Kind.SEA, 2, .52f, .69f),
            // The line crosses Batiquitos and San Elijo lagoons on either side of Encinitas.
            Feature(Feature.Kind.RIVER, 3, .42f, .49f),
            Feature(Feature.Kind.SEA, 3, .65f, .79f),
            Feature(Feature.Kind.RIVER, 3, .82f, .91f),
            // Del Mar bluffs, then the turn inland at Los Peñasquitos Lagoon toward Rose Canyon.
            Feature(Feature.Kind.SEA, 4, .07f, .17f),
            Feature(Feature.Kind.RIVER, 4, .18f, .22f),
        ),
        landmarks = listOf(
            LandmarkSpot.atStation(LandmarkKind.LA_UNION_STATION, 0, -92f),
            LandmarkSpot.atStation(LandmarkKind.ANAHEIM_ARTIC, 1, -82f),
            LandmarkSpot.along(LandmarkKind.SAN_CLEMENTE_PIER, 2, .255f),
            LandmarkSpot.atStation(LandmarkKind.SANTA_FE_DEPOT, 5, 92f),
        ),
    )

    val GRAND_CANYON = Course(
        "usa-grand-canyon", 10, R.string.region_usa_grand_canyon, R.string.vehicle_heritage_train,
        listOf(
            Station(R.string.station_williams_depot, "Williams", "Williams Depot",
                CityLook(rise = .45f, apt = 0f, mountains = .35f, near = false)),
            Station(R.string.station_grand_canyon_depot, "Grand Canyon", "Grand Canyon Depot",
                CityLook(rise = .32f, apt = 0f, near = false)),
        ),
        Course.REGIONAL, "course_usa_grand_canyon", snowy = true, Themes.GRAND_CANYON,
        guide = RouteGuides.USA_GRAND_CANYON,
        purchasePreview = CoursePurchasePreview(
            R.drawable.grand_canyon_south_rim,
            R.string.grand_canyon_purchase_preview_caption,
            postcardStation = R.string.station_grand_canyon_depot,
        ),
        features = listOf(
            Feature(Feature.Kind.PRAIRIE, 0, .15f, .70f),
            Feature(Feature.Kind.DRY_WASH, 0, 34.8f / 65f, 36f / 65f),
            Feature(Feature.Kind.LIMESTONE_CANYON, 0, 54f / 65f, 58f / 65f),
        ),
        landmarks = listOf(
            LandmarkSpot.atStation(LandmarkKind.WILLIAMS_DEPOT, 0, -85f),
            LandmarkSpot.atStation(LandmarkKind.GRAND_CANYON_DEPOT, 1, 85f),
        ),
    )

    val ALL = listOf(PACIFIC_COAST, GRAND_CANYON)
}
