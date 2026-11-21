package com.zeno.assistant.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only settings readable before the first unlock (Direct Boot). Two booleans, nothing personal:
 * no names, phrases, contacts or voice data (docs/02 "Boot flow", docs/06).
 */
data class BootFlags(val listeningEnabled: Boolean, val startListeningAfterBoot: Boolean)

interface BootFlagStore {
    suspend fun read(): BootFlags?
    suspend fun write(flags: BootFlags)
}

/** SharedPreferences in device-protected storage, so a future boot receiver (M6) can read it while locked. */
class DeviceProtectedBootFlagStore(context: Context) : BootFlagStore {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override suspend fun read(): BootFlags? = withContext(Dispatchers.IO) {
        if (!prefs.contains(LISTENING)) null
        else BootFlags(prefs.getBoolean(LISTENING, false), prefs.getBoolean(AFTER_BOOT, true))
    }

    override suspend fun write(flags: BootFlags): Unit = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) {
            putBoolean(LISTENING, flags.listeningEnabled)
            putBoolean(AFTER_BOOT, flags.startListeningAfterBoot)
        }
    }

    companion object {
        const val FILE = "zeno_boot_flags"
        private const val LISTENING = "listening_enabled"
        private const val AFTER_BOOT = "start_listening_after_boot"
    }
}
