package com.zeno.assistant.ui

import com.zeno.assistant.voiceid.ClipRecorder
import com.zeno.assistant.voiceid.ClipResult
import com.zeno.domain.voiceid.EnrollmentSession
import com.zeno.domain.voiceid.ProfileState
import com.zeno.testing.FakeSpeakerEmbedder
import com.zeno.testing.InMemoryVoiceProfileStore
import com.zeno.testing.SyntheticAudio
import com.zeno.testing.SyntheticAudio.OTHER_HZ
import com.zeno.testing.SyntheticAudio.OWNER_HZ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Enrollment, voice test and deletion with scripted clips: no microphone, no real voice, no files. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTrainingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val store = InMemoryVoiceProfileStore()
    private val embedder = FakeSpeakerEmbedder()
    private val clips = ArrayDeque<ClipResult>()
    private var recordCalls = 0
    private var profileChanges = 0
    private val recorder = ClipRecorder { onLevel -> recordCalls++; onLevel(-20.0); clips.removeFirst() }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(withEngine: Boolean = true) = VoiceTrainingViewModel(
        store, { if (withEngine) embedder else null }, recorder, { "Ada" }, { profileChanges++ }, { 7L }, dispatcher,
    )

    private fun owner(seed: Int) = ClipResult.Captured(SyntheticAudio.utterance(OWNER_HZ, seed = seed))

    @Test fun `without a speaker model nothing can be recorded`() {
        val vm = vm(withEngine = false)
        assertNull(vm.state.value.engine)
        vm.startEnrollment()
        assertFalse(vm.state.value.enrolling)
        assertTrue(vm.state.value.message!!.contains("No speaker model"))
        vm.recordSample()
        vm.testVoice()
        assertEquals(0, recordCalls)
    }

    @Test fun `owner enrolls, profile is saved and status is refreshed`() {
        val vm = vm()
        assertEquals(ProfileState.NONE, vm.state.value.profile)
        vm.startEnrollment()
        assertEquals("Hey Ada, open my messages.", vm.state.value.prompt)
        repeat(5) { i ->
            clips += owner(i + 1)
            vm.recordSample()
        }
        assertEquals(5, vm.state.value.accepted)
        assertTrue(vm.state.value.canFinish)
        assertFalse(vm.state.value.recording)
        vm.finishEnrollment()
        assertNotNull(store.profile)
        assertEquals(7L, store.profile!!.createdAtMillis)
        assertEquals(ProfileState.READY, vm.state.value.profile)
        assertFalse(vm.state.value.enrolling)
        assertEquals(1, profileChanges)
    }

    @Test fun `bad samples and another speaker are explained and not counted`() {
        val vm = vm()
        vm.startEnrollment()
        clips += ClipResult.Captured(SyntheticAudio.utterance(amplitude = 200))
        vm.recordSample()
        assertTrue(vm.state.value.messageIsProblem)
        assertTrue(vm.state.value.message!!.startsWith("Too quiet"))
        clips += owner(1); vm.recordSample()
        clips += owner(2); vm.recordSample()
        clips += ClipResult.Captured(SyntheticAudio.utterance(OTHER_HZ, seed = 3)); vm.recordSample()
        assertTrue(vm.state.value.message!!.contains("doesn't sound like your earlier samples"))
        clips += ClipResult.NoSpeech; vm.recordSample()
        assertTrue(vm.state.value.message!!.startsWith("I didn't hear speech"))
        clips += ClipResult.Failed("The microphone couldn't be opened."); vm.recordSample()
        assertEquals("The microphone couldn't be opened.", vm.state.value.message)
        assertEquals(2, vm.state.value.accepted)
        assertFalse(vm.state.value.canFinish)
    }

    @Test fun `cancel saves nothing`() {
        val vm = vm()
        vm.startEnrollment()
        repeat(5) { clips += owner(it + 1); vm.recordSample() }
        vm.cancelEnrollment()
        assertNull(store.profile)
        assertFalse(vm.state.value.enrolling)
    }

    @Test fun `test my voice matches the owner and refuses someone else`() {
        store.profile = EnrollmentSession(embedder).run { (1..5).forEach { addSample(SyntheticAudio.utterance(OWNER_HZ, seed = it)) }; finish() }
        val vm = vm()
        assertEquals(ProfileState.READY, vm.state.value.profile)
        clips += owner(50); vm.testVoice()
        assertTrue(vm.state.value.message!!, vm.state.value.message!!.startsWith("Matched your voice"))
        clips += ClipResult.Captured(SyntheticAudio.utterance(OTHER_HZ, seed = 51)); vm.testVoice()
        assertTrue(vm.state.value.message!!, vm.state.value.message!!.startsWith("Didn't match"))
        assertTrue(vm.state.value.messageIsProblem)
    }

    @Test fun `a profile from another model is shown as stale`() {
        store.profile = EnrollmentSession(FakeSpeakerEmbedder(modelVersion = "0")).run {
            (1..5).forEach { addSample(SyntheticAudio.utterance(OWNER_HZ, seed = it)) }; finish()
        }
        assertEquals(ProfileState.STALE, vm().state.value.profile)
    }

    @Test fun `delete erases the profile and refreshes status`() {
        store.profile = EnrollmentSession(embedder).run { (1..5).forEach { addSample(SyntheticAudio.utterance(OWNER_HZ, seed = it)) }; finish() }
        val vm = vm()
        vm.deleteProfile()
        assertNull(store.profile)
        assertEquals(1, store.deletes)
        assertEquals(ProfileState.NONE, vm.state.value.profile)
        assertEquals(1, profileChanges)
    }

    @Test fun `mic permission denied is explained`() {
        val vm = vm()
        vm.onMicPermissionDenied()
        assertTrue(vm.state.value.message!!.contains("Microphone permission is off"))
        assertEquals(0, recordCalls)
    }
}
