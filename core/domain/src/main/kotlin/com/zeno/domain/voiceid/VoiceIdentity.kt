package com.zeno.domain.voiceid

import com.zeno.domain.audio.AudioAnalyzer
import com.zeno.domain.audio.SampleProblem
import com.zeno.domain.audio.SampleQualityGate
import com.zeno.domain.policy.OwnerVerification
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Turns a voice clip into a fixed-size speaker embedding. Implemented by an owner-installed, licence-checked
 * model (docs/08, ADR-007/ADR-009); this build ships none. Runs fully on device.
 */
interface SpeakerEmbedder {
    val modelId: String
    val modelVersion: String
    fun embed(pcm: ShortArray): FloatArray
}

object Vectors {
    fun norm(v: FloatArray): Double = sqrt(v.sumOf { it.toDouble() * it })

    fun normalize(v: FloatArray): FloatArray {
        val n = norm(v)
        require(n > 0.0) { "zero vector" }
        return FloatArray(v.size) { (v[it] / n).toFloat() }
    }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "dimension mismatch ${a.size} vs ${b.size}" }
        val na = norm(a)
        val nb = norm(b)
        if (na == 0.0 || nb == 0.0) return 0.0
        var dot = 0.0
        for (i in a.indices) dot += a[i].toDouble() * b[i]
        return dot / (na * nb)
    }

    /** Mean of the L2-normalized vectors, normalized again. */
    fun centroid(vs: List<FloatArray>): FloatArray {
        require(vs.isNotEmpty()) { "no vectors" }
        val sum = DoubleArray(vs[0].size)
        vs.map(::normalize).forEach { v -> v.indices.forEach { sum[it] += v[it].toDouble() } }
        return normalize(FloatArray(sum.size) { sum[it].toFloat() })
    }
}

/**
 * Score thresholds for one speaker model. The defaults are placeholders until calibrated on the Pixel with
 * real owner and consenting non-owner samples (docs/08 measurement protocol); they are not a security claim.
 */
@Serializable
data class VerificationThresholds(val accept: Double = 0.70, val reject: Double = 0.50) {
    init { require(reject < accept) { "reject threshold must be below accept threshold" } }
}

/**
 * The owner's enrolled voice. Holds embeddings only, never audio. Bound to the exact model that produced
 * it: a different model or version makes the profile STALE and it is never used for verification.
 * Stored encrypted by the app (Keystore key, no backup).
 */
@Serializable
data class VoiceProfile(
    val modelId: String,
    val modelVersion: String,
    val embeddings: List<List<Float>>,
    val thresholds: VerificationThresholds,
    val createdAtMillis: Long,
) {
    init { require(embeddings.isNotEmpty()) { "a profile needs at least one embedding" } }

    val vectors: List<FloatArray> get() = embeddings.map { it.toFloatArray() }
    val centroid: FloatArray get() = Vectors.centroid(vectors)

    fun matches(embedder: SpeakerEmbedder) = modelId == embedder.modelId && modelVersion == embedder.modelVersion
}

enum class ProfileState { NONE, READY, STALE }

fun profileState(profile: VoiceProfile?, embedder: SpeakerEmbedder?): ProfileState = when {
    profile == null -> ProfileState.NONE
    embedder == null || !profile.matches(embedder) -> ProfileState.STALE
    else -> ProfileState.READY
}

/** Port for the encrypted profile file (Z-005 retrain/delete, Z-031 erase). */
interface VoiceProfileStore {
    fun load(): VoiceProfile?
    fun save(profile: VoiceProfile)
    /** Removes the profile and its key. After this, the old voice can never verify again (T014). */
    fun delete()
}

// ---------------------------------------------------------------------------------------------- enrollment

data class EnrollmentConfig(
    /** Accepted samples needed before the profile can be saved. A starting value, not a guarantee (docs/07). */
    val minSamples: Int = 5,
    val maxSamples: Int = 20,
    val gate: SampleQualityGate = SampleQualityGate(),
    val thresholds: VerificationThresholds = VerificationThresholds(),
) {
    init { require(minSamples in 3..maxSamples) { "minSamples must be at least 3 and at most maxSamples" } }
}

