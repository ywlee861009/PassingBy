package com.passingby.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.passingby.app.scene.SceneKind
import com.passingby.app.scene.TimeOfDay
import com.passingby.app.scene.Weather
import com.passingby.app.ui.journey.JourneyViewModel
import com.passingby.app.ui.journey.JourneyScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 펀치홀 영역까지 풍경으로 채운다. LayoutParams는 다시 대입해야 적용된다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        if (savedInstanceState == null && (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            applyDebugExtras(intent)
        }
        setContent { JourneyScreen() }
    }

    /**
     * 개발용 실행 옵션(디버그 빌드에서만). 화면을 누르지 않고 상태를 정해 확인할 때 쓴다.
     * 예: adb shell am start -n com.passingby.app/.MainActivity --es weather HEAVY_SNOW --es time NIGHT --es scene CITY --ez instant true
     */
    private fun applyDebugExtras(i: Intent) {
        val vm = ViewModelProvider(this)[JourneyViewModel::class.java]
        i.getStringExtra("scene")?.let { n -> SceneKind.entries.firstOrNull { it.name == n }?.let(vm::selectSceneKind) }
        i.getStringExtra("time")?.let { n -> TimeOfDay.entries.firstOrNull { it.name == n }?.let(vm::selectTime) }
        i.getStringExtra("weather")?.let { n -> Weather.entries.firstOrNull { it.name == n }?.let(vm::selectWeather) }
        if (i.getBooleanExtra("instant", false)) {
            vm.scene.snapTimeOfDay()
            vm.scene.snapWeather()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
