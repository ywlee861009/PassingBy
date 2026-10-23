package com.kero.passingby.content

import com.kero.passingby.experience.train.R

/**
 * 코스 정방향의 실제 경유 순서와 대략적인 영업거리(km).
 * 역 이름은 코스와 동일한 도시 표기를 쓴다. 자료·거리 축의 범위는 docs/content/route-guides.md 참고.
 * 중간 지점은 정차역을 늘리지 않고 위치 안내에만 사용한다.
 */
object RouteGuides {
    val GYEONGBU = RouteGuide(
        stops = listOf(
            RoutePlace(R.string.station_seoul, 0.0),
            RoutePlace(R.string.station_daejeon, 166.0),
            RoutePlace(R.string.station_daegu, 293.0),
            RoutePlace(R.string.station_busan, 417.0),
        ),
        via = listOf(
            RoutePlace(R.string.place_gwangmyeong, 22.0),
            RoutePlace(R.string.place_cheonan_asan, 96.0),
            RoutePlace(R.string.place_osong, 141.0),
            RoutePlace(R.string.place_gimcheon_gumi, 235.0),
            RoutePlace(R.string.place_gyeongju, 343.0),
            RoutePlace(R.string.place_ulsan, 372.0),
        ),
    )

    val TOKAIDO = RouteGuide(
        stops = listOf(
            RoutePlace(R.string.station_tokyo, 0.0),
            RoutePlace(R.string.station_shizuoka, 180.2),
            RoutePlace(R.string.station_nagoya, 366.0),
            RoutePlace(R.string.station_kyoto, 513.6),
            RoutePlace(R.string.station_osaka, 552.6),
        ),
        via = listOf(
            RoutePlace(R.string.place_shinagawa, 6.8),
            RoutePlace(R.string.place_yokohama, 28.8),
            RoutePlace(R.string.place_odawara, 83.9),
            RoutePlace(R.string.place_atami, 104.6),
            RoutePlace(R.string.place_mishima, 120.7),
            RoutePlace(R.string.place_fuji, 146.2),
            RoutePlace(R.string.place_kakegawa, 229.3),
            RoutePlace(R.string.place_hamamatsu, 257.1),
            RoutePlace(R.string.place_toyohashi, 293.6),
            RoutePlace(R.string.place_mikawa_anjo, 336.3),
            RoutePlace(R.string.place_gifu_hashima, 396.3),
            RoutePlace(R.string.place_maibara, 445.9),
            RoutePlace(R.string.place_takatsuki, 535.0),
        ),
    )

    val JEOLLA = RouteGuide(
        stops = listOf(
            RoutePlace(R.string.station_seoul, 0.0),
            RoutePlace(R.string.station_jeonju, 237.0),
            RoutePlace(R.string.station_suncheon, 357.0),
            RoutePlace(R.string.station_yeosu, 392.0),
        ),
        via = listOf(
            RoutePlace(R.string.place_gwangmyeong, 18.0),
            RoutePlace(R.string.place_cheonan_asan, 92.0),
            RoutePlace(R.string.place_osong, 137.0),
            RoutePlace(R.string.place_gongju, 183.0),
            RoutePlace(R.string.place_iksan, 212.0),
            RoutePlace(R.string.place_namwon, 290.0),
            RoutePlace(R.string.place_gokseong, 308.0),
            RoutePlace(R.string.place_gurye, 325.0),
            RoutePlace(R.string.place_yeocheon, 383.0),
        ),
    )

    val GANGNEUNG = RouteGuide(
        stops = listOf(
            RoutePlace(R.string.station_seoul, 0.0),
            RoutePlace(R.string.station_yangpyeong, 62.0),
            RoutePlace(R.string.station_pyeongchang, 183.0),
            RoutePlace(R.string.station_gangneung, 223.0),
        ),
        via = listOf(
            RoutePlace(R.string.place_cheongnyangni, 18.0),
            RoutePlace(R.string.place_sangbong, 24.0),
            RoutePlace(R.string.place_deokso, 39.0),
            RoutePlace(R.string.place_wonju, 109.0),
            RoutePlace(R.string.place_hoengseong, 128.0),
            RoutePlace(R.string.place_dunnae, 150.0),
            RoutePlace(R.string.place_jinbu, 199.0),
        ),
    )

