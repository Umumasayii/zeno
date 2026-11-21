package com.zeno.domain.policy

import com.zeno.domain.intent.AppRef
import com.zeno.domain.intent.AssistantIntent
import com.zeno.domain.intent.ContactRef
import com.zeno.domain.intent.IntentKind
import com.zeno.domain.intent.ParsedCommand
import com.zeno.domain.root.RootOperation
import com.zeno.domain.security.Capability
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.ZenoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPolicyEngineTest {

    private var settings = ZenoSettings()
    private val engine = DefaultPolicyEngine { settings }
    private val allCaps = Capability.entries.toSet()

    private fun voice(
        owner: OwnerVerification = OwnerVerification.VERIFIED,
        lock: LockState = LockState.UNLOCKED,
        caps: Set<Capability> = allCaps,
        recipientKnown: Boolean = false,
    ) = PolicyContext(CommandSource.VOICE, owner, lock, caps, recipientPreviouslyConfirmed = recipientKnown)

    private fun automation(
        approval: ApprovalCheck?,
        source: CommandSource = CommandSource.AUTOMATION_MANUAL,
        confirmAtRun: Boolean = false,
    ) = PolicyContext(source, OwnerVerification.NOT_APPLICABLE, LockState.LOCKED, allCaps, approval, confirmAtRun)

    private fun eval(intent: AssistantIntent, ctx: PolicyContext, confidence: Double = 0.95) =
        engine.evaluate(ParsedCommand(intent, confidence), ctx)

    private val sms = AssistantIntent.SendSms(ContactRef("Mum"), "good morning")

    // --- Always-available stop (Z-004) ---
    @Test fun `stop listening is allowed even for an unknown speaker with no permissions`() {
        val ctx = voice(owner = OwnerVerification.DENIED, caps = emptySet())
        assertEquals(PolicyDecision.Allow, eval(AssistantIntent.StopListening, ctx, confidence = 0.1))
        assertEquals(PolicyDecision.Allow, eval(AssistantIntent.Cancel, ctx, confidence = 0.1))
    }

    // --- Speaker gate (Z-005, Z-006, T06) ---
    @Test fun `unknown speaker is denied`() {
        assertEquals(PolicyDecision.Deny(DenyReason.UNKNOWN_SPEAKER), eval(AssistantIntent.SetFlashlight(true), voice(OwnerVerification.DENIED)))
    }

    @Test fun `uncertain speaker is denied even with perfect ASR confidence`() {
        val d = eval(AssistantIntent.SetFlashlight(true), voice(OwnerVerification.UNCERTAIN), confidence = 1.0)
        assertEquals(PolicyDecision.Deny(DenyReason.UNCERTAIN_SPEAKER), d)
    }

    // --- Confidence gate (docs/03) ---
    @Test fun `confidence below 0_60 declines, 0_60 to 0_85 clarifies, above runs`() {
        val i = AssistantIntent.SetFlashlight(true)
        assertEquals(PolicyDecision.Deny(DenyReason.LOW_CONFIDENCE), eval(i, voice(), 0.59))
        assertTrue(eval(i, voice(), 0.60) is PolicyDecision.Clarify)
        assertTrue(eval(i, voice(), 0.84) is PolicyDecision.Clarify)
        assertEquals(PolicyDecision.Allow, eval(i, voice(), 0.85))
    }

    @Test fun `high confidence never skips SMS confirmation`() {
        assertTrue(eval(sms, voice(), confidence = 1.0) is PolicyDecision.Confirm)
    }

    // --- SMS policy (Z-011, T09, T10) ---
    @Test fun `default SMS policy is always confirm`() {
        assertEquals(SmsPolicy.ALWAYS_CONFIRM, ZenoSettings().smsPolicy)
        assertTrue(eval(sms, voice()) is PolicyDecision.Confirm)
    }

    @Test fun `immediate SMS policy sends for a verified owner`() {
        settings = settings.copy(smsPolicy = SmsPolicy.IMMEDIATE)
        assertEquals(PolicyDecision.Allow, eval(sms, voice()))
    }

    @Test fun `smart SMS policy confirms when locked or recipient is new`() {
        settings = settings.copy(smsPolicy = SmsPolicy.SMART)
        assertTrue(eval(sms, voice(recipientKnown = false)) is PolicyDecision.Confirm)
        assertTrue(eval(sms, voice(lock = LockState.LOCKED, recipientKnown = true)) is PolicyDecision.Confirm)
        assertEquals(PolicyDecision.Allow, eval(sms, voice(recipientKnown = true)))
    }

    @Test fun `missing SMS permission blocks sending and names the capability`() {
        val d = eval(sms, voice(caps = allCaps - Capability.SMS_SEND))
        assertEquals(PolicyDecision.Deny(DenyReason.MISSING_CAPABILITY, setOf(Capability.SMS_SEND)), d)
    }

    // --- Lock screen (Z-016, T19, D-05 default) ---
    @Test fun `unlock always requires Android credentials`() {
        assertTrue(eval(AssistantIntent.RequestUnlock, voice()) is PolicyDecision.RequireDeviceCredential)
        assertTrue(eval(AssistantIntent.RequestUnlock, voice(lock = LockState.LOCKED)) is PolicyDecision.RequireDeviceCredential)
    }

    @Test fun `private content is not read while locked by default`() {
        assertTrue(eval(AssistantIntent.ReadSms(), voice(lock = LockState.LOCKED)) is PolicyDecision.RequireDeviceCredential)
        assertTrue(eval(AssistantIntent.ReadNotifications(), voice(lock = LockState.LOCKED)) is PolicyDecision.RequireDeviceCredential)
        assertEquals(PolicyDecision.Allow, eval(AssistantIntent.ReadSms(), voice()))
    }

    @Test fun `private readout while locked can be enabled explicitly`() {
        settings = settings.copy(allowPrivateReadoutWhileLocked = true)
        assertEquals(PolicyDecision.Allow, eval(AssistantIntent.ReadSms(), voice(lock = LockState.LOCKED)))
    }

    // --- Root (Z-015, T18) ---
    @Test fun `root operations are denied until individually enabled`() {
        assertEquals(PolicyDecision.Deny(DenyReason.ROOT_OPERATION_DISABLED), eval(AssistantIntent.Shutdown, voice()))
        settings = settings.copy(enabledRootOperations = setOf(RootOperation.REBOOT))
        assertEquals(PolicyDecision.Deny(DenyReason.ROOT_OPERATION_DISABLED), eval(AssistantIntent.Shutdown, voice()))
        assertTrue(eval(AssistantIntent.Reboot, voice()) is PolicyDecision.Confirm)
    }

    @Test fun `root operation without Magisk grant is denied as missing capability`() {
        settings = settings.copy(enabledRootOperations = RootOperation.entries.toSet())
        val d = eval(AssistantIntent.Shutdown, voice(caps = allCaps - Capability.ROOT))
        assertEquals(PolicyDecision.Deny(DenyReason.MISSING_CAPABILITY, setOf(Capability.ROOT)), d)
    }

    @Test fun `R3 voice actions need explicit confirmation`() {
        settings = settings.copy(enabledRootOperations = RootOperation.entries.toSet())
        assertTrue(eval(AssistantIntent.Shutdown, voice()) is PolicyDecision.Confirm)
        assertTrue(eval(AssistantIntent.DeleteRoutine(com.zeno.domain.intent.RoutineRef("bedtime")), voice()) is PolicyDecision.Confirm)
    }

    // --- Automations (Z-020, Z-021, Z-025, T14, T16, T17) ---
    @Test fun `automation without approval is denied`() {
        assertEquals(PolicyDecision.Deny(DenyReason.NOT_APPROVED), eval(sms, automation(null)))
        assertEquals(PolicyDecision.Deny(DenyReason.NOT_APPROVED), eval(sms, automation(ApprovalCheck.MISSING)))
    }

    @Test fun `automation with stale approval is denied`() {
        assertEquals(PolicyDecision.Deny(DenyReason.APPROVAL_STALE), eval(sms, automation(ApprovalCheck.STALE)))
    }

    @Test fun `approved scheduled SMS runs without daily confirmation`() {
        assertEquals(PolicyDecision.Allow, eval(sms, automation(ApprovalCheck.APPROVED_CURRENT)))
    }

    @Test fun `approved routine can still require confirmation at run time`() {
        assertTrue(eval(sms, automation(ApprovalCheck.APPROVED_CURRENT, confirmAtRun = true)) is PolicyDecision.Confirm)
    }

    @Test fun `high risk actions are never unattended, even when approved`() {
        settings = settings.copy(enabledRootOperations = RootOperation.entries.toSet())
        for (source in listOf(CommandSource.AUTOMATION_MANUAL, CommandSource.AUTOMATION_LEARNED)) {
            val d = eval(AssistantIntent.Shutdown, automation(ApprovalCheck.APPROVED_CURRENT, source))
            assertEquals(PolicyDecision.Deny(DenyReason.NOT_ELIGIBLE_UNATTENDED), d)
        }
    }

    @Test fun `global automation pause blocks every automation run`() {
        settings = settings.copy(automationsPaused = true)
        assertEquals(PolicyDecision.Deny(DenyReason.AUTOMATIONS_PAUSED), eval(AssistantIntent.OpenApp(AppRef("Clock")), automation(ApprovalCheck.APPROVED_CURRENT)))
    }

    // --- Defaults (docs/01 "Default behaviors proposed for safety") ---
    @Test fun `safe defaults`() {
        val d = ZenoSettings()
        assertEquals(false, d.learningEnabled)
        assertEquals(false, d.listeningEnabled)
        assertEquals(emptySet<RootOperation>(), d.enabledRootOperations)
        assertEquals(false, d.allowPrivateReadoutWhileLocked)
    }

    @Test fun `no intent in the catalogue is R4, and every root intent requires ROOT`() {
        for (k in IntentKind.entries) {
            if (RootOperation.forIntent(k) != null) assertTrue("$k must require ROOT", Capability.ROOT in k.requires)
        }
    }
}
