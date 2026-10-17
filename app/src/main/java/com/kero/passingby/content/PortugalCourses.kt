package com.kero.passingby.content

import android.graphics.Color
import com.kero.passingby.R
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.Hill
import com.kero.passingby.scene.LandmarkKind
import com.kero.passingby.scene.RoofCap
import com.kero.passingby.scene.Span
import com.kero.passingby.scene.Themes

/** 포르투갈 노선. */
object PortugalCourses {
    /** 흰 벽이 대부분이고 크림·연노랑이 섞인 시가지(리스본·코임브라·레구아). */
    private val WHITE_TOWN = intArrayOf(
        Color.rgb(244, 240, 232), Color.rgb(236, 228, 210), Color.rgb(240, 236, 228), Color.rgb(232, 214, 170),
    )

    /** 포르투 강변: 황토·주황·연노랑·흰색·옅은 파랑의 좁은 집들. */
    private val PORTO_FACADES = intArrayOf(
        Color.rgb(214, 170, 92), Color.rgb(192, 112, 80), Color.rgb(238, 234, 226), Color.rgb(150, 180, 200),
        Color.rgb(222, 164, 150), Color.rgb(232, 214, 176),
    )

    /** 리스본: 테주강가에서 알파마 언덕으로 흰 벽·주황 지붕이 오르고, 꼭대기에 성. */
    private val LISBOA = CityLook(
        rise = .8f, apt = 0f, cap = RoofCap.TILE, facades = WHITE_TOWN,
        hills = listOf(Hill(70f, 210f, 74f)), river = Span(-235f, 70f), hillside = true, near = false,
    )

    /** 코임브라: 몬데구강에서 언덕 꼭대기 대학까지 흰 집이 쌓인다. */
    private val COIMBRA = CityLook(
        rise = .75f, apt = 0f, cap = RoofCap.TILE, facades = WHITE_TOWN,
        hills = listOf(Hill(40f, 170f, 88f)), river = Span(-185f, 55f), hillside = true, near = false,
    )

    /** 포르투: 도루강 양쪽 가파른 언덕에 색색의 좁은 집이 겹겹이 쌓이고, 두 언덕을 다리가 잇는다. */
    private val PORTO = CityLook(
        rise = .85f, apt = 0f, cap = RoofCap.TILE, facades = PORTO_FACADES,
        hills = listOf(Hill(-190f, 150f, 80f), Hill(195f, 150f, 72f)), river = Span(0f, 50f),
        hillside = true, near = false,
    )

    /** 레구아: 강가의 작은 흰 마을, 양쪽은 포도밭 계단이 덮인 가파른 언덕. */
    private val REGUA = CityLook(
        rise = .5f, apt = 0f, cap = RoofCap.TILE, facades = WHITE_TOWN,
        hills = listOf(Hill(-215f, 170f, 100f), Hill(215f, 170f, 96f)), river = Span(0f, 60f),
        hillside = true, near = false, terraces = true, townTop = 30f,
    )

    /** 북부선과 도루선(리스본 → 포르투 → 레구아). */
    val NORTE_DOURO = Course(
        "portugal", 7, R.string.region_portugal, R.string.vehicle_express,
        listOf(
            Station(R.string.station_lisbon, "Lisboa", "Santa Apolónia", LISBOA),
            Station(R.string.station_coimbra, "Coimbra", "Coimbra-B", COIMBRA),
            Station(R.string.station_porto, "Porto", "Campanhã", PORTO),
            Station(R.string.station_regua, "Régua", "Peso da Régua", REGUA),
        ),
        Course.REGIONAL, "course_portugal", snowy = false, Themes.PORTUGAL,
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
        landmarks = listOf(
            // 테주강 하구 건너편으로 바스쿠 다 가마 다리. 시가지 건물에 가리지 않게 도시를 벗어난 뒤에 둔다.
            LandmarkSpot.along(LandmarkKind.CABLE_BRIDGE, 0, .18f),
            // 리스본 알파마 언덕 꼭대기의 성, 코임브라 언덕 꼭대기의 대학 종탑.
            LandmarkSpot.atStation(LandmarkKind.CASTLE_WALLS, 0, 70f),
            LandmarkSpot.atStation(LandmarkKind.UNIVERSITY_TOWER, 1, 40f),
            // 포르투에 서면 도루강 골짜기를 잇는 동 루이스 1세 다리.
            LandmarkSpot.atStation(LandmarkKind.ARCH_BRIDGE, 2, 0f),
        ),
    )

    val ALL = listOf(NORTE_DOURO)
}
