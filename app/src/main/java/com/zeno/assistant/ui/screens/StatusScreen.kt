package com.zeno.assistant.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeno.assistant.platform.CapabilityState
import com.zeno.assistant.platform.CapabilityStatus
import com.zeno.assistant.ui.ZenoActions
import com.zeno.assistant.ui.ZenoUiState
import com.zeno.assistant.ui.theme.StatusActive
import com.zeno.assistant.ui.theme.StatusWarning
import com.zeno.domain.model.ModelKind
import com.zeno.domain.model.ModelStatus
import com.zeno.domain.security.Capability

internal fun Capability.label(): String = when (this) {
    Capability.MICROPHONE -> "Microphone"
    Capability.ASSISTANT_ROLE -> "Default digital assistant"
    Capability.CONTACTS -> "Contacts"
    Capability.SMS_SEND -> "Send SMS"
    Capability.SMS_READ -> "Read SMS"
    Capability.CALL_PHONE -> "Phone calls"
    Capability.NOTIFICATION_LISTENER -> "Notification access"
    Capability.USAGE_ACCESS -> "Usage access"
    Capability.ACCESSIBILITY -> "Accessibility service"
    Capability.DND_ACCESS -> "Do Not Disturb access"
    Capability.EXACT_ALARM -> "Alarms & reminders (exact timing)"
    Capability.WRITE_SETTINGS -> "Modify system settings"
    Capability.POST_NOTIFICATIONS -> "Notifications"
    Capability.ROOT -> "Root (Magisk)"
}

private fun ModelKind.label() = when (this) {
    ModelKind.WAKE_WORD -> "Wake word"
    ModelKind.ASR -> "Speech recognition"
    ModelKind.SPEAKER_VERIFICATION -> "Owner voice check"
    ModelKind.TTS -> "Voice replies"
}

private fun ModelStatus.label() = when (this) {
    ModelStatus.Verified -> "Verified"
    ModelStatus.Missing -> "File missing"
    is ModelStatus.SizeMismatch -> "Wrong size (expected ${expected} bytes, found ${actual})"
    ModelStatus.ChecksumMismatch -> "Checksum doesn't match: file changed or corrupt"
    is ModelStatus.Rejected -> "Rejected: $reason"
}

private fun launch(context: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: ActivityNotFoundException) { /* try the next one */ }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun StatusRow(title: String, value: String, valueColor: Color, detail: String? = null, tag: String? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.labelLarge, color = valueColor)
    }
}

@Composable
private fun CapabilityRow(c: CapabilityStatus) {
    val (value, color) = when (c.state) {
        CapabilityState.GRANTED -> "Granted" to StatusActive
        CapabilityState.NOT_GRANTED -> "Not granted" to MaterialTheme.colorScheme.onSurfaceVariant
        CapabilityState.NOT_CHECKED -> "Off · not checked" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val detail = if (c.usedByThisBuild) "Needed now" else "Not needed yet · used from ${c.usedFrom}"
    StatusRow(c.capability.label(), value, color, detail, tag = "cap_${c.capability.name}")
}

@Composable
fun StatusScreen(state: ZenoUiState, actions: ZenoActions) {
    val context = LocalContext.current
    val p = state.platform
    LazyColumn(Modifier.fillMaxSize().testTag("status_list")) {
        item { ScreenTitle("Permissions & status", "What Android actually reports right now") }

        item { Header("Listening") }
        item {
            StatusRow(
                "Listening status", state.status.title(),
                if (state.status == com.zeno.domain.status.ListeningStatus.ACTIVE) StatusActive else StatusWarning,
                state.status.explanation(), tag = "status_listening",
            )
        }
        if (p == null) {
            item { Text("Checking…", modifier = Modifier.padding(20.dp)) }
        } else {
            item {
                StatusRow(
                    "Phone unlocked since restart",
                    if (p.snapshot.userUnlocked) "Yes" else "No",
                    if (p.snapshot.userUnlocked) StatusActive else StatusWarning,
                )
            }
            item {
                StatusRow(
                    "Default digital assistant",
                    if (p.snapshot.assistantRoleHeld) "Zeno" else "Not Zeno",
                    if (p.snapshot.assistantRoleHeld) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant,
                    "Zeno appears in Android's list from M2b. Android lets you choose it only in Settings.",
                    tag = "status_role",
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        launch(
                            context,
                            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("open_assistant_settings"),
                ) { Text("Open Digital assistant settings") }
            }

            item { Header("Permissions and special access") }
            items(p.capabilities, key = { it.capability.name }) { CapabilityRow(it) }
            item {
                OutlinedButton(
                    onClick = {
                        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text("Open Android app info") }
            }

            item { Header("Offline models") }
            val inv = p.models
            if (inv.manifestProblem != null) {
                item { StatusRow("Model list", "Problem", StatusWarning, inv.manifestProblem, tag = "models_problem") }
            } else if (inv.reports.isEmpty()) {
                item {
                    StatusRow(
                        "No models installed", "", MaterialTheme.colorScheme.onSurfaceVariant,
                        "Zeno never downloads models. You copy them, with a manifest.json listing licence, source, size and SHA-256, to: " +
                            (state.modelsLocation ?: "app storage (unavailable)"),
                        tag = "models_empty",
                    )
                }
            }
            ModelKind.entries.forEach { kind ->
                item(key = "kind_${kind.name}") {
                    val ready = inv.isReady(kind)
                    StatusRow(kind.label(), if (ready) "Ready" else "Missing", if (ready) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant, tag = "model_${kind.name}")
                }
            }
            items(inv.reports, key = { "r_" + it.descriptor.id + it.status.toString() }) { r ->
                StatusRow(
                    "${r.descriptor.id} (${r.descriptor.version})",
                    if (r.status == ModelStatus.Verified) "OK" else "Not used",
                    if (r.status == ModelStatus.Verified) StatusActive else StatusWarning,
                    "${r.status.label()} · licence ${r.descriptor.license.ifBlank { "missing" }}",
                )
            }
            item {
                OutlinedButton(
                    onClick = actions::refresh,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("recheck"),
                ) { Text("Check again") }
            }
        }
    }
}
