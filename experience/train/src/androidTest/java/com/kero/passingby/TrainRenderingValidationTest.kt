package com.kero.passingby

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.audio.AmbientSound
import com.kero.passingby.audio.LookaheadLimiter
import com.kero.passingby.content.Course
import com.kero.passingby.content.IndiaCourses
import com.kero.passingby.content.RouteGuides
import com.kero.passingby.content.UsaCourses
import com.kero.passingby.journey.CoursePlan
import com.kero.passingby.journey.CourseZones
import com.kero.passingby.journey.WindowEventKind
import com.kero.passingby.scene.*
import com.kero.passingby.experience.*
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** 에뮬레이터에서도 재실행 가능한 회귀·캡처. 실기기 청취·배터리·Play 결제 시험을 대신하지 않는다. */
class TrainRenderingValidationTest {
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

    @Test fun renderAllCoursesAndRepresentativeWeatherMatrix() {
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
        assertEquals(Course.ALL.map { it.guide }, RouteGuides.ALL)
    }

    @Test fun californiaLandmarksAreVisibleInBothDirections() =
        verifyLandmarks(UsaCourses.PACIFIC_COAST, "california")

    @Test fun grandCanyonDepotsAndPostcardFollowTheTrainDirection() {
        val course = UsaCourses.GRAND_CANYON
        assertNotNull(course.purchasePreview)
        assertEquals(course.purchasePreview, course.arrivalPostcard(1))
        assertNull(course.arrivalPostcard(-1))
        assertNull(UsaCourses.PACIFIC_COAST.arrivalPostcard(1))
        verifyLandmarks(course, course.id)
    }

    @Test fun grandCanyonTerrainIsVisibleAlongBothDirections() {
        val course = UsaCourses.GRAND_CANYON
        val plan = CoursePlan(1500.0, course.stations.size, course.cruise)
        val state = SceneState().apply {
            theme = course.theme
            mode = RenderMode.BAKED
            windowFrame = false
            autoWeather = false
            setTimeOfDay(TimeOfDay.DAY)
            snapTimeOfDay()
        }
        val renderer = SceneRenderer(state)
        try {
            for (direction in listOf(1, -1)) {
                val zones = CourseZones(plan, course, direction, 0.0)
                state.zones = zones
                fun at(fraction: Double) = plan.segDist * if (direction > 0) fraction else 1.0 - fraction
                for (fraction in listOf(0.0, .48, 1.0)) {
                    assertEquals("Red cliffs outside the approach at $fraction", 0f, zones.redCliffs(at(fraction)), .001f)
                }
                assertTrue(zones.redCliffs(at(.515)) > 0f)
                for ((scene, fraction) in listOf("forest" to .08, "prairie" to .38,
                    "miller-wash" to (35.5 / 65), "coconino-canyon" to (56.0 / 65))) {
                    val x = at(fraction)
                    when (scene) {
                        "forest" -> assertEquals(0f, zones.prairie(x), .001f)
                        "prairie" -> assertEquals(1f, zones.prairie(x), .001f)
                        "miller-wash" -> {
                            assertEquals(1f, zones.dryWash(x), .001f)
                            assertTrue(zones.redCliffs(x) in 0.01f..0.3f)
                        }
                        "coconino-canyon" -> {
                            assertEquals(1f, zones.limestoneCanyon(x), .001f)
                            assertTrue(zones.redCliffs(x) in 0.01f..0.3f)
                        }
                    }
                    state.setTravel(x, 0.0)
                    capture(renderer, state, "${course.id}-$scene-$direction", TimeOfDay.DAY, Weather.CLEAR, true)
                }
            }
        } finally { renderer.release() }
    }

