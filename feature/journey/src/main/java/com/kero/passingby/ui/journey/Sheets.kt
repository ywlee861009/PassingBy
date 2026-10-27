package com.kero.passingby.ui.journey

import com.kero.passingby.feature.journey.R
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import android.os.SystemClock
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import java.util.Locale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.kero.passingby.experience.JourneyCourse as Course
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.experience.TimeOfDay
import com.kero.passingby.experience.Season
import com.kero.passingby.experience.SeasonChoice
import com.kero.passingby.ui.theme.Pb
import com.kero.passingby.ui.theme.PbType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 본문 스크롤이 맨 위일 때 남는 드래그만 시트 이동으로 이어 받는다. */
private class SheetDragConnection(
    private val scroll: ScrollState,
    private val scope: CoroutineScope,
    private val distanceThreshold: Float,
    private val velocityThreshold: Float,
    private val enabled: () -> Boolean,
    private val onDismiss: () -> Unit,
) : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set
    var height by mutableFloatStateOf(0f)
        private set
    private var returnAnimation: Job? = null

    fun updateHeight(height: Int) {
        this.height = height.toFloat()
        offset = offset.coerceAtMost(this.height)
    }

    fun stopAnimation() {
        returnAnimation?.cancel()
    }

    private fun dragBy(delta: Float): Offset {
        stopAnimation()
        val previous = offset
        offset = (offset + delta).coerceIn(0f, height)
        return Offset(0f, offset - previous)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabled() || source != NestedScrollSource.UserInput) return Offset.Zero
        // 내려온 시트를 되돌리는 움직임도 본문 스크롤보다 먼저 처리한다.
        return if (offset > 0f || (available.y > 0f && scroll.value == 0)) {
            dragBy(available.y)
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // 관성으로 맨 위에 도달한 것만으로 닫히지 않게, 직접 당긴 움직임만 받는다.
        return if (enabled() && source == NestedScrollSource.UserInput && available.y > 0f) {
            dragBy(available.y)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!enabled() || offset <= 0f) return Velocity.Zero
        stopAnimation()
        val farEnough = offset >= minOf(distanceThreshold, height * .25f)
        val dismiss = available.y >= velocityThreshold || (farEnough && available.y > -velocityThreshold)
        if (dismiss) {
            // 당긴 위치를 유지한 채 기존 퇴장 애니메이션으로 이어진다.
            onDismiss()
        } else {
            val animation = scope.launch {
                animate(
                    initialValue = offset,
                    targetValue = 0f,
                    initialVelocity = available.y,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                ) { value, _ -> offset = value.coerceIn(0f, height) }
            }
            returnAnimation = animation
            animation.join()
        }
        return Velocity(0f, available.y)
    }
}

/**
 * 아래에서 올라오는 시트. 바깥(어두운 막)을 누르거나, 본문 맨 위에서 아래로 끌면 닫힌다.
 * 막은 시트와 같이 움직이지 않고 제자리에 있으면서, 시트가 올라온 만큼 짙어진다.
 */
@Composable
fun AnimatedVisibilityScope.BottomSheet(paper: Color, maxHeight: Dp, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // 0 = 다 내려감, 1 = 다 올라옴. 시트 위치와 막 농도가 이 값 하나를 같이 따른다.
    val raised by transition.animateFloat(
        transitionSpec = { tween(if (targetState == EnterExitState.Visible) 300 else 220) },
        label = "sheet",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val canDrag = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
    val enabled by rememberUpdatedState(canDrag)
    val dismiss by rememberUpdatedState(onDismiss)
    val drag = remember(scroll, scope, density) {
        SheetDragConnection(
            scroll, scope,
            distanceThreshold = with(density) { 96.dp.toPx() },
            velocityThreshold = with(density) { 900.dp.toPx() },
            enabled = { enabled },
            onDismiss = { dismiss() },
        )
    }
    LaunchedEffect(canDrag) {
        if (!canDrag) drag.stopAnimation()
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = raised * (1f - drag.offset / drag.height.coerceAtLeast(1f)) }
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.close),
                    onClick = onDismiss,
                ),
        )
        SheetBody(paper, maxHeight, { raised }, scroll, drag, canDrag, content)
    }
}

