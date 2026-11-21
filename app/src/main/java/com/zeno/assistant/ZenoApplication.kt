package com.zeno.assistant

import android.app.Application
import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zeno.assistant.data.DataStoreSettingsRepository
import com.zeno.assistant.data.DeviceProtectedBootFlagStore
import com.zeno.assistant.model.ModelScanner
import com.zeno.assistant.platform.AndroidPlatformStatus
import com.zeno.assistant.voiceid.AndroidKeystoreKeyProvider
import com.zeno.assistant.voiceid.EncryptedVoiceProfileStore
import com.zeno.domain.voiceid.SpeakerEmbedder
import com.zeno.domain.voiceid.VoiceProfileStore
import com.zeno.domain.voiceid.profileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class ZenoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Manual dependency wiring. One instance per process (DataStore requires a single owner per file). */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bootFlags = DeviceProtectedBootFlagStore(context)
    val settings = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(
            // A corrupt file is replaced with defaults (safe: listening off) instead of crashing.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
        ) { context.preferencesDataStoreFile("zeno_settings") },
        bootFlags,
    )
    val platform = AndroidPlatformStatus(context)
    val models = ModelScanner(context.getExternalFilesDir(null)?.let { File(it, "models") })

    /**
     * No wake-word engine, speaker engine or listening service is built yet (M2b part 2 waits for the
     * engine decision, ADR-009). Kept explicit, never faked.
     */
    val wakeWordEngineBuilt = false
    val speakerEmbedder: SpeakerEmbedder? = null

    /** Owner voice profile: embeddings only, AES-GCM with a Keystore key, in no-backup storage. */
    val voiceProfiles: VoiceProfileStore = EncryptedVoiceProfileStore(File(context.noBackupFilesDir, "voice"), AndroidKeystoreKeyProvider())

    fun voiceProfileState() = profileState(runCatching { voiceProfiles.load() }.getOrNull(), speakerEmbedder)

    init {
        scope.launch { runCatching { settings.syncBootFlags() } }
    }
}
