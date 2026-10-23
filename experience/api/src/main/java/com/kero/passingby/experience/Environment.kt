package com.kero.passingby.experience

import com.kero.passingby.experience.api.R
import androidx.annotation.StringRes

/** 저장 ID는 UI 순서와 독립적이다. 이전 기본 풍경(default)은 여름으로 복원한다. */
enum class Season(val id: String, @param:StringRes val label: Int) {
    SPRING("spring", R.string.season_spring), SUMMER("summer", R.string.season_summer),
    AUTUMN("autumn", R.string.season_autumn), WINTER("winter", R.string.season_winter);

    companion object {
        fun fromId(id: String?): Season = entries.firstOrNull { it.id == id } ?: SUMMER
    }
}

/** 설정의 선택과 실제 그리는 계절을 구분한다. 무작위는 여행 시드로 정해 복원 시에도 유지한다. */
enum class SeasonChoice(val season: Season?) {
    RANDOM(null), SPRING(Season.SPRING), SUMMER(Season.SUMMER), AUTUMN(Season.AUTUMN), WINTER(Season.WINTER);

    val id: String get() = season?.id ?: "random"
    val label: Int get() = season?.label ?: R.string.random

    fun resolve(seed: Int): Season = season ?: Season.entries[
        (seed xor (seed ushr 16)).ushr(1) % Season.entries.size
    ]

    companion object {
        fun fromId(id: String?): SeasonChoice = entries.firstOrNull { it.id == id } ?: SUMMER
        fun fromSeason(season: Season): SeasonChoice = entries.first { it.season == season }
    }
}

enum class TimeOfDay(@param:StringRes val label: Int) {
    DAWN(R.string.time_dawn), DAY(R.string.time_day), DUSK(R.string.time_dusk), NIGHT(R.string.time_night);

    companion object {
        /** 이전 새벽/노을 설정을 현재 낮/밤 선택으로 옮긴다. */
        fun fromSetting(name: String?): TimeOfDay? = when (name) {
            "DAY", "DAWN" -> DAY
            "NIGHT", "DUSK" -> NIGHT
            else -> null
        }
    }
}

enum class Weather(@param:StringRes val label: Int, val rain: Float, val snow: Float) {
    CLEAR(R.string.weather_clear, 0f, 0f),
    DRIZZLE(R.string.weather_drizzle, .4f, 0f),
    SHOWER(R.string.weather_shower, 1f, 0f),
    LIGHT_SNOW(R.string.weather_light_snow, 0f, .35f),
    HEAVY_SNOW(R.string.weather_heavy_snow, 0f, 1f),
}

enum class RenderMode(val label: String) {
    /** 매 프레임 지형 곡선을 다시 그린다. 메모리 최소, 프레임 비용 최대. */
    DIRECT("직접"),
    /** 지형을 조각 비트맵으로 한 번만 굽고 옮겨 그린다. 권장 방식. */
    BAKED("조각"),
    /** 원래 명세의 레이어 이미지 반복을 흉내 낸 비교용. 메모리 최대. */
    TILES("타일"),
}

enum class SceneKind(val label: String) { COUNTRY("시골"), CITY("도시") }
