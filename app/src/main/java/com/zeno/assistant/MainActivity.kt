package com.zeno.assistant

import android.app.KeyguardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zeno.assistant.ui.AssistantViewModel
import com.zeno.assistant.ui.VoiceTrainingViewModel
import com.zeno.assistant.voiceid.AudioRecordClipRecorder
import com.zeno.assistant.ui.ZenoRoot
import com.zeno.assistant.voice.AndroidSpeaker
import com.zeno.assistant.voice.OnDeviceSpeechInput
import com.zeno.domain.policy.LockState
import com.zeno.assistant.ui.ZenoViewModel
import com.zeno.assistant.ui.theme.ZenoTheme

class MainActivity : ComponentActivity() {

    private val vm: ZenoViewModel by viewModels {
        val c = (application as ZenoApplication).container
        viewModelFactory {
            initializer {
                ZenoViewModel(
                    repo = c.settings,
                    platform = c.platform,
                    scanModels = c.models::scan,
                    modelsLocation = c.models.location,
                    wakeWordEngineBuilt = c.wakeWordEngineBuilt,
                    voiceProfileState = c::voiceProfileState,
                )
            }
        }
    }

    private val assistant: AssistantViewModel by viewModels {
        val c = (application as ZenoApplication).container
        val keyguard = getSystemService(KeyguardManager::class.java)
        viewModelFactory {
            initializer {
                AssistantViewModel(
                    repo = c.settings,
                    platform = c.platform,
                    speech = OnDeviceSpeechInput(applicationContext),
                    speaker = AndroidSpeaker(applicationContext),
                    lockState = { if (keyguard.isDeviceLocked) LockState.LOCKED else LockState.UNLOCKED },
                )
            }
        }
    }

    private val voiceTraining: VoiceTrainingViewModel by viewModels {
        val c = (application as ZenoApplication).container
        viewModelFactory {
            initializer {
                VoiceTrainingViewModel(
                    store = c.voiceProfiles,
                    embedder = { c.speakerEmbedder },
                    recorder = AudioRecordClipRecorder(applicationContext),
                    assistantName = { vm.state.value.settings.assistantName },
                    onProfileChanged = { vm.refresh() },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZenoTheme {
                ZenoRoot(vm, assistant, voiceTraining)
            }
        }
    }
}
