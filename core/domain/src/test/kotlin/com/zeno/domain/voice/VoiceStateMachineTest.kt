package com.zeno.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {

    private fun run(start: VoiceSession, vararg events: VoiceEvent): VoiceSession =
        events.fold(start) { s, e ->
            val t = VoiceStateMachine.next(s, e)
            assertTrue("rejected $e in ${s.state}", t.accepted)
            t.session
        }

    private val standby = VoiceSession.initial(listeningEnabled = true)

    @Test fun `happy path returns to standby`() {
        val end = run(
            standby,
            VoiceEvent.HotwordDetected, VoiceEvent.OwnerCheckStarted, VoiceEvent.OwnerVerified,
            VoiceEvent.UtteranceCaptured, VoiceEvent.Parsed(ParseResult.UNDERSTOOD),
            VoiceEvent.PolicyEvaluated(PolicyOutcome.ALLOWED), VoiceEvent.ExecutionFinished, VoiceEvent.ReportFinished,
        )
        assertEquals(VoiceState.STANDBY, end.state)
    }

    @Test fun `text Mum then clarification then confirm`() {
        val end = run(
            standby,
            VoiceEvent.HotwordDetected, VoiceEvent.OwnerCheckStarted, VoiceEvent.OwnerVerified,
            VoiceEvent.UtteranceCaptured, VoiceEvent.Parsed(ParseResult.NEEDS_CLARIFICATION),
            VoiceEvent.UtteranceCaptured, VoiceEvent.Parsed(ParseResult.UNDERSTOOD),
            VoiceEvent.PolicyEvaluated(PolicyOutcome.NEEDS_CONFIRMATION), VoiceEvent.UserConfirmed,
        )
        assertEquals(VoiceState.EXECUTING, end.state)
    }

    @Test fun `kill switch works from every state and releases the microphone`() {
        for (state in VoiceState.entries) {
            val t = VoiceStateMachine.next(VoiceSession(state, listeningEnabled = true), VoiceEvent.DisableListening)
            assertTrue(t.accepted)
            assertEquals(VoiceState.DISABLED, t.session.state)
            assertFalse(t.session.listeningEnabled)
            assertFalse(t.session.state.holdsMicrophone)
        }
    }

    @Test fun `cancel and timeout release the microphone from every active state`() {
        for (state in VoiceState.entries.filter { it.isActiveSession }) {
            for (e in listOf(VoiceEvent.Cancel, VoiceEvent.Timeout, VoiceEvent.Failure)) {
                val t = VoiceStateMachine.next(VoiceSession(state, true), e)
                assertTrue("$e from $state", t.accepted)
                assertFalse("$e from $state must release mic", t.session.state.holdsMicrophone)
            }
        }
    }

    @Test fun `unknown voice is rejected and never reaches listening`() {
        val s = run(standby, VoiceEvent.HotwordDetected, VoiceEvent.OwnerCheckStarted, VoiceEvent.OwnerNotVerified)
        assertEquals(VoiceState.REJECTED_UNKNOWN_VOICE, s.state)
        assertFalse(VoiceStateMachine.next(s, VoiceEvent.UtteranceCaptured).accepted)
    }

    @Test fun `cannot skip owner check or policy check`() {
        assertFalse(VoiceStateMachine.next(standby, VoiceEvent.UtteranceCaptured).accepted)
        val parsing = VoiceSession(VoiceState.PARSING, true)
        assertFalse(VoiceStateMachine.next(parsing, VoiceEvent.UserConfirmed).accepted)
        assertFalse(VoiceStateMachine.next(parsing, VoiceEvent.ExecutionFinished).accepted)
    }

    @Test fun `push to talk works while wake listening is off and returns to disabled`() {
        val off = VoiceSession.initial(listeningEnabled = false)
        assertFalse(off.state.holdsMicrophone)
        val end = run(
            off, VoiceEvent.PushToTalk, VoiceEvent.OwnerVerified, VoiceEvent.UtteranceCaptured,
            VoiceEvent.Parsed(ParseResult.UNDERSTOOD), VoiceEvent.PolicyEvaluated(PolicyOutcome.ALLOWED),
            VoiceEvent.ExecutionFinished, VoiceEvent.ReportFinished,
        )
        assertEquals(VoiceState.DISABLED, end.state)
    }

    @Test fun `hotword is ignored while disabled`() {
        assertFalse(VoiceStateMachine.next(VoiceSession.initial(false), VoiceEvent.HotwordDetected).accepted)
    }

    @Test fun `mic denied gives a blocked state that never holds the microphone`() {
        val s = run(standby, VoiceEvent.MicrophoneUnavailable)
        assertEquals(VoiceState.PERMISSION_BLOCKED, s.state)
        assertFalse(s.state.holdsMicrophone)
        assertEquals(VoiceState.STANDBY, run(s, VoiceEvent.EnableListening).state)
    }

    @Test fun `outcome states return to idle on acknowledge`() {
        val s = run(standby, VoiceEvent.HotwordDetected, VoiceEvent.Cancel, VoiceEvent.Acknowledge)
        assertEquals(VoiceState.STANDBY, s.state)
    }

    @Test fun `policy outcomes map to honest states`() {
        val pc = VoiceSession(VoiceState.POLICY_CHECK, true)
        fun out(o: PolicyOutcome) = VoiceStateMachine.next(pc, VoiceEvent.PolicyEvaluated(o)).session.state
        assertEquals(VoiceState.PERMISSION_BLOCKED, out(PolicyOutcome.PERMISSION_BLOCKED))
        assertEquals(VoiceState.LOCKED_UNAVAILABLE, out(PolicyOutcome.LOCKED))
        assertEquals(VoiceState.REJECTED_UNKNOWN_VOICE, out(PolicyOutcome.SPEAKER_REJECTED))
        assertEquals(VoiceState.UNSUPPORTED, out(PolicyOutcome.NOT_ALLOWED))
    }
}
