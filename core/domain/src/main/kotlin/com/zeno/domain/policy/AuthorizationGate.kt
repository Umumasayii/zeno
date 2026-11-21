package com.zeno.domain.policy

import com.zeno.domain.intent.AssistantIntent

/**
 * An intent that passed the policy engine and any confirmation it required.
 * The constructor is internal to :core:domain, so Android adapters cannot forge one:
 * the ActionRouter only accepts this type (docs/06 "Only a typed, validated, separately authorized
 * intent may reach execution").
 */
class AuthorizedIntent internal constructor(
    val intent: AssistantIntent,
    val decision: PolicyDecision,
    val source: CommandSource,
) {
    override fun toString(): String = "AuthorizedIntent(${intent.kind}, source=$source)"
}

/** Evidence collected after a Confirm/RequireDeviceCredential decision. */
sealed interface UserApproval {
    /** Owner said "send"/"yes"/"confirm" or tapped confirm, for exactly this intent. */
    data class Confirmed(val intent: AssistantIntent) : UserApproval
    /** Android Keyguard reported a successful credential/biometric check. Never voice. */
    data object DeviceCredentialPassed : UserApproval
}

object AuthorizationGate {
    /**
     * Returns an [AuthorizedIntent] only when the decision permits execution, or when the required
     * extra approval was supplied. Returns null otherwise. Confirmation must match the same intent,
     * so "yes" to one message cannot authorize an edited one.
     */
    fun authorize(
        intent: AssistantIntent,
        decision: PolicyDecision,
        source: CommandSource,
        approval: UserApproval? = null,
    ): AuthorizedIntent? = when (decision) {
        PolicyDecision.Allow -> AuthorizedIntent(intent, decision, source)
        is PolicyDecision.Confirm ->
            if (approval is UserApproval.Confirmed && approval.intent == intent) AuthorizedIntent(intent, decision, source) else null
        is PolicyDecision.RequireDeviceCredential ->
            if (approval == UserApproval.DeviceCredentialPassed) AuthorizedIntent(intent, decision, source) else null
        is PolicyDecision.Clarify, is PolicyDecision.Deny -> null
    }
}
