package com.zeno.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zeno.assistant.ui.AssistantActions
import com.zeno.assistant.ui.AssistantUiState
import com.zeno.assistant.ui.MicState
import com.zeno.assistant.ui.theme.StatusActive
import com.zeno.assistant.ui.theme.StatusWarning
import com.zeno.assistant.voice.SpeechAvailability

/**
 * Push-to-talk and typed commands (M2a). The mic button is the only way the microphone opens in this
 * build; it closes after one utterance. "Listening…" is shown only while Android delivers audio.
 */
@Composable
fun AssistantPanel(state: AssistantUiState, actions: AssistantActions, assistantName: String) {
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) actions.startPushToTalk() else actions.onMicPermissionDenied()
    }
    LaunchedEffect(Unit) { actions.checkSpeech() }

    fun send() {
        actions.submitTyped(draft)
        draft = ""
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("assistant_panel"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Talk to $assistantName", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val listening = state.mic == MicState.LISTENING
                FilledIconButton(
                    onClick = {
                        when {
                            listening -> actions.stopPushToTalk()
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> actions.startPushToTalk()
                            else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = state.mic != MicState.PROCESSING && state.speech !is SpeechAvailability.Unavailable,
                    modifier = Modifier.size(56.dp).testTag("mic_button"),
                ) {
                    Icon(if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, contentDescription = if (listening) "Stop listening now" else "Push to talk")
                }
                Column(Modifier.padding(start = 12.dp).weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    when {
                        listening -> {
                            Text("Listening… (microphone on)", color = StatusActive, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ptt_listening"))
                            if (state.partial.isNotBlank()) Text(state.partial, style = MaterialTheme.typography.bodySmall)
                        }
                        state.mic == MicState.PROCESSING -> Text("Working on it…", style = MaterialTheme.typography.bodyMedium)
                        state.speech is SpeechAvailability.Unavailable ->
                            Text(state.speech.reason, color = StatusWarning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("speech_unavailable"))
                        else -> Text("Tap the mic and say a command, or type it below. Microphone is off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(if (state.awaitingAnswer) "Type your answer" else "Type a command") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailingIcon = {
                    IconButton(onClick = { send() }, enabled = draft.isNotBlank(), modifier = Modifier.testTag("command_send")) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send command")
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("command_field"),
            )

            if (state.awaitingConfirmation) {
                // Touch confirmation is equivalent to saying it (docs/07 "secure actions by voice and by touch").
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.submitTyped("yes") }, modifier = Modifier.testTag("confirm_yes")) { Text("Confirm") }
                    OutlinedButton(onClick = { actions.submitTyped("cancel") }, modifier = Modifier.testTag("confirm_cancel")) { Text("Cancel") }
                }
            }

            state.lines.takeLast(VISIBLE_LINES).forEach { line ->
                Text(
                    (if (line.fromOwner) "You: " else "$assistantName: ") + line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.fromOwner) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .let { if (!line.fromOwner) it.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp) else it },
                )
            }
        }
    }
}

private const val VISIBLE_LINES = 6
