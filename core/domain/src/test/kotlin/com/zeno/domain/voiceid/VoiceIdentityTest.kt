package com.zeno.domain.voiceid

import com.zeno.domain.audio.SampleProblem
import com.zeno.domain.policy.OwnerVerification
import com.zeno.testing.FakeSpeakerEmbedder
import com.zeno.testing.SyntheticAudio
import com.zeno.testing.SyntheticAudio.OTHER_HZ
import com.zeno.testing.SyntheticAudio.OWNER_HZ
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceIdentityTest {

    private val embedder = FakeSpeakerEmbedder()
    private fun owner(seed: Int) = SyntheticAudio.utterance(OWNER_HZ, seed = seed)
    private fun other(seed: Int) = SyntheticAudio.utterance(OTHER_HZ, seed = seed)

    private fun enrolledProfile(): VoiceProfile {
        val s = EnrollmentSession(embedder, clock = { 42L })
        (1..5).forEach { assertTrue(s.addSample(owner(it)) is SampleResult.Accepted) }
        return s.finish()
    }

    @Test fun `vector maths`() {
        assertEquals(1.0, Vectors.cosine(floatArrayOf(1f, 0f), floatArrayOf(3f, 0f)), 1e-9)
        assertEquals(0.0, Vectors.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 2f)), 1e-9)
        assertEquals(0.0, Vectors.cosine(floatArrayOf(0f, 0f), floatArrayOf(0f, 2f)), 1e-9)
        val c = Vectors.centroid(listOf(floatArrayOf(2f, 0f), floatArrayOf(0f, 5f)))
        assertEquals(c[0].toDouble(), c[1].toDouble(), 1e-6)
        assertEquals(1.0, Vectors.norm(c), 1e-6)
    }

    @Test fun `enrollment needs the minimum number of good samples`() {
        val s = EnrollmentSession(embedder)
        assertEquals("Hey Ada, open my messages.", s.prompt("Ada"))
        repeat(4) { assertFalse((s.addSample(owner(it)) as SampleResult.Accepted).canFinish) }
        assertTrue(runCatching { s.finish() }.isFailure)
        assertTrue((s.addSample(owner(9)) as SampleResult.Accepted).canFinish)
        val p = s.finish()
        assertEquals(5, p.embeddings.size)
        assertEquals("fake-speaker", p.modelId)
    }

    @Test fun `bad audio and a different speaker are not added to the profile`() {
        val s = EnrollmentSession(embedder)
        assertEquals(SampleResult.Rejected(SampleProblem.TOO_QUIET), s.addSample(SyntheticAudio.utterance(amplitude = 200)))
        assertEquals(0, embedder.calls) // rejected audio never reaches the model
        s.addSample(owner(1)); s.addSample(owner(2))
        assertTrue(s.addSample(other(3)) is SampleResult.Inconsistent)
        assertEquals(2, s.acceptedCount)
    }

    @Test fun `enrollment stops at the maximum`() {
        val s = EnrollmentSession(embedder, EnrollmentConfig(minSamples = 3, maxSamples = 3))
        (1..3).forEach { s.addSample(owner(it)) }
        assertEquals(SampleResult.Full, s.addSample(owner(4)))
    }

    @Test fun `owner is verified, someone else is denied`() {
        val p = enrolledProfile()
        val v = SpeakerVerifier()
        val ok = v.verify(embedder.embed(owner(100)), p, embedder, nowMs = 0)
        assertEquals(OwnerVerification.VERIFIED, ok.outcome)
        assertEquals(VerificationNote.MATCH, ok.note)
        val no = v.verify(embedder.embed(other(101)), p, embedder, nowMs = 0)
        assertEquals(OwnerVerification.DENIED, no.outcome)
    }

    @Test fun `borderline scores are uncertain, never verified`() {
        val p = enrolledProfile().copy(thresholds = VerificationThresholds(accept = 0.999, reject = 0.3))
        val r = SpeakerVerifier().verify(embedder.embed(owner(100)), p, embedder, 0)
        assertEquals(OwnerVerification.UNCERTAIN, r.outcome)
        assertEquals(VerificationNote.BORDERLINE, r.note)
    }

    @Test fun `no profile or a profile from another model never verifies`() {
        val e = embedder.embed(owner(100))
        assertEquals(VerificationNote.NOT_ENROLLED, SpeakerVerifier().verify(e, null, embedder, 0).note)
        val newModel = FakeSpeakerEmbedder(modelVersion = "2")
        val stale = SpeakerVerifier().verify(e, enrolledProfile(), newModel, 0)
        assertEquals(OwnerVerification.UNCERTAIN, stale.outcome)
        assertEquals(VerificationNote.PROFILE_STALE, stale.note)
        assertEquals(ProfileState.STALE, profileState(enrolledProfile(), newModel))
        assertEquals(ProfileState.STALE, profileState(enrolledProfile(), null))
        assertEquals(ProfileState.NONE, profileState(null, embedder))
    }

    @Test fun `an identical clip played again is treated as a possible replay`() {
        val p = enrolledProfile()
        val v = SpeakerVerifier()
        val clip = owner(100)
        assertEquals(OwnerVerification.VERIFIED, v.verify(embedder.embed(clip), p, embedder, 1_000).outcome)
        val again = v.verify(embedder.embed(clip), p, embedder, 2_000)
        assertEquals(OwnerVerification.UNCERTAIN, again.outcome)
        assertEquals(VerificationNote.POSSIBLE_REPLAY, again.note)
        // Replaying an enrollment sample is caught too.
        assertEquals(VerificationNote.POSSIBLE_REPLAY, SpeakerVerifier().verify(embedder.embed(owner(1)), p, embedder, 0).note)
        // A fresh utterance is fine.
        assertEquals(OwnerVerification.VERIFIED, v.verify(embedder.embed(owner(101)), p, embedder, 3_000).outcome)
    }

    @Test fun `replay memory expires and can be cleared`() {
        val guard = ReplayGuard(windowMs = 1_000)
        val e = floatArrayOf(1f, 2f, 3f)
        guard.remember(e, 0)
        assertTrue(guard.isSuspicious(e, null, 500))
        assertFalse(guard.isSuspicious(e, null, 2_000))
        guard.remember(e, 3_000)
        guard.clear()
        assertFalse(guard.isSuspicious(e, null, 3_000))
    }

    @Test fun `profile round-trips through JSON without audio`() {
        val p = enrolledProfile()
        val json = Json.encodeToString(VoiceProfile.serializer(), p)
        assertEquals(p, Json.decodeFromString(VoiceProfile.serializer(), json))
        assertFalse(json.contains("pcm"))
        assertTrue(runCatching { VerificationThresholds(accept = 0.4, reject = 0.5) }.isFailure)
    }

    @Test fun `calibration computes FAR, FRR, EER and a threshold for a target FAR`() {
        val genuine = listOf(0.9, 0.85, 0.8, 0.6)
        val impostor = listOf(0.1, 0.2, 0.3, 0.7)
        assertEquals(0.25, Calibration.falseAcceptRate(impostor, 0.5), 1e-9)
        assertEquals(0.25, Calibration.falseRejectRate(genuine, 0.7), 1e-9)
        val eer = Calibration.equalErrorRate(genuine, impostor)
        assertEquals(eer.far, eer.frr, 1e-9)
        assertEquals(0.25, eer.far, 1e-9)
        val t = Calibration.thresholdForFar(impostor, 0.0)
        assertEquals(0.0, Calibration.falseAcceptRate(impostor, t), 0.0)
        assertTrue(t > 0.7 && t < 0.70001)
        assertNull(null)
    }
}
