package com.kero.passingby.ui.journey

import com.kero.passingby.feature.journey.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kero.passingby.experience.JourneyCourse as Course
import com.kero.passingby.ui.theme.Pb
import com.kero.passingby.ui.theme.PbType
import java.util.Locale

// ── 정차 카드 ──

/**
 * 정차·출발 전 승차권 카드(명세 5.1). 절취선 모티프는 이 카드에만 쓴다.
 * 잠긴 코스를 미리 보는 중이면 시간 선택 대신 코스 열기 버튼을 보여 준다.
 */
@Composable
fun TicketCard(
    course: Course,
    direction: Int,
    minutes: Int,
    today: Pair<Int, Int>,
    restDone: Boolean,
    owned: Boolean,
    price: String?,
    paper: Color,
    panel: Color,
    onMinutes: (Int) -> Unit,
    onDepart: () -> Unit,
    onBuy: () -> Unit,
    onCourses: () -> Unit,
    onSettings: () -> Unit,
    onJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalResources.current
    val first = stringResource(course.ordered(direction).first().name)
    val region = stringResource(course.region)
    Sheet(paper, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                when {
                    restDone -> stringResource(R.string.rest_over_at, first)
                    !owned -> stringResource(R.string.ticket_kicker_preview, course.number, region)
                    else -> stringResource(R.string.ticket_kicker, course.number, region, stringResource(course.vehicle))
                },
                style = PbType.kicker.copy(color = Pb.InkSoft),
                modifier = Modifier.weight(1f),
            )
            SmallLink(stringResource(R.string.journal_short), onJournal)
            SmallLink(stringResource(R.string.settings), onSettings)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(course.title(res, direction), style = PbType.title.copy(color = Pb.Ink), modifier = Modifier.weight(1f))
            SmallLink(stringResource(R.string.change_course), onCourses)
        }
        BasicText(course.stopsLine(res, direction), style = PbType.body.copy(color = Pb.InkSoft))
        Perforation(panel)
        if (owned) {
            MinuteSelector(minutes, onMinutes)
            PrimaryButton(stringResource(R.string.depart_minutes, minutes), onDepart, Modifier.fillMaxWidth())
            BasicText(
                todayLine(today) ?: stringResource(R.string.ticket_note),
                style = PbType.body.copy(color = Pb.InkSoft),
            )
        } else {
            PrimaryButton(
                stringResource(R.string.unlock_course_price, price ?: stringResource(R.string.price_pending)),
                onBuy,
                Modifier.fillMaxWidth(),
            )
            BasicText(stringResource(R.string.unlock_once), style = PbType.body.copy(color = Pb.InkSoft))
        }
    }
}

/** 카드 안 작은 글자 버튼. 누르는 영역은 48dp를 지킨다. */
@Composable
private fun SmallLink(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = PbType.kicker.copy(color = Pb.Ink, textDecoration = TextDecoration.Underline))
    }
}

/** 종착역 도착 카드(명세 5.4). */
@Composable
fun ArrivalCard(
    terminus: String,
    tripMinutes: Int,
    today: Pair<Int, Int>,
    paper: Color,
    onRest: () -> Unit,
    onGetOff: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Sheet(paper, modifier) {
        BasicText(stringResource(R.string.arrived_at, terminus), style = PbType.voice.copy(color = Pb.Ink))
        BasicText(
            listOfNotNull(stringResource(R.string.trip_minutes, tripMinutes), todayLine(today)).joinToString(" · "),
            style = PbType.body.copy(color = Pb.InkSoft),
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(stringResource(R.string.rest_and_continue, REST_MINUTES), onRest, Modifier.fillMaxWidth())
        QuietButton(stringResource(R.string.get_off_here), onGetOff, Modifier.fillMaxWidth())
        // 다 왔을 때가 친구에게 알려 주고 싶은 순간이라 여기에 둔다. 두 버튼보다 눈에 덜 띄게 작은 글자로.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SmallLink(stringResource(R.string.share_app), onShare)
        }
    }
}

