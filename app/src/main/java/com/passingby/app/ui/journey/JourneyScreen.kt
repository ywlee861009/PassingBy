package com.passingby.app.ui.journey

import android.app.Activity
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.passingby.app.BuildConfig
import com.passingby.app.ui.debug.DevPanel
import kotlinx.coroutines.delay
import kotlin.math.abs

/** 화면 크기별 레이아웃(명세 5.3). 화면 방향이 아니라 창 크기로 정한다. */
enum class FormFactor { PORTRAIT, LANDSCAPE, EXPANDED, TABLETOP }

/**
 * 여행 화면. 앱의 유일한 화면이다. 출발·여행·도착·휴식을 화면 전환 없이 열차 상태로 보여 준다(명세 5.0).
 *
 * - 정차 중(DOCKED·ARRIVED·RESTING): 벽 패널이 올라와 창 일부를 가리고, 그 위에 카드를 놓는다.
 * - 주행 중(RUNNING·PAUSED): UI 0개. 화면을 누르면 오버레이가 나타났다가 4초 뒤 사라진다. 멈춘 동안에는 남는다.
 * - 반접기(테이블탑): 위쪽 반은 창, 아래쪽 반은 늘 보이는 정보판.
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
                if (last != 0L) vm.onFrame(now - last, view.display?.refreshRate ?: 60f)
                last = now
                frameTick = now
            }
        }
    }

    val fold = rememberFold(activity)
    val hingeY = fold.tabletopY
    val screenReader = rememberTouchExploration()
    val phase = vm.phase
    val moving = phase == TripPhase.RUNNING || phase == TripPhase.PAUSED

    var overlay by remember { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    var devPanel by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var backArmed by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }

    LaunchedEffect(overlay, interactions, phase) {
        if (overlay && phase == TripPhase.RUNNING) {
            delay(OVERLAY_HIDE_MS)
            overlay = false
        }
    }
    LaunchedEffect(phase) {
        overlay = phase == TripPhase.PAUSED
        if (phase == TripPhase.RUNNING && !vm.hintShown) {
            vm.markHintShown()
            hint = true
            delay(HINT_MS)
            hint = false
        }
    }
    LaunchedEffect(devPanel, interactions) {
        if (devPanel) {
            delay(DEV_HIDE_MS)
            devPanel = false
        }
    }
    // 뒤로가기: 첫 번째는 오버레이와 안내, 두 번째에 여행을 끝낸다(명세 5.5).
    BackHandler(enabled = moving) {
        if (backArmed) {
            backArmed = false
            vm.endTrip()
        } else {
            backArmed = true
            overlay = true
            interactions++
        }
    }
    LaunchedEffect(backArmed) {
        if (backArmed) {
            delay(BACK_ARM_MS)
            backArmed = false
        }
    }
    // 여행 중간에 끝내면 장면이 출발역으로 순간 이동한다. 검은 화면에서 밝아지며 그 순간을 가린다.
    val cut = remember { Animatable(0f) }
    LaunchedEffect(vm.cutTick) {
        if (vm.cutTick > 0) {
            cut.snapTo(1f)
            cut.animateTo(0f, tween(700))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val form = when {
            hingeY != null -> FormFactor.TABLETOP
            maxWidth < 600.dp -> FormFactor.PORTRAIT
            maxHeight < 480.dp -> FormFactor.LANDSCAPE
            else -> FormFactor.EXPANDED
        }
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val insets = WindowInsets.safeDrawing
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val gap = with(density) { CARD_MARGIN.toPx() }
        val seatTarget = when {
            form == FormFactor.TABLETOP -> constraints.maxHeight - (hingeY ?: 0f)
            moving -> 0f
            form == FormFactor.LANDSCAPE -> cardSize.width + insets.getRight(density, layoutDirection) + gap * 2
            else -> cardSize.height + insets.getBottom(density) + gap * 2
        }
        val seatEdge = animateFloatAsState(seatTarget, tween(SEAT_MS, easing = FastOutSlowInEasing), label = "seat")
        val fromEnd = form == FormFactor.LANDSCAPE
        val onTap by rememberUpdatedState {
            interactions++
            if (moving && form != FormFactor.TABLETOP) overlay = !overlay
        }

        Spacer(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = {
                            if (BuildConfig.DEV_TOOLS) {
                                devPanel = !devPanel
                                interactions++
                            }
                        },
                    )
                }
                .drawBehind {
                    val tick = frameTick
                    if (tick >= 0L) {
                        vm.scene.seatFromEnd = fromEnd
                        vm.scene.seatEdgePx = seatEdge.value
                        drawIntoCanvas { vm.renderer.render(it.nativeCanvas, size.width.toInt(), size.height.toInt()) }
                    }
                },
        )

        // 반접기에서는 아래쪽 반 화면의 가운데, 그 밖에는 화면 아래에 둔다.
        val lowerHalf = if (form == FormFactor.TABLETOP) {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { (constraints.maxHeight - (hingeY ?: 0f)).toDp() })
                // 앞의 fillMaxWidth·height가 고정 크기를 강제하므로, 안쪽이 최대 폭 제한을 지키도록 풀어 준다.
                .wrapContentSize(Alignment.Center)
        } else {
            Modifier.align(Alignment.BottomCenter)
        }
        // 세로로 접히는 선이 화면 가운데를 지나면 카드를 오른쪽 반에 둬서 버튼이 선 위에 걸치지 않게 한다(명세 5.3).
        val creaseX = fold.verticalX
        val endHalf = if (form == FormFactor.EXPANDED && creaseX != null) {
            with(density) { (constraints.maxWidth - creaseX).toDp() } - CARD_MARGIN * 2
        } else {
            null
        }

        // ── 정차 카드 ──
        val cardModifier = when {
            form == FormFactor.LANDSCAPE -> Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(insets)
                .padding(CARD_MARGIN)
                .width(300.dp)
            endHalf != null -> Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(insets)
                .padding(CARD_MARGIN)
                .width(minOf(420.dp, endHalf))
            else -> lowerHalf
                .windowInsetsPadding(insets)
                .padding(CARD_MARGIN)
                .widthIn(max = if (form == FormFactor.PORTRAIT) 480.dp else 420.dp)
                .fillMaxWidth()
        }
        AnimatedVisibility(
            visible = !moving,
            enter = fadeIn(tween(400, delayMillis = 250)),
            exit = fadeOut(tween(200)),
            modifier = cardModifier.onSizeChanged { cardSize = it },
        ) {
            val paper = Color(vm.paper)
            val terminus = vm.stations.last().name
            when (phase) {
                TripPhase.ARRIVED -> ArrivalCard(terminus, vm.tripMinutes, vm.today, paper, vm::rest, vm::getOff)
                TripPhase.RESTING -> RestCard(
                    terminus, vm.course.title(-vm.direction), vm.minutes, vm.restLeftSec, paper, vm::depart, vm::getOff,
                )
                else -> TicketCard(
                    vm.course, vm.direction, vm.minutes, vm.today, vm.restDone, paper, Color(vm.panel),
                    onMinutes = vm::selectMinutes, onDepart = vm::depart,
                )
            }
        }

        // ── 주행 중 ──
        val paused = phase == TripPhase.PAUSED
        val togglePause = {
            interactions++
            if (paused) vm.resume() else vm.pause()
        }
        val ride = RideInfo(
            vm.stations.map { it.name }, vm.stationMarks, vm.progress, vm.remainingSec,
            vm.stoppedAt, vm.nextStop, vm.nextStopSec, paused,
        )
        if (form == FormFactor.TABLETOP) {
            var dim by remember { mutableStateOf(false) }
            LaunchedEffect(interactions, phase) {
                dim = false
                delay(TABLETOP_DIM_MS)
                dim = true
            }
            val alpha by animateFloatAsState(if (dim) .4f else 1f, tween(800), label = "dim")
            AnimatedVisibility(
                visible = moving,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(200)),
                modifier = lowerHalf
                    .windowInsetsPadding(insets)
                    .padding(CARD_MARGIN)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .graphicsLayer { this.alpha = alpha },
            ) {
                TabletopPanel(ride, togglePause, onClose = { confirmEnd = true })
            }
        } else {
            AnimatedVisibility(
                visible = moving && (overlay || screenReader),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(insets)
                        .padding(CARD_MARGIN),
                ) {
                    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        Capsule(vm.course.title(vm.direction))
                        Spacer(Modifier.weight(1f))
                        CloseButton({
                            interactions++
                            confirmEnd = true
                        })
                    }
                    if (form == FormFactor.PORTRAIT) {
                        StackedPanel(
                            ride, togglePause,
                            Modifier.align(Alignment.BottomCenter).widthIn(max = 480.dp).fillMaxWidth(),
                        )
                    } else {
                        RowPanel(
                            ride, togglePause,
                            Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // ── 안내 ──
        val notice = when {
            backArmed -> "한 번 더 누르면 여행이 끝나요"
            hint && form != FormFactor.TABLETOP -> "화면을 누르면 메뉴가 보여요"
            else -> null
        }
        if (notice != null) Notice(notice, Modifier.align(Alignment.Center))

        // ── 개발용 ──
        if (BuildConfig.DEV_TOOLS && vm.showHud && vm.hud.isNotEmpty()) {
            BasicText(
                vm.hud,
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp),
                modifier = Modifier
                    .windowInsetsPadding(insets)
                    .padding(12.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (BuildConfig.DEV_TOOLS && devPanel) {
            DevPanel(vm, onInteract = { interactions++ }, modifier = Modifier.align(Alignment.TopCenter))
        }

        if (cut.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = cut.value }
                    .background(Color.Black),
            )
        }
    }

    if (confirmEnd) {
        Dialog(onDismissRequest = { confirmEnd = false }) {
            EndTripDialog(
                vm.stations.first().name, Color(vm.paper),
                onKeep = { confirmEnd = false },
                onEnd = {
                    confirmEnd = false
                    vm.endTrip()
                },
            )
        }
    }
}

/**
 * 접히는 선 정보(px, 창 좌표).
 * @param tabletopY 반접기(가로 힌지, 반쯤 열림)면 힌지의 세로 위치.
 * @param verticalX 세로로 접히는 선이 있으면(펼침·북 모드) 그 가로 위치. 카드·버튼을 이 선 위에 두지 않는다.
 */
