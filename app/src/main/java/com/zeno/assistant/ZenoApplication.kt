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

    /** No wake-word engine or listening service exists in M1 (arrive in M2b). Kept explicit, never faked. */
    val wakeWordEngineBuilt = false

    init {
        scope.launch { runCatching { settings.syncBootFlags() } }
    }
}
