package com.zeno.domain.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** All voice audio in Zeno is 16 kHz mono PCM16, processed in memory and never written to disk (docs/08). */
object Pcm {
    const val SAMPLE_RATE = 16_000
    /** 20 ms analysis frame. */
    const val FRAME_SAMPLES = 320
    const val FRAME_MS = 20L
    const val SILENCE_DBFS = -96.0

    fun durationMs(samples: Int): Long = samples * 1000L / SAMPLE_RATE

    /** RMS level of [samples] in dBFS (0 = full scale). Digital silence returns [SILENCE_DBFS]. */
    fun rmsDbfs(samples: ShortArray, from: Int = 0, to: Int = samples.size): Double {
        if (to <= from) return SILENCE_DBFS
        var sum = 0.0
        for (i in from until to) { val v = samples[i].toDouble(); sum += v * v }
        val rms = sqrt(sum / (to - from))
        return if (rms < 1.0) SILENCE_DBFS else 20 * log10(rms / 32768.0)
    }
}

/** Measured properties of one clip. Numbers only; no audio is kept. */
data class AudioStats(
    val durationMs: Long,
    val speechMs: Long,
    val speechDbfs: Double,
    val noiseFloorDbfs: Double,
    val clippedRatio: Double,
) {
    val snrDb: Double get() = speechDbfs - noiseFloorDbfs
}

object AudioAnalyzer {
    private const val CLIP_LEVEL = 32_700
    /** A frame is speech when it is this much louder than the noise floor and above [MIN_SPEECH_FRAME_DBFS]. */
    const val SPEECH_ABOVE_NOISE_DB = 10.0
    const val MIN_SPEECH_FRAME_DBFS = -55.0

    fun analyze(pcm: ShortArray): AudioStats {
        val frames = pcm.size / Pcm.FRAME_SAMPLES
        if (frames == 0) return AudioStats(Pcm.durationMs(pcm.size), 0, Pcm.SILENCE_DBFS, Pcm.SILENCE_DBFS, 0.0)
        val levels = DoubleArray(frames) { f -> Pcm.rmsDbfs(pcm, f * Pcm.FRAME_SAMPLES, (f + 1) * Pcm.FRAME_SAMPLES) }
        // Noise floor: 10th percentile of frame levels. Recordings start before the owner speaks, so
        // the quietest frames are background noise.
        val noise = levels.sorted()[(frames - 1) / 10]
        val speechThreshold = max(noise + SPEECH_ABOVE_NOISE_DB, MIN_SPEECH_FRAME_DBFS)
        val speech = levels.filter { it >= speechThreshold }
        val speechDb = if (speech.isEmpty()) Pcm.SILENCE_DBFS else 10 * log10(speech.sumOf { Math.pow(10.0, it / 10) } / speech.size)
        val clipped = pcm.count { it >= CLIP_LEVEL || it <= -CLIP_LEVEL }
        return AudioStats(
            durationMs = Pcm.durationMs(pcm.size),
            speechMs = speech.size * Pcm.FRAME_MS,
            speechDbfs = speechDb,
            noiseFloorDbfs = noise,
            clippedRatio = clipped.toDouble() / pcm.size,
        )
    }
}

enum class SampleProblem { NO_SPEECH, TOO_SHORT, TOO_QUIET, CLIPPED, TOO_NOISY }

/** Rejects clips that would make a poor voice sample (docs/08 "VAD/quality gating"). */
data class SampleQualityGate(
    val minSpeechMs: Long = 1_000,
    val minSpeechDbfs: Double = -40.0,
    val maxClippedRatio: Double = 0.01,
    val minSnrDb: Double = 12.0,
) {
    fun check(stats: AudioStats): SampleProblem? = when {
        stats.speechMs == 0L -> SampleProblem.NO_SPEECH
        stats.clippedRatio > maxClippedRatio -> SampleProblem.CLIPPED
        stats.speechDbfs < minSpeechDbfs -> SampleProblem.TOO_QUIET
        stats.speechMs < minSpeechMs -> SampleProblem.TOO_SHORT
        stats.snrDb < minSnrDb -> SampleProblem.TOO_NOISY
        else -> null
    }
}

