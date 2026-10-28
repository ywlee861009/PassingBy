package com.kero.passingby

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.experience.SceneKind
import com.kero.passingby.experience.SeasonChoice
import com.kero.passingby.experience.RenderMode
import com.kero.passingby.session.AppVisibility
import com.kero.passingby.session.TripNotifications
import com.kero.passingby.experience.TimeOfDay
import com.kero.passingby.experience.Weather
import com.kero.passingby.ui.journey.JourneyViewModel
import com.kero.passingby.ui.journey.JourneyScreen
import com.kero.passingby.ui.startup.LaunchScreen

class MainActivity : ComponentActivity() {
    private var notificationsEnabled by mutableStateOf(false)
    private val journeyViewModel: JourneyViewModel by lazy {
        ViewModelProvider(this, (application as PassingByApp).graph.journeyViewModelFactory)[JourneyViewModel::class.java]
    }

    /** Android 13 미만에서 설정에서 고른 언어를 화면에 입힌다([AppLanguage]). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 첫 프레임부터 전체 여행 그림을 보여 준다. 시스템 아이콘 화면을 더 붙잡아 두지 않는다.
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
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
        val showLaunchArtwork = (application as PassingByApp).claimLaunchArtwork()
        notificationsEnabled = TripNotifications.areEnabled(this)
        setContent {
            LaunchScreen(showOnLaunch = showLaunchArtwork) {
                JourneyScreen(
                    journeyViewModel,
                    onNotificationPermissionGranted = ::refreshSessionEffects,
                    notificationsEnabled = notificationsEnabled,
                    onNotificationSettings = { startActivity(TripNotifications.settingsIntent(this)) },
                )
            }
        }
    }

    /**
     * 개발용 실행 옵션(개발 도구가 켜진 빌드에서만). 화면을 누르지 않고 상태를 정해 확인할 때 쓴다.
     * - course: 코스 id (korea, korea-jeolla, korea-gangneung, japan, china-north, china-south, portugal, france)
     * - scene: COUNTRY | CITY (생략하면 여정을 따름)
     * - time: DAWN | DAY | DUSK | NIGHT (개발용, 생략하면 저장된 설정/자동 순환)
     * - season: random | spring | summer | autumn | winter, mode: DIRECT | BAKED | TILES
     * - weather: CLEAR | DRIZZLE | SHOWER | LIGHT_SNOW | HEAVY_SNOW
     * - minutes: 구간 길이(분), at: 구간의 몇 % 지점에서 시작할지(0~1), speedup: 빨리 감기 배율
     * - pos: 정방향 기준 노선 위치(구간 단위, 예 2.97). 랜드마크 위치를 확인할 때 쓴다.
     * - hud: true면 성능 HUD(프레임 간격·그리기 시간)를 켠다.
     * 예: adb shell am start -n com.kero.passingby/.MainActivity --ei minutes 25 --ef at 0.15 --es weather DRIZZLE --ez instant true
     * 예: adb shell am start -n com.kero.passingby/.MainActivity --es course korea --ef pos 0.99 --es time DAY --ez instant true
     */
    private fun applyDebugExtras(i: Intent) {
        val vm = journeyViewModel
        i.getStringExtra("course")?.let { id -> vm.forceCourse(vm.courseById(id)) }
        i.getStringExtra("scene")?.let { n -> SceneKind.entries.firstOrNull { it.name == n }?.let(vm::selectLand) }
        i.getStringExtra("time")?.let { n -> TimeOfDay.entries.firstOrNull { it.name == n }?.let(vm::selectTime) }
        i.getStringExtra("season")?.let { vm.selectSeasonChoice(SeasonChoice.fromId(it)) }
        i.getStringExtra("weather")?.let { n -> Weather.entries.firstOrNull { it.name == n }?.let(vm::selectWeather) }
        i.getStringExtra("mode")?.let { n -> RenderMode.entries.firstOrNull { it.name == n }?.let(vm::selectMode) }
        if (i.getBooleanExtra("unlock", false) && !vm.devUnlockAll) vm.toggleDevUnlock()
        if (i.hasExtra("minutes")) vm.selectMinutes(i.getIntExtra("minutes", 25))
        if (i.hasExtra("at")) vm.seekFraction(i.getFloatExtra("at", 0f))
        if (i.hasExtra("pos")) vm.seekPosition(i.getFloatExtra("pos", 0f).toDouble())
        if (i.getBooleanExtra("hud", false) && !vm.showHud) vm.toggleHud()
        if (i.hasExtra("speedup")) vm.selectScale(i.getFloatExtra("speedup", 1f).toDouble())
        if (i.getBooleanExtra("instant", false)) {
            vm.scene.snapTimeOfDay()
            vm.scene.snapWeather()
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
        journeyViewModel.setVisible(true)
    }

    override fun onResume() {
        super.onResume()
        journeyViewModel.onResume()
        refreshSessionEffects()
    }

    override fun onStop() {
        AppVisibility.foreground = false
        journeyViewModel.setVisible(false)
        refreshSessionEffects()
        super.onStop()
    }

    private fun refreshSessionEffects() {
        notificationsEnabled = TripNotifications.areEnabled(this)
        (application as PassingByApp).graph.refreshSessionEffects()
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
