package com.kero.passingby.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
            BasicText(stringResource(mode.label), style = PbType.label.copy(color = Pb.Ink))
            BasicText(stringResource(mode.description), style = PbType.body.copy(color = Pb.InkSoft))
        }
    }
}

@Composable
fun SecretCodeDialog(onSubmit: (String) -> Boolean, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val submit = {
        if (code.isNotBlank()) {
            failed = !onSubmit(code)
            code = ""
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Pb.Paper).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(stringResource(R.string.secret_ticket), style = PbType.title.copy(color = Pb.Ink))
        BasicText(stringResource(R.string.secret_ticket_hint), style = PbType.body.copy(color = Pb.InkSoft))
        BasicTextField(
            value = code, onValueChange = { if (it.length <= 128) { code = it; failed = false } },
            textStyle = PbType.body.copy(color = Pb.Ink), singleLine = true,
            visualTransformation = PasswordVisualTransformation(), cursorBrush = SolidColor(Pb.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("secret-code")
                .border(1.dp, if (failed) Pb.Accent else Pb.Line, RoundedCornerShape(12.dp)).padding(16.dp),
            decorationBox = { inner ->
                Box {
                    if (code.isEmpty()) BasicText(stringResource(R.string.secret_code_label), style = PbType.body.copy(color = Pb.InkSoft))
                    inner()
                }
            },
        )
        if (failed) BasicText(stringResource(R.string.secret_code_wrong), style = PbType.body.copy(color = Pb.Accent))
        PrimaryButton(stringResource(R.string.secret_open), submit, Modifier.fillMaxWidth())
        QuietButton(stringResource(R.string.close), onDismiss, Modifier.fillMaxWidth())
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
