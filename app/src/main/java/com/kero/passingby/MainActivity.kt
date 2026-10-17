package com.kero.passingby

import android.content.Context
import android.content.Intent
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
import com.kero.passingby.content.Course
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.scene.SceneKind
import com.kero.passingby.session.AppVisibility
import com.kero.passingby.scene.TimeOfDay
import com.kero.passingby.scene.Weather
import com.kero.passingby.ui.journey.JourneyViewModel
import com.kero.passingby.ui.journey.JourneyScreen

class MainActivity : ComponentActivity() {
    /** Android 13 미만에서 설정에서 고른 언어를 화면에 입힌다([AppLanguage]). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

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
        if (savedInstanceState == null && BuildConfig.DEV_TOOLS) {
            applyDebugExtras(intent)
        }
        setContent { JourneyScreen() }
    }

    /**
     * 개발용 실행 옵션(개발 도구가 켜진 빌드에서만). 화면을 누르지 않고 상태를 정해 확인할 때 쓴다.
     * - course: korea | japan | china-north | china-south | portugal | france
     * - scene: COUNTRY | CITY (생략하면 여정을 따름)
     * - time: DAWN | DAY | DUSK | NIGHT (생략하면 실제 시각)
     * - weather: CLEAR | DRIZZLE | SHOWER | LIGHT_SNOW | HEAVY_SNOW
     * - minutes: 구간 길이(분), at: 구간의 몇 % 지점에서 시작할지(0~1), speedup: 빨리 감기 배율
     * 예: adb shell am start -n com.kero.passingby/.MainActivity --ei minutes 25 --ef at 0.15 --es weather DRIZZLE --ez instant true
     */
    private fun applyDebugExtras(i: Intent) {
        val vm = ViewModelProvider(this)[JourneyViewModel::class.java]
        i.getStringExtra("course")?.let { id -> vm.forceCourse(Course.byId(id)) }
        i.getStringExtra("scene")?.let { n -> SceneKind.entries.firstOrNull { it.name == n }?.let(vm::selectLand) }
        i.getStringExtra("time")?.let { n -> TimeOfDay.entries.firstOrNull { it.name == n }?.let(vm::selectTime) }
        i.getStringExtra("weather")?.let { n -> Weather.entries.firstOrNull { it.name == n }?.let(vm::selectWeather) }
        if (i.hasExtra("minutes")) vm.selectMinutes(i.getIntExtra("minutes", 25))
        if (i.hasExtra("at")) vm.seekFraction(i.getFloatExtra("at", 0f))
        if (i.hasExtra("speedup")) vm.selectScale(i.getFloatExtra("speedup", 1f).toDouble())
        if (i.getBooleanExtra("instant", false)) {
            vm.scene.snapTimeOfDay()
            vm.scene.snapWeather()
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
        ViewModelProvider(this)[JourneyViewModel::class.java].setVisible(true)
    }

    override fun onStop() {
        AppVisibility.foreground = false
        ViewModelProvider(this)[JourneyViewModel::class.java].setVisible(false)
        super.onStop()
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
