package com.kero.passingby

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.experience.airplane.AirplaneExperienceProvider
import com.kero.passingby.experience.ship.ShipExperienceProvider
import com.kero.passingby.experience.spaceship.SpaceExperienceProvider
import com.kero.passingby.feature.journey.R
import org.junit.Assert.*
import org.junit.Test

/** Checks that the train-only app exposes no entry to private modes. */
class ModeEntryUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (predicate(node)) return node
            for (i in 0 until node.childCount) walk(node.getChild(i))?.let { return it }
            return null
        }
        return walk(automation.rootInActiveWindow)
    }
    private fun text(value: String) = find { it.text?.toString() == value && it.isVisibleToUser }
    private fun awaitText(value: String): AccessibilityNodeInfo {
        repeat(40) { text(value)?.let { return it }; Thread.sleep(150) }
        error("Missing screen text: $value")
    }
    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        assertTrue(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        Thread.sleep(90)
    }

    @Test fun ordinaryUsersHaveNoModeMenuOrHiddenCourseEntries() = withFreshUser { context ->
        val hiddenText = listOf(AirplaneExperienceProvider(), ShipExperienceProvider(), SpaceExperienceProvider())
            .flatMap { listOf(it.mode.label, it.mode.description) + it.courses.map { course -> course.region } }
            .plus(R.string.travel_modes)
            .map(context::getString)
            .toSet()
        fun assertPrivateModesAreHidden() {
            val visible = find { it.isVisibleToUser && it.text?.toString() in hiddenText }
            assertNull("Private mode text is visible: ${visible?.text}", visible)
        }
        for ((entry, title) in listOf(
            R.string.settings to R.string.settings,
            R.string.change_course to R.string.courses,
            R.string.journal_short to R.string.journal,
        )) {
            click(awaitText(context.getString(entry)))
            awaitText(context.getString(title))
            repeat(12) {
                assertPrivateModesAreHidden()
                find { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(100)
            }
            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            awaitText(context.getString(R.string.depart_minutes, 25))
        }
    }

    @Test fun repeatedTapsOnTheSettingsFooterKeepTheSettingsOpen() = withFreshUser { context ->
        click(awaitText(context.getString(R.string.settings)))
        repeat(12) {
            if (text("Passing By") == null) {
                find { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(150)
            }
        }
        val footer = awaitText("Passing By")
        val bounds = Rect().also(footer::getBoundsInScreen)
        assertFalse(bounds.isEmpty)
        assertFalse("The footer must not have a click action", footer.isClickable)

        repeat(8) {
            tap(bounds.exactCenterX(), bounds.exactCenterY())
            Thread.sleep(90)
        }
        instrumentation.waitForIdleSync()

        assertNotNull(text("Passing By"))
        assertNull(find { it.isVisibleToUser && it.isEditable })
        assertNull(text(context.getString(R.string.travel_modes)))
    }

    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    private fun withFreshUser(test: (Context) -> Unit) {
        val context = instrumentation.targetContext
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            automation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val prefs = context.getSharedPreferences("trip", 0)
        val saved = prefs.all
        prefs.edit().clear().commit()
        com.kero.passingby.session.TripStore(context).apply { overlayAlways = true; hintShown = true }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        val activity = monitor.waitForActivityWithTimeout(5_000) ?: error("Activity did not launch")
        instrumentation.removeMonitor(monitor)
        try { test(context) } finally {
            instrumentation.runOnMainSync { activity.finish(); com.kero.passingby.session.TripTimer.stop(context) }
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
}