/**
 * End-of-utterance detection for one command (energy VAD). Feed 20 ms frames; it finishes after
 * [trailingSilenceMs] of quiet following at least [minSpeechMs] of speech, after [maxMs], or with no speech
 * after [noSpeechTimeoutMs]. Shorter bursts (a click, the tail of the wake word) are treated as noise.
 * Captured audio stays in memory and is handed to the caller exactly once.
 */
class CommandEndpointer(
    private val trailingSilenceMs: Long = 700,
    private val minSpeechMs: Long = 200,
    private val maxMs: Long = 10_000,
    private val noSpeechTimeoutMs: Long = 4_000,
    initialNoiseDbfs: Double = -60.0,
    preRoll: ShortArray = ShortArray(0),
) {
    sealed interface Result {
        data object Continue : Result
        data class Complete(val pcm: ShortArray, val truncated: Boolean) : Result
        data object NoSpeech : Result
    }

    private val buffer = ShortArrayBuilder().apply { append(preRoll) }
    private var noise = initialNoiseDbfs
    private var elapsed = 0L
    private var speechMs = 0L
    private var silenceAfterSpeech = 0L
    private var done = false

    fun accept(frame: ShortArray): Result {
        check(!done) { "endpointer already finished" }
        buffer.append(frame)
        elapsed += Pcm.durationMs(frame.size)
        val level = Pcm.rmsDbfs(frame)
        val isSpeech = level >= max(noise + AudioAnalyzer.SPEECH_ABOVE_NOISE_DB, AudioAnalyzer.MIN_SPEECH_FRAME_DBFS)
        if (isSpeech) {
            speechMs += Pcm.durationMs(frame.size)
            silenceAfterSpeech = 0
        } else {
            noise = 0.95 * noise + 0.05 * level // track background slowly, only from non-speech frames
            if (speechMs > 0) silenceAfterSpeech += Pcm.durationMs(frame.size)
        }
        if (speechMs in 1 until minSpeechMs && silenceAfterSpeech >= trailingSilenceMs) {
            speechMs = 0 // a short burst, not a command
            silenceAfterSpeech = 0
        }
        return when {
            speechMs >= minSpeechMs && silenceAfterSpeech >= trailingSilenceMs -> finish(truncated = false)
            elapsed >= maxMs -> if (speechMs > 0) finish(truncated = true) else { done = true; Result.NoSpeech }
            speechMs == 0L && elapsed >= noSpeechTimeoutMs -> { done = true; Result.NoSpeech }
            else -> Result.Continue
        }
    }

    private fun finish(truncated: Boolean): Result { done = true; return Result.Complete(buffer.build(), truncated) }
}

/** Growable PCM buffer without boxing. */
class ShortArrayBuilder(capacity: Int = Pcm.SAMPLE_RATE) {
    private var data = ShortArray(capacity)
    var size = 0
        private set

    fun append(src: ShortArray) {
        if (size + src.size > data.size) data = data.copyOf(max(data.size * 2, size + src.size))
        src.copyInto(data, size)
        size += src.size
    }

    fun build(): ShortArray = data.copyOf(size)
}

/** Fixed-size ring of the most recent audio, used to include the wake phrase in the owner check. */
class PreRollBuffer(private val capacitySamples: Int = Pcm.SAMPLE_RATE * 3 / 2) {
    private val ring = ShortArray(capacitySamples)
    private var write = 0
    private var filled = 0

    fun append(frame: ShortArray) {
        for (s in frame) {
            ring[write] = s
            write = (write + 1) % capacitySamples
        }
        filled = minOf(capacitySamples, filled + frame.size)
    }

    fun snapshot(): ShortArray = ShortArray(filled) { i -> ring[(write - filled + i + capacitySamples) % capacitySamples] }

    fun clear() { write = 0; filled = 0; ring.fill(0) }
}
