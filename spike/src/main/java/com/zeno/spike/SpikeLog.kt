package com.zeno.spike

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.os.UserManager
import android.util.Log
import java.io.File
import java.time.Instant

/**
 * Append-only probe log in DEVICE-PROTECTED storage so it can be written before first unlock.
 * Readable with:  adb shell run-as com.zeno.spike cat /data/user_de/0/com.zeno.spike/files/spike_log.txt
 * and live with:  adb logcat -s ZenoSpike
 * Contains timestamps, states and numbers only. No audio, no transcripts, no personal data.
 */
object SpikeLog {
    const val TAG = "ZenoSpike"
    private const val FILE = "spike_log.txt"

    private fun dp(context: Context): Context = context.createDeviceProtectedStorageContext()

    fun prefs(context: Context): SharedPreferences = dp(context).getSharedPreferences("spike_prefs", Context.MODE_PRIVATE)

    @Synchronized
    fun write(context: Context, event: String) {
        val line = "${Instant.now()} | ${deviceState(context)} | $event"
        Log.i(TAG, line)
        runCatching { File(dp(context).filesDir, FILE).appendText(line + "\n") }
            .onFailure { Log.e(TAG, "log write failed", it) }
    }

    fun read(context: Context): String =
        runCatching { File(dp(context).filesDir, FILE).readText() }.getOrDefault("(empty)")

    fun clear(context: Context) {
        runCatching { File(dp(context).filesDir, FILE).delete() }
    }

    fun deviceState(context: Context): String {
        val um = context.getSystemService(UserManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        return "unlocked=${um.isUserUnlocked} screenOn=${pm.isInteractive} keyguard=${km.isKeyguardLocked}"
    }
}
