package com.zeno.testing

import com.zeno.domain.audio.Pcm
import com.zeno.domain.voice.WakeWordDetector
import com.zeno.domain.voiceid.SpeakerEmbedder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic 16 kHz PCM for tests. A "speaker" is a pitch: no real voices are used anywhere in the repo.
 * OWNER_HZ and OTHER_HZ are far enough apart for [FakeSpeakerEmbedder] to tell them apart.
 */
object SyntheticAudio {
    const val OWNER_HZ = 200.0
    const val OTHER_HZ = 450.0

    private fun samples(ms: Long) = (Pcm.SAMPLE_RATE * ms / 1000).toInt()

    fun noise(ms: Long, amplitude: Int = 50, seed: Int = 1): ShortArray {
        val r = Random(seed)
        return ShortArray(samples(ms)) { if (amplitude == 0) 0 else r.nextInt(-amplitude, amplitude + 1).toShort() }
    }

    fun tone(ms: Long, hz: Double, amplitude: Int = 8_000, noise: Int = 50, seed: Int = 1): ShortArray {
        val r = Random(seed)
        // Slight per-seed pitch wobble so two clips of the same "speaker" are never bit-identical.
        val f = hz * (1 + r.nextDouble(-0.02, 0.02))
        return ShortArray(samples(ms)) { i ->
            val v = amplitude * sin(2 * PI * f * i / Pcm.SAMPLE_RATE) + r.nextInt(-noise, noise + 1)
            v.roundToInt().coerceIn(-32_768, 32_767).toShort()
        }
    }

    /** Lead-in quiet, speech, trailing quiet: what one press of "Record sample" captures. */
    fun utterance(
        hz: Double = OWNER_HZ,
        speechMs: Long = 1_500,
        leadMs: Long = 400,
        tailMs: Long = 400,
        amplitude: Int = 8_000,
        noise: Int = 50,
        seed: Int = 1,
    ): ShortArray = noise(leadMs, noise, seed + 1000) + tone(speechMs, hz, amplitude, noise, seed) + noise(tailMs, noise, seed + 2000)

    fun frames(pcm: ShortArray, frame: Int = Pcm.FRAME_SAMPLES): List<ShortArray> =
        (0 until pcm.size / frame).map { pcm.copyOfRange(it * frame, (it + 1) * frame) }
}

/**
 * Deterministic stand-in for a speaker model: pitch picks the base direction (owner vs other), and a small
 * content-derived jitter makes every distinct clip slightly different. The same clip always gives the
 * same vector, like a digital replay.
 */
class FakeSpeakerEmbedder(
    override val modelId: String = "fake-speaker",
    override val modelVersion: String = "1",
    private val dimension: Int = 16,
    private val jitter: Double = 0.15,
) : SpeakerEmbedder {
    var calls = 0

    override fun embed(pcm: ShortArray): FloatArray {
        calls++
        val hz = voicedPitch(pcm)
        val base = DoubleArray(dimension).also { it[if (hz < 300) 0 else 1] = 1.0 }
        val r = Random(pcm.contentHashCode())
        return FloatArray(dimension) { (base[it] + jitter * r.nextDouble(-1.0, 1.0)).toFloat() }
    }

    /** Zero-crossing pitch estimate over loud frames only (background noise is ignored). */
    private fun voicedPitch(pcm: ShortArray): Double {
        var crossings = 0
        var voicedSamples = 0
        SyntheticAudio.frames(pcm).filter { Pcm.rmsDbfs(it) > -40 }.forEach { f ->
            voicedSamples += f.size
            for (i in 1 until f.size) if ((f[i - 1] < 0) != (f[i] < 0)) crossings++
        }
        return if (voicedSamples == 0) 0.0 else crossings / 2.0 * Pcm.SAMPLE_RATE / voicedSamples
    }
}

/** Scores loud frames as the wake word. */
class FakeWakeWordDetector(
    override val phrases: List<String> = listOf("Hey Zeno"),
    private val loudDbfs: Double = -30.0,
) : WakeWordDetector {
    override val modelId = "fake-wake"
    override val frameSamples = Pcm.FRAME_SAMPLES
    var resets = 0
    override fun score(frame: ShortArray): Float = if (Pcm.rmsDbfs(frame) > loudDbfs) 0.9f else 0.05f
    override fun reset() { resets++ }
}

class InMemoryVoiceProfileStore(var profile: com.zeno.domain.voiceid.VoiceProfile? = null) : com.zeno.domain.voiceid.VoiceProfileStore {
    var deletes = 0
    override fun load() = profile
    override fun save(profile: com.zeno.domain.voiceid.VoiceProfile) { this.profile = profile }
    override fun delete() { deletes++; profile = null }
}
