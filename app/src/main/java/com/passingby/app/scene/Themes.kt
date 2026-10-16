package com.passingby.app.scene

import android.graphics.Color

/** 코스별 테마 모음. 코스 id와 같은 id를 쓴다. */
object Themes {
    val KOREA = Theme(
        "korea",
        Scenery.layers,
        CityScenery.far, CityScenery.mid, CityScenery.near,
        Scenery.cityStreetTrees,
        // 시골 집: 흰·베이지 벽에 파랑·주황·초록 지붕.
        HouseStyle(
            RoofStyle.GABLE,
            intArrayOf(Color.rgb(236, 232, 222), Color.rgb(226, 214, 196), Color.rgb(214, 210, 202)),
            intArrayOf(Color.rgb(64, 104, 150), Color.rgb(190, 96, 64), Color.rgb(82, 128, 98), Color.rgb(120, 120, 128)),
        ),
        BarrierStyle.PANEL,
        SignStyle(Color.rgb(246, 244, 238), Color.rgb(28, 42, 72), Color.rgb(40, 74, 140)),
    )

    private val ALL = listOf(KOREA)

    fun byId(id: String): Theme = ALL.firstOrNull { it.id == id } ?: KOREA
}