    val CHINA_NORTH = RouteGuide(
        stops = listOf(
            RoutePlace(R.string.station_beijing, 0.0),
            RoutePlace(R.string.station_jinan, 406.0),
            RoutePlace(R.string.station_nanjing, 1023.0),
            RoutePlace(R.string.station_shanghai, 1318.0),
        ),
        via = listOf(
            RoutePlace(R.string.place_langfang, 60.0),
            RoutePlace(R.string.place_tianjin, 122.0),
            RoutePlace(R.string.place_cangzhou, 210.0),
            RoutePlace(R.string.place_dezhou, 314.0),
            RoutePlace(R.string.place_taian, 465.0),
            RoutePlace(R.string.place_qufu, 535.0),
            RoutePlace(R.string.place_xuzhou, 692.0),
            RoutePlace(R.string.place_bengbu, 848.0),
            RoutePlace(R.string.place_chuzhou, 964.0),
            RoutePlace(R.string.place_zhenjiang, 1088.0),
            RoutePlace(R.string.place_changzhou, 1153.0),
            RoutePlace(R.string.place_wuxi, 1210.0),
            RoutePlace(R.string.place_suzhou, 1237.0),
            RoutePlace(R.string.place_kunshan, 1268.0),
        ),
    )

    // 귀광선의 원래 거리 축(구이양동 → 광저우남)을 코스 방향으로 뒤집었다.
    val CHINA_SOUTH = RouteGuide(
        stops = listOf(
            RoutePlace(R.string.station_guangzhou, 0.0),
            RoutePlace(R.string.station_guilin, 444.0),
            RoutePlace(R.string.station_guiyang, 857.0),
        ),
        via = listOf(
            RoutePlace(R.string.place_foshan, 33.0),
            RoutePlace(R.string.place_zhaoqing, 76.0),
            RoutePlace(R.string.place_guangning, 125.0),
            RoutePlace(R.string.place_huaiji, 171.0),
            RoutePlace(R.string.place_hezhou, 261.0),
            RoutePlace(R.string.place_zhongshan, 285.0),
            RoutePlace(R.string.place_gongcheng, 345.0),
            RoutePlace(R.string.place_yangshuo, 379.0),
            RoutePlace(R.string.place_sanjiang, 531.0),
            RoutePlace(R.string.place_congjiang, 583.0),
            RoutePlace(R.string.place_rongjiang, 640.0),
            RoutePlace(R.string.place_duyun, 741.0),
            RoutePlace(R.string.place_longli, 815.0),
        ),
    )

    // 포르투에서 북부선 거리 축에 도루선의 캄파냥 출발 거리를 이어 붙인다.
    val PORTUGAL = RouteGuide(
        stops = listOf(
            RoutePlace(R.string.station_lisbon, 0.0),
            RoutePlace(R.string.station_coimbra, 217.0),
            RoutePlace(R.string.station_porto, 336.0),
            RoutePlace(R.string.station_regua, 438.0),
        ),
        via = listOf(
            RoutePlace(R.string.place_santarem, 74.0),
            RoutePlace(R.string.place_entroncamento, 106.0),
            RoutePlace(R.string.place_pombal, 167.0),
            RoutePlace(R.string.place_aveiro, 273.0),
            RoutePlace(R.string.place_espinho, 318.0),
            RoutePlace(R.string.place_penafiel, 369.0),
            RoutePlace(R.string.place_marco_canaveses, 389.0),
            RoutePlace(R.string.place_aregos, 408.0),
        ),
    )

    val FRANCE = RouteGuide(
        stops = listOf(
            RoutePlace(R.string.station_paris, 0.0),
            RoutePlace(R.string.station_lyon, 427.0),
            RoutePlace(R.string.station_avignon, 676.0),
            RoutePlace(R.string.station_marseille, 762.0),
        ),
        via = listOf(
            RoutePlace(R.string.place_le_creusot, 305.0),
            RoutePlace(R.string.place_macon, 365.0),
            RoutePlace(R.string.place_valence, 541.0),
            RoutePlace(R.string.place_aix_provence, 744.0),
        ),
    )

    val ALL = listOf(GYEONGBU, TOKAIDO, JEOLLA, GANGNEUNG, CHINA_NORTH, CHINA_SOUTH, PORTUGAL, FRANCE)
}
