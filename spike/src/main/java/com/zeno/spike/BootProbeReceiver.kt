package com.zeno.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** SPIKE-02: records when each boot/unlock broadcast arrives and, if opted in, tries a mic probe. */
class BootProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.substringAfterLast('.') ?: "null"
        SpikeLog.write(context, "BROADCAST $action")
        if (SpikeLog.prefs(context).getBoolean(PREF_PROBE_ON_BOOT, false)) {
            MicProbeService.start(context, trigger = "BOOT_$action", seconds = 30)
        }
    }

    companion object {
        const val PREF_PROBE_ON_BOOT = "probe_mic_on_boot"
    }
}
