package com.kero.passingby.ui.journey

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kero.passingby.experience.ExperienceRegistry
import com.kero.passingby.experience.TrainExperienceProvider
import com.kero.passingby.experience.train.R as TrainR
import com.kero.passingby.feature.journey.R
import com.kero.passingby.journey.SessionRuntime
import com.kero.passingby.journey.SessionScheduler
import com.kero.passingby.session.TripStore
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hosts the production screen and train provider inside the library's isolated test APK. */
class PostcardTestActivity : ComponentActivity() {
    private lateinit var session: SessionRuntime
    lateinit var journey: JourneyViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSharedPreferences("trip", MODE_PRIVATE).edit().clear().commit()
        val course = "usa-grand-canyon"
        val store = TripStore(this).apply {
            courseId = course
            unlocked = setOf(course)
            overlayAlways = true
            hintShown = true
        }
        val registry = ExperienceRegistry(listOf(TrainExperienceProvider()))
        // The test advances both trips explicitly through the runtime.
        session = SessionRuntime(store, registry.create(course, store), SystemClock::elapsedRealtime,
            SessionScheduler { _, _ -> {} }).also { it.start() }
        val factory = JourneyViewModel.Factory(application, JourneyOptions(false), store, { session }, registry)
        journey = ViewModelProvider(this, factory)[JourneyViewModel::class.java]
        journey.seekFraction(1f)
        setContent { JourneyScreen(journey) }
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}

class ArrivalPostcardTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private lateinit var host: PostcardTestActivity

    @Test fun opensAndDismissesTheCanyonPostcardOnlyAtTheCanyonTerminus() {
        val intent = Intent(inst.context, PostcardTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        host = inst.startActivitySync(intent) as PostcardTestActivity
        try {
            val postcardLink = host.getString(R.string.view_arrival_postcard)
            val caption = host.getString(TrainR.string.grand_canyon_purchase_preview_caption)
            val canyon = host.getString(TrainR.string.station_grand_canyon_depot)
            val williams = host.getString(TrainR.string.station_williams_depot)
            awaitVisible(host.getString(R.string.arrived_at, canyon))
            click(awaitVisible(postcardLink))
            awaitVisible(caption)
            click(awaitVisible(host.getString(R.string.close)))
            awaitCondition("Postcard dismissed") { visible(caption) == null }
            awaitVisible(postcardLink)

            inst.runOnMainSync {
                host.journey.getOff()
                host.journey.seekFraction(1f)
            }
            awaitVisible(host.getString(R.string.arrived_at, williams))
            assertNull(visible(postcardLink))
        } finally {
            inst.runOnMainSync { host.finish() }
        }
    }

    private fun visible(text: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        return find(inst.uiAutomation.rootInActiveWindow)
    }

    private fun awaitCondition(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(80)
        }
        val root = inst.uiAutomation.rootInActiveWindow
        val texts = mutableListOf<String>()
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null || texts.size >= 20) return
            if (node.isVisibleToUser) node.text?.toString()?.let(texts::add)
            for (i in 0 until node.childCount) collect(node.getChild(i))
        }
        collect(root)
        var session = ""
        inst.runOnMainSync {
            val vm = host.journey
            session = "course=${vm.course.id}, phase=${vm.phase}, direction=${vm.direction}, focus=${host.hasWindowFocus()}"
        }
        error("$message ($session, activeWindow=${root?.packageName}, visibleText=$texts)")
    }

    private fun awaitVisible(text: String): AccessibilityNodeInfo {
        var node: AccessibilityNodeInfo? = null
        awaitCondition("Missing screen text: $text") { node = visible(text); node != null }
        return checkNotNull(node)
    }

    private fun click(node: AccessibilityNodeInfo) {
        var button: AccessibilityNodeInfo? = node
        while (button != null && !button.isClickable) button = button.parent
        assertTrue(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }
}
