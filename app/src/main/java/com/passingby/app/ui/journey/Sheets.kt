package com.passingby.app.ui.journey

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.passingby.app.BuildConfig
import com.passingby.app.content.Course
import com.passingby.app.scene.Weather
import com.passingby.app.ui.theme.Pb
import com.passingby.app.ui.theme.PbType

/** 아래에서 올라오는 시트. 바깥(어두운 막)을 누르면 닫힌다. */
@Composable
fun BottomSheet(paper: Color, maxHeight: Dp, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "닫기",
                    onClick = onDismiss,
                ),
        )
        SheetBody(paper, maxHeight, content)
    }
}

@Composable
private fun BoxScope.SheetBody(paper: Color, maxHeight: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
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
    BasicText("코스", style = PbType.title.copy(color = Pb.Ink))
    BasicText("잠긴 코스도 골라서 창밖을 미리 볼 수 있어요.", style = PbType.body.copy(color = Pb.InkSoft))
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
                BasicText("${c.number}코스 · ${c.region}", style = PbType.kicker.copy(color = Pb.InkSoft))
                BasicText(c.title(1), style = PbType.label.copy(color = Pb.Ink))
                BasicText(c.stopsLine(1), style = PbType.kicker.copy(color = Pb.InkSoft))
            }
            Tag(
                when {
                    c.free -> "무료"
                    owns(c) -> "열림"
                    else -> "잠김 · ${priceOf(c) ?: "가격 준비 중"}"
                },
                strong = !owns(c),
            )
        }
    }
}

/** 설정: 창밖 날씨, 화면, 코스 구매. */
@Composable
fun SettingsSheetContent(
    weather: Weather?,
    sound: Boolean,
    soundLevel: Int,
    ledge: Boolean,
    overlay: Boolean,
    power: Boolean,
    owns: (Course) -> Boolean,
    priceOf: (Course) -> String?,
    devUnlock: Boolean,
    onWeather: (Weather?) -> Unit,
    onSound: (Boolean) -> Unit,
    onSoundLevel: (Int) -> Unit,
    onLedge: (Boolean) -> Unit,
    onOverlay: (Boolean) -> Unit,
    onPower: (Boolean) -> Unit,
    onBuy: (Course) -> Unit,
    onRestore: () -> Unit,
    onDevUnlock: () -> Unit,
    onLicenses: () -> Unit,
) {
    BasicText("설정", style = PbType.title.copy(color = Pb.Ink))

    SectionTitle("창밖 날씨")
    val options = listOf<Weather?>(null) + Weather.entries
    for (row in options.chunked(3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in row) {
                val on = w == weather
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(if (on) Pb.Ink else Color.Transparent)
                        .border(1.dp, if (on) Pb.Ink else Pb.Line, CircleShape)
                        .selectable(on, role = Role.RadioButton) { onWeather(w) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(w?.label ?: "무작위", style = PbType.body.copy(color = if (on) Pb.Paper else Pb.Ink))
                }
            }
        }
    }

    SectionTitle("소리")
    Toggle("환경음", "레일·바람·빗소리를 들려줘요. 다른 앱 음악을 멈추지 않아요. 앱이 화면에 있을 때만 나요.", sound, onSound)
    if (sound) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, label) in listOf("작게", "보통", "크게").withIndex()) {
                val on = i == soundLevel
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(if (on) Pb.Ink else Color.Transparent)
                        .border(1.dp, if (on) Pb.Ink else Pb.Line, CircleShape)
                        .selectable(on, role = Role.RadioButton) { onSoundLevel(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = PbType.body.copy(color = if (on) Pb.Paper else Pb.Ink))
                }
            }
        }
    }

    SectionTitle("화면")
    Toggle("창턱 안내", "창 아래 벽에 다음 역까지 남은 분을 늘 어둡게 표시해요. 거치해 두고 쓸 때 좋아요.", ledge, onLedge)
    Toggle("메뉴 늘 보이기", "달리는 동안에도 남은 시간과 진행 바를 숨기지 않아요.", overlay, onOverlay)
    Toggle("절전 모드", "화면을 1초에 30번만 그려 배터리와 발열을 줄여요. 기기 절전 모드에서는 저절로 켜져요.", power, onPower)

    SectionTitle("코스 구매")
    for (c in Course.ALL.filter { !it.free }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("${c.number}코스 · ${c.region}", style = PbType.label.copy(color = Pb.Ink))
                BasicText(c.title(1), style = PbType.kicker.copy(color = Pb.InkSoft))
            }
            if (owns(c)) {
                Tag("열림", strong = false)
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
                    BasicText(priceOf(c) ?: "열기", style = PbType.label.copy(color = Pb.OnAccent))
                }
            }
        }
    }
    QuietButton("구매 복원", onRestore, Modifier.fillMaxWidth(), color = Pb.Ink)
    QuietButton("오픈소스 라이선스", onLicenses, Modifier.fillMaxWidth())
    if (BuildConfig.DEV_TOOLS) {
        Toggle("개발용: 모든 코스 열기", "결제 상품 등록 전 확인용. 출시 빌드에는 나오지 않아요.", devUnlock) { onDevUnlock() }
    }
    Spacer(Modifier.height(4.dp))
}

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
    BasicText("여행 수첩", style = PbType.title.copy(color = Pb.Ink))
    BasicText(
        "오늘 ${today.first}회 · ${today.second}분   지금까지 ${lifetime.first}회 · ${hours(lifetime.second)}",
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
                    "${c.number}코스 · ${c.region}",
                    style = PbType.label.copy(color = Pb.Ink),
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    when {
                        done > 0 -> "완주 ${done}회"
                        owns(c) -> "아직 완주 전"
                        else -> "잠김"
                    },
                    style = PbType.kicker.copy(color = Pb.InkSoft),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for ((i, st) in c.stations.withIndex()) {
                    Stamp(st.name, "${c.id}:$i" in visited)
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

/** 오픈소스 라이선스(글꼴). */
@Composable
fun LicenseSheetContent(text: String) {
    BasicText("오픈소스 라이선스", style = PbType.title.copy(color = Pb.Ink))
    BasicText("Pretendard — SIL Open Font License 1.1", style = PbType.label.copy(color = Pb.Ink))
    BasicText(text, style = PbType.kicker.copy(color = Pb.InkSoft))
}

private fun hours(min: Int): String = if (min >= 60) "${min / 60}시간 ${min % 60}분" else "${min}분"
