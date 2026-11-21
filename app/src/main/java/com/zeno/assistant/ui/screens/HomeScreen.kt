package com.zeno.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeno.assistant.ui.AssistantActions
import com.zeno.assistant.ui.AssistantUiState
import com.zeno.assistant.ui.ZenoActions
import com.zeno.assistant.ui.ZenoUiState
import com.zeno.assistant.ui.theme.StatusActive
import com.zeno.assistant.ui.theme.StatusWarning
import com.zeno.domain.status.ListeningStatus
import com.zeno.domain.status.ListeningStatusResolver

/** Owner-facing wording for each status (docs/07 "persistent listening status", screens 03, 28, 31). */
internal fun ListeningStatus.title(): String = when (this) {
    ListeningStatus.PAUSED -> "Listening paused"
    ListeningStatus.WAITING_FOR_UNLOCK -> "Waiting for first unlock"
    ListeningStatus.PERMISSION_BLOCKED -> "Microphone permission needed"
    ListeningStatus.ROLE_MISSING -> "Zeno isn't your digital assistant"
    ListeningStatus.ENGINE_UNAVAILABLE -> "Wake-word engine not installed"
    ListeningStatus.VOICE_NOT_ENROLLED -> "Voice not enrolled"
    ListeningStatus.NOT_RUNNING -> "Listening service not running"
    ListeningStatus.ACTIVE -> "Listening for your wake phrase"
}

internal fun ListeningStatus.explanation(): String = when (this) {
    ListeningStatus.PAUSED -> "Zeno is not using the microphone. Turn listening on when you want it back."
    ListeningStatus.WAITING_FOR_UNLOCK -> "The phone restarted. Zeno resumes after you unlock it with your PIN or fingerprint."
    ListeningStatus.PERMISSION_BLOCKED -> "Listening is switched on, but Android hasn't given Zeno the microphone. Tap the mic button below to allow it (used only while you talk)."
    ListeningStatus.ROLE_MISSING -> "Android lets only the default digital assistant start listening in the background. Choose Zeno in Digital assistant settings (available from M2b)."
    ListeningStatus.ENGINE_UNAVAILABLE -> "This build has no wake-word or speaker engine yet, so Zeno can't listen for its name. Push-to-talk below still works. Models you install are checked now."
    ListeningStatus.VOICE_NOT_ENROLLED -> "Zeno only answers your voice. Record your voice in Settings › Train your voice before turning on wake-word listening."
    ListeningStatus.NOT_RUNNING -> "Everything is set up, but the listening service isn't running right now."
    ListeningStatus.ACTIVE -> "The listening service reports that it is running."
}

@Composable
fun HomeScreen(
    state: ZenoUiState,
    actions: ZenoActions,
    assistant: AssistantUiState,
    assistantActions: AssistantActions,
    onOpenStatus: () -> Unit,
) {
    val status = state.status
    val active = ListeningStatusResolver.mayShowListeningIndicator(status)
    LazyColumn(Modifier.fillMaxSize().testTag("home_list")) {
        item { ScreenTitle(state.settings.assistantName, "Offline voice assistant · development build M2a") }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(112.dp).background(
                        if (active) StatusActive.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    // A microphone icon is shown only when Android reports active listening (docs/07).
                    Icon(
                        if (active) Icons.Outlined.Mic else Icons.Outlined.MicOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (active) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    status.title(),
                    style = MaterialTheme.typography.titleLarge,
                    color = when (status) {
                        ListeningStatus.ACTIVE -> StatusActive
                        ListeningStatus.PAUSED -> MaterialTheme.colorScheme.onSurface
                        else -> StatusWarning
                    },
                    modifier = Modifier.testTag("listening_status").semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    status.explanation(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                )
                // One accessible switch: the whole row toggles and TalkBack reads "Listening, on/off"
                // (device test 2026-11-21 found the label and the switch were separate nodes).
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .toggleable(
                            value = state.settings.listeningEnabled,
                            enabled = !state.loading,
                            role = Role.Switch,
                            onValueChange = actions::setListening,
                        )
                        .testTag("home_listening_switch")
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Listening", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
                    Switch(checked = state.settings.listeningEnabled, onCheckedChange = null, enabled = !state.loading)
                }
                if (status != ListeningStatus.PAUSED && status != ListeningStatus.ACTIVE) {
                    OutlinedButton(onClick = onOpenStatus, modifier = Modifier.padding(top = 8.dp).testTag("home_open_status")) {
                        Text("See permissions & status")
                    }
                }
            }
        }
        item { AssistantPanel(assistant, assistantActions, state.settings.assistantName) }
        if (state.settings.automationsPaused) {
            item { InfoCard("Automations paused", "No automation will run until you resume them in Settings.") }
        }
        item { InfoCard("Upcoming automation", "None. You haven't created any automations.") }
        item { InfoCard("Recent activity", "No actions have been run.") }
        item {
            InfoCard(
                "What works in this build",
                "Push-to-talk and typed commands with on-device speech recognition, spoken replies, settings, status and model checks. Commands are understood and checked, but phone actions, SMS, automations and learning come in later milestones. Wake word arrives in M2b.",
            )
        }
    }
}
