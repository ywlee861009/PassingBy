package com.kero.passingby.ui.journey

import com.kero.passingby.feature.journey.R
import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.kero.passingby.journey.SessionPhase
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.telemetry.Telemetry
import com.kero.passingby.ui.debug.DevPanel
import com.kero.passingby.ui.theme.PbType
import kotlinx.coroutines.delay
import kotlin.math.abs

/** 화면 크기별 레이아웃(명세 5.3). 화면 방향이 아니라 창 크기로 정한다. */
enum class FormFactor { PORTRAIT, LANDSCAPE, EXPANDED, TABLETOP }

/**
 * 여행 화면. 앱의 유일한 화면이다. 출발·여행·도착·휴식을 화면 전환 없이 열차 상태로 보여 준다(명세 5.0).
 *
 * - 정차 중(DOCKED·ARRIVED·RESTING): 벽 패널이 올라와 창 일부를 가리고, 그 위에 카드를 놓는다.
 * - 주행 중(RUNNING·PAUSED): 지역을 지날 때 작은 안내가 잠깐 뜬다. 화면을 누르면 조작 오버레이가 나타났다가 4초 뒤 사라진다.
 * - 반접기(테이블탑): 위쪽 반은 창, 아래쪽 반은 늘 보이는 정보판.
 */
