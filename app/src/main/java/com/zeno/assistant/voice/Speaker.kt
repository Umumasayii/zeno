package com.zeno.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Spoken replies. Zeno bundles no TTS voice; it uses the phone's installed engine (docs/08 licensing). */
interface Speaker {
    fun speak(text: String)
    fun stop()
    fun release()
}

/**
 * Android TextToSpeech with the installed engine. Speaks only with an English voice that the engine
 * reports as not needing the network; if there is none, replies are shown as text only.
 */
class AndroidSpeaker(context: Context) : Speaker {
    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS && setUp()
    }

    private fun setUp(): Boolean {
        val lang = tts.setLanguage(Locale.US)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return false
        // Only a voice that doesn't need the network; without one, replies stay text-only.
        val offline = tts.voices?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            ?.minByOrNull { it.latency } ?: return false
        return tts.setVoice(offline) == TextToSpeech.SUCCESS
    }

    override fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zeno-reply")
    }

    override fun stop() { tts.stop() }

    override fun release() { tts.shutdown() }
}
