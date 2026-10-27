package com.kero.passingby

import android.content.Intent
import java.io.File
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.feature.journey.R
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real hidden gesture and password field using the accessibility tree. */
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

    @Test fun sevenTapsThenCodeOpensTheSharedModePicker() {
        val code = InstrumentationRegistry.getArguments().getString("previewCode")
        org.junit.Assume.assumeNotNull(code)
        val context = instrumentation.targetContext
        // Departing for the first time opens a system dialog above the journey on Android 13+.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            automation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val prefs = context.getSharedPreferences("trip", 0)
        val saved = prefs.all
        val grant = File(context.noBackupFilesDir, "preview-mode-grant")
        val savedGrant = grant.takeIf { it.exists() }?.readBytes()
        prefs.edit().clear().commit(); grant.delete()
        com.kero.passingby.session.TripStore(context).apply { overlayAlways = true; hintShown = true }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        val activity = monitor.waitForActivityWithTimeout(5_000) ?: error("Activity did not launch")
        instrumentation.removeMonitor(monitor)
        try {
            click(awaitText(context.getString(R.string.settings)))
            repeat(12) {
                if (text("Passing By") == null) {
                    find { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    Thread.sleep(100)
                }
            }
            // Six taps do not expose the code input. The seventh does.
            repeat(6) { click(awaitText("Passing By")) }
            assertNull(text(context.getString(R.string.secret_ticket)))
            click(awaitText("Passing By"))
            awaitText(context.getString(R.string.secret_ticket))
            val field = find { it.isEditable } ?: error("No password field")
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, code)
            }))
            click(awaitText(context.getString(R.string.secret_open)))
            awaitText(context.getString(R.string.travel_modes))
            click(awaitText(context.getString(com.kero.passingby.experience.api.R.string.mode_spaceship)))
            awaitText(context.getString(com.kero.passingby.experience.spaceship.R.string.body_earth) + " → " +
                context.getString(com.kero.passingby.experience.spaceship.R.string.body_jupiter))
            click(awaitText(context.getString(R.string.depart_minutes, 25)))
            try {
                for (name in listOf(com.kero.passingby.experience.spaceship.R.string.body_moon,
                    com.kero.passingby.experience.spaceship.R.string.body_mars,
                    com.kero.passingby.experience.spaceship.R.string.body_jupiter)) {
                    awaitText("→ " + context.getString(name))
                }
                // Let the overlay entrance settle before saving a reviewable screen capture.
                Thread.sleep(600)
            } finally {
                val bitmap = automation.takeScreenshot() ?: error("No flyby UI screenshot")
                try {
                    File(context.cacheDir, "flyby-route-ui.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { bitmap.recycle() }
                val labels = mutableListOf<String>()
                find { labels += "text=${it.text}; description=${it.contentDescription}"; false }
                File(context.cacheDir, "flyby-route-ui-labels.txt").writeText(labels.joinToString("\n"))
            }
        } finally {
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
            if (savedGrant == null) grant.delete() else grant.writeBytes(savedGrant)
        }
    }
}
