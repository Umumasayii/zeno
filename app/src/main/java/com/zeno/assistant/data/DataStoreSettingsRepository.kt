package com.zeno.assistant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zeno.domain.root.RootOperation
import com.zeno.domain.settings.LearningMode
import com.zeno.domain.settings.SettingsRepository
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.ZenoSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore adapter for [SettingsRepository] (Z-026). Lives in credential-encrypted storage, so it is
 * readable only after the first unlock. The two flags the boot path needs are mirrored to
 * [BootFlagStore] (device-protected storage), per docs/02 "Boot flow" and SPIKE-02.
 *
 * Unknown or corrupt values fall back to the safe default for that field; they never throw.
 */
class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
    private val bootFlags: BootFlagStore,
) : SettingsRepository {

    override val settings: Flow<ZenoSettings> = store.data.map { it.toSettings() }

    override suspend fun update(transform: (ZenoSettings) -> ZenoSettings) {
        // If transform throws (e.g. ZenoSettings.init rejects a value), DataStore aborts and writes nothing.
        val written = store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
        bootFlags.write(written.toSettings().toBootFlags())
    }

    override suspend fun resetToDefaults() {
        store.edit { it.clear() }
        bootFlags.write(ZenoSettings().toBootFlags())
    }

    /** Repairs the mirror if the process died between the DataStore write and the mirror write. */
    suspend fun syncBootFlags() {
        val expected = settings.first().toBootFlags()
        if (bootFlags.read() != expected) bootFlags.write(expected)
    }

    private object Keys {
        val assistantName = stringPreferencesKey("assistant_name")
        val wakePhrase = stringPreferencesKey("wake_phrase")
        val listeningEnabled = booleanPreferencesKey("listening_enabled")
        val startAfterBoot = booleanPreferencesKey("start_listening_after_boot")
        val smsPolicy = stringPreferencesKey("sms_policy")
        val confirmCalls = booleanPreferencesKey("confirm_calls")
        val privateReadoutLocked = booleanPreferencesKey("private_readout_while_locked")
        val learningEnabled = booleanPreferencesKey("learning_enabled")
        val learningMode = stringPreferencesKey("learning_mode")
        val learningMinRepetitions = intPreferencesKey("learning_min_repetitions")
        val retentionDays = intPreferencesKey("retention_days")
        val rootOperations = stringSetPreferencesKey("enabled_root_operations")
        val automationsPaused = booleanPreferencesKey("automations_paused")
    }

    companion object {
        private val D = ZenoSettings()

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

        internal fun Preferences.toSettings(): ZenoSettings = ZenoSettings(
            assistantName = this[Keys.assistantName]?.takeIf { it.isNotBlank() } ?: D.assistantName,
            wakePhrase = this[Keys.wakePhrase]?.takeIf { it.isNotBlank() } ?: D.wakePhrase,
            listeningEnabled = this[Keys.listeningEnabled] ?: D.listeningEnabled,
            startListeningAfterBoot = this[Keys.startAfterBoot] ?: D.startListeningAfterBoot,
            smsPolicy = enumOr(this[Keys.smsPolicy], D.smsPolicy),
            confirmCalls = this[Keys.confirmCalls] ?: D.confirmCalls,
            allowPrivateReadoutWhileLocked = this[Keys.privateReadoutLocked] ?: D.allowPrivateReadoutWhileLocked,
            learningEnabled = this[Keys.learningEnabled] ?: D.learningEnabled,
            learningMode = enumOr<LearningMode>(this[Keys.learningMode], D.learningMode),
            learningMinRepetitions = (this[Keys.learningMinRepetitions] ?: D.learningMinRepetitions).takeIf { it >= 2 } ?: D.learningMinRepetitions,
            retentionDays = (this[Keys.retentionDays] ?: D.retentionDays).takeIf { it in 1..365 } ?: D.retentionDays,
            // Unknown operation names are dropped: only the typed allowlist can ever be enabled (Z-015).
            enabledRootOperations = this[Keys.rootOperations].orEmpty()
                .mapNotNull { n -> RootOperation.entries.firstOrNull { it.name == n } }.toSet(),
            automationsPaused = this[Keys.automationsPaused] ?: D.automationsPaused,
        )

        private fun MutablePreferences.write(s: ZenoSettings) {
            this[Keys.assistantName] = s.assistantName
            this[Keys.wakePhrase] = s.wakePhrase
            this[Keys.listeningEnabled] = s.listeningEnabled
            this[Keys.startAfterBoot] = s.startListeningAfterBoot
            this[Keys.smsPolicy] = s.smsPolicy.name
            this[Keys.confirmCalls] = s.confirmCalls
            this[Keys.privateReadoutLocked] = s.allowPrivateReadoutWhileLocked
            this[Keys.learningEnabled] = s.learningEnabled
            this[Keys.learningMode] = s.learningMode.name
            this[Keys.learningMinRepetitions] = s.learningMinRepetitions
            this[Keys.retentionDays] = s.retentionDays
            this[Keys.rootOperations] = s.enabledRootOperations.map { it.name }.toSet()
            this[Keys.automationsPaused] = s.automationsPaused
        }

        private fun ZenoSettings.toBootFlags() = BootFlags(listeningEnabled, startListeningAfterBoot)
    }
}
