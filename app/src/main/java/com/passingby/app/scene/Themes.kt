package com.passingby.app.scene

import android.graphics.Color

/**
 * 코스별 테마 모음. 코스 id와 같은 id를 쓴다.
 * 지형 레이어 값의 순서는 [TerrainLayer] 생성자와 같다: 속도, 바닥, 높이 폭, 주파수, 능선 정도, 시드, 색 키, 안개, 아래 안개.
 */
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

    /** 일본: 높은 산줄기, 소나무·대나무, 짙은 기와 모임지붕, 낮고 촘촘한 시가지. */
    val JAPAN = Theme(
        "japan",
        arrayOf(
            TerrainLayer(0.035, .60f, .30f, 1.0 / 280, 0.85, 12, Palette.FAR, .5f, .4f),
            TerrainLayer(0.075, .67f, .18f, 1.0 / 190, 0.5, 24, Palette.FAR, .22f, .35f),
            TerrainLayer(
                0.2, .77f, .09f, 1.0 / 240, 0.0, 38, Palette.MID, .08f, .2f, spriteHaze = .35f,
                objects = ObjectRule(
                    64.0, 5.4, 17f, 30f, town = true, sink = 2f,
                    kinds = arrayOf(SpriteKind.PINE, SpriteKind.PINE, SpriteKind.ROUND, SpriteKind.BAMBOO),
                ),
            ),
            TerrainLayer(
                0.55, .875f, .035f, 1.0 / 320, 0.0, 52, Palette.NEAR, 0f, 0f,
                objects = ObjectRule(
                    105.0, 3.0, 50f, 92f, town = false, sink = 6f,
                    kinds = arrayOf(SpriteKind.PINE, SpriteKind.ROUND, SpriteKind.BAMBOO, SpriteKind.BUSH, SpriteKind.BUSH, SpriteKind.REED),
                ),
            ),
        ),
        CityLayer(0.075, .70f, 28.0, 3, 12f, 26f, 22f, 90f, 62, WindowStyle.DOTS, 4.5f, 3.6f),
        CityLayer(0.2, .80f, 60.0, 3, 20f, 44f, 18f, 44f, 72, WindowStyle.GRID, 3.4f, 3.6f, apt = .25f, aptH = 30f, aptVar = 14f),
        CityLayer(0.55, .885f, 56.0, 2, 18f, 36f, 10f, 24f, 82, WindowStyle.SHOP, 4.6f, 4.2f),
        ObjectRule(105.0, 1.2, 46f, 80f, town = false, sink = 6f, kinds = arrayOf(SpriteKind.ROUND, SpriteKind.BUSH, SpriteKind.PINE)),
        HouseStyle(
            RoofStyle.HIP,
            intArrayOf(Color.rgb(236, 230, 218), Color.rgb(214, 210, 202), Color.rgb(170, 136, 100)),
            intArrayOf(Color.rgb(70, 74, 82), Color.rgb(88, 98, 112), Color.rgb(52, 54, 58)),
        ),
        BarrierStyle.PANEL,
        SignStyle(Color.rgb(250, 250, 248), Color.rgb(30, 30, 32), Color.rgb(46, 125, 74)),
    )

    /** 중국 북부: 끝없는 평야, 미루나무 줄, 회색 벽돌집, 아주 높은 아파트 단지, 고가 선로 난간. */
    val CHINA_NORTH = Theme(
        "china-north",
        arrayOf(
            TerrainLayer(0.035, .62f, .07f, 1.0 / 400, 0.3, 13, Palette.FAR, .6f, .5f),
            TerrainLayer(0.075, .70f, .04f, 1.0 / 260, 0.1, 25, Palette.FAR, .35f, .4f),
            TerrainLayer(
                0.2, .78f, .03f, 1.0 / 300, 0.0, 39, Palette.MID, .12f, .25f, spriteHaze = .4f,
                objects = ObjectRule(
                    52.0, 6.0, 20f, 34f, town = true, sink = 2f,
                    kinds = arrayOf(SpriteKind.POPLAR, SpriteKind.POPLAR, SpriteKind.POPLAR, SpriteKind.ROUND),
                ),
            ),
            TerrainLayer(
                0.55, .88f, .02f, 1.0 / 360, 0.0, 53, Palette.NEAR, 0f, 0f,
                objects = ObjectRule(
                    105.0, 2.6, 56f, 100f, town = false, sink = 6f,
                    kinds = arrayOf(SpriteKind.POPLAR, SpriteKind.POPLAR, SpriteKind.ROUND, SpriteKind.BUSH, SpriteKind.REED),
                ),
            ),
        ),
        CityLayer(0.075, .70f, 30.0, 3, 14f, 30f, 40f, 190f, 63, WindowStyle.DOTS, 4.5f, 3.6f),
        CityLayer(0.2, .80f, 70.0, 2, 26f, 50f, 30f, 80f, 73, WindowStyle.GRID, 3.2f, 3.6f, apt = .8f, aptH = 70f, aptVar = 30f),
        CityLayer(0.55, .885f, 64.0, 2, 22f, 44f, 10f, 26f, 83, WindowStyle.SHOP, 4.6f, 4.4f),
        ObjectRule(105.0, 1.6, 56f, 96f, town = false, sink = 6f, kinds = arrayOf(SpriteKind.POPLAR, SpriteKind.ROUND)),
        HouseStyle(
            RoofStyle.GABLE,
            intArrayOf(Color.rgb(168, 162, 154), Color.rgb(214, 208, 198), Color.rgb(158, 104, 84)),
            intArrayOf(Color.rgb(98, 100, 106), Color.rgb(120, 116, 110), Color.rgb(84, 86, 92)),
        ),
        BarrierStyle.PARAPET,
        SignStyle(Color.rgb(36, 84, 160), Color.rgb(250, 250, 250), Color.rgb(250, 250, 250)),
    )

    /** 중국 남부: 안개 낀 카르스트 봉우리, 대나무, 흰 벽에 검은 기와집. */
    val CHINA_SOUTH = Theme(
        "china-south",
        arrayOf(
            TerrainLayer(0.035, .62f, .44f, 1.0 / 72, 0.0, 14, Palette.FAR, .62f, .5f, shape = TerrainShape.KARST),
            // 앞쪽 봉우리는 숲에 덮인 초록, 뒤쪽은 안개 속으로 흐려진다.
            TerrainLayer(0.075, .70f, .34f, 1.0 / 56, 0.0, 26, Palette.MID, .3f, .38f, shape = TerrainShape.KARST),
            TerrainLayer(
                0.2, .78f, .05f, 1.0 / 220, 0.0, 40, Palette.MID, .1f, .3f, spriteHaze = .35f,
                objects = ObjectRule(
                    60.0, 5.0, 18f, 32f, town = true, sink = 2f,
                    kinds = arrayOf(SpriteKind.BAMBOO, SpriteKind.BAMBOO, SpriteKind.ROUND, SpriteKind.BUSH),
                ),
            ),
            TerrainLayer(
                0.55, .88f, .03f, 1.0 / 300, 0.0, 54, Palette.NEAR, 0f, 0f,
                objects = ObjectRule(
                    100.0, 3.2, 50f, 92f, town = false, sink = 6f,
                    kinds = arrayOf(SpriteKind.BAMBOO, SpriteKind.BAMBOO, SpriteKind.ROUND, SpriteKind.BUSH, SpriteKind.REED),
                ),
            ),
        ),
        CityLayer(0.075, .70f, 34.0, 2, 14f, 28f, 30f, 110f, 64, WindowStyle.DOTS, 4.5f, 3.6f),
        CityLayer(0.2, .80f, 70.0, 2, 26f, 50f, 30f, 70f, 74, WindowStyle.GRID, 3.2f, 3.6f, apt = .7f, aptH = 56f, aptVar = 26f),
        CityLayer(0.55, .885f, 64.0, 2, 22f, 44f, 10f, 26f, 84, WindowStyle.SHOP, 4.6f, 4.4f),
        ObjectRule(105.0, 1.6, 50f, 90f, town = false, sink = 6f, kinds = arrayOf(SpriteKind.ROUND, SpriteKind.BAMBOO)),
        HouseStyle(
            RoofStyle.HIP,
            intArrayOf(Color.rgb(238, 236, 230), Color.rgb(226, 222, 214)),
            intArrayOf(Color.rgb(58, 60, 66), Color.rgb(74, 76, 82)),
        ),
        BarrierStyle.NONE,
        SignStyle(Color.rgb(36, 84, 160), Color.rgb(250, 250, 250), Color.rgb(250, 250, 250)),
    )

    private val ALL = listOf(KOREA, JAPAN, CHINA_NORTH, CHINA_SOUTH)

    fun byId(id: String): Theme = ALL.firstOrNull { it.id == id } ?: KOREA
}
