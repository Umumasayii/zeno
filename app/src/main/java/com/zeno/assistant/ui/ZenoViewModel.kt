package com.zeno.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeno.assistant.platform.CapabilityStatus
import com.zeno.assistant.platform.PlatformStatusSource
import com.zeno.domain.model.ModelInventory
import com.zeno.domain.model.ModelKind
import com.zeno.domain.settings.SettingsRepository
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.WakePhraseCheck
import com.zeno.domain.settings.WakePhrasePolicy
import com.zeno.domain.settings.ZenoSettings
import com.zeno.domain.status.ListeningStatus
import com.zeno.domain.status.ListeningStatusResolver
import com.zeno.domain.status.PlatformSnapshot
import com.zeno.domain.voiceid.ProfileState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlatformInfo(
    val snapshot: PlatformSnapshot,
    val capabilities: List<CapabilityStatus>,
    val models: ModelInventory,
    val voiceProfile: ProfileState = ProfileState.NONE,
)

data class ZenoUiState(
    val loading: Boolean = true,
    val settings: ZenoSettings = ZenoSettings(),
    val platform: PlatformInfo? = null,
    val status: ListeningStatus = ListeningStatus.PAUSED,
    val wakePhraseCheck: WakePhraseCheck = WakePhrasePolicy.check(ZenoSettings().wakePhrase, emptySet()),
    val modelsLocation: String? = null,
    val message: String? = null,
)

/**
 * Everything the UI may change. Learning and root controls are deliberately absent: they stay locked
 * until M8 / M7, so no UI path can enable them in this build (Z-023, Z-015).
 */
interface ZenoActions {
    fun refresh()
    fun setListening(enabled: Boolean)
    fun setStartAfterBoot(enabled: Boolean)
    fun setAssistantName(name: String)
    fun setWakePhrase(phrase: String)
    fun setSmsPolicy(policy: SmsPolicy)
    fun setConfirmCalls(enabled: Boolean)
    fun setPrivateReadoutWhileLocked(enabled: Boolean)
    fun setRetentionDays(days: Int)
    fun setAutomationsPaused(paused: Boolean)
    fun resetToDefaults()
    fun dismissMessage()
}

class ZenoViewModel(
    private val repo: SettingsRepository,
    private val platform: PlatformStatusSource,
    private val scanModels: () -> ModelInventory,
    private val modelsLocation: String?,
    private val wakeWordEngineBuilt: Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val voiceProfileState: () -> ProfileState = { ProfileState.NONE },
) : ViewModel(), ZenoActions {

    private val info = MutableStateFlow<PlatformInfo?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val settingsFlow = repo.settings.catch {
        message.value = "Settings couldn't be read, so safe defaults are shown."
        emit(ZenoSettings())
    }

    val state: StateFlow<ZenoUiState> = combine(settingsFlow, info, message) { s, p, m ->
        val supported = p?.models?.supportedWakePhrases.orEmpty()
        ZenoUiState(
            loading = p == null,
            settings = s,
            platform = p,
            status = p?.let { ListeningStatusResolver.resolve(s, it.snapshot) }
                ?: if (s.listeningEnabled) ListeningStatus.NOT_RUNNING else ListeningStatus.PAUSED,
            wakePhraseCheck = WakePhrasePolicy.check(s.wakePhrase, supported),
            modelsLocation = modelsLocation,
            message = m,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ZenoUiState(modelsLocation = modelsLocation))

    init { refresh() }

    override fun refresh() {
        viewModelScope.launch {
            val models = withContext(io) { runCatching(scanModels).getOrElse { ModelInventory(emptyList(), "Model check failed: ${it.message}") } }
            // Wake word and owner check need both engines and both verified models.
            val engineReady = wakeWordEngineBuilt && models.isReady(ModelKind.WAKE_WORD) && models.isReady(ModelKind.SPEAKER_VERIFICATION)
            val profile = withContext(io) { runCatching(voiceProfileState).getOrDefault(ProfileState.NONE) }
            // No listening service exists yet, so it is never reported as running.
            val snap = platform.snapshot(engineReady, listeningServiceRunning = false).copy(ownerVoiceEnrolled = profile == ProfileState.READY)
            info.value = PlatformInfo(snap, platform.capabilities(), models, profile)
        }
    }

    private fun save(failure: String, transform: (ZenoSettings) -> ZenoSettings) {
        viewModelScope.launch {
            runCatching { repo.update(transform) }.onFailure { message.value = failure }
        }
    }

    override fun setListening(enabled: Boolean) = save("Couldn't change listening.") { it.copy(listeningEnabled = enabled) }
    override fun setStartAfterBoot(enabled: Boolean) = save("Couldn't save the startup setting.") { it.copy(startListeningAfterBoot = enabled) }

    override fun setAssistantName(name: String) {
        val n = name.trim()
        when {
            n.isEmpty() -> message.value = "The assistant name can't be empty."
            n.length > MAX_NAME -> message.value = "Keep the name under $MAX_NAME characters."
            else -> save("Couldn't save the name.") { it.copy(assistantName = n) }
        }
    }

    override fun setWakePhrase(phrase: String) {
        val supported = info.value?.models?.supportedWakePhrases.orEmpty()
        when (val c = WakePhrasePolicy.check(phrase, supported)) {
            is WakePhraseCheck.Invalid -> message.value = c.reason
            else -> save("Couldn't save the wake phrase.") { it.copy(wakePhrase = phrase.trim().replace(Regex("\\s+"), " ")) }
        }
    }

    override fun setSmsPolicy(policy: SmsPolicy) = save("Couldn't save the SMS setting.") { it.copy(smsPolicy = policy) }
    override fun setConfirmCalls(enabled: Boolean) = save("Couldn't save the call setting.") { it.copy(confirmCalls = enabled) }
    override fun setPrivateReadoutWhileLocked(enabled: Boolean) = save("Couldn't save the privacy setting.") { it.copy(allowPrivateReadoutWhileLocked = enabled) }

    override fun setRetentionDays(days: Int) {
        if (days !in 1..365) { message.value = "Retention must be between 1 and 365 days."; return }
        save("Couldn't save retention.") { it.copy(retentionDays = days) }
    }

    override fun setAutomationsPaused(paused: Boolean) = save("Couldn't change automations.") { it.copy(automationsPaused = paused) }

    override fun resetToDefaults() {
        viewModelScope.launch {
            runCatching { repo.resetToDefaults() }
                .onSuccess { message.value = "Settings restored to safe defaults. Listening is off." }
                .onFailure { message.value = "Couldn't reset settings." }
        }
    }

    override fun dismissMessage() { message.value = null }

    companion object { const val MAX_NAME = 30 }
}