sealed interface SampleResult {
    data class Accepted(val accepted: Int, val canFinish: Boolean) : SampleResult
    data class Rejected(val problem: SampleProblem) : SampleResult
    /** Clean audio, but it doesn't sound like the earlier samples (another person, or a very different voice). */
    data class Inconsistent(val score: Double) : SampleResult
    data object Full : SampleResult
}

/**
 * Collects owner samples with quality gating and a consistency check, then builds a [VoiceProfile].
 * Varied phrases are prompted so the profile is not tied to one sentence. Nothing is saved until [finish].
 */
class EnrollmentSession(
    private val embedder: SpeakerEmbedder,
    private val config: EnrollmentConfig = EnrollmentConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val accepted = mutableListOf<FloatArray>()
    val acceptedCount: Int get() = accepted.size
    val canFinish: Boolean get() = accepted.size >= config.minSamples
    val minSamples: Int get() = config.minSamples

    fun prompt(assistantName: String): String = PROMPTS[accepted.size % PROMPTS.size].replace("{name}", assistantName)

    fun addSample(pcm: ShortArray): SampleResult {
        if (accepted.size >= config.maxSamples) return SampleResult.Full
        config.gate.check(AudioAnalyzer.analyze(pcm))?.let { return SampleResult.Rejected(it) }
        val e = embedder.embed(pcm)
        if (accepted.size >= 2) {
            val score = Vectors.cosine(e, Vectors.centroid(accepted))
            if (score < config.thresholds.reject) return SampleResult.Inconsistent(score)
        }
        accepted += e
        return SampleResult.Accepted(accepted.size, canFinish)
    }

    fun finish(): VoiceProfile {
        check(canFinish) { "need ${config.minSamples} accepted samples, have ${accepted.size}" }
        // The first two samples were accepted before a centroid existed: re-check them against the rest.
        val c = Vectors.centroid(accepted)
        val kept = accepted.filter { Vectors.cosine(it, c) >= config.thresholds.reject }
        check(kept.size >= config.minSamples) { "samples are not consistent enough; record more" }
        return VoiceProfile(
            modelId = embedder.modelId,
            modelVersion = embedder.modelVersion,
            embeddings = kept.map { it.toList() },
            thresholds = config.thresholds,
            createdAtMillis = clock(),
        )
    }

    companion object {
        val PROMPTS = listOf(
            "Hey {name}, open my messages.",
            "Hey {name}, what time is it?",
            "Turn on the flashlight, please.",
            "Hey {name}, set an alarm for seven tomorrow.",
            "Call my sister when I get home.",
            "Hey {name}, read my last notification.",
            "Lower the volume a little.",
            "Hey {name}, stop listening.",
        )
    }
}

// --------------------------------------------------------------------------------------------- verification

enum class VerificationNote { MATCH, BORDERLINE, NO_MATCH, NOT_ENROLLED, PROFILE_STALE, POSSIBLE_REPLAY }

data class Verification(val outcome: OwnerVerification, val note: VerificationNote, val score: Double? = null)

/**
 * Baseline replay check. A digital replay of the same recording produces an (almost) identical embedding,
 * which live speech never does. Catches exact replays of recent commands and of enrollment samples only;
 * it does NOT detect a new recording or a synthetic voice (docs/06, Z-006). Memory only.
 */
class ReplayGuard(
    private val windowMs: Long = 10 * 60_000L,
    private val maxRemembered: Int = 50,
    val nearIdentical: Double = 0.995,
) {
    private val recent = ArrayDeque<Pair<Long, FloatArray>>()

    fun isSuspicious(e: FloatArray, profile: VoiceProfile?, nowMs: Long): Boolean {
        prune(nowMs)
        val known = recent.map { it.second } + (profile?.vectors ?: emptyList())
        return known.any { it.size == e.size && Vectors.cosine(it, e) >= nearIdentical }
    }

    fun remember(e: FloatArray, nowMs: Long) {
        recent.addLast(nowMs to e)
        prune(nowMs)
        while (recent.size > maxRemembered) recent.removeFirst()
    }

    fun clear() = recent.clear()

    private fun prune(nowMs: Long) { while (recent.isNotEmpty() && nowMs - recent.first().first > windowMs) recent.removeFirst() }
}

