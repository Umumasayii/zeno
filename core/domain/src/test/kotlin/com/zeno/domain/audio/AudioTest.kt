package com.zeno.domain.audio

import com.zeno.testing.SyntheticAudio
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTest {

    private val gate = SampleQualityGate()
    private fun problem(pcm: ShortArray) = gate.check(AudioAnalyzer.analyze(pcm))

    @Test fun `levels are measured in dBFS`() {
        assertEquals(Pcm.SILENCE_DBFS, Pcm.rmsDbfs(ShortArray(320)), 0.0)
        // Full-scale sine: RMS = 1/sqrt(2) of full scale = -3 dBFS.
        assertEquals(-3.0, Pcm.rmsDbfs(SyntheticAudio.tone(100, 1000.0, amplitude = 32_767, noise = 0)), 0.2)
        val stats = AudioAnalyzer.analyze(SyntheticAudio.utterance(speechMs = 1_500))
        assertEquals(2_300, stats.durationMs)
        assertTrue(stats.speechMs in 1_400..1_600)
        assertTrue(stats.snrDb > 30)
    }

    @Test fun `a clean sample passes the quality gate`() {
        assertNull(problem(SyntheticAudio.utterance()))
    }

    @Test fun `bad samples are rejected with a reason the owner can act on`() {
        assertEquals(SampleProblem.NO_SPEECH, problem(SyntheticAudio.noise(2_000)))
        assertEquals(SampleProblem.NO_SPEECH, problem(ShortArray(0)))
        assertEquals(SampleProblem.TOO_SHORT, problem(SyntheticAudio.utterance(speechMs = 500)))
        assertEquals(SampleProblem.TOO_QUIET, problem(SyntheticAudio.utterance(amplitude = 200)))
        assertEquals(SampleProblem.CLIPPED, problem(SyntheticAudio.utterance(amplitude = 40_000)))
        assertEquals(SampleProblem.TOO_NOISY, problem(SyntheticAudio.utterance(noise = 3_000)))
    }

    @Test fun `endpointer completes after trailing silence and keeps the pre-roll`() {
        val preRoll = ShortArray(800) { 7 }
        val e = CommandEndpointer(preRoll = preRoll)
        val stream = SyntheticAudio.utterance(speechMs = 1_000, leadMs = 200, tailMs = 1_500)
        var result: CommandEndpointer.Result = CommandEndpointer.Result.Continue
        var fed = 0
        for (f in SyntheticAudio.frames(stream)) {
            result = e.accept(f)
            fed += f.size
            if (result != CommandEndpointer.Result.Continue) break
        }
        val done = result as CommandEndpointer.Result.Complete
        assertTrue(!done.truncated)
        // 200 ms lead + 1 s speech + ~700 ms trailing silence, well before the stream ends.
        assertTrue(Pcm.durationMs(fed) in 1_850..2_000)
        assertArrayEquals(preRoll, done.pcm.copyOf(800))
        assertEquals(preRoll.size + fed, done.pcm.size)
    }

    @Test fun `endpointer gives up when nobody speaks, and truncates endless speech`() {
        val quiet = CommandEndpointer(noSpeechTimeoutMs = 1_000)
        val r = SyntheticAudio.frames(SyntheticAudio.noise(2_000)).asSequence().map { quiet.accept(it) }.first { it != CommandEndpointer.Result.Continue }
        assertEquals(CommandEndpointer.Result.NoSpeech, r)

        val chatty = CommandEndpointer(maxMs = 2_000)
        val long = SyntheticAudio.frames(SyntheticAudio.tone(5_000, 200.0)).asSequence().map { chatty.accept(it) }.first { it != CommandEndpointer.Result.Continue }
        assertTrue((long as CommandEndpointer.Result.Complete).truncated)
    }

    @Test fun `a short click is not a command`() {
        val e = CommandEndpointer(noSpeechTimeoutMs = 3_000)
        val stream = SyntheticAudio.tone(60, 200.0) + SyntheticAudio.noise(4_000)
        val r = SyntheticAudio.frames(stream).asSequence().map { e.accept(it) }.first { it != CommandEndpointer.Result.Continue }
        assertEquals(CommandEndpointer.Result.NoSpeech, r)
    }

    @Test fun `pre-roll ring keeps only the newest audio in order`() {
        val ring = PreRollBuffer(capacitySamples = 5)
        ring.append(shortArrayOf(1, 2, 3))
        assertArrayEquals(shortArrayOf(1, 2, 3), ring.snapshot())
        ring.append(shortArrayOf(4, 5, 6, 7))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), ring.snapshot())
        ring.clear()
        assertEquals(0, ring.snapshot().size)
    }
}
