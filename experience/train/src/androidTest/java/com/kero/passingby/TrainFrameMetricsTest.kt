package com.kero.passingby

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.content.Feature
import com.kero.passingby.content.PortugalCourses
import com.kero.passingby.content.UsaCourses
import com.kero.passingby.scene.LandmarkKind
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TrainFrameMetricsTest {
    @Test fun profileMovingScenesInHardwareWindow() {
        assumeTrue(Build.VERSION.SDK_INT >= 24)
        val inst = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(inst.context, TrainFrameMetricsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val host = inst.startActivitySync(intent) as TrainFrameMetricsActivity
        try {
            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.let(::File) ?: File(inst.targetContext.getExternalFilesDir(null), "handoff-validation")
            output.mkdirs()
            val csv = File(output, "coastal-hardware-frames.csv")
            csv.writeText("course,scene,frames,p50_total_ms,p95_total_ms,deadline_frames,missed_deadlines,dropped_reports\n")
            for (course in listOf(PortugalCourses.NORTE_DOURO, UsaCourses.PACIFIC_COAST)) {
                val sea = course.features.first { it.kind == Feature.Kind.SEA }
                val coast = course.landmarks.firstOrNull { it.kind == LandmarkKind.SAN_CLEMENTE_PIER }?.pos
                    ?: (sea.seg + (sea.from + sea.to) * .5)
                for ((name, position) in listOf("city" to .025, "coast" to coast)) {
                    val finished = CountDownLatch(1)
                    val durations = LongArray(180)
                    var warmup = 0
                    var count = 0
                    var deadlines = 0
                    var missed = 0
                    var dropped = 0
                    val listener = Window.OnFrameMetricsAvailableListener { _, metrics, lost ->
                        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                        if (!host.hardwareCanvas || total <= 0 || count == durations.size) return@OnFrameMetricsAvailableListener
                        if (++warmup <= 60) return@OnFrameMetricsAvailableListener
                        durations[count++] = total
                        dropped += lost
                        if (Build.VERSION.SDK_INT >= 31) {
                            val budget = metrics.getMetric(FrameMetrics.DEADLINE)
                            if (budget > 0) {
                                deadlines++
                                if (total > budget) missed++
                            }
                        }
                        if (count == durations.size) finished.countDown()
                    }
                    inst.runOnMainSync {
                        host.showScene(course, position)
                        host.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
                    }
                    try {
                        assertTrue("Timed out measuring ${course.id}/$name on a 960×480 hardware canvas",
                            finished.await(30, TimeUnit.SECONDS))
                    } finally {
                        inst.runOnMainSync { host.window.removeOnFrameMetricsAvailableListener(listener) }
                    }
                    if (Build.VERSION.SDK_INT >= 31) assertTrue("No frame deadlines reported", deadlines > 0)
                    durations.sort()
                    val row = "${course.id},$name,$count,${durations[89] / 1e6},${durations[170] / 1e6},$deadlines,$missed,$dropped"
                    csv.appendText("$row\n")
                    Log.i("TrainFrameMetrics", row)
                }
            }
        } finally {
            inst.runOnMainSync { host.finishAndRemoveTask() }
        }
    }
}
