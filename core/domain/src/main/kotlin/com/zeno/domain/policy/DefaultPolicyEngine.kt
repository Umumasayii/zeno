package com.zeno.domain.policy

import com.zeno.domain.intent.IntentKind
import com.zeno.domain.intent.ParsedCommand
import com.zeno.domain.root.RootOperation
import com.zeno.domain.security.Capability
import com.zeno.domain.security.RiskLevel
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.ZenoSettings

/**
 * Baseline policy from docs/03 (confidence gate), docs/04 (approval model) and docs/06 (risk levels,
 * lock screen, root). Rules are evaluated in a fixed order; the first matching rule wins.
 *
 * Invariants (each has a unit test):
 *  - STOP/CANCEL always allowed, for any speaker.
 *  - R4 never runs.
 *  - Speech confidence never upgrades an uncertain speaker or skips a required confirmation.
 *  - Automation runs need an approval that matches the current routine version.
 *  - Unlock always goes through Android credentials.
 */
class DefaultPolicyEngine(
    private val settings: () -> ZenoSettings,
) : PolicyEngine {

    override fun evaluate(command: ParsedCommand, context: PolicyContext): PolicyDecision {
        val kind = command.intent.kind
        val s = settings()

        if (kind.isSafeStop) return PolicyDecision.Allow
        if (kind.risk == RiskLevel.R4) return PolicyDecision.Deny(DenyReason.DISALLOWED_IN_V1)

        identityOrApprovalGate(kind, command.confidence, context, s)?.let { return it }

        val missing = kind.requires - context.grantedCapabilities
        if (missing.isNotEmpty()) return PolicyDecision.Deny(DenyReason.MISSING_CAPABILITY, missing)

        RootOperation.forIntent(kind)?.let { op ->
            if (op !in s.enabledRootOperations) return PolicyDecision.Deny(DenyReason.ROOT_OPERATION_DISABLED)
        }

        if (kind == IntentKind.REQUEST_UNLOCK) {
            return PolicyDecision.RequireDeviceCredential("Unlocking always uses your PIN, pattern or fingerprint.")
        }
        if (kind.revealsPrivateContent && context.lockState == LockState.LOCKED && !s.allowPrivateReadoutWhileLocked) {
            return PolicyDecision.RequireDeviceCredential("Unlock the phone to hear private content.")
        }

        return riskGate(kind, context, s)
    }

    private fun identityOrApprovalGate(
        kind: IntentKind,
        confidence: Double,
        context: PolicyContext,
        s: ZenoSettings,
    ): PolicyDecision? = when (context.source) {
        CommandSource.VOICE -> when {
            context.owner == OwnerVerification.DENIED -> PolicyDecision.Deny(DenyReason.UNKNOWN_SPEAKER)
            context.owner != OwnerVerification.VERIFIED -> PolicyDecision.Deny(DenyReason.UNCERTAIN_SPEAKER)
            confidence < MIN_CONFIDENCE -> PolicyDecision.Deny(DenyReason.LOW_CONFIDENCE)
            confidence < AUTO_CONFIDENCE -> PolicyDecision.Clarify("I'm not sure I understood. Did you mean ${kind.name}?")
            else -> null
        }
        CommandSource.TOUCH_UI -> null
        // Low confidence still clarifies; a locked phone needs Android credentials, never voice (Z-016).
        CommandSource.PUSH_TO_TALK -> when {
            context.lockState == LockState.LOCKED -> PolicyDecision.RequireDeviceCredential("Unlock your phone to use push-to-talk.")
            confidence < MIN_CONFIDENCE -> PolicyDecision.Deny(DenyReason.LOW_CONFIDENCE)
            confidence < AUTO_CONFIDENCE -> PolicyDecision.Clarify("I'm not sure I understood. Could you say that again?")
            else -> null
        }
        CommandSource.AUTOMATION_MANUAL, CommandSource.AUTOMATION_LEARNED -> when {
            s.automationsPaused -> PolicyDecision.Deny(DenyReason.AUTOMATIONS_PAUSED)
            context.approval == ApprovalCheck.STALE -> PolicyDecision.Deny(DenyReason.APPROVAL_STALE)
            context.approval != ApprovalCheck.APPROVED_CURRENT -> PolicyDecision.Deny(DenyReason.NOT_APPROVED)
            // High-risk actions are not eligible for unattended runs in v1 (docs/04 action catalog).
            kind.risk >= RiskLevel.R3 -> PolicyDecision.Deny(DenyReason.NOT_ELIGIBLE_UNATTENDED)
            else -> null
        }
    }

    private fun riskGate(kind: IntentKind, context: PolicyContext, s: ZenoSettings): PolicyDecision {
        val unattended = context.source == CommandSource.AUTOMATION_MANUAL || context.source == CommandSource.AUTOMATION_LEARNED
        if (unattended) {
            // Approval already binds exact recipient/content/schedule; optional run-time confirmation.
            return if (context.routineRequiresConfirmationAtRun) PolicyDecision.Confirm("This routine asks for confirmation before it runs.")
            else PolicyDecision.Allow
        }
        return when (kind.risk) {
            RiskLevel.R0, RiskLevel.R1 -> PolicyDecision.Allow
            RiskLevel.R2 -> r2Decision(kind, context, s)
            // Review decision #2: spoken confirmation is enough for R3 (no PIN) unless changed later.
            RiskLevel.R3 -> PolicyDecision.Confirm("This is a high-risk action. Say \"confirm\" to continue.")
            RiskLevel.R4 -> PolicyDecision.Deny(DenyReason.DISALLOWED_IN_V1)
        }
    }

    private fun r2Decision(kind: IntentKind, context: PolicyContext, s: ZenoSettings): PolicyDecision = when (kind) {
        IntentKind.SEND_SMS, IntentKind.REPLY_SMS -> when (s.smsPolicy) {
            SmsPolicy.IMMEDIATE -> PolicyDecision.Allow
            SmsPolicy.ALWAYS_CONFIRM -> PolicyDecision.Confirm("Should I send it?")
            SmsPolicy.SMART ->
                if (context.lockState == LockState.LOCKED || !context.recipientPreviouslyConfirmed) PolicyDecision.Confirm("Should I send it?")
                else PolicyDecision.Allow
        }
        IntentKind.START_CALL, IntentKind.END_CALL ->
            if (s.confirmCalls) PolicyDecision.Confirm("Should I place the call?") else PolicyDecision.Allow
        // Verified owner reading their own content while unlocked (locked case handled earlier).
        IntentKind.READ_SMS, IntentKind.READ_NOTIFICATIONS -> PolicyDecision.Allow
        else -> PolicyDecision.Confirm("Please confirm.")
    }

    companion object {
        /** Below this, decline and offer help (docs/03). */
        const val MIN_CONFIDENCE = 0.60
        /** At or above this, low-risk actions may run without a clarifying question (docs/03). */
        const val AUTO_CONFIDENCE = 0.85

        /** Capabilities a command would need; convenient for UI badges. */
        fun requiredCapabilities(kind: IntentKind): Set<Capability> = kind.requires
    }
}
