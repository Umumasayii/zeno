package com.zeno.domain.settings

import com.zeno.domain.root.RootOperation

/** Voice-initiated SMS behaviour (Z-011). Names unified per review conflict C3. */
enum class SmsPolicy {
    /** Send right after owner verification, no read-back. */
    IMMEDIATE,
    /** Always read back and wait for an explicit "send". Default. */
    ALWAYS_CONFIRM,
    /** Confirm when locked or when the recipient has not been confirmed before. */
    SMART,
}

enum class LearningMode { SUGGESTIONS_ONLY, EXECUTE_APPROVED_ROUTINES }

/**
 * User preferences with the SAFE defaults from docs/01 "Default behaviors proposed for safety".
 * Persisted by a DataStore adapter in M1; M0 only defines the contract and defaults.
 */
data class ZenoSettings(
    val assistantName: String = "Zeno",
    /** Display text only. A working hotword needs a compatible model (docs/08 "Custom names"). */
    val wakePhrase: String = "Hey Zeno",
    /** OFF until the owner finishes setup and consents; never enabled covertly (docs/01). */
    val listeningEnabled: Boolean = false,
    val startListeningAfterBoot: Boolean = true,
    val smsPolicy: SmsPolicy = SmsPolicy.ALWAYS_CONFIRM,
    val confirmCalls: Boolean = true,
    /** Read SMS/notifications/screen aloud while locked. OFF by default (open decision D-05). */
    val allowPrivateReadoutWhileLocked: Boolean = false,
    /** Adaptive learning is OFF by default (Z-023, ADR-004). */
    val learningEnabled: Boolean = false,
    val learningMode: LearningMode = LearningMode.SUGGESTIONS_ONLY,
    val learningMinRepetitions: Int = 5,
    val retentionDays: Int = 30,
    /** Root controls OFF until the owner enables individual operations (Z-015). */
    val enabledRootOperations: Set<RootOperation> = emptySet(),
    /** Global kill switch for all automation execution (docs/04 "Approval model"). */
    val automationsPaused: Boolean = false,
) {
    init {
        require(assistantName.isNotBlank()) { "assistant name must not be blank" }
        require(learningMinRepetitions >= 2) { "a single occurrence is never a pattern" }
        require(retentionDays in 1..365) { "retention must be 1..365 days" }
    }
}
