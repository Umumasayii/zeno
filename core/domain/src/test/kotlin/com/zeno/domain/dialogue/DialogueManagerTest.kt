package com.zeno.domain.dialogue

import com.zeno.domain.intent.AssistantIntent
import com.zeno.domain.intent.ContactRef
import com.zeno.domain.nlu.RuleBasedCommandParser
import com.zeno.domain.policy.CommandSource
import com.zeno.domain.policy.DefaultPolicyEngine
import com.zeno.domain.policy.LockState
import com.zeno.domain.policy.OwnerVerification
import com.zeno.domain.policy.PolicyContext
import com.zeno.domain.root.RootOperation
import com.zeno.domain.security.Capability
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.ZenoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueManagerTest {

    private var settings = ZenoSettings()
    private var lock = LockState.UNLOCKED
    private var granted: Set<Capability> = setOf(Capability.SMS_SEND, Capability.ACCESSIBILITY)

    private fun dm() = DialogueManager(
        RuleBasedCommandParser(),
        DefaultPolicyEngine { settings },
    ) { PolicyContext(CommandSource.PUSH_TO_TALK, OwnerVerification.NOT_APPLICABLE, lock, granted) }

    @Test fun `free-form sms asks for the body, reads back, and sends only after send`() {
        val d = dm()
        val t1 = d.handle("text my mother")
        assertEquals(TurnOutcome.ASKING, t1.outcome)
        assertEquals("What should I say to my mother?", t1.reply)

        val t2 = d.handle("I will be home at six")
        assertEquals(TurnOutcome.CONFIRMING, t2.outcome)
        assertTrue(t2.reply, t2.reply.contains("“I will be home at six”"))
        assertNull(t2.authorized)

        val t3 = d.handle("send")
        assertEquals(TurnOutcome.EXECUTE, t3.outcome)
        assertEquals(AssistantIntent.SendSms(ContactRef("my mother"), "I will be home at six"), t3.authorized!!.intent)
        assertEquals(DialogueState.Idle, d.state)
    }

    @Test fun `change it to re-reads and authorizes only the edited message`() {
        val d = dm()
        d.handle("text Mum hello")
        val edited = d.handle("change it to good morning")
        assertEquals(TurnOutcome.CONFIRMING, edited.outcome)
        val sent = d.handle("yes")
        assertEquals(AssistantIntent.SendSms(ContactRef("Mum"), "good morning"), sent.authorized!!.intent)
    }

    @Test fun `cancel works while asking and while confirming`() {
        val d = dm()
        d.handle("text Mum")
        assertEquals(TurnOutcome.CANCELLED, d.handle("never mind").outcome)
        d.handle("text Mum hello")
        val t = d.handle("no")
        assertEquals(TurnOutcome.CANCELLED, t.outcome)
        assertNull(t.authorized)
        assertEquals("Okay, I won't send it.", t.reply)
    }

    @Test fun `a different command is not accepted as a confirmation`() {
        val d = dm()
        d.handle("text Mum hello")
        val t = d.handle("call John")
        assertEquals(TurnOutcome.CONFIRMING, t.outcome)
        assertNull(t.authorized)
        // Second unclear answer cancels instead of looping or guessing.
        assertEquals(TurnOutcome.CANCELLED, d.handle("banana").outcome)
    }

    @Test fun `immediate sms policy sends without read-back`() {
        settings = ZenoSettings(smsPolicy = SmsPolicy.IMMEDIATE)
        val t = dm().handle("text Mum hello")
        assertEquals(TurnOutcome.EXECUTE, t.outcome)
        assertNotNull(t.authorized)
    }

    @Test fun `missing permission is explained, not executed`() {
        granted = emptySet()
        val t = dm().handle("text Mum hello")
        assertEquals(TurnOutcome.DENIED, t.outcome)
        assertTrue(t.reply, t.reply.contains("SMS permission"))
    }

    @Test fun `root actions are denied while root controls are off, and confirmed when on`() {
        granted = granted + Capability.ROOT
        assertEquals(TurnOutcome.DENIED, dm().handle("reboot the device").outcome)
        settings = ZenoSettings(enabledRootOperations = setOf(RootOperation.REBOOT))
        val d = dm()
        assertEquals(TurnOutcome.CONFIRMING, d.handle("reboot the device").outcome)
        assertEquals(AssistantIntent.Reboot, d.handle("confirm").authorized!!.intent)
    }

    @Test fun `locked phone needs android unlock for push-to-talk`() {
        lock = LockState.LOCKED
        val t = dm().handle("open Chrome")
        assertEquals(TurnOutcome.NEEDS_UNLOCK, t.outcome)
        assertNull(t.authorized)
    }

    @Test fun `stop listening is always executed, starting listening by voice is refused`() {
        granted = emptySet()
        lock = LockState.LOCKED
        assertEquals(AssistantIntent.StopListening, dm().handle("stop listening").authorized!!.intent)
        lock = LockState.UNLOCKED
        val t = dm().handle("start listening")
        assertNull(t.authorized)
        assertEquals(TurnOutcome.INFO, t.outcome)
    }

    @Test fun `unknown input, help and repeat`() {
        val d = dm()
        assertEquals(TurnOutcome.NOT_UNDERSTOOD, d.handle("banana helicopter").outcome)
        val help = d.handle("help")
        assertEquals(DialogueManager.HELP_TEXT, help.reply)
        assertEquals(help.reply, d.handle("repeat").reply)
    }

    @Test fun `unlock request never authorizes by voice`() {
        val t = dm().handle("unlock my phone")
        assertEquals(TurnOutcome.NEEDS_UNLOCK, t.outcome)
        assertNull(t.authorized)
    }

    @Test fun `not-built commands are explained after slot filling, never confirmed or authorized`() {
        granted = emptySet()
        val seen = mutableListOf<String>()
        val d = DialogueManager(
            RuleBasedCommandParser(), DefaultPolicyEngine { settings },
            notBuilt = { i, readBack -> if (i == AssistantIntent.StopListening) null else { seen += readBack; "not built: $readBack" } },
        ) { PolicyContext(CommandSource.PUSH_TO_TALK, OwnerVerification.NOT_APPLICABLE, lock, granted) }

        assertEquals(TurnOutcome.ASKING, d.handle("text Mum").outcome)
        val t = d.handle("hello there")
        assertEquals(TurnOutcome.INFO, t.outcome)
        assertNull(t.authorized)
        assertEquals("Send to Mum: “hello there”", seen.single())
        assertEquals(DialogueState.Idle, d.state)

        // Built commands still go through policy and execute.
        assertNotNull(d.handle("stop listening").authorized)
        // Lock-screen refusal wins over the not-built explanation.
        lock = LockState.LOCKED
        assertEquals(TurnOutcome.NEEDS_UNLOCK, d.handle("open Chrome").outcome)
    }
}
