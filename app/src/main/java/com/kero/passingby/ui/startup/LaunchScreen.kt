package com.kero.passingby.ui.startup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kero.passingby.R
import com.kero.passingby.ui.theme.GowunBatang
import com.kero.passingby.ui.theme.Pb
import kotlinx.coroutines.delay

/** 실제 여행 화면은 뒤에서 바로 준비하고, 프로세스의 첫 실행에만 짧게 그림을 겹친다. */
@Composable
fun LaunchScreen(showOnLaunch: Boolean, content: @Composable () -> Unit) {
    val visibility = remember { MutableTransitionState(showOnLaunch) }
    val artworkPresent = visibility.currentState || visibility.targetState
    Box(Modifier.fillMaxSize()) {
        // 그림이 사라질 때까지 뒤쪽 승차권이 접근성 탐색에 끼어들지 않게 한다.
        Box(if (artworkPresent) Modifier.clearAndSetSemantics {} else Modifier) { content() }
        AnimatedVisibility(
            visibleState = visibility,
            enter = EnterTransition.None,
            exit = fadeOut(tween(400)),
            label = "launch artwork",
        ) {
            LaunchArtwork(onDismiss = { visibility.targetState = false })
        }
    }
    // 페이드가 끝나면 화면과 비트맵이 composition에서 빠지고 뒤쪽 접근성도 열린다.
    BackHandler(enabled = artworkPresent) { visibility.targetState = false }
}

@Composable
private fun LaunchArtwork(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(900)
        onDismiss()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.launch_cream))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.skip_launch_artwork),
                onClick = onDismiss,
            ),
    ) {
        Image(
            painter = painterResource(R.drawable.launch_journey),
            contentDescription = null,
            // 정사각 원본 하나를 방향에 관계없이 중앙 기준으로 꽉 채우고 넘치는 부분만 자른다.
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().clipToBounds(),
        )
        val compact = maxHeight < 480.dp
        val titleTop = if (compact) 24.dp else maxHeight * 0.13f
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        ) {
            BasicText(
                text = stringResource(R.string.launch_wordmark),
                style = TextStyle(
                    fontFamily = GowunBatang,
                    fontSize = if (compact) 30.sp else 38.sp,
                    letterSpacing = 1.sp,
                    color = Pb.Ink,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = titleTop),
            )
        }
    }
}
