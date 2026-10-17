package com.kero.passingby.scene

import android.graphics.Color

/**
 * 테마 모음. 코스가 자기 테마를 고른다([com.kero.passingby.content.Course.theme]).
 * 같은 나라의 노선끼리는 마을·도시·이름판을 같이 쓰고 지형만 바꾼다.
 * 지형 레이어 값의 순서는 [TerrainLayer] 생성자와 같다: 속도, 바닥, 높이 폭, 주파수, 능선 정도, 시드, 색 키, 안개, 아래 안개.
 * 레이어 바닥(base)은 도시 건물 바닥선과 짝이 맞춰져 있으므로 바꾸지 않고 높이 폭·능선만 바꾼다.
 */
object Themes {
    /** 경부선: 낮은 산, 논, 아파트 단지. */
    val KOREA = korea("korea", Scenery.layers, 0)

    /** 전라선: 지리산 자락의 조금 더 높은 원경. */
    val KOREA_JEOLLA = korea(
        "korea-jeolla",
        arrayOf(
            TerrainLayer(0.035, .60f, .32f, 1.0 / 280, 0.85, 17, Palette.FAR, .5f, .4f),
            TerrainLayer(0.075, .67f, .19f, 1.0 / 200, 0.5, 29, Palette.FAR, .22f, .35f),
            Scenery.layers[Scenery.MID].reseeded(100),
            Scenery.layers[Scenery.NEAR].reseeded(100),
        ),
        100,
    )

    /** 강릉선: 태백산맥의 높고 뾰족한 능선, 소나무가 많은 중경. */
    val KOREA_GANGNEUNG = korea(
        "korea-gangneung",
        arrayOf(
            TerrainLayer(0.035, .60f, .38f, 1.0 / 240, 0.9, 18, Palette.FAR, .5f, .4f),
            TerrainLayer(0.075, .67f, .25f, 1.0 / 170, 0.6, 30, Palette.FAR, .22f, .35f),
            TerrainLayer(
                0.2, .77f, .12f, 1.0 / 220, 0.2, 43, Palette.MID, .08f, .2f, spriteHaze = .35f,
                objects = ObjectRule(
                    64.0, 5.6, 17f, 30f, town = true, sink = 2f,
                    kinds = arrayOf(SpriteKind.PINE, SpriteKind.PINE, SpriteKind.PINE, SpriteKind.ROUND),
                ),
            ),
            TerrainLayer(
                0.55, .875f, .035f, 1.0 / 340, 0.0, 57, Palette.NEAR, 0f, 0f,
                objects = ObjectRule(
                    105.0, 3.4, 50f, 92f, town = false, sink = 6f,
                    kinds = arrayOf(SpriteKind.PINE, SpriteKind.PINE, SpriteKind.ROUND, SpriteKind.BUSH, SpriteKind.BUSH, SpriteKind.REED),
                ),
            ),
        ),
        200,
    )

