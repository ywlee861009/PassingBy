package com.kero.passingby.ui.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kero.passingby.feature.journey.R
import com.kero.passingby.ui.theme.Pb
import com.kero.passingby.ui.theme.PbType

internal enum class NotificationRecoveryAction { REQUEST_PERMISSION, OPEN_SETTINGS }

internal fun notificationRecoveryAction(sdk: Int, permissionGranted: Boolean, askedBefore: Boolean,
    showRationale: Boolean): NotificationRecoveryAction =
    if (sdk >= 33 && !permissionGranted && (!askedBefore || showRationale))
        NotificationRecoveryAction.REQUEST_PERMISSION else NotificationRecoveryAction.OPEN_SETTINGS

/** A shared, user-initiated way to restore reminders without interrupting a journey. */
@Composable
internal fun NotificationPermissionPrompt(
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = Pb.Ink,
    detailColor: Color = Pb.InkSoft,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(stringResource(R.string.notifications_disabled), style = PbType.body.copy(color = color),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!compact) BasicText(stringResource(R.string.notifications_hint), style = PbType.kicker.copy(color = detailColor),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        QuietButton(stringResource(R.string.notifications_enable), onEnable,
            Modifier.padding(horizontal = 6.dp), color = color)
    }
}
