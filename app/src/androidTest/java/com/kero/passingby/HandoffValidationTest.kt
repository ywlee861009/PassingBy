package com.kero.passingby

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.kero.passingby.ui.journey.JourneyViewModel
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.audio.AmbientSound
import com.kero.passingby.audio.LookaheadLimiter
import com.kero.passingby.content.Course
import com.kero.passingby.journey.CoursePlan
import com.kero.passingby.journey.CourseZones
import com.kero.passingby.scene.*
import com.kero.passingby.session.TripStore
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** 에뮬레이터에서도 재실행 가능한 회귀·캡처. 실기기 청취·배터리·Play 결제 시험을 대신하지 않는다. */
class HandoffValidationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val output get(): File {
        val additional = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        return (additional?.let(::File) ?: File(context.getExternalFilesDir(null), "handoff-validation")).apply { mkdirs() }
    }

    @Test fun tileCacheRejectsChangedSeasonAndWeather() {
        val state = SceneState()
        val key = TileStyle.of(state)
        assertTrue(key.matches(state))
        state.season = Season.AUTUMN
        assertFalse(key.matches(state))
        val autumn = TileStyle.of(state)
        state.autoWeather = false
        state.weather = Weather.HEAVY_SNOW
        state.snapWeather()
        assertFalse(autumn.matches(state))
        assertTrue(TileStyle.of(state).matches(state))
    }

    @Test fun seasonStorageDefaultsAndSurvivesNewStore() {
        val prefs = context.getSharedPreferences("trip", 0)
        val saved = prefs.getString("season", null)
        val savedTime = prefs.getString("time_of_day", null)
        try {
            prefs.edit().remove("season").commit()
            assertEquals(Season.SUMMER, TripStore(context).season)
            for (season in Season.entries) {
                TripStore(context).season = season
                assertEquals(season, TripStore(context).season)
            }
            prefs.edit().putString("season", "default").commit()
            assertEquals(Season.SUMMER, TripStore(context).season)
            prefs.edit().putString("time_of_day", "DAWN").commit()
            assertEquals(TimeOfDay.DAY, TripStore(context).timeOfDay)
            prefs.edit().putString("time_of_day", "DUSK").commit()
            assertEquals(TimeOfDay.NIGHT, TripStore(context).timeOfDay)
            prefs.edit().putString("season", "unknown").commit()
            assertEquals(Season.SUMMER, TripStore(context).season)
        } finally {
            prefs.edit().putString("season", saved).putString("time_of_day", savedTime).commit()
        }
    }

    @Test fun winterSceneryDoesNotRequireSnowfallAndClearsWhenSeasonChanges() {
        val state = SceneState().apply { season = Season.WINTER; autoWeather = true }
        state.update(0f, .4f)
        assertEquals(0f, state.snowAmount, 0f)
        assertEquals(0f, state.snowCover, 0f)
        assertTrue(state.effective.f[Palette.SNOW] > 0f)
        state.snowy = false
        state.update(0f, .4f)
        assertEquals(0f, state.effective.f[Palette.SNOW], 0f)
        state.snowy = true
        state.autoSnow = 1f
        state.snapWeather()
        state.season = Season.SUMMER
        state.update(0f, .4f)
        assertEquals(0f, state.snowAmount, 0f)
        assertEquals(0f, state.snowCover, 0f)
        assertEquals(0f, state.glassSnow, 0f)
        assertEquals(0f, state.effective.f[Palette.SNOW], 0f)
    }

    @Test fun legacyManualWeatherCannotOverrideAutomaticWeatherAfterRestart() {
        val prefs = context.getSharedPreferences("trip", 0)
        val savedWeather = prefs.getString("weather", null)
        val savedSeason = prefs.getString("season", null)
        try {
            prefs.edit().putString("weather", "HEAVY_SNOW").putString("season", "summer").commit()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val models = ViewModelStore()
                try {
                    val app = context.applicationContext as Application
                    val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
                    val vm = ViewModelProvider(models, factory)[JourneyViewModel::class.java]
                    assertNull(vm.fixedWeather)
                    assertTrue(vm.scene.autoWeather)
                    assertEquals(Season.SUMMER, vm.season)
                    vm.selectSeason(Season.WINTER)
                    assertEquals(Season.WINTER, vm.scene.season)
                    assertTrue(vm.scene.autoWeather)
                } finally { models.clear() }
            }
        } finally {
            prefs.edit().putString("weather", savedWeather).putString("season", savedSeason).commit()
        }
    }

    @Test fun renderEightCoursesAndRepresentativeWeatherMatrix() {
        for (course in Course.ALL) {
            val plan = CoursePlan(25 * 60.0, course.stations.size, course.cruise)
            val state = SceneState().apply {
                theme = course.theme
                snowy = course.snowy
                zones = CourseZones(plan, course, 1, 0.0)
                urban = com.kero.passingby.scene.UrbanField { x -> plan.at(x) }
                windowFrame = false
                autoWeather = false
                setTravel(plan.segDist * .45, 0.0)
            }
            val renderer = SceneRenderer(state)
            try {
                for (season in Season.entries) {
                    state.season = season
                    capture(renderer, state, "${course.id}-${season.id}-DAY-CLEAR-portrait", TimeOfDay.DAY, Weather.CLEAR, false)
                }
                // 같은 위치·크기에서 시간·날씨만 바꿔 밤·비·적설 합성을 비교한다.
                if (course.id == "korea" || course.id == "japan") {
                    for (time in listOf(TimeOfDay.DAY, TimeOfDay.NIGHT)) {
                        for (weather in listOf(Weather.CLEAR, Weather.SHOWER, Weather.HEAVY_SNOW)) {
                            for (landscape in listOf(false, true)) {
                                for (season in Season.entries) {
                                    state.season = season
                                    capture(renderer, state, "${course.id}-${season.id}-$time-$weather-${if (landscape) "landscape" else "portrait"}", time, weather, landscape)
                                }
                            }
                        }
                    }
                }
                // 색만 바꿔도 BAKED 마스크가 새로 생기지 않는지 확인한다.
                state.mode = RenderMode.BAKED
                state.weather = Weather.CLEAR
                state.snapWeather()
                state.setTimeOfDay(TimeOfDay.DAY)
                state.snapTimeOfDay()
                val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                repeat(40) { state.update(0f, .4f); renderer.render(canvas, 720, 1280); SystemClock.sleep(10) }
                val before = renderer.bitmapBytes
                repeat(100) {
                    state.season = Season.entries[it % Season.entries.size]
                    state.update(0f, .4f)
                    renderer.render(canvas, 720, 1280)
                }
                assertEquals("${course.id} seasonal mask allocation", before, renderer.bitmapBytes)
                for (season in Season.entries) {
                    state.season = season
                    state.update(0f, .4f)
                    repeat(20) { renderer.render(canvas, 720, 1280) }
                    val cpu = Debug.threadCpuTimeNanos()
                    repeat(100) { renderer.render(canvas, 720, 1280) }
                    File(output, "render-cpu.csv").appendText("${course.id},${season.id},${(Debug.threadCpuTimeNanos() - cpu) / 100},$before\n")
                }
                // 3개 모드와 타일 생성 중 빠른 전환을 실 렌더링으로 확인한다.
                for (mode in RenderMode.entries) {
                    state.mode = mode
                    repeat(12) {
                        state.season = Season.entries[it % Season.entries.size]
                        state.update(0f, .4f)
                        renderer.render(canvas, 720, 1280)
                    }
                    state.season = Season.WINTER
                    state.update(0f, .4f)
                    if (mode == RenderMode.TILES) {
                        val field = SceneRenderer::class.java.getDeclaredField("tileSet").apply { isAccessible = true }
                        val end = SystemClock.elapsedRealtime() + 5_000
                        while (SystemClock.elapsedRealtime() < end) {
                            renderer.render(canvas, 720, 1280)
                            val tiles = field.get(renderer) as TileSet?
                            if (tiles != null && tiles.key.matches(state)) break
                            SystemClock.sleep(20)
                        }
                        val tiles = field.get(renderer) as TileSet?
                        assertTrue("${course.id} stale tile result", tiles?.key?.matches(state) == true)
                    } else repeat(30) { renderer.render(canvas, 720, 1280); SystemClock.sleep(10) }
                    File(output, "${course.id}-winter-$mode.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
                bitmap.recycle()
            } finally { renderer.release() }
            for (season in Season.entries) {
                assertTrue(File(output, "${course.id}-${season.id}-DAY-CLEAR-portrait.png").isFile)
            }
        }
        assertEquals(8, Course.ALL.size)
    }

    private fun capture(renderer: SceneRenderer, state: SceneState, name: String, time: TimeOfDay, weather: Weather, landscape: Boolean) {
        state.setTimeOfDay(time)
        state.snapTimeOfDay()
        state.weather = weather
        state.snapWeather()
        state.update(0f, .4f)
        val w = if (landscape) 1280 else 720
        val h = if (landscape) 720 else 1280
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderer.render(Canvas(bitmap), w, h)
        File(output, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun audioRapidToggleAndForegroundRestartEndTheirThread() {
        val sound = AmbientSound()
        repeat(8) { sound.start(); SystemClock.sleep(30); sound.stop(); SystemClock.sleep(20) }
        sound.start()
        SystemClock.sleep(300)
        assertEquals(1, Thread.getAllStackTraces().keys.count { it.name == "ambient-sound" && it.isAlive })
        sound.stop()
        SystemClock.sleep(6_500)
        assertEquals(0, Thread.getAllStackTraces().keys.count { it.name == "ambient-sound" && it.isAlive })
        sound.start()
        SystemClock.sleep(100)
        assertEquals(1, Thread.getAllStackTraces().keys.count { it.name == "ambient-sound" && it.isAlive })
        sound.stop()
        SystemClock.sleep(6_500)
        assertEquals(0, Thread.getAllStackTraces().keys.count { it.name == "ambient-sound" && it.isAlive })
    }

    @Test fun limiterCpuComparisonMatchesOriginalPcm() {
        val random = Random(11)
        val input = FloatArray(22050 * 4) { random.nextFloat() * 4 - 2 }
        fun original(): Long {
            val ahead = FloatArray(128)
            var at = 0
            var gain = 1f
            var checksum = 0L
            for (s in input) {
                ahead[at] = s
                at = (at + 1) % 128
                var top = 0f
                for (v in ahead) top = max(top, abs(v))
                val want = if (top > .9f) .9f / top else 1f
                gain += (want - gain) * if (want < gain) .04f else 1f / (22050 * .5f)
                checksum += ((ahead[at] * gain).coerceIn(-1f, 1f) * 32000).toInt().toShort()
            }
            return checksum
        }
        fun optimized(): Long {
            val limiter = LookaheadLimiter()
            var checksum = 0L
            for (sample in input) checksum += limiter.process(sample)
            return checksum
        }
        repeat(4) { assertEquals(original(), optimized()) }
        repeat(7) { round ->
            val first = Debug.threadCpuTimeNanos()
            val a = if (round % 2 == 0) original() else optimized()
            val middle = Debug.threadCpuTimeNanos()
            val b = if (round % 2 == 0) optimized() else original()
            val end = Debug.threadCpuTimeNanos()
            assertEquals(a, b)
            val old = if (round % 2 == 0) middle - first else end - middle
            val new = if (round % 2 == 0) end - middle else middle - first
            File(output, "limiter-cpu.csv").appendText("$round,$old,$new,$a\n")
        }
    }
}