    /**
     * 한국 노선 공통: 아파트 도시, 시골 집, 방음벽, 흰 이름판.
     * 노선마다 [seedShift]를 달리해, 같은 시각에 같은 자리의 건물·나무가 노선끼리 똑같이 나오지 않게 한다.
     */
    private fun korea(id: String, layers: Array<TerrainLayer>, seedShift: Int) = Theme(
        id,
        layers,
        CityScenery.far.reseeded(seedShift), CityScenery.mid.reseeded(seedShift), CityScenery.near.reseeded(seedShift),
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

    private val PASTEL = intArrayOf(
        Color.rgb(232, 200, 112), Color.rgb(224, 168, 160), Color.rgb(156, 184, 208),
        Color.rgb(240, 236, 228), Color.rgb(200, 120, 88), Color.rgb(176, 196, 160),
    )

    /** 포르투갈: 완만한 산, 해송·유칼립투스, 흰 벽에 주황 기와, 파스텔 시가지, 파란 타일 이름판. */
    val PORTUGAL = Theme(
        "portugal",
        arrayOf(
            TerrainLayer(0.035, .61f, .2f, 1.0 / 320, 0.6, 15, Palette.FAR, .48f, .4f),
            TerrainLayer(0.075, .68f, .14f, 1.0 / 220, 0.35, 27, Palette.FAR, .22f, .35f),
            TerrainLayer(
                0.2, .77f, .09f, 1.0 / 230, 0.0, 41, Palette.MID, .08f, .2f, spriteHaze = .35f,
                objects = ObjectRule(
                    64.0, 5.0, 18f, 32f, town = true, sink = 2f,
                    kinds = arrayOf(SpriteKind.PINE, SpriteKind.POPLAR, SpriteKind.ROUND, SpriteKind.ROUND),
                ),
            ),
            TerrainLayer(
                0.55, .875f, .035f, 1.0 / 330, 0.0, 55, Palette.NEAR, 0f, 0f,
                objects = ObjectRule(
                    105.0, 3.0, 50f, 96f, town = false, sink = 6f,
                    kinds = arrayOf(SpriteKind.ROUND, SpriteKind.PINE, SpriteKind.POPLAR, SpriteKind.POPLAR, SpriteKind.BUSH, SpriteKind.REED),
                ),
            ),
        ),
        CityLayer(0.075, .70f, 26.0, 3, 12f, 24f, 18f, 56f, 65, WindowStyle.DOTS, 4.5f, 3.6f, facades = PASTEL, cap = RoofCap.TILE),
        CityLayer(0.2, .80f, 52.0, 3, 18f, 34f, 16f, 34f, 75, WindowStyle.GRID, 3.6f, 3.8f, apt = .15f, aptH = 26f, aptVar = 10f, facades = PASTEL, cap = RoofCap.TILE),
        CityLayer(0.55, .885f, 50.0, 2, 18f, 32f, 10f, 22f, 85, WindowStyle.SHOP, 4.6f, 4.2f, facades = PASTEL),
        ObjectRule(105.0, 1.4, 50f, 90f, town = false, sink = 6f, kinds = arrayOf(SpriteKind.ROUND, SpriteKind.POPLAR)),
        HouseStyle(
            RoofStyle.TILE,
            intArrayOf(Color.rgb(244, 240, 232), Color.rgb(236, 224, 196), Color.rgb(240, 236, 228)),
            intArrayOf(Color.rgb(200, 100, 60), Color.rgb(184, 90, 54), Color.rgb(206, 116, 72)),
        ),
        BarrierStyle.NONE,
        SignStyle(Color.rgb(40, 86, 160), Color.rgb(250, 250, 250), Color.rgb(236, 236, 236)),
    )

    /** 프랑스: 낮은 구릉, 플라타너스·포플러·사이프러스, 석조 마을, 망사르드 지붕의 파리 시가지, 짙은 남색 이름판. */
    val FRANCE = Theme(
        "france",
        arrayOf(
            TerrainLayer(0.035, .61f, .16f, 1.0 / 340, 0.6, 16, Palette.FAR, .5f, .4f),
            TerrainLayer(0.075, .69f, .1f, 1.0 / 240, 0.3, 28, Palette.FAR, .24f, .35f),
            TerrainLayer(
                0.2, .78f, .06f, 1.0 / 260, 0.0, 42, Palette.MID, .08f, .2f, spriteHaze = .35f,
                objects = ObjectRule(
                    60.0, 5.0, 18f, 32f, town = true, sink = 2f,
                    kinds = arrayOf(SpriteKind.ROUND, SpriteKind.POPLAR, SpriteKind.CYPRESS, SpriteKind.ROUND),
                ),
            ),
            TerrainLayer(
                0.55, .88f, .03f, 1.0 / 330, 0.0, 56, Palette.NEAR, 0f, 0f,
                objects = ObjectRule(
                    105.0, 3.0, 52f, 96f, town = false, sink = 6f,
                    kinds = arrayOf(SpriteKind.ROUND, SpriteKind.POPLAR, SpriteKind.CYPRESS, SpriteKind.BUSH, SpriteKind.BUSH, SpriteKind.REED),
                ),
            ),
        ),
        CityLayer(0.075, .70f, 30.0, 2, 12f, 26f, 20f, 70f, 66, WindowStyle.DOTS, 4.5f, 3.6f, cap = RoofCap.MANSARD),
        CityLayer(
            0.2, .80f, 54.0, 3, 22f, 40f, 30f, 38f, 76, WindowStyle.GRID, 3.6f, 3.6f,
            facades = intArrayOf(Color.rgb(222, 206, 176), Color.rgb(232, 222, 200), Color.rgb(210, 196, 168)),
            cap = RoofCap.MANSARD,
        ),
        CityLayer(0.55, .885f, 56.0, 2, 20f, 36f, 10f, 22f, 86, WindowStyle.SHOP, 4.6f, 4.2f),
        ObjectRule(105.0, 1.8, 52f, 90f, town = false, sink = 6f, kinds = arrayOf(SpriteKind.ROUND, SpriteKind.ROUND, SpriteKind.POPLAR)),
        HouseStyle(
            RoofStyle.GABLE,
            intArrayOf(Color.rgb(218, 200, 168), Color.rgb(230, 220, 198), Color.rgb(200, 184, 156)),
            intArrayOf(Color.rgb(90, 96, 104), Color.rgb(184, 106, 72), Color.rgb(176, 96, 64)),
        ),
        BarrierStyle.NONE,
        SignStyle(Color.rgb(30, 54, 104), Color.rgb(250, 250, 250), Color.rgb(250, 250, 250)),
    )

    private fun TerrainLayer.reseeded(shift: Int) =
        TerrainLayer(speed, base, amp, freq, ridge, seed + shift, colorKey, haze, mist, spriteHaze, objects, shape)

    private fun CityLayer.reseeded(shift: Int) = CityLayer(
        speed, base, chunkUnits, perChunk, minW, maxW, minH, maxH, seed + shift, style, floorU, colU,
        apt, aptH, aptVar, facades, cap,
    )
}
