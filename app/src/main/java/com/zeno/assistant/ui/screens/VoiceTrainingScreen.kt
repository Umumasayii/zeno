package com.zeno.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zeno.assistant.ui.VoiceTrainingActions
import com.zeno.assistant.ui.VoiceTrainingUiState
import com.zeno.assistant.ui.theme.StatusActive
import com.zeno.assistant.ui.theme.StatusWarning
import com.zeno.domain.voiceid.ProfileState

/** Screen 06 "Train your voice": owner enrollment, sample quality feedback, test, retrain, delete (Z-005). */
@Composable
fun VoiceTrainingScreen(state: VoiceTrainingUiState, actions: VoiceTrainingActions, assistantName: String) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) } // which recording waits for the permission
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            !granted -> actions.onMicPermissionDenied()
            pending == "test" -> actions.testVoice()
            else -> actions.recordSample()
        }
        pending = null
    }
    fun withMic(kind: String, start: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else { pending = kind; askMic.launch(Manifest.permission.RECORD_AUDIO) }
    }
    LaunchedEffect(Unit) { actions.refresh() }

    LazyColumn(Modifier.fillMaxSize().testTag("voice_training_list")) {
        item { ScreenTitle("Train your voice", "On-device speaker verification") }

        if (!state.loading && state.engine == null) {
            item {
                InfoCard(
                    "No speaker model installed",
                    "Voice training needs a speaker model that you install and that Zeno checks (licence and checksum, see docs/MODEL_INSTALL.md). " +
                        "This build doesn't include a speaker engine yet, so nothing is recorded here. Push-to-talk on Home works without it.",
                    Modifier.testTag("speaker_model_missing"),
                )
            }
        }

        item {
            val (title, body) = when (state.profile) {
                ProfileState.NONE -> "No voice profile" to "Spoken wake-word commands are refused until you train your voice."
                ProfileState.READY -> "Voice profile saved" to "${state.profileSamples} samples, encrypted on this phone only. Retrain any time."
                ProfileState.STALE -> "Profile needs retraining" to "It was made with a different speaker model, so it is not used. Train your voice again."
            }
            InfoCard(title, body, Modifier.testTag("profile_status"))
        }

        if (state.engine != null) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (!state.enrolling) {
                            Text("Teach $assistantName your voice", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Read a few short phrases. Record some in quiet places and some with everyday noise.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = actions::startEnrollment, enabled = !state.recording, modifier = Modifier.testTag("start_training")) {
                                    Text(if (state.profile == ProfileState.READY) "Retrain" else "Start training")
                                }
                                if (state.profile == ProfileState.READY) {
                                    OutlinedButton(onClick = { withMic("test", actions::testVoice) }, enabled = !state.recording, modifier = Modifier.testTag("test_voice")) {
                                        Text("Test my voice")
                                    }
                                }
                            }
                        } else {
                            Row {
                                Text("Training progress", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text("${state.accepted} / ${state.minSamples} samples", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("training_count"))
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (state.accepted.toFloat() / state.minSamples.coerceAtLeast(1)).coerceAtMost(1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Read the phrase aloud:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("“${state.prompt}”", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp).testTag("training_prompt"))
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { if (state.recording) actions.stopRecording() else withMic("sample", actions::recordSample) },
                                modifier = Modifier.fillMaxWidth().testTag("record_sample"),
                            ) {
                                Icon(if (state.recording) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord, contentDescription = null)
                                Text(if (state.recording) "  Stop" else "  Record sample")
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = actions::finishEnrollment, enabled = state.canFinish && !state.recording, modifier = Modifier.testTag("finish_training")) { Text("Finish") }
                                TextButton(onClick = actions::cancelEnrollment, modifier = Modifier.testTag("cancel_training")) { Text("Cancel") }
                            }
                        }
                        if (state.recording) {
                            Text("Recording… (microphone on)", color = StatusActive, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp).testTag("recording_indicator"))
                            LinearProgressIndicator(progress = { state.level }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        state.message?.let { m ->
            item {
                Text(
                    m,
                    color = if (state.messageIsProblem) StatusWarning else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("training_message"),
                )
            }
        }

        if (state.profile != ProfileState.NONE) {
            item {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("delete_profile")) {
                    Text("Delete voice profile")
                }
            }
        }

        item {
            Text(
                "Voice matching reduces unauthorized use but cannot guarantee protection against recordings or imitation. " +
                    "It is never used to unlock your phone: unlocking always needs your PIN, pattern or fingerprint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete voice profile?") },
            text = { Text("Your saved voice samples (embeddings) and their key are erased from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.deleteProfile() }, modifier = Modifier.testTag("delete_confirm")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
