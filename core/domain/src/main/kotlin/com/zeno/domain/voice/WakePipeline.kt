package com.zeno.domain.voice

import com.zeno.domain.audio.CommandEndpointer
import com.zeno.domain.audio.PreRollBuffer
import com.zeno.domain.policy.OwnerVerification
import com.zeno.domain.voiceid.SpeakerEmbedder
import com.zeno.domain.voiceid.SpeakerVerifier
import com.zeno.domain.voiceid.Verification
import com.zeno.domain.voiceid.VoiceProfile

/**
 * Wake-word engine port. Implemented by an owner-installed, licence-checked model (ADR-007); this build
 * ships none. [phrases] are the phrases the installed model was trained for (Z-001: a typed name is not
 * proof the model recognizes it).
 */
interface WakeWordDetector {
    val modelId: String
    val phrases: List<String>
    /** Samples per call to [score]; the pipeline feeds exactly this many. */
    val frameSamples: Int
    /** Detection score in 0..1 for the newest frame. */
    fun score(frame: ShortArray): Float
    fun reset()
}

/**
 * Engine-independent decision on raw scores: [patience] consecutive frames at or above [threshold], then a
 * [refractoryMs] pause so one utterance triggers once. Scores are ignored while Zeno itself is speaking
 * (docs/03 "avoid responding to its own TTS").
 */
class WakeScoreFilter(
    val threshold: Float = 0.5f,
    val patience: Int = 2,
    val refractoryMs: Long = 2_000,
) {
    private var run = 0
    private var lastFireMs = Long.MIN_VALUE / 2

    fun onScore(score: Float, nowMs: Long, zenoSpeaking: Boolean): Boolean {
        if (zenoSpeaking || nowMs - lastFireMs < refractoryMs) { run = 0; return false }
        run = if (score >= threshold) run + 1 else 0
        if (run < patience) return false
        run = 0
        lastFireMs = nowMs
        return true
    }

    fun reset() { run = 0 }
}

/** What the wake pipeline tells the app. Audio leaves the pipeline only inside [CommandReady]. */
sealed interface WakeEvent {
    data object WakeDetected : WakeEvent
    /** Verified owner. [pcm] includes the wake phrase; hand it to the transcriber once, then drop it. */
    class CommandReady(val pcm: ShortArray, val verification: Verification) : WakeEvent
    /** Not the owner (or not sure): the audio was discarded without transcription. */
    data class NotVerified(val verification: Verification) : WakeEvent
    data object NoCommand : WakeEvent
}

enum class WakePhase(val holdsMicrophone: Boolean) {
    DISABLED(false),
    /** Only the wake-word detector reads audio; the last 1.5 s stay in a memory ring. */
    STANDBY(true),
    /** After the wake word: capturing one command for the owner check (VoiceState.OWNER_CHECK). */
    CAPTURING(true),
    /** Command handed to the app; the pipeline ignores audio until [WakePipeline.resume]. */
    HANDED_OFF(false),
}

/**
 * Wake word → capture → owner check, with no transcription before the owner is verified: speech from
 * anyone else is dropped in memory (docs/08 pipeline separation, Z-005). Not thread-safe; feed it from the
 * single audio thread. The app releases the microphone whenever [phase] does not hold it.
 */
class WakePipeline(
    private val detector: WakeWordDetector,
    private val filter: WakeScoreFilter,
    private val embedder: SpeakerEmbedder,
    private val verifier: SpeakerVerifier,
    private val profile: () -> VoiceProfile?,
    private val clock: () -> Long,
    private val zenoSpeaking: () -> Boolean = { false },
    private val newEndpointer: (preRoll: ShortArray) -> CommandEndpointer = { CommandEndpointer(preRoll = it) },
) {
    var phase: WakePhase = WakePhase.DISABLED
        private set
    private val preRoll = PreRollBuffer()
    private var endpointer: CommandEndpointer? = null

    fun start() {
        if (phase == WakePhase.DISABLED) enterStandby()
    }

    /** Kill switch (Z-004): drops all buffered audio immediately. */
    fun stop() {
        phase = WakePhase.DISABLED
        endpointer = null
        preRoll.clear()
        detector.reset()
        filter.reset()
    }

    /** The app finished with the last command (or it was rejected): listen for the wake word again. */
    fun resume() {
        if (phase == WakePhase.HANDED_OFF) enterStandby()
    }

    fun onFrame(frame: ShortArray): WakeEvent? = when (phase) {
        WakePhase.DISABLED, WakePhase.HANDED_OFF -> null
        WakePhase.STANDBY -> standby(frame)
        WakePhase.CAPTURING -> capture(frame)
    }

    private fun standby(frame: ShortArray): WakeEvent? {
        require(frame.size == detector.frameSamples) { "expected ${detector.frameSamples} samples, got ${frame.size}" }
        preRoll.append(frame)
        val fired = filter.onScore(detector.score(frame), clock(), zenoSpeaking())
        if (!fired) return null
        endpointer = newEndpointer(preRoll.snapshot())
        preRoll.clear()
        phase = WakePhase.CAPTURING
        return WakeEvent.WakeDetected
    }

    private fun capture(frame: ShortArray): WakeEvent? = when (val r = endpointer!!.accept(frame)) {
        CommandEndpointer.Result.Continue -> null
        CommandEndpointer.Result.NoSpeech -> { endpointer = null; enterStandby(); WakeEvent.NoCommand }
        is CommandEndpointer.Result.Complete -> {
            endpointer = null
            val v = verifier.verify(embedder.embed(r.pcm), profile(), embedder, clock())
            if (v.outcome == OwnerVerification.VERIFIED) {
                phase = WakePhase.HANDED_OFF
                WakeEvent.CommandReady(r.pcm, v)
            } else {
                enterStandby() // r.pcm goes out of scope here: never transcribed, never stored
                WakeEvent.NotVerified(v)
            }
        }
    }

    private fun enterStandby() {
        detector.reset()
        filter.reset()
        phase = WakePhase.STANDBY
    }
}