/**
 * verified / uncertain / denied (docs/03). Uncertain is never upgraded by ASR confidence; the policy engine
 * denies voice commands unless the outcome is VERIFIED (DefaultPolicyEngine).
 */
class SpeakerVerifier(private val replay: ReplayGuard = ReplayGuard()) {

    fun verify(embedding: FloatArray, profile: VoiceProfile?, embedder: SpeakerEmbedder, nowMs: Long): Verification {
        when (profileState(profile, embedder)) {
            ProfileState.NONE -> return Verification(OwnerVerification.UNCERTAIN, VerificationNote.NOT_ENROLLED)
            ProfileState.STALE -> return Verification(OwnerVerification.UNCERTAIN, VerificationNote.PROFILE_STALE)
            ProfileState.READY -> Unit
        }
        val p = profile!!
        val score = Vectors.cosine(embedding, p.centroid)
        val suspicious = replay.isSuspicious(embedding, p, nowMs)
        replay.remember(embedding, nowMs)
        return when {
            score < p.thresholds.reject -> Verification(OwnerVerification.DENIED, VerificationNote.NO_MATCH, score)
            suspicious -> Verification(OwnerVerification.UNCERTAIN, VerificationNote.POSSIBLE_REPLAY, score)
            score >= p.thresholds.accept -> Verification(OwnerVerification.VERIFIED, VerificationNote.MATCH, score)
            else -> Verification(OwnerVerification.UNCERTAIN, VerificationNote.BORDERLINE, score)
        }
    }

    fun forgetRecent() = replay.clear()
}

// ---------------------------------------------------------------------------------------------- calibration

/**
 * False-accept / false-reject measurement from similarity scores (docs/08). Only scores are kept, never audio.
 * Genuine = owner trials; impostor = consenting non-owners or recordings.
 */
object Calibration {
    fun falseAcceptRate(impostor: List<Double>, threshold: Double): Double =
        if (impostor.isEmpty()) 0.0 else impostor.count { it >= threshold }.toDouble() / impostor.size

    fun falseRejectRate(genuine: List<Double>, threshold: Double): Double =
        if (genuine.isEmpty()) 0.0 else genuine.count { it < threshold }.toDouble() / genuine.size

    data class Point(val threshold: Double, val far: Double, val frr: Double)

    /** Threshold where FAR and FRR are closest (equal error rate), searched over the observed scores. */
    fun equalErrorRate(genuine: List<Double>, impostor: List<Double>): Point {
        require(genuine.isNotEmpty() && impostor.isNotEmpty()) { "need both genuine and impostor scores" }
        return (genuine + impostor).distinct().sorted()
            .map { Point(it, falseAcceptRate(impostor, it), falseRejectRate(genuine, it)) }
            .minWith(compareBy<Point> { kotlin.math.abs(it.far - it.frr) }.thenBy { it.far + it.frr })
    }

    /** Lowest threshold whose measured FAR is at most [targetFar]. */
    fun thresholdForFar(impostor: List<Double>, targetFar: Double): Double {
        require(impostor.isNotEmpty()) { "need impostor scores" }
        val candidates = impostor.distinct().sorted().map { Math.nextUp(it) } + impostor.min()
        return candidates.filter { falseAcceptRate(impostor, it) <= targetFar }.min()
    }
}

/** Serialized form for the app's encrypted store. Unknown fields are ignored, malformed input throws. */
object VoiceProfileCodec {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    fun encode(profile: VoiceProfile): ByteArray = json.encodeToString(VoiceProfile.serializer(), profile).encodeToByteArray()
    fun decode(bytes: ByteArray): VoiceProfile = json.decodeFromString(VoiceProfile.serializer(), bytes.decodeToString())
}