@Composable
private fun BoxScope.SheetBody(
    paper: Color,
    maxHeight: Dp,
    raised: () -> Float,
    scroll: ScrollState,
    drag: SheetDragConnection,
    canDrag: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .onSizeChanged { drag.updateHeight(it.height) }
            .graphicsLayer { translationY = (1f - raised()) * size.height + raised() * drag.offset }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(paper)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .nestedScroll(drag)
            .verticalScroll(scroll, enabled = canDrag, overscrollEffect = null)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 코스 고르기. 잠긴 코스도 고를 수 있고, 고르면 정차 화면 창으로 미리 본다. */
@Composable
fun CourseSheetContent(
    courses: List<Course>,
    current: Course,
    owns: (Course) -> Boolean,
    priceOf: (Course) -> String?,
    onPick: (Course) -> Unit,
) {
    val res = LocalResources.current
    val uriHandler = LocalUriHandler.current
    BasicText(stringResource(R.string.courses), style = PbType.title.copy(color = Pb.Ink))
    BasicText(stringResource(R.string.courses_hint), style = PbType.body.copy(color = Pb.InkSoft))
    for (c in courses) {
        val selected = c == current
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) Pb.Ink else Pb.Line, RoundedCornerShape(14.dp))
                .selectable(selected, role = Role.RadioButton) { onPick(c) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(courseLabel(c), style = PbType.kicker.copy(color = Pb.InkSoft))
                BasicText(c.title(res, 1), style = PbType.label.copy(color = Pb.Ink))
                BasicText(c.stopsLine(res, 1), style = PbType.kicker.copy(color = Pb.InkSoft))
                c.description?.let { BasicText(stringResource(it), style = PbType.kicker.copy(color = Pb.InkSoft)) }
                c.distanceLightYears?.let { distance ->
                    BasicText(stringResource(R.string.system_distance, String.format(Locale.US, if (distance < 10) "%.2f" else "%.1f", distance)), style = PbType.kicker.copy(color = Pb.InkSoft))
                }
                if (selected) {
                    c.researchNote?.let { BasicText(stringResource(it), style = PbType.kicker.copy(color = Pb.InkSoft)) }
                    for (reference in c.references) BasicText(
                        reference.title + " ↗", style = PbType.kicker.copy(color = Pb.Accent),
                        modifier = Modifier.clickable { runCatching { uriHandler.openUri(reference.url) } }.padding(vertical = 8.dp),
                    )
                }
            }
            Tag(
                when {
                    c.free -> stringResource(R.string.free)
                    owns(c) -> stringResource(R.string.unlocked)
                    else -> stringResource(R.string.locked_price, priceOf(c) ?: stringResource(R.string.price_pending))
                },
                strong = !owns(c),
            )
        }
    }
}

