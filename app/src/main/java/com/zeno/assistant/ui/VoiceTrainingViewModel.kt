package com.zeno.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeno.assistant.voiceid.ClipRecorder
import com.zeno.assistant.voiceid.ClipResult
import com.zeno.domain.audio.SampleProblem
import com.zeno.domain.policy.OwnerVerification
import com.zeno.domain.voiceid.EnrollmentSession
import com.zeno.domain.voiceid.ProfileState
import com.zeno.domain.voiceid.SampleResult
import com.zeno.domain.voiceid.SpeakerEmbedder
import com.zeno.domain.voiceid.SpeakerVerifier
import com.zeno.domain.voiceid.VerificationNote
import com.zeno.domain.voiceid.VoiceProfile
import com.zeno.domain.voiceid.VoiceProfileStore
import com.zeno.domain.voiceid.profileState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoiceTrainingUiState(
    val loading: Boolean = true,
    /** Name of the installed speaker model, or null when none is installed (this build ships none). */
    val engine: String? = null,
    val profile: ProfileState = ProfileState.NONE,
    val profileSamples: Int = 0,
    val enrolling: Boolean = false,
    val accepted: Int = 0,
    val minSamples: Int = 0,
    val canFinish: Boolean = false,
    val prompt: String = "",
    val recording: Boolean = false,
    /** 0..1 microphone level while recording. */
    val level: Float = 0f,
    val message: String? = null,
    val messageIsProblem: Boolean = false,
)

interface VoiceTrainingActions {
    fun refresh()
    fun startEnrollment()
    fun recordSample()
    fun testVoice()
    fun stopRecording()
    fun finishEnrollment()
    fun cancelEnrollment()
    fun deleteProfile()
    fun onMicPermissionDenied()
}

/**
 * Owner voice enrollment, voice test and deletion (Z-005, screen 06). Audio exists only in memory for the
 * length of one sample; only embeddings are saved, encrypted. Without an installed speaker model nothing
 * is recorded at all.
 */
