package com.kero.passingby.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kero.passingby.feature.journey.R
import com.kero.passingby.ui.theme.Pb
import com.kero.passingby.ui.theme.PbType

enum class AppUpdateStage { AVAILABLE, REQUESTING, DOWNLOADING, READY, INSTALLING }

data class AppUpdateUiState(val stage: AppUpdateStage, val showPrompt: Boolean = false)

internal val AppUpdateStage.actionable: Boolean
    get() = this == AppUpdateStage.AVAILABLE || this == AppUpdateStage.READY

internal val AppUpdateStage.label: Int
    get() = when (this) {
        AppUpdateStage.AVAILABLE -> R.string.app_update_download
        AppUpdateStage.REQUESTING -> R.string.app_update_requesting
        AppUpdateStage.DOWNLOADING -> R.string.app_update_downloading
        AppUpdateStage.READY -> R.string.app_update_install
        AppUpdateStage.INSTALLING -> R.string.app_update_installing
    }

@Composable
internal fun AppUpdatePrompt(stage: AppUpdateStage, onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val title = if (stage == AppUpdateStage.READY) R.string.app_update_ready else R.string.app_update_available
    Column(
        modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Pb.Paper)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(stringResource(title), style = PbType.label.copy(color = Pb.Ink))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietButton(stringResource(R.string.app_update_later), onLater)
            PrimaryButton(stringResource(stage.label), onUpdate)
        }
    }
}
