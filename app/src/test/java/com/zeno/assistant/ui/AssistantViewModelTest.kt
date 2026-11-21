package com.zeno.assistant.ui

import com.zeno.assistant.voice.FakePlatform
import com.zeno.assistant.voice.FakeSpeaker
import com.zeno.assistant.voice.FakeSpeechInput
import com.zeno.assistant.voice.SpeechAvailability
import com.zeno.domain.policy.LockState
import com.zeno.domain.settings.ZenoSettings
import com.zeno.testing.InMemorySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Plain JVM tests of the M2a assistant flow with scripted speech (no microphone, no Android). */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private lateinit var repo: InMemorySettingsRepository
    private val speech = FakeSpeechInput()
    private val speaker = FakeSpeaker()
    private var lock = LockState.UNLOCKED
    private var now = 1_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(settings: ZenoSettings = ZenoSettings()): AssistantViewModel {
        repo = InMemorySettingsRepository(settings)
        return AssistantViewModel(repo, FakePlatform(), speech, speaker, { lock }, { now })
    }

    private fun AssistantViewModel.lastReply() = state.value.lines.last { !it.fromOwner }.text

    @Test fun `typed sms is understood, explained as M4, never confirmed or sent`() {
        val vm = vm()
        vm.submitTyped("text Mum hello")
        val reply = vm.lastReply()
        assertTrue(reply, reply.startsWith("I understood: Send to Mum: “hello”."))
        assertTrue(reply, reply.contains("built in M4") && reply.endsWith("Nothing was sent."))
        assertFalse(vm.state.value.awaitingConfirmation)
        assertTrue("typed input gets no spoken reply", speaker.spoken.isEmpty())
    }

    @Test fun `missing message body is asked for, then explained`() {
        val vm = vm()
        vm.submitTyped("text Mum")
        assertEquals("What should I say to Mum?", vm.lastReply())
        assertTrue(vm.state.value.awaitingAnswer)
        vm.submitTyped("running late")
        assertTrue(vm.lastReply(), vm.lastReply().contains("“running late”"))
        assertFalse(vm.state.value.awaitingAnswer)
    }

    @Test fun `stop listening really turns listening off`() {
        val vm = vm(ZenoSettings(listeningEnabled = true))
        vm.submitTyped("stop listening")
        assertFalse(repo.settings.value.listeningEnabled)
        assertTrue(vm.lastReply(), vm.lastReply().startsWith("Listening is off"))
    }

    @Test fun `push-to-talk shows listening only while audio flows, then speaks the reply`() {
        val vm = vm()
        vm.startPushToTalk()
        assertEquals(MicState.LISTENING, vm.state.value.mic)
        assertEquals(1, speaker.stopCalls) // TTS is silenced before the mic opens
        speech.partial("open chr")
        assertEquals("open chr", vm.state.value.partial)

        speech.hear("open Chrome")
        assertEquals(MicState.IDLE, vm.state.value.mic)
        assertEquals("", vm.state.value.partial)
        assertEquals(ChatLine(true, "open Chrome"), vm.state.value.lines[0])
        assertTrue(vm.lastReply(), vm.lastReply().contains("built in M3"))
        assertEquals(listOf(vm.lastReply()), speaker.spoken)
    }

    @Test fun `second tap while listening does not open a second session, stop cancels`() {
        val vm = vm()
        vm.startPushToTalk()
        vm.startPushToTalk()
        assertEquals(1, speech.listenCalls)
        vm.stopPushToTalk()
        assertEquals(1, speech.cancelCalls)
        assertFalse(speech.isListening)
        assertEquals(MicState.IDLE, vm.state.value.mic)
    }

    @Test fun `recognizer error closes the mic and is shown`() {
        val vm = vm()
        vm.startPushToTalk()
        speech.fail("I didn't hear anything.")
        assertEquals(MicState.IDLE, vm.state.value.mic)
        assertEquals("I didn't hear anything.", vm.lastReply())
    }

    @Test fun `mic permission denied is explained and typing still works`() {
        val vm = vm()
        vm.onMicPermissionDenied()
        assertTrue(vm.lastReply().contains("You can still type commands"))
        vm.submitTyped("help")
        assertTrue(vm.lastReply().startsWith("Try:"))
    }

    @Test fun `locked phone needs android unlock, stop still works`() {
        lock = LockState.LOCKED
        val vm = vm(ZenoSettings(listeningEnabled = true))
        vm.submitTyped("open Chrome")
        assertEquals("Unlock your phone to use push-to-talk.", vm.lastReply())
        vm.submitTyped("stop listening")
        assertFalse(repo.settings.value.listeningEnabled)
    }

    @Test fun `dialogue context expires after the timeout`() {
        val vm = vm()
        vm.submitTyped("text Mum")
        now += AssistantViewModel.DIALOGUE_TIMEOUT_MS + 1
        vm.submitTyped("banana helicopter") // not taken as the message body any more
        assertTrue(vm.lastReply(), vm.lastReply().startsWith("Sorry, I didn't understand"))
        assertFalse(vm.state.value.awaitingAnswer)
    }

    @Test fun `assistant name from settings is used by the parser`() {
        val vm = vm(ZenoSettings(assistantName = "Ada"))
        vm.submitTyped("hey Ada, help")
        assertTrue(vm.lastReply(), vm.lastReply().startsWith("Try:"))
    }

    @Test fun `speech availability is reported, transcript is bounded, blank input ignored`() {
        speech.availability = SpeechAvailability.Unavailable("Offline English speech isn't installed.")
        val vm = vm()
        vm.checkSpeech()
        assertEquals(SpeechAvailability.Unavailable("Offline English speech isn't installed."), vm.state.value.speech)
        vm.submitTyped("   ")
        assertTrue(vm.state.value.lines.isEmpty())
        repeat(40) { vm.submitTyped("help") }
        assertEquals(AssistantViewModel.MAX_LINES, vm.state.value.lines.size)
    }
}
