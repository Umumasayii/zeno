package com.zeno.domain.policy

import com.zeno.domain.intent.ParsedCommand
import com.zeno.domain.security.Capability

/** Where a request came from. Automation sources never have a live speaker. */
enum class CommandSource {
    /** Wake-word voice: needs speaker verification (M2b). */
    VOICE,
    TOUCH_UI,
    /**
     * Owner tapped the mic (or typed) inside the unlocked app. Android's own unlock is the identity
     * check; when the phone is locked, Android credentials are required (ADR-008).
     */
    PUSH_TO_TALK,
    AUTOMATION_MANUAL,
    AUTOMATION_LEARNED,
}

/** Speaker verifier outcome (docs/03 "Confidence/risk gate"). Not equivalent to Android biometrics. */
enum class OwnerVerification { VERIFIED, UNCERTAIN, DENIED, NOT_APPLICABLE }

enum class LockState { UNLOCKED, LOCKED }

/** Result of checking a routine's stored approval against its current definition. */
enum class ApprovalCheck { APPROVED_CURRENT, STALE, MISSING }

data class PolicyContext(
    val source: CommandSource,
    val owner: OwnerVerification,
    val lockState: LockState,
    val grantedCapabilities: Set<Capability>,
    /** Automation runs only. */
    val approval: ApprovalCheck? = null,
    /** Automation runs only: routine asked for a confirmation at run time. */
    val routineRequiresConfirmationAtRun: Boolean = false,
    /** SMART SMS policy: recipient was explicitly confirmed by the owner before. */
    val recipientPreviouslyConfirmed: Boolean = false,
)

enum class DenyReason {
    DISALLOWED_IN_V1,
    UNKNOWN_SPEAKER,
    UNCERTAIN_SPEAKER,
    LOW_CONFIDENCE,
    MISSING_CAPABILITY,
    ROOT_OPERATION_DISABLED,
    NOT_APPROVED,
    APPROVAL_STALE,
    NOT_ELIGIBLE_UNATTENDED,
    AUTOMATIONS_PAUSED,
}

sealed interface PolicyDecision {
    data object Allow : PolicyDecision

    /** Ask the owner to confirm (spoken "send"/"yes" or a tap). */
    data class Confirm(val reason: String) : PolicyDecision

    /** Android PIN/pattern/fingerprint via Keyguard is required; voice cannot satisfy this (Z-016). */
    data class RequireDeviceCredential(val reason: String) : PolicyDecision

    /** Intent is plausible but unclear; ask a clarifying question instead of acting. */
    data class Clarify(val reason: String) : PolicyDecision

    data class Deny(val reason: DenyReason, val missing: Set<Capability> = emptySet()) : PolicyDecision
}

/** Decides whether a typed command may run and with which extra checks. Pure; no side effects. */
fun interface PolicyEngine {
    fun evaluate(command: ParsedCommand, context: PolicyContext): PolicyDecision
}