/** 설정: 창밖 시간대·날씨, 소리, 화면, 언어, 정보 보내기, 구매 복원, Play 스토어. */
@Composable
fun SettingsSheetContent(
    time: TimeOfDay?,
    sound: Boolean,
    soundLevel: Int,
    ledge: Boolean,
    overlay: Boolean,
    power: Boolean,
    language: AppLanguage,
    telemetry: Boolean,
    devUnlock: Boolean,
    restoringPurchases: Boolean,
    developerTools: Boolean,
    season: Season,
    seasonChoice: SeasonChoice,
    onSeason: (SeasonChoice) -> Unit,
    onTime: (TimeOfDay?) -> Unit,
    onSound: (Boolean) -> Unit,
    onSoundLevel: (Int) -> Unit,
    onLedge: (Boolean) -> Unit,
    onOverlay: (Boolean) -> Unit,
    onPower: (Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onTelemetry: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onDevUnlock: () -> Unit,
    onShare: () -> Unit,
    onPlayStore: () -> Unit,
    onLicenses: () -> Unit,
    onPrivacy: () -> Unit,
    supportsSeasons: Boolean = true,
    supportsTimeOfDay: Boolean = true,
    modeMenu: Boolean = false,
    previewUnlocked: Boolean = false,
    canSwitch: Boolean = true,
    onModes: () -> Unit = {},
    onLockPreview: () -> Unit = {},
    onSecretEntry: (() -> Unit)? = null,
    flyby: Boolean = false,
) {
    BasicText(stringResource(R.string.settings), style = PbType.title.copy(color = Pb.Ink))

    if (modeMenu) QuietButton(stringResource(R.string.travel_modes), onModes, Modifier.fillMaxWidth())
    if (supportsTimeOfDay) {
    SectionTitle(stringResource(R.string.time_of_day))
    BasicText(stringResource(R.string.time_of_day_desc), style = PbType.kicker.copy(color = Pb.InkSoft))
    val times = listOf(null, TimeOfDay.DAY, TimeOfDay.NIGHT)
    for (row in times.chunked(3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in row) {
                Choice(stringResource(t?.label ?: R.string.automatic), t == time) { onTime(t) }
            }
        }
    }

    }
    if (supportsSeasons) {
    SectionTitle(stringResource(R.string.season))
    BasicText(stringResource(R.string.season_desc), style = PbType.kicker.copy(color = Pb.InkSoft))
    for (row in SeasonChoice.entries.chunked(3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in row) Choice(stringResource(option.label), option == seasonChoice) { onSeason(option) }
        }
    }
    if (seasonChoice == SeasonChoice.RANDOM) {
        BasicText(
            stringResource(R.string.season_random_desc, stringResource(season.label)),
            style = PbType.kicker.copy(color = Pb.InkSoft),
        )
    }

    }
    SectionTitle(stringResource(R.string.sound))
    Toggle(stringResource(R.string.ambient), stringResource(when {
        flyby -> R.string.space_ambient_desc
        supportsSeasons -> R.string.ambient_desc
        else -> R.string.voyage_ambient_desc
    }), sound, onSound)
    if (sound) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, label) in listOf(R.string.volume_low, R.string.volume_mid, R.string.volume_high).withIndex()) {
                Choice(stringResource(label), i == soundLevel) { onSoundLevel(i) }
            }
        }
    }

    SectionTitle(stringResource(R.string.display))
    Toggle(stringResource(R.string.ledge), stringResource(if (supportsSeasons) R.string.ledge_desc else R.string.voyage_ledge_desc), ledge, onLedge)
    Toggle(stringResource(R.string.overlay_always), stringResource(R.string.overlay_always_desc), overlay, onOverlay)
    Toggle(stringResource(R.string.power_save), stringResource(R.string.power_save_desc), power, onPower)

    // 언어 이름은 그 언어로 쓴다. 잘못 골라 읽을 수 없게 돼도 되돌릴 수 있게.
    SectionTitle(stringResource(R.string.language))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in AppLanguage.entries) {
            val label = when (l) {
                AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                AppLanguage.KOREAN -> "한국어"
                AppLanguage.ENGLISH -> "English"
            }
            Choice(label, l == language) { onLanguage(l) }
        }
    }

    SectionTitle(stringResource(R.string.data_section))
    Toggle(stringResource(R.string.telemetry), stringResource(R.string.telemetry_desc), telemetry, onTelemetry)

    SectionTitle(stringResource(R.string.purchases))
    QuietButton(
        stringResource(if (restoringPurchases) R.string.purchases_checking else R.string.restore_purchases),
        onRestore, Modifier.fillMaxWidth(), color = if (restoringPurchases) Pb.InkSoft else Pb.Ink,
        enabled = !restoringPurchases,
    )
    QuietButton(stringResource(R.string.share_app), onShare, Modifier.fillMaxWidth())
    QuietButton(stringResource(R.string.play_store), onPlayStore, Modifier.fillMaxWidth())
    QuietButton(stringResource(R.string.licenses), onLicenses, Modifier.fillMaxWidth())
    QuietButton(stringResource(R.string.privacy), onPrivacy, Modifier.fillMaxWidth())
    // 개발용 줄은 출시 빌드에 나오지 않으므로 번역하지 않는다.
    if (developerTools) {
        Toggle("개발용: 모든 코스 열기", "결제 상품 등록 전 확인용. 출시 빌드에는 나오지 않아요.", devUnlock) { onDevUnlock() }
    }
    if (previewUnlocked) QuietButton(stringResource(R.string.preview_lock), onLockPreview, Modifier.fillMaxWidth(), enabled = canSwitch)
    var taps by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    BasicText(
        "Passing By", style = PbType.kicker.copy(color = Pb.InkSoft),
        modifier = Modifier.fillMaxWidth().testTag("secret-entry").then(
            if (onSecretEntry != null) Modifier.clickable {
                val now = SystemClock.elapsedRealtime()
                taps = if (now - lastTap <= 1_200L) taps + 1 else 1
                lastTap = now
                if (taps >= 7) { taps = 0; onSecretEntry() }
            } else Modifier,
        ).padding(vertical = 16.dp),
    )
    Spacer(Modifier.height(4.dp))
}

