package com.zeno.assistant.voice

import com.zeno.assistant.platform.CapabilityPlan
import com.zeno.assistant.platform.CapabilityState
import com.zeno.assistant.platform.CapabilityStatus
import com.zeno.assistant.platform.PlatformStatusSource
import com.zeno.domain.security.Capability
import com.zeno.domain.status.PlatformSnapshot

/** Scripted speech input: the test decides what Android "heard". Never touches a microphone. */
class FakeSpeechInput(var availability: SpeechAvailability = SpeechAvailability.Ready) : SpeechInput {
    var listenCalls = 0
    var cancelCalls = 0
    var released = false
    private var listener: ((SpeechEvent) -> Unit)? = null
    val isListening get() = listener != null

    override fun checkAvailability(onResult: (SpeechAvailability) -> Unit) = onResult(availability)
    override fun listen(onEvent: (SpeechEvent) -> Unit) {
        listenCalls++
        listener = onEvent
        onEvent(SpeechEvent.ListeningStarted)
    }
    override fun cancel() { cancelCalls++; listener = null }
    override fun release() { released = true; listener = null }

    fun partial(text: String) = listener!!.invoke(SpeechEvent.Partial(text))
    fun hear(text: String) { val l = listener!!; listener = null; l(SpeechEvent.Final(text)) }
    fun fail(message: String) { val l = listener!!; listener = null; l(SpeechEvent.Error(message)) }
}

class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    var stopCalls = 0
    var released = false
    override fun speak(text: String) { spoken += text }
    override fun stop() { stopCalls++ }
    override fun release() { released = true }
}

/** Platform where only [granted] capabilities are granted; root is never checked. */
class FakePlatform(
    var snap: PlatformSnapshot = PlatformSnapshot(
        userUnlocked = true, microphoneGranted = false, assistantRoleHeld = false,
        wakeWordEngineReady = false, listeningServiceRunning = false,
    ),
    var granted: Set<Capability> = emptySet(),
) : PlatformStatusSource {
    override fun snapshot(wakeWordEngineReady: Boolean, listeningServiceRunning: Boolean) =
        snap.copy(wakeWordEngineReady = wakeWordEngineReady, listeningServiceRunning = listeningServiceRunning)

    override fun capabilities() = Capability.entries.map {
        val state = when {
            it == Capability.ROOT -> CapabilityState.NOT_CHECKED
            it in granted -> CapabilityState.GRANTED
            else -> CapabilityState.NOT_GRANTED
        }
        CapabilityStatus(it, state, CapabilityPlan.usedFrom.getValue(it), it in CapabilityPlan.usedByThisBuild)
    }
}
