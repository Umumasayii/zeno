package com.zeno.spike

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * SPIKE-03: can Android's ON-DEVICE recognizer transcribe audio that Zeno already captured (wake word +
 * command, after the owner check), passed through `RecognizerIntent.EXTRA_AUDIO_SOURCE` (API 33)?
 * If not, the wake pipeline must hand the microphone to SpeechRecognizer after verification instead.
 *
 * Records [seconds] s into memory, then streams it through a pipe. The log gets numbers only (word count,
 * latency, error codes); the transcript is shown on screen for the owner and never written anywhere.
 */
object BufferedAsrProbe {
    private const val RATE = 16_000
    private val main = Handler(Looper.getMainLooper())

    fun logSupport(context: Context) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpikeLog.write(context, "SPIKE03 onDeviceAvailable=false")
            return
        }
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        r.checkRecognitionSupport(baseIntent(), context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(s: RecognitionSupport) {
                SpikeLog.write(context, "SPIKE03 support installed=${s.installedOnDeviceLanguages} pending=${s.pendingOnDeviceLanguages} supported=${s.supportedOnDeviceLanguages.size} online=${s.onlineLanguages.size}")
                r.destroy()
            }
            override fun onError(error: Int) {
                SpikeLog.write(context, "SPIKE03 support error=$error")
                r.destroy()
            }
        })
    }

    @SuppressLint("MissingPermission") // checked below
    fun run(context: Context, seconds: Int = 4, onTranscript: (String) -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            SpikeLog.write(context, "SPIKE03 skipped: RECORD_AUDIO not granted")
            return
        }
        SpikeLog.write(context, "SPIKE03 recording ${seconds}s into memory")
        thread(name = "spike03-record") {
            val pcm = ShortArray(RATE * seconds)
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4)
            try {
                rec.startRecording()
                var read = 0
                while (read < pcm.size) {
                    val n = rec.read(pcm, read, minOf(1_600, pcm.size - read))
                    if (n < 0) { SpikeLog.write(context, "SPIKE03 record error=$n"); return@thread }
                    read += n
                }
            } finally {
                runCatching { rec.stop() }
                rec.release() // microphone closed before recognition starts
            }
            main.post { recognize(context, pcm, onTranscript) }
        }
    }

    private fun recognize(context: Context, pcm: ShortArray, onTranscript: (String) -> Unit) {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val started = SystemClock.elapsedRealtime()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = SpikeLog.write(context, "SPIKE03 readyForSpeech")
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
                SpikeLog.write(context, "SPIKE03 RESULT words=$words latencyMs=${SystemClock.elapsedRealtime() - started}")
                onTranscript(text)
                done()
            }
            override fun onError(error: Int) {
                SpikeLog.write(context, "SPIKE03 ERROR code=$error latencyMs=${SystemClock.elapsedRealtime() - started}")
                onTranscript("(error $error)")
                done()
            }
            private fun done() {
                recognizer.destroy()
                runCatching { readEnd.close() }
            }
        })
        recognizer.startListening(baseIntent().apply {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readEnd)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
        })
        thread(name = "spike03-pipe") {
            runCatching {
                FileOutputStream(writeEnd.fileDescriptor).use { out ->
                    val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    pcm.forEach { bytes.putShort(it) }
                    out.write(bytes.array())
                }
            }.onFailure { SpikeLog.write(context, "SPIKE03 pipe write failed ${it.javaClass.simpleName}") }
            runCatching { writeEnd.close() }
            SpikeLog.write(context, "SPIKE03 wrote ${pcm.size * 2} bytes, pipe closed")
        }
    }

    private fun baseIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
}
