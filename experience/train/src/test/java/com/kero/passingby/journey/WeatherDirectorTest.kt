package com.kero.passingby.journey

import com.kero.passingby.experience.Season
import org.junit.Assert.*
import org.junit.Test

class WeatherDirectorTest {
    @Test fun snowOnlyOccursInWinterOnSnowyCoursesAndNeverMixesWithRain() {
        var winterSnow = false
        for (season in Season.entries) for (snowy in listOf(false, true)) {
            for (seed in -50..150) {
                val weather = WeatherDirector(seed, snowy, season)
                for (time in 0..1800 step 60) {
                    val rain = weather.rainAt(time.toDouble())
                    val snow = weather.snowAt(time.toDouble())
                    assertTrue(rain in 0f..1f && snow in 0f..1f)
                    if (season != Season.WINTER || !snowy) assertEquals(0f, snow, 0f)
                    if (snow > 0f) { winterSnow = true; assertEquals(0f, rain, 0f) }
                }
            }
        }
        assertTrue(winterSnow)
    }

    @Test fun restoredSeedAndSeasonReproduceTheSameSmoothWeather() {
        for (season in Season.entries) {
            val before = WeatherDirector(41, true, season)
            val restored = WeatherDirector(41, true, season)
            for (time in 0..3600 step 17) {
                val t = time.toDouble()
                assertEquals(before.rainAt(t), restored.rainAt(t), 0f)
                assertEquals(before.snowAt(t), restored.snowAt(t), 0f)
                assertTrue(kotlin.math.abs(before.rainAt(t + .05) - before.rainAt(t)) < .001f)
                assertTrue(kotlin.math.abs(before.snowAt(t + .05) - before.snowAt(t)) < .001f)
            }
        }
    }

    @Test fun summerHasMoreRainAndAutumnHasMoreClearSessions() {
        fun wetSessions(season: Season): Int = (0..1000).count { seed ->
            val weather = WeatherDirector(seed, false, season)
            (0..3600 step 120).any { weather.rainAt(it.toDouble()) > .4f }
        }
        assertTrue(wetSessions(Season.SUMMER) > wetSessions(Season.SPRING))
        assertTrue(wetSessions(Season.SPRING) > wetSessions(Season.AUTUMN))
    }
}