class VoiceTrainingViewModel(
    private val store: VoiceProfileStore,
    private val embedder: () -> SpeakerEmbedder?,
    private val recorder: ClipRecorder,
    private val assistantName: () -> String,
    private val onProfileChanged: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel(), VoiceTrainingActions {

    private val _state = MutableStateFlow(VoiceTrainingUiState())
    val state: StateFlow<VoiceTrainingUiState> = _state.asStateFlow()
    private var session: EnrollmentSession? = null
    private var recording: Job? = null

    init { refresh() }

    override fun refresh() {
        viewModelScope.launch {
            val p = withContext(io) { runCatching { store.load() }.getOrNull() }
            showProfile(p)
        }
    }

    private fun showProfile(p: VoiceProfile?) {
        val e = embedder()
        _state.update {
            it.copy(
                loading = false,
                engine = e?.let { m -> "${m.modelId} ${m.modelVersion}" },
                profile = profileState(p, e),
                profileSamples = p?.embeddings?.size ?: 0,
            )
        }
    }

    override fun startEnrollment() {
        val e = embedder() ?: return say("No speaker model is installed, so voice training can't start.", problem = true)
        val s = EnrollmentSession(e, clock = clock)
        session = s
        _state.update {
            it.copy(enrolling = true, accepted = 0, minSamples = s.minSamples, canFinish = false, prompt = s.prompt(assistantName()), message = null)
        }
    }

    override fun recordSample() {
        val s = session ?: return
        capture { pcm ->
            val (msg, problem) = when (val r = withContext(io) { s.addSample(pcm) }) {
                is SampleResult.Accepted -> (if (r.canFinish) "Sample saved. You can finish now, or add more in other places (quiet, noisy, far)." else "Sample saved.") to false
                is SampleResult.Rejected -> problemText(r.problem) to true
                is SampleResult.Inconsistent -> "That doesn't sound like your earlier samples, so it wasn't added. If someone else spoke, record again yourself." to true
                SampleResult.Full -> "That's the maximum number of samples. Tap Finish to save." to false
            }
            _state.update { it.copy(accepted = s.acceptedCount, canFinish = s.canFinish, prompt = s.prompt(assistantName()), message = msg, messageIsProblem = problem) }
        }
    }

    override fun testVoice() {
        val e = embedder() ?: return
        capture { pcm ->
            val p = withContext(io) { store.load() }
            val v = withContext(io) { SpeakerVerifier().verify(e.embed(pcm), p, e, clock()) }
            val score = v.score?.let { " (score %.2f)".format(it) }.orEmpty()
            val msg = when (v.note) {
                VerificationNote.MATCH -> "Matched your voice$score."
                VerificationNote.BORDERLINE -> "Not sure it's you$score. Zeno would ask you to repeat."
                VerificationNote.NO_MATCH -> "Didn't match your voice$score. Zeno would refuse the command."
                VerificationNote.NOT_ENROLLED -> "There is no voice profile yet."
                VerificationNote.PROFILE_STALE -> "Your profile was made with a different speaker model. Record your voice again."
                VerificationNote.POSSIBLE_REPLAY -> "This sounds like a replayed recording, so it wasn't trusted."
            }
            say(msg, problem = v.outcome != OwnerVerification.VERIFIED)
        }
    }

    /** Opens the microphone for one clip; the mic closes when the clip ends, on error, or on [stopRecording]. */
    private fun capture(use: suspend (ShortArray) -> Unit) {
        if (recording?.isActive == true) return
        _state.update { it.copy(recording = true, level = 0f, message = null) }
        recording = viewModelScope.launch {
            try {
                when (val r = recorder.record { db -> _state.update { it.copy(level = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)) } }) {
                    is ClipResult.Captured -> {
                        _state.update { it.copy(recording = false, level = 0f) }
                        use(r.pcm)
                    }
                    ClipResult.NoSpeech -> say(problemText(SampleProblem.NO_SPEECH), problem = true)
                    is ClipResult.Failed -> say(r.reason, problem = true)
                }
            } finally {
                _state.update { it.copy(recording = false, level = 0f) }
            }
        }
    }

    override fun stopRecording() {
        recording?.cancel()
        _state.update { it.copy(recording = false, level = 0f) }
    }

    override fun finishEnrollment() {
        val s = session ?: return
        viewModelScope.launch {
            val saved = withContext(io) {
                runCatching { s.finish().also(store::save) }
            }
            saved.onSuccess { p ->
                session = null
                _state.update { it.copy(enrolling = false, message = "Voice profile saved on this phone (${p.embeddings.size} samples, encrypted).", messageIsProblem = false) }
                showProfile(p)
                onProfileChanged()
            }.onFailure {
                say("Couldn't save: the samples weren't consistent enough. Record a few more.", problem = true)
            }
        }
    }

    override fun cancelEnrollment() {
        stopRecording()
        session = null
        _state.update { it.copy(enrolling = false, accepted = 0, canFinish = false, message = "Training cancelled. Nothing was saved.", messageIsProblem = false) }
    }

    override fun deleteProfile() {
        stopRecording()
        session = null
        viewModelScope.launch {
            withContext(io) { store.delete() }
            _state.update { it.copy(enrolling = false, message = "Voice profile deleted. Spoken commands are refused until you train your voice again.", messageIsProblem = false) }
            showProfile(null)
            onProfileChanged()
        }
    }

    override fun onMicPermissionDenied() = say("Microphone permission is off, so Zeno can't record your voice.", problem = true)

    private fun say(text: String, problem: Boolean) = _state.update { it.copy(message = text, messageIsProblem = problem) }

    override fun onCleared() { recording?.cancel() }

    companion object {
        fun problemText(p: SampleProblem): String = when (p) {
            SampleProblem.NO_SPEECH -> "I didn't hear speech. Tap Record, then read the phrase."
            SampleProblem.TOO_SHORT -> "That was too short. Read the whole phrase."
            SampleProblem.TOO_QUIET -> "Too quiet. Hold the phone a little closer or speak up."
            SampleProblem.CLIPPED -> "Too loud: the sound was distorted. Hold the phone a little further away."
            SampleProblem.TOO_NOISY -> "Too much background noise for a clear sample. Try again somewhere a bit quieter."
        }
    }
}
