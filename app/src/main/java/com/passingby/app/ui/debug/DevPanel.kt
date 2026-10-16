package com.passingby.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.passingby.app.scene.RenderMode
import com.passingby.app.scene.SceneKind
import com.passingby.app.scene.TimeOfDay
import com.passingby.app.scene.Weather
import com.passingby.app.ui.journey.JourneyViewModel

/**
 * 개발용 패널. 사용자 UI가 아니며 [com.passingby.app.BuildConfig.DEV_TOOLS]가 켜진 빌드에서만 보인다.
 * 줄마다 가로로 밀어 볼 수 있어 가로 화면에서도 넘치지 않는다.
 */
@Composable
fun DevPanel(vm: JourneyViewModel, onInteract: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChipRow {
            Chip("여정", vm.land == null) { vm.selectLand(null); onInteract() }
            for (k in SceneKind.entries) {
                Chip(k.label, vm.land == k) { vm.selectLand(k); onInteract() }
            }
            Gap()
            Chip("완행", !vm.fast) { vm.selectFast(false); onInteract() }
            Chip("고속", vm.fast) { vm.selectFast(true); onInteract() }
            Gap()
            Chip("창틀", vm.windowFrame) { vm.toggleWindowFrame(); onInteract() }
            Chip("구조", vm.boxes) { vm.toggleBoxes(); onInteract() }
        }
        ChipRow {
            Chip("실시간", vm.fixedTime == null) { vm.selectTime(null); onInteract() }
            for (t in TimeOfDay.entries) {
                Chip(t.label, vm.fixedTime == t) { vm.selectTime(t); onInteract() }
            }
        }
        ChipRow {
            for (w in Weather.entries) {
                Chip(w.label, vm.weather == w) { vm.selectWeather(w); onInteract() }
            }
        }
        ChipRow {
            for (m in MINUTES) {
                Chip("${m}분", vm.minutes == m) { vm.selectMinutes(m); onInteract() }
            }
            Chip("처음부터", false) { vm.restart(); onInteract() }
            Gap()
            for (s in SCALES) {
                Chip("×${s.toInt()}", vm.timeScale == s) { vm.selectScale(s); onInteract() }
            }
        }
        ChipRow {
            for (m in RenderMode.entries) {
                Chip(m.label, vm.mode == m) { vm.selectMode(m); onInteract() }
            }
            Gap()
            Chip("60Hz", vm.cap60) { vm.toggleCap60(); onInteract() }
            Chip("HUD", vm.showHud) { vm.toggleHud(); onInteract() }
        }
    }
}

private val MINUTES = intArrayOf(25, 50, 90)
private val SCALES = doubleArrayOf(1.0, 10.0, 30.0)

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Gap() {
    Box(Modifier.width(8.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) Color(0xEBFFFFFF) else Color(0x80000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(color = if (selected) Color(0xFF1A1A1A) else Color.White, fontSize = 14.sp),
        )
    }
}
