package com.kero.passingby.ui.journey

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kero.passingby.BuildConfig
import com.kero.passingby.R
import com.kero.passingby.content.Course
import com.kero.passingby.locale.AppLanguage
import com.kero.passingby.scene.Weather
import com.kero.passingby.ui.theme.Pb
import com.kero.passingby.ui.theme.PbType

/**
 * 아래에서 올라오는 시트. 바깥(어두운 막)을 누르면 닫힌다.
 * 막은 시트와 같이 움직이지 않고 제자리에 있으면서, 시트가 올라온 만큼 짙어진다.
 */
@Composable
fun AnimatedVisibilityScope.BottomSheet(paper: Color, maxHeight: Dp, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // 0 = 다 내려감, 1 = 다 올라옴. 시트 위치와 막 농도가 이 값 하나를 같이 따른다.
    val raised by transition.animateFloat(
        transitionSpec = { tween(if (targetState == EnterExitState.Visible) 300 else 220) },
        label = "sheet",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = raised }
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.close),
                    onClick = onDismiss,
                ),
        )
        SheetBody(paper, maxHeight, { raised }, content)
    }
}

@Composable
private fun BoxScope.SheetBody(paper: Color, maxHeight: Dp, raised: () -> Float, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .graphicsLayer { translationY = (1f - raised()) * size.height }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(paper)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 코스 고르기. 잠긴 코스도 고를 수 있고, 고르면 정차 화면 창으로 미리 본다. */
@Composable
fun CourseSheetContent(
    current: Course,
    owns: (Course) -> Boolean,
    priceOf: (Course) -> String?,
    onPick: (Course) -> Unit,
) {
    val res = LocalResources.current
    BasicText(stringResource(R.string.courses), style = PbType.title.copy(color = Pb.Ink))
    BasicText(stringResource(R.string.courses_hint), style = PbType.body.copy(color = Pb.InkSoft))
    for (c in Course.ALL) {
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

/** 설정: 창밖 날씨, 소리, 화면, 언어, 정보 보내기, 코스 구매, Play 스토어. */
@Composable
fun SettingsSheetContent(
    weather: Weather?,
    sound: Boolean,
    soundLevel: Int,
    ledge: Boolean,
    overlay: Boolean,
    power: Boolean,
    language: AppLanguage,
    telemetry: Boolean,
    owns: (Course) -> Boolean,
    priceOf: (Course) -> String?,
    devUnlock: Boolean,
    onWeather: (Weather?) -> Unit,
    onSound: (Boolean) -> Unit,
    onSoundLevel: (Int) -> Unit,
    onLedge: (Boolean) -> Unit,
    onOverlay: (Boolean) -> Unit,
    onPower: (Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onTelemetry: (Boolean) -> Unit,
    onBuy: (Course) -> Unit,
    onRestore: () -> Unit,
    onDevUnlock: () -> Unit,
    onPlayStore: () -> Unit,
    onLicenses: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val res = LocalResources.current
    BasicText(stringResource(R.string.settings), style = PbType.title.copy(color = Pb.Ink))

    SectionTitle(stringResource(R.string.weather_outside))
    val options = listOf<Weather?>(null) + Weather.entries
    for (row in options.chunked(3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in row) {
                Choice(stringResource(w?.label ?: R.string.random), w == weather) { onWeather(w) }
            }
        }
    }

    SectionTitle(stringResource(R.string.sound))
    Toggle(stringResource(R.string.ambient), stringResource(R.string.ambient_desc), sound, onSound)
    if (sound) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, label) in listOf(R.string.volume_low, R.string.volume_mid, R.string.volume_high).withIndex()) {
                Choice(stringResource(label), i == soundLevel) { onSoundLevel(i) }
            }
        }
    }

    SectionTitle(stringResource(R.string.display))
    Toggle(stringResource(R.string.ledge), stringResource(R.string.ledge_desc), ledge, onLedge)
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

    SectionTitle(stringResource(R.string.buy_courses))
    for (c in Course.ALL.filter { !it.free }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(courseLabel(c), style = PbType.label.copy(color = Pb.Ink))
                BasicText(c.title(res, 1), style = PbType.kicker.copy(color = Pb.InkSoft))
            }
            if (owns(c)) {
                Tag(stringResource(R.string.unlocked), strong = false)
            } else {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(CircleShape)
                        .background(Pb.Accent)
                        .clickable(role = Role.Button) { onBuy(c) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(priceOf(c) ?: stringResource(R.string.unlock), style = PbType.label.copy(color = Pb.OnAccent))
                }
            }
        }
    }
    QuietButton(stringResource(R.string.restore_purchases), onRestore, Modifier.fillMaxWidth(), color = Pb.Ink)
    QuietButton(stringResource(R.string.play_store), onPlayStore, Modifier.fillMaxWidth())
    QuietButton(stringResource(R.string.licenses), onLicenses, Modifier.fillMaxWidth())
    QuietButton(stringResource(R.string.privacy), onPrivacy, Modifier.fillMaxWidth())
    // 개발용 줄은 출시 빌드에 나오지 않으므로 번역하지 않는다.
    if (BuildConfig.DEV_TOOLS) {
        Toggle("개발용: 모든 코스 열기", "결제 상품 등록 전 확인용. 출시 빌드에는 나오지 않아요.", devUnlock) { onDevUnlock() }
    }
    Spacer(Modifier.height(4.dp))
}

/** 셋 중 하나를 고르는 둥근 버튼. Row 안에서 같은 폭으로 나눠 쓴다. */
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
    for (c in Course.ALL) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for ((i, st) in c.stations.withIndex()) {
                    Stamp(stringResource(st.name), "${c.id}:$i" in visited)
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
