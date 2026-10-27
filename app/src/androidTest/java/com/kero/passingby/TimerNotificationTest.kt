package com.kero.passingby

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Chronometer
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.feature.journey.R
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.experience.ExperienceProgram
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.session.AppVisibility
import com.kero.passingby.session.TripNotifications
import com.kero.passingby.session.TripStore
import com.kero.passingby.session.TripTimer
import com.kero.passingby.ui.journey.JourneyViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test

class TimerNotificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as PassingByApp
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Test fun grantingPermissionDuringSpaceFlightImmediatelyPublishesTheCountdown() {
        // Run this test after `adb shell pm revoke <package> android.permission.POST_NOTIFICATIONS`.
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33)
        Assume.assumeTrue(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        withFlight { _, vm ->
            click(awaitNode { it.text?.toString() == context.getString(R.string.depart_minutes, 25) })
            awaitNode { it.viewIdResourceName?.endsWith(":id/permission_allow_button") == true }
            instrumentation.runOnMainSync {
                assertEquals(SessionPhase.RUNNING, vm.phase)
                vm.seekFraction(.02f) // Keep the next flyby far away so a waypoint cannot mask the missing retry.
            }
            assertNull(ongoing())
            click(awaitNode { it.viewIdResourceName?.endsWith(":id/permission_allow_button") == true })
            awaitCondition { context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
            assertCountdown()
            home()
            assertCountdown()
        }
    }

    @Test fun homeRestoresAMissingSpaceCountdownWithoutWaitingForAnotherPlanet() {
        grantPermission()
        withFlight { _, vm ->
            instrumentation.runOnMainSync { vm.depart(); vm.seekFraction(.02f) }
            assertCountdown()
            TripNotifications.cancelOngoing(context)
            awaitCondition { ongoing() == null }
            home()
            assertCountdown()
        }
    }

    @Test fun backgroundCountdownTicksAndPauseResumeKeepTheSameSpaceSession() {
        grantPermission()
        withFlight { activity, vm ->
            instrumentation.runOnMainSync { vm.depart(); vm.seekFraction(.02f) }
            val sessionId = TripStore(context).loadActive()!!.sessionId
            home()
            assertCountdown()
            val before = countdownSeconds()
            Thread.sleep(1200)
            assertTrue("The system countdown must keep decreasing on Home", countdownSeconds() < before)
            instrumentation.runOnMainSync { vm.pause() }
            awaitCondition { ongoing()?.`when`?.let { it < System.currentTimeMillis() + 1000 } == true }
            val paused = awaitOngoing()
            var pausedText = ""
            instrumentation.runOnMainSync {
                val views = paused.contentView.apply(context, null)
                assertEquals(View.GONE, views.findViewById<View>(com.kero.passingby.platform.notifications.R.id.notification_countdown).visibility)
                pausedText = views.findViewById<TextView>(com.kero.passingby.platform.notifications.R.id.notification_paused_time).text.toString()
                assertTrue(pausedText.isNotBlank())
            }
            Thread.sleep(1200)
            instrumentation.runOnMainSync {
                val views = awaitOngoing().contentView.apply(context, null)
                assertEquals(pausedText, views.findViewById<TextView>(com.kero.passingby.platform.notifications.R.id.notification_paused_time).text.toString())
            }
            instrumentation.runOnMainSync { vm.resume() }
            assertCountdown()
            assertEquals(sessionId, TripStore(context).loadActive()!!.sessionId)
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
            val remainingLabel = AppLanguage.localized(context).getString(com.kero.passingby.platform.notifications.R.string.notification_time_remaining)
            awaitNode { it.text?.toString() == remainingLabel }
            val displayed = awaitNode { it.viewIdResourceName?.endsWith(":id/notification_countdown") == true }.text.toString()
            Thread.sleep(1200)
            awaitNode { it.viewIdResourceName?.endsWith(":id/notification_countdown") == true && it.text.toString() != displayed }
            val bitmap = automation.takeScreenshot() ?: error("No timer notification screenshot")
            try {
                File(context.cacheDir, "space-timer-notification.png").outputStream().use {
                    assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            awaitCondition { AppVisibility.foreground }
            assertFalse(activity.isFinishing)
            assertCountdown()
        }
    }

    @Test fun trainCountdownAlsoSurvivesHomeAndReturn() {
        grantPermission()
        withFlight("korea") { _, vm ->
            instrumentation.runOnMainSync { vm.depart() }
            home()
            assertCountdown()
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            awaitCondition { AppVisibility.foreground }
            assertCountdown()
        }
    }

    private fun grantPermission() {
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun home() {
        assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        awaitCondition { !AppVisibility.foreground }
    }

    private fun ongoing() = manager.activeNotifications.firstOrNull { it.id == 1 }?.notification
    private fun awaitOngoing(): Notification {
        awaitCondition { ongoing() != null }
        return ongoing()!!
    }

    private fun assertCountdown() {
        val notification = awaitOngoing()
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        instrumentation.runOnMainSync {
            val program = app.graph.session.program as ExperienceProgram
            assertEquals(program.course.title(AppLanguage.localized(context).resources, program.direction), notification.extras.getString(Notification.EXTRA_TITLE))
            val views = notification.contentView.apply(context, null)
            val clock = views.findViewById<Chronometer>(com.kero.passingby.platform.notifications.R.id.notification_countdown)
            assertEquals(View.VISIBLE, clock.visibility)
            assertTrue(clock.isCountDown)
            assertEquals(app.graph.session.deadlineElapsedMillis!!.toDouble(), clock.base.toDouble(), 100.0)
        }
    }

    private fun countdownSeconds(): Double {
        var seconds = 0.0
        instrumentation.runOnMainSync {
            val views = awaitOngoing().contentView.apply(context, null)
            seconds = (views.findViewById<Chronometer>(com.kero.passingby.platform.notifications.R.id.notification_countdown).base - android.os.SystemClock.elapsedRealtime()) / 1000.0
        }
        return seconds
    }

    private fun awaitCondition(condition: () -> Boolean) {
        repeat(30) { if (condition()) return; Thread.sleep(100) }
        assertTrue("Expected notification/lifecycle condition within three seconds", condition())
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && predicate(node)) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        repeat(50) { find(automation.rootInActiveWindow)?.let { return it }; Thread.sleep(100) }
        error("Missing permission or departure control")
    }

    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        assertTrue(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private fun withFlight(courseId: String = "space_solar_escape", test: (MainActivity, JourneyViewModel) -> Unit) {
        Assume.assumeTrue(BuildConfig.PREVIEW_CODE_SHA256.isNotEmpty())
        val prefs = context.getSharedPreferences("trip", 0)
        val saved = prefs.all
        val grant = File(context.noBackupFilesDir, "preview-mode-grant")
        val savedGrant = grant.takeIf { it.exists() }?.readBytes()
        prefs.edit().clear().commit()
        grant.writeText(BuildConfig.PREVIEW_CODE_SHA256)
        TripStore(context).apply { this.courseId = courseId; hintShown = true; soundOn = false; overlayAlways = true }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("course", courseId).putExtra("minutes", 25))
        val activity = monitor.waitForActivityWithTimeout(5000) as? MainActivity ?: error("Activity did not launch")
        instrumentation.removeMonitor(monitor)
        var vm: JourneyViewModel? = null
        try {
            instrumentation.runOnMainSync { vm = ViewModelProvider(activity, app.graph.journeyViewModelFactory)[JourneyViewModel::class.java] }
            assertEquals(courseId, vm!!.course.id)
            test(activity, vm!!)
        } finally {
            instrumentation.runOnMainSync { vm?.endTrip(); activity.finish(); TripTimer.stop(context) }
            instrumentation.waitForIdleSync()
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
            if (savedGrant == null) grant.delete() else grant.writeBytes(savedGrant)
        }
    }
}
