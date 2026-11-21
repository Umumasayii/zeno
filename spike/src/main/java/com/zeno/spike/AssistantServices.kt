package com.zeno.spike

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * SPIKE-01 candidate assistant. Once the owner selects "Zeno Spike" as the default digital assistant,
 * Android calls onReady().
 *
 * Desk finding (2026-11-21, compile time): in the public SDK (compileSdk 37) the hotword APIs
 * `createAlwaysOnHotwordDetector`, `createHotwordDetector`, `AlwaysOnHotwordDetector`,
 * `HotwordDetector` and `HotwordDetectionService` are NOT available to third-party code
 * (@SystemApi). A sideloaded app cannot compile against them. This probe records what is visible
 * at RUNTIME via reflection (hidden-API enforcement normally hides them too). It never invokes them.
 */
class SpikeVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        SpikeLog.write(this, "VIS onReady (Zeno Spike is the active assistant)")
        logPermissions(this, "VIS")
        logHotwordApiVisibility(this)
        if (SpikeLog.prefs(this).getBoolean(PREF_PROBE_ON_VIS_READY, false)) {
            MicProbeService.start(this, trigger = "VIS_READY", seconds = 30)
        }
    }

    override fun onShutdown() {
        SpikeLog.write(this, "VIS onShutdown")
        super.onShutdown()
    }

    companion object {
        const val PREF_PROBE_ON_VIS_READY = "probe_mic_on_vis_ready"

        fun logPermissions(context: Context, where: String) {
            val perms = listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                "android.permission.MANAGE_HOTWORD_DETECTION",
                "android.permission.CAPTURE_AUDIO_HOTWORD",
            )
            val states = perms.joinToString { p ->
                "${p.substringAfterLast('.')}=${context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED}"
            }
            SpikeLog.write(context, "PERMISSIONS@$where $states")
        }

        /** Lists hotword-related methods/classes the runtime exposes to this app. Read-only. */
        fun logHotwordApiVisibility(context: Context) {
            val methods = runCatching {
                VoiceInteractionService::class.java.methods.map { it.name }.filter { it.contains("Hotword", ignoreCase = true) }.distinct()
            }.getOrElse { listOf("error: ${it.javaClass.simpleName}") }
            SpikeLog.write(context, "HOTWORD_API visible VoiceInteractionService methods=$methods")
            for (name in listOf("AlwaysOnHotwordDetector", "HotwordDetectionService", "HotwordDetector", "VisualQueryDetectionService")) {
                val visible = runCatching { Class.forName("android.service.voice.$name") }.isSuccess
                SpikeLog.write(context, "HOTWORD_API class $name visible=$visible")
            }
        }
    }
}

/** Minimal session so the system accepts the assistant; logs long-press/assist invocations. */
class SpikeSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = object : VoiceInteractionSession(this) {
        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            SpikeLog.write(context, "SESSION onShow flags=$showFlags")
        }
    }
}

/** Required by the assistant config; this spike does not recognise speech. */
class SpikeRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}