private class Fold(val tabletopY: Float?, val verticalX: Float?)

@Composable
private fun rememberFold(activity: Activity?): Fold {
    if (activity == null) return NO_FOLD
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info by flow.collectAsState(initial = null)
    val f = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return NO_FOLD
    val horizontal = f.orientation == FoldingFeature.Orientation.HORIZONTAL
    return Fold(
        tabletopY = if (horizontal && f.state == FoldingFeature.State.HALF_OPENED) f.bounds.exactCenterY() else null,
        verticalX = if (!horizontal) f.bounds.exactCenterX() else null,
    )
}

private val NO_FOLD = Fold(null, null)

/** 스크린리더(터치 탐색)가 켜져 있으면 true. 이때는 주행 중 오버레이를 늘 보여 준다(명세 6장). */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val am = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember { mutableStateOf(am?.isTouchExplorationEnabled == true) }
    DisposableEffect(am) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        am?.addTouchExplorationStateChangeListener(listener)
        onDispose { am?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

private val CARD_MARGIN = 16.dp
private const val SEAT_MS = 700
private const val OVERLAY_HIDE_MS = 4_000L
private const val HINT_MS = 4_000L
private const val BACK_ARM_MS = 2_500L
private const val DEV_HIDE_MS = 8_000L
private const val TABLETOP_DIM_MS = 8_000L

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
