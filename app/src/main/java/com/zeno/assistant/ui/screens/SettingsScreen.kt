package com.zeno.assistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeno.assistant.ui.ZenoActions
import com.zeno.domain.voiceid.ProfileState
import com.zeno.assistant.ui.ZenoUiState
import com.zeno.assistant.ui.theme.StatusWarning
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.WakePhraseCheck

internal fun SmsPolicy.label() = when (this) {
    SmsPolicy.IMMEDIATE -> "Send immediately"
    SmsPolicy.ALWAYS_CONFIRM -> "Always confirm"
    SmsPolicy.SMART -> "Smart confirmation"
}

private fun SmsPolicy.detail() = when (this) {
    SmsPolicy.IMMEDIATE -> "Sends right after Zeno recognises your voice, without reading the message back. Riskier."
    SmsPolicy.ALWAYS_CONFIRM -> "Reads the recipient and message back and waits for you to say \"send\". Recommended."
    SmsPolicy.SMART -> "Asks for confirmation when the phone is locked or the recipient is new."
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String?,
    checked: Boolean,
    tag: String,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .testTag(tag)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The row handles the click so the whole row is one accessible switch.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun TextSettingRow(
    label: String,
    current: String,
    tag: String,
    supporting: String?,
    supportingIsWarning: Boolean,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable(current) { mutableStateOf(current) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("${tag}_field"),
            supportingText = supporting?.let { { Text(it, color = if (supportingIsWarning) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onSave(draft) }, enabled = draft.trim() != current, modifier = Modifier.testTag("${tag}_save")) {
                Text("Save")
            }
        }
    }
}

@Composable
fun SettingsScreen(state: ZenoUiState, actions: ZenoActions, onOpenStatus: () -> Unit, onOpenVoiceTraining: () -> Unit = {}) {
    val s = state.settings
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().testTag("settings_list")) {
        item { ScreenTitle("Settings", "Kept on this phone only. Nothing is uploaded.") }

        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenStatus).testTag("open_status").padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Permissions & status", style = MaterialTheme.typography.bodyLarge)
                    Text(state.status.title(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }

        item { SectionHeader("Assistant") }
        item {
            TextSettingRow("Assistant name", s.assistantName, "assistant_name", "Shown in the app and spoken replies.", false, actions::setAssistantName)
        }
        item {
            val check = state.wakePhraseCheck
            val (text, warn) = when (check) {
                WakePhraseCheck.Supported -> "An installed wake-word model supports this phrase." to false
                is WakePhraseCheck.NeedsCompatibleModel ->
                    "Saved as text only. Zeno can wake only on a phrase its installed wake-word model supports, and none is installed yet." to true
                is WakePhraseCheck.Invalid -> check.reason to true
            }
            TextSettingRow("Wake phrase", s.wakePhrase, "wake_phrase", text, warn, actions::setWakePhrase)
        }

        item {
            val profile = when (state.platform?.voiceProfile ?: ProfileState.NONE) {
                ProfileState.NONE -> "No voice profile"
                ProfileState.READY -> "Voice profile saved (encrypted, this phone only)"
                ProfileState.STALE -> "Needs retraining for the installed speaker model"
            }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenVoiceTraining).testTag("open_voice_training").padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Train your voice", style = MaterialTheme.typography.bodyLarge)
                    Text(profile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }

        item { SectionHeader("Listening & startup") }
        item {
            SwitchRow("Background listening", "Main switch. Off means Zeno never uses the microphone.", s.listeningEnabled, "listening_switch", !state.loading, actions::setListening)
        }
        item {
            SwitchRow("Start listening after restart", "Resumes after your first unlock. Never before.", s.startListeningAfterBoot, "after_boot_switch", !state.loading, actions::setStartAfterBoot)
        }

        item { SectionHeader("Messages & calls") }
        item {
            Text(
                "Voice SMS sending (works from M4)",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        SmsPolicy.entries.forEach { p ->
            item(key = "sms_${p.name}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = s.smsPolicy == p, role = Role.RadioButton, onClick = { actions.setSmsPolicy(p) })
                        .testTag("sms_${p.name}")
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.smsPolicy == p, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(p.label(), style = MaterialTheme.typography.bodyLarge)
                        Text(p.detail(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SwitchRow("Confirm before calling", "Zeno says who it will call and waits for \"yes\" (works from M4).", s.confirmCalls, "confirm_calls_switch", onChange = actions::setConfirmCalls)
        }

        item { SectionHeader("Privacy") }
        item {
            SwitchRow(
                "Read private content while locked",
                "Read messages and notifications aloud when the phone is locked. Off is safer.",
                s.allowPrivateReadoutWhileLocked, "private_readout_switch", onChange = actions::setPrivateReadoutWhileLocked,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Keep activity logs for", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { d ->
                        FilterChip(
                            selected = s.retentionDays == d,
                            onClick = { actions.setRetentionDays(d) },
                            label = { Text("$d days") },
                            modifier = Modifier.testTag("retention_$d"),
                        )
                    }
                }
            }
        }
        item {
            // Locked until M8 (Z-023): shown so the owner knows it exists and is off, but cannot be enabled.
            SwitchRow("Adaptive learning", "Off. Available in M8, and always opt-in.", s.learningEnabled, "learning_switch", enabled = false) {}
        }

        item { SectionHeader("Automations") }
        item {
            SwitchRow("Pause all automations", "Stops every automation from running until you turn this off.", s.automationsPaused, "automations_paused_switch", onChange = actions::setAutomationsPaused)
        }

        item { SectionHeader("Root") }
        item {
            SwitchRow(
                "Root controls",
                if (s.enabledRootOperations.isEmpty()) "Off. Optional, never required. Available in M7." else "${s.enabledRootOperations.size} enabled",
                s.enabledRootOperations.isNotEmpty(), "root_switch", enabled = false,
            ) {}
        }

        item { SectionHeader("Reset") }
        item {
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("reset_button"),
            ) { Text("Restore safe defaults") }
        }
        item { Row(Modifier.padding(bottom = 24.dp)) {} }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restore safe defaults?") },
            text = { Text("All settings go back to their defaults and listening turns off. Nothing else is deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; actions.resetToDefaults() }, modifier = Modifier.testTag("reset_confirm")) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
