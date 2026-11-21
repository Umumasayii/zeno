package com.zeno.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeno.assistant.platform.CapabilityState
import com.zeno.assistant.platform.PlatformStatusSource
import com.zeno.assistant.voice.AssistantControlAdapter
import com.zeno.assistant.voice.Roadmap
import com.zeno.assistant.voice.Speaker
import com.zeno.assistant.voice.SpeechAvailability
import com.zeno.assistant.voice.SpeechEvent
import com.zeno.assistant.voice.SpeechInput
import com.zeno.domain.action.ActionAdapter
import com.zeno.domain.action.ActionResult
import com.zeno.domain.action.OrderedActionRouter
import com.zeno.domain.dialogue.DialogueManager
import com.zeno.domain.dialogue.TurnOutcome
import com.zeno.domain.nlu.CommandParser
import com.zeno.domain.nlu.RuleBasedCommandParser
import com.zeno.domain.policy.CommandSource
import com.zeno.domain.policy.DefaultPolicyEngine
import com.zeno.domain.policy.LockState
import com.zeno.domain.policy.OwnerVerification
import com.zeno.domain.policy.PolicyContext
import com.zeno.domain.settings.SettingsRepository
import com.zeno.domain.settings.ZenoSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatLine(val fromOwner: Boolean, val text: String)

enum class MicState { IDLE, LISTENING, PROCESSING }

data class AssistantUiState(
    val lines: List<ChatLine> = emptyList(),
    val mic: MicState = MicState.IDLE,
    val partial: String = "",
    val speech: SpeechAvailability = SpeechAvailability.Checking,
    val awaitingConfirmation: Boolean = false,
    val awaitingAnswer: Boolean = false,
)

interface AssistantActions {
    fun submitTyped(text: String)
    fun startPushToTalk()
    fun stopPushToTalk()
    fun onMicPermissionDenied()
    fun checkSpeech()
}

/**
 * Push-to-talk and typed commands (M2a). Text → parser → policy → dialogue → router. The transcript is
 * kept in memory only and is never written to disk (docs/06 privacy).
 */
class AssistantViewModel(
    private val repo: SettingsRepository,
    private val platform: PlatformStatusSource,
    private val speech: SpeechInput,
    private val speaker: Speaker,
    private val lockState: () -> LockState,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel(), AssistantActions {

    private val settings: StateFlow<ZenoSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ZenoSettings())

    private val adapters: List<ActionAdapter> = listOf(AssistantControlAdapter(repo))
    private val router = OrderedActionRouter(adapters)

    private val parser = CommandParser { RuleBasedCommandParser(settings.value.assistantName).parse(it) }
    private val dialogue = DialogueManager(
        parser,
        DefaultPolicyEngine { settings.value },
        // Commands without an adapter in this build are explained, never confirmed (nothing can happen).
        notBuilt = { intent, readBack -> if (adapters.any { it.supports(intent.kind) }) null else Roadmap.notBuiltYet(intent.kind, readBack) },
    ) {
        PolicyContext(
            source = CommandSource.PUSH_TO_TALK,
            owner = OwnerVerification.NOT_APPLICABLE,
            lockState = lockState(),
            grantedCapabilities = platform.capabilities().filter { it.state == CapabilityState.GRANTED }.map { it.capability }.toSet(),
        )
    }
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()
    private var lastTurnAt = 0L

    override fun checkSpeech() {
        speech.checkAvailability { a -> _state.update { it.copy(speech = a) } }
    }

    override fun submitTyped(text: String) {
        if (text.isBlank()) return
        handle(text.trim(), spoken = false)
    }

    override fun startPushToTalk() {
        if (_state.value.mic != MicState.IDLE) return
        speaker.stop() // never listen to our own voice
        _state.update { it.copy(mic = MicState.LISTENING, partial = "") }
        speech.listen { e ->
            when (e) {
                SpeechEvent.ListeningStarted -> Unit
                is SpeechEvent.Partial -> _state.update { it.copy(partial = e.text) }
                is SpeechEvent.Final -> {
                    _state.update { it.copy(mic = MicState.PROCESSING, partial = "") }
                    handle(e.text, spoken = true)
                }
                is SpeechEvent.Error -> {
                    _state.update { it.copy(mic = MicState.IDLE, partial = "") }
                    addLine(ChatLine(false, e.message))
                }
            }
        }
    }

    override fun stopPushToTalk() {
        speech.cancel()
        _state.update { it.copy(mic = MicState.IDLE, partial = "") }
    }

    override fun onMicPermissionDenied() {
        _state.update { it.copy(mic = MicState.IDLE) }
        addLine(ChatLine(false, "Microphone permission is off, so I can't listen. You can still type commands."))
    }

    private fun handle(text: String, spoken: Boolean) {
        val now = clock()
        if (now - lastTurnAt > DIALOGUE_TIMEOUT_MS) dialogue.reset() // context lives only in an active dialogue
        lastTurnAt = now
        addLine(ChatLine(true, text))
        val turn = dialogue.handle(text)
        viewModelScope.launch {
            val reply = turn.authorized?.let { a ->
                when (val r = router.route(a)) {
                    is ActionResult.Succeeded -> r.message
                    is ActionResult.Unavailable -> Roadmap.notBuiltYet(a.intent.kind, dialogue.describe(a.intent).removeSuffix("?"))
                    is ActionResult.Failed -> "That didn't work: ${r.reason}"
                    is ActionResult.OutcomeUnknown -> "I'm not sure it worked: ${r.reason}"
                }
            } ?: turn.reply
            addLine(ChatLine(false, reply))
            _state.update {
                it.copy(
                    mic = MicState.IDLE,
                    awaitingConfirmation = turn.outcome == TurnOutcome.CONFIRMING,
                    awaitingAnswer = turn.outcome == TurnOutcome.ASKING,
                )
            }
            if (spoken) speaker.speak(reply)
        }
    }

    private fun addLine(line: ChatLine) = _state.update { it.copy(lines = (it.lines + line).takeLast(MAX_LINES)) }

    override fun onCleared() {
        speech.release()
        speaker.release()
    }

    companion object {
        const val DIALOGUE_TIMEOUT_MS = 30_000L
        const val MAX_LINES = 30
    }
}
