package com.zeno.assistant.voiceid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.zeno.domain.audio.CommandEndpointer
import com.zeno.domain.audio.Pcm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface ClipResult {
    class Captured(val pcm: ShortArray) : ClipResult
    data object NoSpeech : ClipResult
    data class Failed(val reason: String) : ClipResult
}

/**
 * Records ONE short clip for voice enrollment or a voice test, then closes the microphone. The clip is
 * returned in memory only; Zeno never writes it to disk (docs/08 data inventory).
 */
fun interface ClipRecorder {
    suspend fun record(onLevel: (dbfs: Double) -> Unit): ClipResult
}

class AudioRecordClipRecorder(private val context: Context) : ClipRecorder {

    @SuppressLint("MissingPermission") // checked explicitly below
    override suspend fun record(onLevel: (dbfs: Double) -> Unit): ClipResult = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withContext ClipResult.Failed("Microphone permission is off.")
        }
        val minBuf = AudioRecord.getMinBufferSize(Pcm.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return@withContext ClipResult.Failed("This phone can't record 16 kHz audio.")
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, Pcm.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, Pcm.FRAME_SAMPLES * 2 * 10),
            )
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            return@withContext ClipResult.Failed("The microphone couldn't be opened. Another app may be using it.")
        }
        val endpointer = CommandEndpointer(trailingSilenceMs = 800, maxMs = 8_000, noSpeechTimeoutMs = 5_000)
        val frame = ShortArray(Pcm.FRAME_SAMPLES)
        try {
            rec.startRecording()
            while (true) {
                coroutineContext.ensureActive() // cancel = microphone closed in finally
                var read = 0
                while (read < frame.size) {
                    val n = rec.read(frame, read, frame.size - read)
                    if (n < 0) return@withContext ClipResult.Failed("The microphone stopped (error $n).")
                    read += n
                }
                onLevel(Pcm.rmsDbfs(frame))
                when (val r = endpointer.accept(frame.copyOf())) {
                    CommandEndpointer.Result.Continue -> Unit
                    CommandEndpointer.Result.NoSpeech -> return@withContext ClipResult.NoSpeech
                    is CommandEndpointer.Result.Complete -> return@withContext ClipResult.Captured(r.pcm)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            ClipResult.NoSpeech
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }
}