/** 쉬는 중 카드. 종착역 플랫폼에 선 채로 쉬는 시간이 흐른다. 끝나도 자동으로 출발하지 않는다. */
@Composable
fun RestCard(
    terminus: String,
    nextTitle: String,
    minutes: Int,
    restLeftSec: Int,
    paper: Color,
    onDepart: () -> Unit,
    onGetOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val left = stringResource(R.string.rest_left_desc, spoken(restLeftSec))
    Sheet(paper, modifier) {
        BasicText(stringResource(R.string.resting_at, terminus), style = PbType.kicker.copy(color = Pb.InkSoft))
        BasicText(
            clock(restLeftSec),
            style = PbType.timerMid.copy(color = Pb.Ink),
            modifier = Modifier.semantics { contentDescription = left },
        )
        BasicText(stringResource(R.string.next_trip, nextTitle, minutes), style = PbType.body.copy(color = Pb.InkSoft))
        Spacer(Modifier.height(4.dp))
        PrimaryButton(stringResource(R.string.depart_now), onDepart, Modifier.fillMaxWidth())
        QuietButton(stringResource(R.string.get_off_here), onGetOff, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Sheet(paper: Color, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(paper)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** 승차권 절취선. 양쪽 끝의 반원 홈은 벽색으로 칠해 카드가 뚫린 것처럼 보인다. */
@Composable
private fun Perforation(panel: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val y = size.height / 2
        val notch = 8.dp.toPx()
        val inset = 20.dp.toPx()
        drawLine(
            Pb.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        if (panel.alpha > 0f) {
            drawCircle(panel, notch, Offset(-inset, y))
            drawCircle(panel, notch, Offset(size.width + inset, y))
        }
    }
}

@Composable
private fun MinuteSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (m in MINUTE_OPTIONS) {
            val on = m == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(if (on) Pb.Ink else Color.Transparent)
                    .border(1.dp, if (on) Pb.Ink else Pb.Line, CircleShape)
                    .selectable(on, role = Role.RadioButton) { onSelect(m) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(stringResource(R.string.minutes, m), style = PbType.label.copy(color = if (on) Pb.Paper else Pb.Ink))
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(Pb.Accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = PbType.button.copy(color = Pb.OnAccent, textAlign = TextAlign.Center))
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Pb.InkSoft, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = PbType.label.copy(color = color))
    }
}

// ── 주행 오버레이 ──

/** 주행 중 화면에 필요한 값 묶음. */
class RideInfo(
    val stations: List<String>,
    val marks: FloatArray,
    val progress: Float,
    val remainingSec: Int,
    val stoppedAt: Int,
    val nextStop: Int,
    val nextStopSec: Int,
    val paused: Boolean,
    val soundOn: Boolean,
) {
    val last: Boolean get() = nextStop == stations.size - 1
}

/** 풍경 위 반투명 캡슐(명세 6장). */
@Composable
fun Capsule(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(Pb.Scrim)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = PbType.label.copy(color = Pb.OnScrim))
    }
}

/** 여행 끝내기 버튼(아이콘만). */
@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier, background: Color = Pb.Scrim, tint: Color = Pb.OnScrim) {
    val label = stringResource(R.string.end_trip)
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val s = 1.8.dp.toPx()
            drawLine(tint, Offset.Zero, Offset(size.width, size.height), s, StrokeCap.Round)
            drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), s, StrokeCap.Round)
        }
    }
}

/** 소리 켜고 끄기(아이콘만). 꺼지면 스피커에 빗금. */
@Composable
fun SoundButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (on) R.string.sound_off else R.string.sound_on)
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .08f, h * .36f)
                lineTo(w * .3f, h * .36f)
                lineTo(w * .55f, h * .12f)
                lineTo(w * .55f, h * .88f)
                lineTo(w * .3f, h * .64f)
                lineTo(w * .08f, h * .64f)
                close()
            }
            drawPath(path, Color.White)
            val s = 1.6.dp.toPx()
            if (on) {
                drawArc(Color.White, -50f, 100f, false, Offset(w * .45f, h * .28f), androidx.compose.ui.geometry.Size(w * .3f, h * .44f), style = Stroke(s, cap = StrokeCap.Round))
                drawArc(Color.White, -50f, 100f, false, Offset(w * .45f, h * .1f), androidx.compose.ui.geometry.Size(w * .5f, h * .8f), style = Stroke(s, cap = StrokeCap.Round))
            } else {
                drawLine(Color.White, Offset(w * .68f, h * .32f), Offset(w * .96f, h * .68f), s, StrokeCap.Round)
                drawLine(Color.White, Offset(w * .96f, h * .32f), Offset(w * .68f, h * .68f), s, StrokeCap.Round)
            }
        }
    }
}

/** 잠깐 멈추기 / 다시 출발. 글자 버튼이라 아이콘 없이도 뜻이 분명하다. */
@Composable
fun PauseButton(paused: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(stringResource(if (paused) R.string.resume else R.string.pause), style = PbType.label.copy(color = Pb.OnScrim))
    }
}

/**
 * 코스 진행 바. 역마다 점을 찍고, 지나온 부분은 굵고 밝게, 남은 부분은 가늘고 흐리게 그려 색에만 기대지 않는다(명세 6장).
 */
