package com.kero.passingby.ui.journey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kero.passingby.experience.RouteCaption
import com.kero.passingby.feature.journey.R
import com.kero.passingby.ui.theme.Pb
import com.kero.passingby.ui.theme.PbType
import kotlinx.coroutines.delay

@Composable
internal fun routeCaptionText(caption: RouteCaption?): String? {
    if (caption == null) return null
    val template = when (caption.kind) {
        RouteCaption.Kind.DEPARTING -> R.string.route_departing
        RouteCaption.Kind.PASSING -> R.string.route_passing
        RouteCaption.Kind.APPROACHING -> R.string.route_approaching
        RouteCaption.Kind.STOPPED -> R.string.stopped_at
    }
    return stringResource(template, stringResource(caption.name))
}

/** 지역이 바뀔 때만 8초간 표시한다. 오버레이·일시정지 중에는 풍경 위에 겹쳐 띄우지 않는다. */
@Composable
internal fun RoutePassingNotice(caption: RouteCaption?, enabled: Boolean, modifier: Modifier = Modifier) {
    val text = routeCaptionText(caption)
    var showing by remember(caption) { mutableStateOf(caption != null) }
    LaunchedEffect(caption) {
        if (caption != null) {
            delay(8_000L)
            showing = false
        }
    }
    AnimatedContent(
        targetState = text.takeIf { enabled && showing },
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
        transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
        label = "routeCaption",
    ) { line ->
        if (line != null) {
            BasicText(
                line,
                style = PbType.kicker.copy(color = Pb.OnScrimSoft, textAlign = TextAlign.Center),
                modifier = Modifier
                    .background(androidx.compose.ui.graphics.Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