/** 옵션 중 하나를 고르는 둥근 버튼. Row 안에서 같은 폭으로 나눠 쓴다. */
@Composable
private fun RowScope.Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(44.dp)
            .clip(CircleShape)
            .background(if (on) Pb.Ink else Color.Transparent)
            .border(1.dp, if (on) Pb.Ink else Pb.Line, CircleShape)
            .selectable(on, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = PbType.body.copy(color = if (on) Pb.Paper else Pb.Ink))
    }
}

@Composable
private fun courseLabel(c: Course): String = stringResource(R.string.course_label, c.number, stringResource(c.region))

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(4.dp))
    BasicText(text, style = PbType.kicker.copy(color = Pb.InkSoft))
}

@Composable
private fun Tag(text: String, strong: Boolean) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (strong) Pb.Ink else Color.Transparent)
            .border(1.dp, if (strong) Pb.Ink else Pb.Line, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        BasicText(text, style = PbType.kicker.copy(color = if (strong) Pb.Paper else Pb.Ink))
    }
}

/** 켜고 끄는 줄. 줄 전체를 눌러도 바뀐다. */
@Composable
fun Toggle(label: String, description: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(label, style = PbType.label.copy(color = Pb.Ink))
            BasicText(description, style = PbType.kicker.copy(color = Pb.InkSoft))
        }
        // 켜짐은 색과 손잡이 위치 둘 다로 구분한다(색에만 기대지 않음).
        Box(
            Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(CircleShape)
                .background(if (value) Pb.Accent else Pb.Line),
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(20.dp)
                    .offset(x = if (value) 18.dp else 0.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** 여행 수첩: 오늘·지금까지의 기록과 코스마다 들른 역 도장. */
@Composable
fun JournalSheetContent(
    courses: List<Course>,
    today: Pair<Int, Int>,
    lifetime: Pair<Int, Int>,
    visited: Set<String>,
    completions: (Course) -> Int,
    owns: (Course) -> Boolean,
) {
    BasicText(stringResource(R.string.journal), style = PbType.title.copy(color = Pb.Ink))
    BasicText(
        pluralStringResource(R.plurals.today_line, today.first, today.first, today.second) + "   " +
            pluralStringResource(R.plurals.lifetime_line, lifetime.first, lifetime.first, hours(lifetime.second)),
        style = PbType.body.copy(color = Pb.InkSoft),
    )
    for (c in courses) {
        val done = completions(c)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Pb.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    courseLabel(c),
                    style = PbType.label.copy(color = Pb.Ink),
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    when {
                        done > 0 -> pluralStringResource(R.plurals.completed, done, done)
                        owns(c) -> stringResource(R.string.not_completed)
                        else -> stringResource(R.string.locked)
                    },
                    style = PbType.kicker.copy(color = Pb.InkSoft),
                )
            }
            for (stops in c.stations.withIndex().toList().chunked(if (c.stopsEnRoute) c.stations.size.coerceAtLeast(1) else 3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for ((i, st) in stops) Stamp(stringResource(st.name), "${c.id}:$i" in visited)
                }
            }
        }
    }
}

/** 역 도장. 들른 역은 채운 원, 안 들른 역은 테두리만(색에만 기대지 않음). */
@Composable
private fun Stamp(name: String, on: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (on) Pb.Accent else Color.Transparent)
                .border(1.5.dp, if (on) Pb.Accent else Pb.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) BasicText(name.take(1), style = PbType.kicker.copy(color = Pb.OnAccent))
        }
        BasicText(name, style = PbType.kicker.copy(color = if (on) Pb.Ink else Pb.InkSoft))
    }
}

/** 오픈소스 라이선스(글꼴). [licenses]는 (제목, 원문) 목록. */
@Composable
fun LicenseSheetContent(licenses: List<Pair<String, String>>) {
    BasicText(stringResource(R.string.licenses), style = PbType.title.copy(color = Pb.Ink))
    for ((title, text) in licenses) {
        Spacer(Modifier.height(4.dp))
        BasicText(title, style = PbType.label.copy(color = Pb.Ink))
        BasicText(text, style = PbType.kicker.copy(color = Pb.InkSoft))
    }
}

@Composable
private fun hours(min: Int): String =
    if (min >= 60) stringResource(R.string.hours_minutes, min / 60, min % 60) else stringResource(R.string.minutes, min)
