package com.kero.passingby.ui.journey

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kero.passingby.experience.JourneyMode
import com.kero.passingby.feature.journey.R
import com.kero.passingby.ui.theme.Pb
import com.kero.passingby.ui.theme.PbType

@Composable
fun ModeSheetContent(modes: List<JourneyMode>, selected: JourneyMode, canSwitch: Boolean, onPick: (JourneyMode) -> Unit) {
    BasicText(stringResource(R.string.travel_modes), style = PbType.title.copy(color = Pb.Ink))
    BasicText(stringResource(if (canSwitch) R.string.travel_modes_hint else R.string.travel_modes_docked), style = PbType.body.copy(color = Pb.InkSoft))
    for (mode in modes) {
        val on = mode == selected
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .border(if (on) 2.dp else 1.dp, if (on) Pb.Accent else Pb.Line, RoundedCornerShape(14.dp))
                .selectable(on, enabled = canSwitch || on, role = Role.RadioButton) { onPick(mode) }
                .testTag("mode-${mode.id}").padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(stringResource(mode.label), style = PbType.label.copy(color = Pb.Ink),
                    modifier = Modifier.weight(1f, fill = false))
                if (mode != JourneyMode.TRAIN) {
                    BasicText(
                        stringResource(R.string.preview_test_label), style = PbType.kicker.copy(color = Pb.Accent),
                        modifier = Modifier.border(1.dp, Pb.Accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            BasicText(stringResource(mode.description), style = PbType.body.copy(color = Pb.InkSoft))
        }
    }
}