@Composable
fun CourseProgress(info: RideInfo, modifier: Modifier = Modifier, labelColor: Color = Pb.OnScrimSoft) {
    val route = stringResource(R.string.progress_desc, info.stations.first(), info.stations.last())
    val state = stringResource(R.string.progress_state, (info.progress * 100).toInt())
    Column(
        modifier.semantics {
            contentDescription = route
            stateDescription = state
        },
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp),
        ) {
            val y = size.height / 2
            val r = 5.dp.toPx()
            val x0 = r
            val x1 = size.width - r
            val xp = x0 + (x1 - x0) * info.progress.coerceIn(0f, 1f)
            drawLine(Color.White.copy(alpha = .35f), Offset(x0, y), Offset(x1, y), 2.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(x0, y), Offset(xp, y), 3.dp.toPx(), StrokeCap.Round)
            for (m in info.marks) {
                val x = x0 + (x1 - x0) * m
                if (m <= info.progress) {
                    drawCircle(Color.White, 3.dp.toPx(), Offset(x, y))
                } else {
                    drawCircle(Color.White, 3.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
                }
            }
            drawCircle(Pb.Marker, r, Offset(xp, y))
            drawCircle(Color.White, r, Offset(xp, y), style = Stroke(1.5.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (name in info.stations) {
                BasicText(name, style = PbType.body.copy(color = labelColor), maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

/** 다음 정차 안내 한 줄. 정차·신호 대기 중에는 그 상태를 알린다. */
@Composable
fun NextStopLine(info: RideInfo, color: Color = Pb.OnScrimSoft) {
    val next = info.stations[info.nextStop]
    val line = when {
        info.paused -> stringResource(R.string.waiting_signal)
        info.stoppedAt >= 0 -> stringResource(R.string.stopped_leaving_soon, info.stations[info.stoppedAt])
        info.last -> stringResource(R.string.final_stop_clock, next, clock(info.nextStopSec))
        else -> stringResource(R.string.next_stop_clock, next, clock(info.nextStopSec))
    }
    BasicText(line, style = PbType.label.copy(color = color, fontFeatureSettings = "tnum"))
}

/** 종착까지 남은 시간(큰 숫자). */
@Composable
fun RemainingTime(info: RideInfo, timer: TextStyle, color: Color = Pb.OnScrim) {
    val left = stringResource(R.string.remaining_desc, spoken(info.remainingSec))
    BasicText(
        clock(info.remainingSec),
        style = timer.copy(color = color),
        modifier = Modifier.semantics { contentDescription = left },
    )
}

/**
 * 가로·반접기 화면의 진행 묶음: 다음 정차 안내를 진행 바 위에 둔다.
 * 안내 문구는 역 이름·상태에 따라 폭이 바뀌므로, 큰 숫자 옆에 두면 그때마다 진행 바가 밀린다.
 */
@Composable
fun ProgressWithNextStop(info: RideInfo, modifier: Modifier = Modifier) {
    Column(modifier) {
        NextStopLine(info)
        CourseProgress(info)
    }
}

/** 세로 화면 하단 묶음. */
@Composable
fun StackedPanel(info: RideInfo, onPause: () -> Unit, onSound: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Pb.Scrim)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            NextStopLine(info)
            RemainingTime(info, PbType.timerLarge)
        }
        CourseProgress(info)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PauseButton(info.paused, onPause)
            SoundButton(info.soundOn, onSound)
        }
    }
}

/** 가로·큰 화면 하단 한 줄: 왼쪽 시간, 가운데 다음 정차 안내와 진행 바, 오른쪽 조작. */
@Composable
fun RowPanel(info: RideInfo, onPause: () -> Unit, onSound: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Pb.Scrim)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        RemainingTime(info, PbType.timerMid)
        ProgressWithNextStop(info, Modifier.weight(1f))
        PauseButton(info.paused, onPause)
        SoundButton(info.soundOn, onSound)
    }
}

/**
 * 반접기(테이블탑) 아래쪽 반 화면. 시야 밖 평면이라 늘 보이게 두고, 조작이 없으면 어둡게 낮춘다(명세 5.3).
 */
@Composable
fun TabletopPanel(info: RideInfo, onPause: () -> Unit, onSound: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        RemainingTime(info, PbType.timerMid)
        ProgressWithNextStop(info, Modifier.weight(1f))
        PauseButton(info.paused, onPause)
        SoundButton(info.soundOn, onSound)
        CloseButton(onClose, background = Color(0x33FFFFFF))
    }
}

/** 첫 여행 안내·뒤로가기 안내 같은 짧은 알림. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Pb.Scrim)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        BasicText(text, style = PbType.body.copy(color = Pb.OnScrim))
    }
}

/** 여행 끝내기 확인. */
@Composable
fun EndTripDialog(origin: String, paper: Color, onKeep: () -> Unit, onEnd: () -> Unit) {
    Sheet(paper, Modifier.width(320.dp)) {
        BasicText(stringResource(R.string.end_trip_title), style = PbType.title.copy(color = Pb.Ink))
        BasicText(
            stringResource(R.string.end_trip_body, origin),
            style = PbType.body.copy(color = Pb.InkSoft),
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(stringResource(R.string.keep_going), onKeep, Modifier.fillMaxWidth())
        QuietButton(stringResource(R.string.end), onEnd, Modifier.fillMaxWidth())
    }
}

// ── 포맷 ──

private val MINUTE_OPTIONS = intArrayOf(25, 50, 90)
const val REST_MINUTES = 5

@Composable
private fun todayLine(today: Pair<Int, Int>): String? =
    if (today.first > 0) pluralStringResource(R.plurals.today_line, today.first, today.first, today.second) else null

/** 18:42, 1:29:59 */
fun clock(sec: Int): String = formatClock(sec)

fun formatClock(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    return if (s >= 3600) {
        String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    } else {
        String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}

/** 스크린리더용 "18분 42초". */
@Composable
private fun spoken(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) stringResource(R.string.spoken_min_sec, m, s) else stringResource(R.string.spoken_sec, s)
}
