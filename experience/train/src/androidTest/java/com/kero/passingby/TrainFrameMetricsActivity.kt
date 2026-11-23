package com.kero.passingby

import android.app.Activity
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.kero.passingby.content.Course
import com.kero.passingby.experience.TimeOfDay
import com.kero.passingby.journey.CoursePlan
import com.kero.passingby.journey.CourseZones
import com.kero.passingby.scene.SceneRenderer
import com.kero.passingby.scene.SceneState
import com.kero.passingby.scene.UrbanField

/** Only included in the instrumentation APK. */
class TrainFrameMetricsActivity : Activity() {
    private lateinit var root: FrameLayout
    private var renderer: SceneRenderer? = null
    var hardwareCanvas = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        setContentView(root)
    }

    fun showScene(course: Course, position: Double) {
        root.removeAllViews()
        renderer?.release()
        val plan = CoursePlan(1500.0, course.stations.size, course.cruise)
        val state = SceneState().apply {
            theme = course.theme
            snowy = course.snowy
            zones = CourseZones(plan, course, 1, 0.0)
            urban = UrbanField { plan.at(it) }
            setStations(DoubleArray(course.stations.size) { plan.stationDistance(it) },
                Array(course.stations.size) { course.stations[it].local }, Array(course.stations.size) { course.stations[it].roman })
            windowFrame = false
            autoWeather = false
            setTimeOfDay(TimeOfDay.DAY)
            snapTimeOfDay()
        }
        val painter = SceneRenderer(state)
        renderer = painter
        hardwareCanvas = false
        val scene = object : View(this) {
            private var frame = 0
            override fun onDraw(canvas: Canvas) {
                hardwareCanvas = canvas.isHardwareAccelerated && width == 960 && height == 480
                state.setTravel(position * plan.segDist + frame++ * course.cruise / 60, course.cruise)
                state.update(1f / 60, .4f)
                painter.render(canvas, width, height)
                postInvalidateOnAnimation()
            }
        }
        root.addView(scene, FrameLayout.LayoutParams(960, 480, android.view.Gravity.CENTER))
    }

    override fun onDestroy() {
        root.removeAllViews()
        renderer?.release()
        super.onDestroy()
    }
}
