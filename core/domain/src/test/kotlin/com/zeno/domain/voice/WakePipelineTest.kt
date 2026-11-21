package com.zeno.domain.voice

import com.zeno.domain.audio.Pcm
import com.zeno.domain.policy.OwnerVerification
import com.zeno.domain.voiceid.EnrollmentSession
import com.zeno.domain.voiceid.SpeakerVerifier
import com.zeno.domain.voiceid.VerificationNote
import com.zeno.domain.voiceid.VoiceProfile
import com.zeno.testing.FakeSpeakerEmbedder
import com.zeno.testing.FakeWakeWordDetector
import com.zeno.testing.SyntheticAudio
import com.zeno.testing.SyntheticAudio.OTHER_HZ
import com.zeno.testing.SyntheticAudio.OWNER_HZ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePipelineTest {

    private val embedder = FakeSpeakerEmbedder()
    private val detector = FakeWakeWordDetector()
    private var now = 0L
    private var speaking = false

    private val profile: VoiceProfile = EnrollmentSession(embedder).run {
        (1..5).forEach { addSample(SyntheticAudio.utterance(OWNER_HZ, seed = it)) }
        finish()
    }

    private fun pipeline(p: VoiceProfile? = profile) = WakePipeline(
        detector, WakeScoreFilter(), embedder, SpeakerVerifier(), { p }, { now }, { speaking },
    ).apply { start() }

    /** Wake word, short pause, command, then quiet; returns every event in order. */
    private fun WakePipeline.feed(hz: Double, seed: Int): List<WakeEvent> {
        val stream = SyntheticAudio.noise(400, seed = seed) +
            SyntheticAudio.tone(600, hz, seed = seed) + SyntheticAudio.noise(150, seed = seed + 1) +
            SyntheticAudio.tone(1_200, hz, seed = seed + 2) + SyntheticAudio.noise(1_500, seed = seed + 3)
        return SyntheticAudio.frames(stream).mapNotNull { f -> now += Pcm.FRAME_MS; onFrame(f) }
    }

    @Test fun `score filter needs patience, honours the refractory period and ignores Zeno's own voice`() {
        val f = WakeScoreFilter(threshold = 0.5f, patience = 2, refractoryMs = 1_000)
        assertFalse(f.onScore(0.9f, 0, false))
        assertTrue(f.onScore(0.9f, 20, false))
        assertFalse(f.onScore(0.9f, 40, false))
        assertFalse(f.onScore(0.9f, 60, false))
        assertFalse(f.onScore(0.9f, 1_100, true))
        assertFalse(f.onScore(0.9f, 1_120, true))
        assertFalse(f.onScore(0.9f, 1_140, false))
        assertTrue(f.onScore(0.9f, 1_160, false))
        assertFalse(f.onScore(0.2f, 3_000, false))
    }

    @Test fun `owner command is captured with the wake phrase and handed off once`() {
        val p = pipeline()
        val events = p.feed(OWNER_HZ, seed = 50)
        assertEquals(WakeEvent.WakeDetected, events[0])
        val ready = events[1] as WakeEvent.CommandReady
        assertEquals(2, events.size)
        assertEquals(OwnerVerification.VERIFIED, ready.verification.outcome)
        // Includes the wake phrase from the pre-roll plus the command.
        assertTrue(Pcm.durationMs(ready.pcm.size) > 1_800)
        assertEquals(WakePhase.HANDED_OFF, p.phase)
        assertFalse(p.phase.holdsMicrophone)
        assertNull(p.onFrame(ShortArray(Pcm.FRAME_SAMPLES)))
        p.resume()
        assertEquals(WakePhase.STANDBY, p.phase)
    }

    @Test fun `another speaker is rejected and their speech is never handed out`() {
        val p = pipeline()
        val events = p.feed(OTHER_HZ, seed = 60)
        assertEquals(WakeEvent.WakeDetected, events[0])
        val rejected = events[1] as WakeEvent.NotVerified
        assertEquals(OwnerVerification.DENIED, rejected.verification.outcome)
        assertTrue(events.none { it is WakeEvent.CommandReady })
        assertEquals(WakePhase.STANDBY, p.phase)
    }

    @Test fun `without an enrolled profile nothing is handed out`() {
        val events = pipeline(p = null).feed(OWNER_HZ, seed = 70)
        assertEquals(VerificationNote.NOT_ENROLLED, (events[1] as WakeEvent.NotVerified).verification.note)
    }

    @Test fun `wake word with no command returns to standby`() {
        val p = pipeline()
        // Real detectors fire at the end of the phrase: only a short tail of it follows the detection.
        val stream = SyntheticAudio.tone(80, OWNER_HZ) + SyntheticAudio.noise(5_000)
        val events = SyntheticAudio.frames(stream).mapNotNull { now += 20; p.onFrame(it) }
        assertEquals(listOf(WakeEvent.WakeDetected, WakeEvent.NoCommand), events)
        assertEquals(WakePhase.STANDBY, p.phase)
    }

    @Test fun `Zeno speaking does not trigger the wake word`() {
        speaking = true
        assertTrue(pipeline().feed(OWNER_HZ, seed = 80).isEmpty())
    }

    @Test fun `kill switch drops the capture immediately`() {
        val p = pipeline()
        val frames = SyntheticAudio.frames(SyntheticAudio.tone(600, OWNER_HZ) + SyntheticAudio.tone(400, OWNER_HZ, seed = 3))
        frames.take(40).forEach { now += 20; p.onFrame(it) }
        assertEquals(WakePhase.CAPTURING, p.phase)
        p.stop()
        assertEquals(WakePhase.DISABLED, p.phase)
        assertFalse(p.phase.holdsMicrophone)
        assertTrue(frames.drop(40).all { p.onFrame(it) == null })
        p.resume() // resume only works from HANDED_OFF
        assertEquals(WakePhase.DISABLED, p.phase)
    }

    @Test fun `frames of the wrong size are refused in standby`() {
        assertTrue(runCatching { pipeline().onFrame(ShortArray(100)) }.isFailure)
    }
}
