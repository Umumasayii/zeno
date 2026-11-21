package com.zeno.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.Executor

/** Why speech input can't be used right now. Shown to the owner as-is. */
sealed interface SpeechAvailability {
    data object Ready : SpeechAvailability
    data object Checking : SpeechAvailability
    data class Unavailable(val reason: String) : SpeechAvailability
}

sealed interface SpeechEvent {
    data object ListeningStarted : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Error(val message: String) : SpeechEvent
}

/**
 * Push-to-talk speech input (one utterance per call). The microphone is used only between [listen]
 * and the end of that utterance, [cancel], or an error. Nothing is recorded or stored by Zeno.
 */
interface SpeechInput {
    fun checkAvailability(onResult: (SpeechAvailability) -> Unit)
    fun listen(onEvent: (SpeechEvent) -> Unit)
    fun cancel()
    fun release()
}

/**
 * Android's ON-DEVICE recognizer (API 31+, `createOnDeviceSpeechRecognizer`). Audio stays on the phone:
 * the on-device service never uses the network for recognition (ADR-008). Requires the offline English
 * speech pack; Zeno never triggers a download itself. Must be used on the main thread.
 */
class OnDeviceSpeechInput(
    private val context: Context,
    private val language: String = "en-US",
    private val mainExecutor: Executor = context.mainExecutor,
) : SpeechInput {

    private var recognizer: SpeechRecognizer? = null

    override fun checkAvailability(onResult: (SpeechAvailability) -> Unit) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onResult(SpeechAvailability.Unavailable("This phone has no on-device speech recognizer."))
            return
        }
        val probe = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrElse {
            onResult(SpeechAvailability.Unavailable("On-device speech recognition couldn't start.")); return
        }
        probe.checkRecognitionSupport(intent(), mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                probe.destroy()
                val installed = support.installedOnDeviceLanguages.any { it.equals(language, ignoreCase = true) }
                onResult(
                    if (installed) SpeechAvailability.Ready
                    else SpeechAvailability.Unavailable(
                        "The offline English speech pack isn't installed. Install it in Android Settings › System › Languages › Speech › Offline speech recognition, then come back.",
                    ),
                )
            }
            override fun onError(error: Int) {
                probe.destroy()
                onResult(SpeechAvailability.Unavailable("Couldn't check offline speech support (${errorText(error)})."))
            }
        })
    }

    override fun listen(onEvent: (SpeechEvent) -> Unit) {
        cancel()
        val r = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrElse {
            onEvent(SpeechEvent.Error("On-device speech recognition couldn't start.")); return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onEvent(SpeechEvent.ListeningStarted)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                first(partialResults)?.let { onEvent(SpeechEvent.Partial(it)) }
            }
            override fun onResults(results: Bundle?) {
                val text = first(results)
                release()
                onEvent(if (text.isNullOrBlank()) SpeechEvent.Error("I didn't hear anything.") else SpeechEvent.Final(text))
            }
            override fun onError(error: Int) {
                release()
                onEvent(SpeechEvent.Error(errorText(error)))
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(intent())
    }

    override fun cancel() {
        recognizer?.cancel()
        release()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun first(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    companion object {
        fun errorText(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off."
            SpeechRecognizer.ERROR_AUDIO -> "The microphone couldn't be used. Another app may be using it."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The offline English speech pack isn't available."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "Speech recognition tried to use the network, which Zeno doesn't allow. Check the offline speech pack."
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests. Wait a moment."
            else -> "Speech recognition error $code."
        }
    }
}
