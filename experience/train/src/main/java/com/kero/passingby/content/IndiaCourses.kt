package com.kero.passingby.content

import com.kero.passingby.experience.CoursePurchasePreview
import com.kero.passingby.experience.train.R
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.Hill
import com.kero.passingby.scene.Themes

/** Indian rail journeys through the Himalayan foothills and across the Pamban Strait. */
object IndiaCourses {
    val DARJEELING = Course(
        "india-darjeeling", 11, R.string.region_india_darjeeling, R.string.vehicle_mountain_train,
        listOf(
            Station(R.string.station_kurseong, "কার্শিয়াং", "Kurseong", CityLook(
                rise = .55f, apt = 0f, hills = listOf(Hill(-95f, 125f, 40f)), hillside = true, near = false,
            )),
            Station(R.string.station_ghum, "ঘুম", "Ghum", CityLook(
                rise = .42f, apt = 0f, hills = listOf(Hill(100f, 120f, 38f)), hillside = true, near = false,
            )),
            Station(R.string.station_darjeeling, "দার্জিলিং", "Darjeeling", CityLook(
                rise = .67f, apt = 0f, hills = listOf(Hill(-155f, 195f, 62f), Hill(110f, 125f, 40f)),
                hillside = true, near = false,
            )),
        ),
        110.0, "course_india_darjeeling", snowy = false, Themes.DARJEELING,
        guide = RouteGuides.INDIA_DARJEELING,
        purchasePreview = CoursePurchasePreview(
            R.drawable.darjeeling_purchase_preview,
            R.string.darjeeling_purchase_preview_caption,
        ),
        features = listOf(
            Feature(Feature.Kind.FIELD, 0, .07f, .34f, FieldStyle.TEA),
            Feature(Feature.Kind.FIELD, 0, .43f, .61f, FieldStyle.TEA),
            // The loop is about 1.17 km after Ghum on the 6.03 km descent to Darjeeling.
            Feature(Feature.Kind.BATASIA_LOOP, 1, .06f, .33f),
        ),
    )

    val PAMBAN = Course(
        "india-pamban", 12, R.string.region_india_pamban, R.string.vehicle_intercity,
        listOf(
            Station(R.string.station_mandapam, "மண்டபம்", "Mandapam",
                CityLook(rise = .42f, apt = 0f, near = false)),
            Station(R.string.station_rameswaram, "இராமேசுவரம்", "Rameswaram",
                CityLook(rise = .54f, apt = 0f, near = false)),
        ),
        Course.REGIONAL, "course_india_pamban", snowy = false, Themes.PAMBAN,
        guide = RouteGuides.INDIA_PAMBAN,
        purchasePreview = CoursePurchasePreview(
            R.drawable.pamban_purchase_preview,
            R.string.pamban_purchase_preview_caption,
        ),
        features = listOf(
            Feature(
                Feature.Kind.PAMBAN_CROSSING, 0,
                (RouteGuides.PAMBAN_BRIDGE_START_KM / RouteGuides.PAMBAN_ROUTE_KM).toFloat(),
                (RouteGuides.PAMBAN_BRIDGE_END_KM / RouteGuides.PAMBAN_ROUTE_KM).toFloat(),
            ),
        ),
    )

    val ALL = listOf(DARJEELING, PAMBAN)
}
