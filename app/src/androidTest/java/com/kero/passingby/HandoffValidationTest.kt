package com.kero.passingby

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.experience.Season
import com.kero.passingby.experience.SeasonChoice
import com.kero.passingby.experience.TimeOfDay
import com.kero.passingby.session.TripStore
import com.kero.passingby.ui.journey.JourneyViewModel
import org.junit.Assert.*
import org.junit.Test

/** 에뮬레이터에서도 재실행 가능한 회귀·캡처. 실기기 청취·배터리·Play 결제 시험을 대신하지 않는다. */
class HandoffValidationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun seasonStorageDefaultsAndSurvivesNewStore() {
        val prefs = context.getSharedPreferences("trip", 0)
        val saved = prefs.getString("season", null)
        val savedTime = prefs.getString("time_of_day", null)
        try {
            prefs.edit().remove("season").commit()
            assertEquals(SeasonChoice.SUMMER, SeasonChoice.fromId(TripStore(context).seasonChoiceId))
            for (choice in SeasonChoice.entries) {
                TripStore(context).seasonChoiceId = choice.id
                assertEquals(choice, SeasonChoice.fromId(TripStore(context).seasonChoiceId))
            }
            prefs.edit().putString("season", "default").commit()
            assertEquals(SeasonChoice.SUMMER, SeasonChoice.fromId(TripStore(context).seasonChoiceId))
            prefs.edit().putString("time_of_day", "DAWN").commit()
            assertEquals(TimeOfDay.DAY, TimeOfDay.fromSetting(TripStore(context).timeOfDayId))
            prefs.edit().putString("time_of_day", "DUSK").commit()
            assertEquals(TimeOfDay.NIGHT, TimeOfDay.fromSetting(TripStore(context).timeOfDayId))
            prefs.edit().putString("season", "unknown").commit()
            assertEquals(SeasonChoice.SUMMER, SeasonChoice.fromId(TripStore(context).seasonChoiceId))
        } finally {
            prefs.edit().putString("season", saved).putString("time_of_day", savedTime).commit()
        }
    }

    @Test fun randomSeasonRestoresForTheActiveJourneyAndResolvesAgainOnDeparture() {
        val prefs = context.getSharedPreferences("trip", 0)
        val saved = prefs.all
        try {
            prefs.edit().clear().commit()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val app = context.applicationContext as PassingByApp
                var graph = AppGraph(app)
                var factory = graph.journeyViewModelFactory
                var models = ViewModelStore()
                try {
                    var vm = ViewModelProvider(models, factory)[JourneyViewModel::class.java]
                    vm.selectSeasonChoice(SeasonChoice.RANDOM)
                    assertEquals(SeasonChoice.RANDOM, SeasonChoice.fromId(TripStore(context).seasonChoiceId))
                    vm.depart()
                    val active = TripStore(context).loadActive()!!
                    val first = vm.season
                    assertEquals(SeasonChoice.RANDOM.resolve(active.weatherSeed), first)
                    models.clear()
                    graph.closeSession()
                    graph = AppGraph(app)
                    factory = graph.journeyViewModelFactory
                    models = ViewModelStore()
                    vm = ViewModelProvider(models, factory)[JourneyViewModel::class.java]
                    assertEquals(SeasonChoice.RANDOM, vm.seasonChoice)
                    assertEquals(first, vm.season)
                    assertEquals(first, vm.scene.season)
                    repeat(4) {
                        vm.endTrip()
                        vm.depart()
                        val next = TripStore(context).loadActive()!!
                        assertEquals(SeasonChoice.RANDOM.resolve(next.weatherSeed), vm.season)
                        assertEquals(vm.season, vm.scene.season)
                        assertTrue(vm.scene.autoWeather)
                    }
                    vm.selectSeason(Season.WINTER)
                    vm.endTrip()
                    vm.depart()
                    assertEquals(SeasonChoice.WINTER, vm.seasonChoice)
                    assertEquals(Season.WINTER, vm.season)
                } finally {
                    models.clear()
                    graph.closeSession()
                    com.kero.passingby.session.TripTimer.stop(context)
                }
            }
        } finally {
            val editor = prefs.edit().clear()
            for ((key, value) in saved) when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            editor.commit()
        }
    }

    @Test fun legacyManualWeatherCannotOverrideAutomaticWeatherAfterRestart() {
        val prefs = context.getSharedPreferences("trip", 0)
        val savedWeather = prefs.getString("weather", null)
        val savedSeason = prefs.getString("season", null)
        try {
            prefs.edit().putString("weather", "HEAVY_SNOW").putString("season", "summer").commit()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val models = ViewModelStore()
                val graph = AppGraph(context.applicationContext as PassingByApp)
                try {
                    val factory = graph.journeyViewModelFactory
                    val vm = ViewModelProvider(models, factory)[JourneyViewModel::class.java]
                    assertNull(vm.fixedWeather)
                    assertTrue(vm.scene.autoWeather)
                    assertEquals(Season.SUMMER, vm.season)
                    vm.selectSeason(Season.WINTER)
                    assertEquals(Season.WINTER, vm.scene.season)
                    assertTrue(vm.scene.autoWeather)
                } finally { models.clear(); graph.closeSession() }
            }
        } finally {
            prefs.edit().putString("weather", savedWeather).putString("season", savedSeason).commit()
        }
    }
}
