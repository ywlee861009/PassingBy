package com.passingby.app.ui.journey

import android.app.Activity
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passingby.app.scene.RenderMode
import com.passingby.app.scene.SceneKind
import com.passingby.app.scene.TimeOfDay
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 여행 중 화면(시제품).
 * 기본 상태는 풍경만 보이고, 화면을 누르면 조작 패널이 나타났다가 4초 뒤 사라진다.
 */
@Composable
fun JourneyScreen(vm: JourneyViewModel = viewModel()) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val activity = LocalActivity.current
    LaunchedEffect(vm.cap60) {
        activity?.let { applyRefreshCap(it, vm.cap60) }
    }

    // 매 프레임 값이 바뀌는 상태. 그리기 단계에서만 읽으므로 재구성은 일어나지 않는다.
    var frameTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val interval = now - last
                    vm.scene.update((interval / 1e9).toFloat().coerceAtMost(0.05f))
                    vm.onFrame(interval, view.display?.refreshRate ?: 60f)
                }
                last = now
                frameTick = now
            }
        }
    }

    var overlay by remember { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    LaunchedEffect(overlay, interactions) {
        if (overlay) {
            delay(4_000)
            overlay = false
        }
    }
    val overlayAlpha by animateFloatAsState(if (overlay) 1f else 0f, tween(300), label = "overlay")
    var hint by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3_500)
        hint = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    overlay = !overlay
                    interactions++
                }
            },
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val tick = frameTick
                    if (tick >= 0L) {
                        drawIntoCanvas { vm.renderer.render(it.nativeCanvas, size.width.toInt(), size.height.toInt()) }
                    }
                },
        )
        if (vm.showHud && vm.hud.isNotEmpty()) {
            BasicText(
                vm.hud,
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (hint) {
            BasicText(
                "화면을 누르면 메뉴가 보여요",
                style = TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x80000000), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (overlayAlpha > 0.01f) {
            Controls(
                vm,
                onInteract = { interactions++ },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = overlayAlpha },
            )
        }
    }
}

@Composable
private fun Controls(vm: JourneyViewModel, onInteract: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in SceneKind.entries) {
                Chip(k.label, vm.sceneKind == k) { vm.selectSceneKind(k); onInteract() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in TimeOfDay.entries) {
                Chip(t.label, vm.timeOfDay == t) { vm.selectTime(t); onInteract() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("완행", !vm.fast) { vm.selectFast(false); onInteract() }
            Chip("고속", vm.fast) { vm.selectFast(true); onInteract() }
            Chip("비", vm.raining) { vm.toggleRain(); onInteract() }
            Chip("창틀", vm.windowFrame) { vm.toggleWindowFrame(); onInteract() }
            Chip("구조", vm.boxes) { vm.toggleBoxes(); onInteract() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in RenderMode.entries) {
                Chip(m.label, vm.mode == m) { vm.selectMode(m); onInteract() }
            }
            Chip("60Hz", vm.cap60) { vm.toggleCap60(); onInteract() }
            Chip("HUD", vm.showHud) { vm.toggleHud(); onInteract() }
        }
    }
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

/**
 * 화면 주사율을 60Hz 근처로 요청한다. 코드에서 프레임을 건너뛰는 것만으로는
 * 디스플레이 주사율이 내려간다는 보장이 없어서, 창에 원하는 모드를 직접 알린다.
 */
private fun applyRefreshCap(activity: Activity, cap60: Boolean) {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay
    } ?: return
    val current = display.mode
    val modeId = if (cap60) {
        display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { abs(it.refreshRate - 60f) }
            ?.modeId ?: 0
    } else 0
    val attrs = activity.window.attributes
    attrs.preferredDisplayModeId = modeId
    activity.window.attributes = attrs
}