    @Test fun brightAngelRedCliffsAreVisibleThroughTheTrainWindowInBothDirections() {
        val course = UsaCourses.GRAND_CANYON
        val plan = CoursePlan(1500.0, course.stations.size, course.cruise)
        val state = SceneState().apply {
            theme = course.theme
            mode = RenderMode.BAKED
            windowFrame = true
            autoWeather = false
            weather = Weather.CLEAR
            snapWeather()
            urban = UrbanField { x -> plan.at(x) }
        }
        val renderer = SceneRenderer(state)
        try {
            for (direction in listOf(1, -1)) {
                val zones = CourseZones(plan, course, direction, 0.0)
                val withoutCliffs = object : Zones by zones {
                    override fun redCliffs(x: Double) = 0f
                }
                val stops = course.ordered(direction)
                state.setStations(DoubleArray(stops.size) { plan.stationDistance(it) },
                    Array(stops.size) { stops[it].local }, Array(stops.size) { stops[it].roman })
                val faultFraction = 63.3 / 65.0
                val progress = if (direction > 0) faultFraction else 1.0 - faultFraction
                val distance = plan.segDist * progress
                state.setTravel(distance, 0.0)
                assertTrue("Bright Angel is fully visible in direction $direction", zones.redCliffs(distance) > .95f)
                for (fixedTime in listOf(TimeOfDay.DAY, null)) {
                    // The default sky is dark near the end of the ride and just turning at the start.
                    state.setTimeOfDay(fixedTime)
                    state.update(0f, progress.toFloat())
                    state.snapTimeOfDay()
                    state.update(0f, progress.toFloat())
                    val automatic = fixedTime == null
                    val expectedTime = if (direction > 0) TimeOfDay.NIGHT else TimeOfDay.DAWN
                    assertEquals(fixedTime ?: expectedTime, state.timeOfDay)
                    for (landscape in listOf(false, true)) {
                        val width = if (landscape) 1280 else 720
                        val height = if (landscape) 720 else 1280
                        val orientation = if (landscape) "landscape" else "portrait"
                        val lighting = if (automatic) "auto" else "day"
                        val label = "${course.id}-bright-angel-$direction-$orientation-$lighting"
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap)
                            state.zones = zones
                            renderer.render(canvas, width, height)
                            awaitPlateauArtwork(renderer)
                            renderer.render(canvas, width, height)
                            File(output, "$label.png").outputStream().use {
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                            val cliffs = windowPixels(bitmap)
                            state.zones = withoutCliffs
                            renderer.render(canvas, width, height)
                            val baseline = windowPixels(bitmap)
                            val redGreenGap = if (automatic) 12 else 24
                            val redBlueGap = if (automatic) 8 else 36
                            val visibleRock = cliffs.indices.count { i ->
                                val pixel = cliffs[i]
                                val red = Color.red(pixel)
                                pixel != baseline[i] && red - Color.green(pixel) >= redGreenGap &&
                                    red - Color.blue(pixel) >= redBlueGap
                            }
                            assertTrue("$label red cliff pixels: $visibleRock", visibleRock >= cliffs.size / 25)
                        } finally { bitmap.recycle() }
                    }
                }
            }
        } finally { renderer.release() }
    }

    @Test fun indiaLandscapesAreVisibleThroughTheTrainWindowInBothDirections() {
        for (course in IndiaCourses.ALL) {
            val plan = CoursePlan(1500.0, course.stations.size, course.cruise)
            val darjeeling = course === IndiaCourses.DARJEELING
            val state = SceneState().apply {
                theme = course.theme
                mode = RenderMode.DIRECT
                autoWeather = false
                weather = Weather.CLEAR
                snapWeather()
                setTimeOfDay(TimeOfDay.DAY)
                snapTimeOfDay()
                urban = UrbanField { x -> plan.at(x) }
            }
            val renderer = SceneRenderer(state)
            try {
                for (direction in listOf(1, -1)) {
                    val zones = CourseZones(plan, course, direction, 0.0)
                    val range = if (darjeeling) zones.batasiaRanges else zones.pambanRanges
                    assertEquals("${course.id}: one landscape range", 2, range.size)
                    val position = if (darjeeling) .5 else zones.pambanLiftProgress.toDouble()
                    val distance = range[0] + (range[1] - range[0]) * position
                    assertEquals(1f, if (darjeeling) zones.batasia(distance) else zones.pambanCrossing(distance), .001f)
                    val baselineZones = object : Zones by zones {
                        override fun batasia(x: Double) = 0f
                        override fun pambanCrossing(x: Double) = 0f
                    }
                    val stops = course.ordered(direction)
                    state.setStations(DoubleArray(stops.size) { plan.stationDistance(it) },
                        Array(stops.size) { stops[it].local }, Array(stops.size) { stops[it].roman })
                    state.setTravel(distance, 0.0)
                    state.update(0f, (distance / plan.totalDistance).toFloat())
                    state.windowFrame = true
                    for (landscape in listOf(false, true)) {
                        val width = if (landscape) 1280 else 720
                        val height = if (landscape) 720 else 1280
                        val orientation = if (landscape) "landscape" else "portrait"
                        val label = "${course.id}-feature-$direction-$orientation"
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap)
                            state.zones = zones
                            renderer.render(canvas, width, height)
                            if (darjeeling) {
                                awaitArtwork(renderer, "darjeelingBake")
                                renderer.render(canvas, width, height)
                            }
                            File(output, "$label.png").outputStream().use {
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                            val feature = windowPixels(bitmap)
                            state.zones = baselineZones
                            renderer.render(canvas, width, height)
                            val baseline = windowPixels(bitmap)
                            val changed = feature.indices.count { feature[it] != baseline[it] }
                            assertTrue("$label visible feature pixels: $changed", changed >= feature.size / 25)
                        } finally { bitmap.recycle() }
                    }
                    if (direction == 1) {
                        state.zones = zones
                        state.windowFrame = false
                        capture(renderer, state, "${course.id}-purchase-preview", TimeOfDay.DAY, Weather.CLEAR, true)
                    }
                }
            } finally { renderer.release() }
        }
    }

    @Test fun glassEventsAreVisibleInOpenAirAndThroughTunnels() {
        val state = eventScene()
        val renderer = SceneRenderer(state)
        val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val tunnel = object : Zones by Zones.NONE {
                override val tunnels = doubleArrayOf(20.0, 1900.0)
            }
            for (zones in listOf(Zones.NONE, tunnel)) {
                state.zones = zones
                state.windowEvent = null
                renderer.render(canvas, bitmap.width, bitmap.height)
                val before = windowPixels(bitmap)
                val wall = bitmap.getPixel(0, 0)
                for (event in listOf(WindowEventKind.GLASS_GLINT, WindowEventKind.WINDOW_DROPS)) {
                    state.windowEvent = event
                    state.windowEventProgress = .5f
                    renderer.render(canvas, bitmap.width, bitmap.height)
                    assertTrue("$event is visible with tunnels=${zones.tunnels.isNotEmpty()}",
                        !before.contentEquals(windowPixels(bitmap)))
                    assertEquals("$event stays within the window", wall, bitmap.getPixel(0, 0))
                }
            }
        } finally {
            bitmap.recycle()
            renderer.release()
        }
    }

    @Test fun meteorBreaksTheSceneryAndThenRestoresTheWindow() {
        val state = eventScene()
        val renderer = SceneRenderer(state)
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            renderer.render(canvas, bitmap.width, bitmap.height)
            val before = windowPixels(bitmap)
            val wall = bitmap.getPixel(0, 0)
            state.windowEvent = WindowEventKind.METEOR
            for ((label, progress) in listOf("approach" to .27f, "impact" to .58f, "clearing" to .81f)) {
                state.windowEventProgress = progress
                renderer.render(canvas, bitmap.width, bitmap.height)
                val after = windowPixels(bitmap)
                val changed = before.indices.count { before[it] != after[it] }
                assertTrue("meteor $label changes the window ($changed pixels)", changed > 100)
                if (label == "impact") assertTrue("terrain visibly breaks across the window", changed > before.size / 5)
                assertEquals(wall, bitmap.getPixel(0, 0))
                File(output, "window-event-meteor-$label.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            state.windowEventProgress = 1f
            renderer.render(canvas, bitmap.width, bitmap.height)
            assertArrayEquals(before, windowPixels(bitmap))
        } finally {
            bitmap.recycle()
            renderer.release()
        }
    }

    private fun eventScene() = SceneState().apply {
        mode = RenderMode.DIRECT
        setTimeOfDay(TimeOfDay.DAY)
        snapTimeOfDay()
        autoWeather = false
        weather = Weather.CLEAR
        snapWeather()
        setTravel(1000.0, Course.REGIONAL)
        update(0f, .4f)
    }

    private fun windowPixels(bitmap: Bitmap): IntArray {
        // Sample inside the glass so the window trim cannot count as red rock.
        val opening = TrainWindowFrame().apply { layout(bitmap.width, bitmap.height) }.opening
        val left = (opening.left + opening.width() * .1f).toInt()
        val top = (opening.top + opening.height() * .1f).toInt()
        val width = (opening.width() * .8f).toInt()
        val height = (opening.height() * .8f).toInt()
        return IntArray(width * height).also { bitmap.getPixels(it, 0, width, left, top, width, height) }
    }

    private fun verifyLandmarks(course: Course, prefix: String) {
        val plan = CoursePlan(25 * 60.0, course.stations.size, course.cruise)
        val width = 960
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val withLandmark = IntArray(width * height)
        val withoutLandmark = IntArray(width * height)
        val state = SceneState().apply {
            theme = course.theme
            urban = UrbanField { x -> plan.at(x) }
            mode = RenderMode.DIRECT
            windowFrame = false
            autoWeather = false
            setTimeOfDay(TimeOfDay.DAY)
            snapTimeOfDay()
        }
        val renderer = SceneRenderer(state)
        try {
            for (direction in listOf(1, -1)) {
                val zones = CourseZones(plan, course, direction, 0.0)
                val stops = course.ordered(direction)
                state.setStations(DoubleArray(stops.size) { plan.stationDistance(it) },
                    Array(stops.size) { stops[it].local }, Array(stops.size) { stops[it].roman })
                for ((spot, mark) in course.landmarks.zip(zones.landmarks)) {
                    state.zones = zones
                    // Use the scheduled rail position; a depot can sit to one side of the platform.
                    val position = if (direction < 0) plan.segments - spot.pos else spot.pos
                    state.setTravel(position * plan.segDist, 0.0)
                    state.update(0f, .4f)
                    renderer.render(canvas, width, height)
                    if (course === UsaCourses.GRAND_CANYON) {
                        awaitPlateauArtwork(renderer)
                        renderer.render(canvas, width, height)
                    }
                    bitmap.getPixels(withLandmark, 0, width, 0, 0, width, height)
                    File(output, "$prefix-${mark.kind}-$direction.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    state.zones = object : Zones by zones {
                        override val landmarks = zones.landmarks.filter { it !== mark }
                    }
                    renderer.render(canvas, width, height)
                    bitmap.getPixels(withoutLandmark, 0, width, 0, 0, width, height)
                    val visiblePixels = withLandmark.indices.count { withLandmark[it] != withoutLandmark[it] }
                    assertTrue("${mark.kind} ($direction) visible pixels: $visiblePixels", visiblePixels > 300)
                }
            }
        } finally {
            renderer.release()
            bitmap.recycle()
        }
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
        val canvas = Canvas(bitmap)
        renderer.render(canvas, w, h)
        if (state.theme === Themes.GRAND_CANYON) {
            awaitPlateauArtwork(renderer)
            renderer.render(canvas, w, h)
        }
        File(output, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun awaitPlateauArtwork(renderer: SceneRenderer) = awaitArtwork(renderer, "plateauBake")

    private fun awaitArtwork(renderer: SceneRenderer, property: String) {
        val bake = SceneRenderer::class.java.getDeclaredField(property).apply { isAccessible = true }
        (bake.get(renderer) as CompletableFuture<*>).get(5, TimeUnit.SECONDS)
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