@Composable
fun JourneyScreen(vm: JourneyViewModel, onNotificationPermissionGranted: () -> Unit = {}) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val activity = LocalActivity.current
    val context = LocalContext.current
    val res = LocalResources.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(vm.cap60) {
        activity?.let { applyRefreshCap(it, vm.cap60) }
    }

    // 절전 모드(설정 또는 기기 절전)에서는 1초에 30번만 장면을 갱신·그린다(명세 11장).
    val systemSaver = rememberSystemPowerSave()
    val saving by rememberUpdatedState(vm.powerSave || systemSaver)

    // 매 프레임 값이 바뀌는 상태. 그리기 단계에서만 읽으므로 재구성은 일어나지 않는다.
    var frameTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    frameTick = now
                } else if (!saving || now - last >= SAVER_FRAME_NANOS) {
                    vm.onFrame(now - last, view.display?.refreshRate ?: 60f)
                    last = now
                    frameTick = now
                }
            }
        }
    }

    // 첫 출발 때 한 번만 알림 권한을 묻는다(Android 13 이상). 거절해도 여행은 그대로 출발한다.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Telemetry.event("notification_permission", "granted" to granted)
        if (granted) onNotificationPermissionGranted()
    }
    val depart = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vm.notificationAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            vm.markNotificationAsked()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.depart()
    }

    val fold = rememberFold(activity)
    val hingeY = fold.tabletopY
    val screenReader = rememberTouchExploration()
    val phase = vm.phase
    val moving = phase == SessionPhase.RUNNING || phase == SessionPhase.PAUSED

    var overlay by remember { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    var devPanel by remember { mutableStateOf(false) }
    var secretCode by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var backArmed by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }
    // 언어를 바꾸면 화면을 다시 만든다. 그때 설정 시트가 닫히지 않게 저장해 둔다.
    var sheet by rememberSaveable { mutableStateOf(SheetKind.NONE) }
    var language by remember { mutableStateOf(AppLanguage.current(context)) }
    var notice by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(vm.messageTick) {
        val m = vm.message ?: return@LaunchedEffect
        notice = m
        delay(MESSAGE_MS)
        notice = null
    }

    LaunchedEffect(overlay, interactions, phase) {
        if (overlay && phase == SessionPhase.RUNNING) {
            delay(OVERLAY_HIDE_MS)
            overlay = false
        }
    }
    LaunchedEffect(phase) {
        overlay = phase == SessionPhase.PAUSED
        if (phase == SessionPhase.RUNNING && !vm.hintShown) {
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
    BackHandler(enabled = sheet != SheetKind.NONE) { sheet = SheetKind.NONE }
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
                            if (vm.options.developerTools) {
                                devPanel = !devPanel
                                interactions++
                            }
                        },
                    )
                }
                .drawBehind {
                    val tick = frameTick
                    if (tick >= 0L) {
                        vm.scene.setViewport(fromEnd, seatEdge.value)
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
            val terminus = stringResource(vm.stations.last().name)
            when (phase) {
                SessionPhase.ARRIVED -> ArrivalCard(
                    terminus, vm.tripMinutes, vm.today, paper, vm::rest, vm::getOff,
                    onShare = { shareApp(context, "arrival") },
                )
                SessionPhase.RESTING -> RestCard(
                    terminus, vm.course.title(res, -vm.direction), vm.minutes, vm.restLeftSec, paper, depart, vm::getOff,
                )
                else -> TicketCard(
                    vm.course, vm.direction, vm.minutes, vm.today, vm.restDone,
                    owned = vm.owns(vm.course), price = vm.priceOf(vm.course),
                    paper = paper, panel = Color(vm.panel),
                    onMinutes = vm::selectMinutes, onDepart = depart,
                    onBuy = { activity?.let { vm.buy(it) } },
                    onCourses = { sheet = SheetKind.COURSES },
                    onSettings = { sheet = SheetKind.SETTINGS },
                    onJournal = { sheet = SheetKind.JOURNAL },
                )
            }
        }

        // ── 주행 중 ──
        val paused = phase == SessionPhase.PAUSED
        val togglePause = {
            interactions++
            if (paused) vm.resume() else vm.pause()
        }
        val ride = RideInfo(
            vm.stations.map { stringResource(it.name) }, vm.stationMarks, vm.progress, vm.remainingSec,
            vm.stoppedAt, vm.nextStop, vm.nextStopSec, paused, vm.soundOn,
            stopsEnRoute = vm.course.stopsEnRoute,
            flyby = vm.course.flyby,
            locationText = if (paused) null else routeCaptionText(vm.routeCaption),
        )
        val toggleSound = {
            interactions++
            vm.toggleSound()
        }
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
                TabletopPanel(ride, togglePause, toggleSound, onClose = { confirmEnd = true })
            }
        } else {
            AnimatedVisibility(
                visible = moving && (overlay || screenReader || vm.overlayAlways),
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
                        Capsule(vm.course.title(res, vm.direction))
                        Spacer(Modifier.weight(1f))
                        CloseButton({
                            interactions++
                            confirmEnd = true
                        })
                    }
                    if (form == FormFactor.PORTRAIT) {
                        StackedPanel(
                            ride, togglePause, toggleSound,
                            Modifier.align(Alignment.BottomCenter).widthIn(max = 480.dp).fillMaxWidth(),
                        )
                    } else {
                        RowPanel(
                            ride, togglePause, toggleSound,
                            Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // ── 실제 노선 위 현재 지역. 조작 UI·창턱 안내와 겹치지 않는 작은 안내. ──
        RoutePassingNotice(
            caption = vm.routeCaption,
            enabled = phase == SessionPhase.RUNNING && form != FormFactor.TABLETOP &&
                !(overlay || vm.overlayAlways || screenReader || devPanel || hint || backArmed) &&
                sheet == SheetKind.NONE && notice == null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(insets)
                .padding(horizontal = 24.dp)
                .padding(bottom = if (vm.ledgeInfo) 36.dp else 12.dp),
        )

        // ── 창턱 안내: 창 아래 벽에 다음 역까지 남은 분(명세 6장) ──
        if (vm.ledgeInfo && moving && form != FormFactor.TABLETOP && !(overlay || vm.overlayAlways || screenReader)) {
            val next = stringResource(vm.stations[vm.nextStop].name)
            val mins = (vm.nextStopSec + 59) / 60
            BasicText(
                when {
                    vm.stoppedAt >= 0 -> stringResource(R.string.stopped_at, stringResource(vm.stations[vm.stoppedAt].name))
                    vm.nextStop == vm.stations.size - 1 -> stringResource(R.string.minutes_to_final, next, mins)
                    else -> stringResource(R.string.minutes_to, next, mins)
                },
                style = PbType.body.copy(color = Color(0x99E9E1D6)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(insets)
                    .padding(bottom = 6.dp),
            )
        }

        // ── 안내 ──
        val message = when {
            backArmed -> R.string.back_again_to_end
            notice != null -> notice
            hint && form != FormFactor.TABLETOP -> R.string.tap_for_menu
            else -> null
        }
        if (message != null) Notice(stringResource(message), Modifier.align(Alignment.Center))

        // ── 개발용 ──
        if (vm.options.developerTools && vm.showHud && vm.hud.isNotEmpty()) {
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
        if (vm.options.developerTools && devPanel) {
            DevPanel(vm, onInteract = { interactions++ }, modifier = Modifier.align(Alignment.TopCenter))
        }

        // ── 시트 ── (올라오고 내려가는 움직임은 BottomSheet가 맡는다)
        AnimatedVisibility(
            visible = sheet != SheetKind.NONE,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize(),
        ) {
            val shown = remember { mutableStateOf(sheet) }
            val licenses = remember {
                fun read(name: String) = runCatching {
                    context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
                }.getOrDefault("")
                listOf(
                    "Pretendard — SIL Open Font License 1.1" to read("pretendard.txt"),
                    "Gowun Batang(고운바탕) — SIL Open Font License 1.1" to read("gowun-batang.txt"),
                )
            }
            if (sheet != SheetKind.NONE) shown.value = sheet
            // 시트 종류가 바뀌면 스크롤 위치를 처음부터 다시 잡는다.
            key(shown.value) {
            BottomSheet(Color(vm.paper), maxHeight * .88f, onDismiss = { sheet = SheetKind.NONE }) {
                when (shown.value) {
                    SheetKind.COURSES -> CourseSheetContent(vm.courses, vm.course, vm::owns, vm::priceOf) {
                        vm.selectCourse(it)
                        sheet = SheetKind.NONE
                    }
                    SheetKind.JOURNAL -> JournalSheetContent(vm.journalCourses, vm.today, vm.lifetime, vm.visited, vm::completions, vm::owns)
                    SheetKind.LICENSES -> LicenseSheetContent(licenses)
                    SheetKind.MODES -> ModeSheetContent(vm.availableModes, vm.journeyMode, vm.phase == SessionPhase.DOCKED) {
                        if (vm.selectJourneyMode(it)) sheet = SheetKind.NONE
                    }
                    else -> SettingsSheetContent(
                        vm.fixedTime, vm.soundOn, vm.soundLevel, vm.ledgeInfo, vm.overlayAlways, vm.powerSave, language,
                        vm.telemetryOn,
                        vm.devUnlockAll, vm.restoringPurchases,
                        developerTools = vm.options.developerTools,
                        season = vm.season, seasonChoice = vm.seasonChoice, onSeason = vm::selectSeasonChoice,
                        supportsSeasons = vm.course.supportsSeasons,
                        supportsTimeOfDay = vm.course.supportsTimeOfDay,
                        flyby = vm.course.flyby,
                        modeMenu = vm.showModeMenu,
                        previewUnlocked = vm.previewUnlocked,
                        canSwitch = vm.phase == SessionPhase.DOCKED,
                        onModes = { sheet = SheetKind.MODES },
                        onLockPreview = vm::lockPreviewModes,
                        onSecretEntry = if (vm.options.modeAccess.codeEnabled) ({ secretCode = true }) else null,
                        onTime = vm::selectTime,
                        onSound = { vm.toggleSound() }, onSoundLevel = vm::selectSoundLevel,
                        onLedge = vm::setLedge, onOverlay = vm::setOverlay, onPower = vm::setPower,
                        onLanguage = { l ->
                            if (l != language) {
                                language = l
                                Telemetry.property("app_language", l.name.lowercase())
                                activity?.let { AppLanguage.set(it, l) }
                            }
                        },
                        onTelemetry = vm::setTelemetry,
                        onRestore = vm::restorePurchases, onDevUnlock = vm::toggleDevUnlock,
                        onShare = { shareApp(context, "settings") },
                        onPlayStore = { openPlayStore(context, uriHandler) },
                        onLicenses = { sheet = SheetKind.LICENSES },
                        // 브라우저가 없는 기기에서는 열 앱이 없어 예외가 난다. 그때는 아무 일도 하지 않는다.
                        onPrivacy = { runCatching { uriHandler.openUri(PRIVACY_URL) } },
                    )
                }
            }
            }
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

    if (secretCode) {
        Dialog(onDismissRequest = { secretCode = false }) {
            SecretCodeDialog(
                onSubmit = { code ->
                    vm.unlockModes(code).also { success ->
                        if (success) { secretCode = false; sheet = SheetKind.MODES }
                    }
                },
                onDismiss = { secretCode = false },
            )
        }
    }
    if (confirmEnd) {
        Dialog(onDismissRequest = { confirmEnd = false }) {
            EndTripDialog(
                stringResource(vm.stations.first().name), Color(vm.paper),
                rail = vm.course.stopsEnRoute,
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

/** 기기 절전 모드가 켜져 있으면 true. 켜고 끌 때 방송을 받아 갱신한다. */
@Composable
private fun rememberSystemPowerSave(): Boolean {
    val context = LocalContext.current
    val pm = remember(context) { context.getSystemService(PowerManager::class.java) }
    var on by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                on = pm?.isPowerSaveMode == true
            }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return on
}

private enum class SheetKind { NONE, COURSES, SETTINGS, JOURNAL, LICENSES, MODES }

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

/** policies 저장소에 게시한 처리방침. 주소를 바꾸면 Play Console에 등록한 주소도 함께 바꾼다. */
private const val PRIVACY_URL = "https://ywlee861009.github.io/policies/passingby/privacy/"

/** Play 스토어 앱이 있으면 앱으로, 없으면 브라우저로 이 앱 페이지를 연다. 둘 다 없으면 아무 일도 하지 않는다. */
private fun openPlayStore(context: Context, uriHandler: UriHandler) {
    val id = context.packageName
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).setPackage("com.android.vending")
    runCatching { context.startActivity(app) }
        .recoverCatching { uriHandler.openUri(playStoreUrl(context.packageName)) }
}

private fun playStoreUrl(applicationId: String) = "https://play.google.com/store/apps/details?id=$applicationId"

private const val KAKAOTALK = "com.kakao.talk"

/**
 * 친구에게 이 앱의 Play 스토어 주소를 보낸다. 카카오톡이 있으면 카카오톡의 친구 고르기 화면을 바로 열고,
 * 없으면 시스템 공유 시트를 띄운다. 설치 여부는 미리 묻지 않고 열어 본다(그래서 <queries>가 필요 없다).
 * 통계는 공유 화면을 연 것까지만 센다. 실제로 보냈는지는 앱이 알 수 없다. [from]은 버튼 위치.
 */
private fun shareApp(context: Context, from: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_app_text, playStoreUrl(context.packageName)))
    val kakao = runCatching { context.startActivity(Intent(send).setPackage(KAKAOTALK)) }.isSuccess
    val opened = kakao || runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_app)))
    }.isSuccess
    if (opened) Telemetry.event("share", "content_type" to "app", "method" to if (kakao) "kakaotalk" else "chooser", "from" to from)
}
private val CARD_MARGIN = 16.dp
private const val SAVER_FRAME_NANOS = 31_000_000L
private const val MESSAGE_MS = 3_200L
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
