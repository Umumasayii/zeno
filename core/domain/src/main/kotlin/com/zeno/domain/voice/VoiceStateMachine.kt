package com.zeno.domain.voice

/**
 * Voice session states from docs/02 "Voice session state machine" and docs/diagrams/voice-state.mmd.
 * [holdsMicrophone] states what the service is allowed to keep open in each state; the Android
 * service must release the microphone whenever it enters a state where this is false.
 */
enum class VoiceState(val holdsMicrophone: Boolean, val isOutcome: Boolean = false) {
    DISABLED(false),
    /** Only the lightweight wake-word detector reads audio. */
    STANDBY(true),
    HOTWORD_DETECTED(true),
    OWNER_CHECK(true),
    LISTENING(true),
    PARSING(false),
    CLARIFYING(true),
    POLICY_CHECK(false),
    CONFIRMING(true),
    EXECUTING(false),
    REPORTING(false),

    REJECTED_UNKNOWN_VOICE(false, isOutcome = true),
    UNSUPPORTED(false, isOutcome = true),
    ROLE_MISSING(false, isOutcome = true),
    PERMISSION_BLOCKED(false, isOutcome = true),
    CANCELLED(false, isOutcome = true),
    TIMEOUT(false, isOutcome = true),
    ERROR(false, isOutcome = true),
    LOCKED_UNAVAILABLE(false, isOutcome = true),
    ;

    val isIdle: Boolean get() = this == DISABLED || this == STANDBY
    val isActiveSession: Boolean get() = !isIdle && !isOutcome
}

sealed interface VoiceEvent {
    /** Owner turned listening ON (explicit UI action or authorized command). */
    data object EnableListening : VoiceEvent
    /** Kill switch (Z-004). Valid from every state. */
    data object DisableListening : VoiceEvent
    data object HotwordDetected : VoiceEvent
    /** Explicit tap on the in-app mic button. Works even when wake-word listening is off. */
    data object PushToTalk : VoiceEvent
    data object OwnerCheckStarted : VoiceEvent
    data object OwnerVerified : VoiceEvent
    data object OwnerNotVerified : VoiceEvent
    data object UtteranceCaptured : VoiceEvent
    data class Parsed(val result: ParseResult) : VoiceEvent
    data class PolicyEvaluated(val outcome: PolicyOutcome) : VoiceEvent
    data object UserConfirmed : VoiceEvent
    data object UserDeclined : VoiceEvent
    data object ExecutionFinished : VoiceEvent
    data object ReportFinished : VoiceEvent
    data object Cancel : VoiceEvent
    data object Timeout : VoiceEvent
    data object Failure : VoiceEvent
    data object MicrophoneUnavailable : VoiceEvent
    data object AssistantRoleMissing : VoiceEvent
    /** Leave an outcome state and return to idle. */
    data object Acknowledge : VoiceEvent
}

enum class ParseResult { UNDERSTOOD, NEEDS_CLARIFICATION, UNSUPPORTED }
enum class PolicyOutcome { ALLOWED, NEEDS_CONFIRMATION, PERMISSION_BLOCKED, LOCKED, SPEAKER_REJECTED, NOT_ALLOWED }

/** Immutable session snapshot. [listeningEnabled] decides whether idle means STANDBY or DISABLED. */
data class VoiceSession(val state: VoiceState, val listeningEnabled: Boolean) {
    val idleState: VoiceState get() = if (listeningEnabled) VoiceState.STANDBY else VoiceState.DISABLED

    companion object {
        fun initial(listeningEnabled: Boolean) =
            VoiceSession(if (listeningEnabled) VoiceState.STANDBY else VoiceState.DISABLED, listeningEnabled)
    }
}

data class Transition(val session: VoiceSession, val accepted: Boolean)

/** Pure transition function. Unknown (state, event) pairs are rejected without changing state. */
object VoiceStateMachine {

    fun next(current: VoiceSession, event: VoiceEvent): Transition {
        val s = current.state
        fun go(state: VoiceState, enabled: Boolean = current.listeningEnabled) =
            Transition(VoiceSession(state, enabled), accepted = true)
        val reject = Transition(current, accepted = false)

        // Global edges first.
        when (event) {
            VoiceEvent.DisableListening -> return go(VoiceState.DISABLED, enabled = false)
            VoiceEvent.Cancel -> return if (s.isActiveSession) go(VoiceState.CANCELLED) else reject
            VoiceEvent.Timeout -> return if (s.isActiveSession) go(VoiceState.TIMEOUT) else reject
            VoiceEvent.Failure -> return if (s.isActiveSession) go(VoiceState.ERROR) else reject
            VoiceEvent.Acknowledge -> return if (s.isOutcome) go(current.idleState) else reject
            else -> Unit
        }

        return when (s) {
            VoiceState.DISABLED -> when (event) {
                VoiceEvent.EnableListening -> go(VoiceState.STANDBY, enabled = true)
                VoiceEvent.PushToTalk -> go(VoiceState.OWNER_CHECK)
                else -> reject
            }
            VoiceState.STANDBY -> when (event) {
                VoiceEvent.HotwordDetected -> go(VoiceState.HOTWORD_DETECTED)
                VoiceEvent.PushToTalk -> go(VoiceState.OWNER_CHECK)
                VoiceEvent.MicrophoneUnavailable -> go(VoiceState.PERMISSION_BLOCKED)
                VoiceEvent.AssistantRoleMissing -> go(VoiceState.ROLE_MISSING)
                else -> reject
            }
            VoiceState.HOTWORD_DETECTED -> when (event) {
                VoiceEvent.OwnerCheckStarted -> go(VoiceState.OWNER_CHECK)
                else -> reject
            }
            VoiceState.OWNER_CHECK -> when (event) {
                VoiceEvent.OwnerVerified -> go(VoiceState.LISTENING)
                VoiceEvent.OwnerNotVerified -> go(VoiceState.REJECTED_UNKNOWN_VOICE)
                else -> reject
            }
            VoiceState.LISTENING -> when (event) {
                VoiceEvent.UtteranceCaptured -> go(VoiceState.PARSING)
                else -> reject
            }
            VoiceState.CLARIFYING -> when (event) {
                VoiceEvent.UtteranceCaptured -> go(VoiceState.PARSING)
                else -> reject
            }
            VoiceState.PARSING -> when (event) {
                is VoiceEvent.Parsed -> when (event.result) {
                    ParseResult.UNDERSTOOD -> go(VoiceState.POLICY_CHECK)
                    ParseResult.NEEDS_CLARIFICATION -> go(VoiceState.CLARIFYING)
                    ParseResult.UNSUPPORTED -> go(VoiceState.UNSUPPORTED)
                }
                else -> reject
            }
            VoiceState.POLICY_CHECK -> when (event) {
                is VoiceEvent.PolicyEvaluated -> when (event.outcome) {
                    PolicyOutcome.ALLOWED -> go(VoiceState.EXECUTING)
                    PolicyOutcome.NEEDS_CONFIRMATION -> go(VoiceState.CONFIRMING)
                    PolicyOutcome.PERMISSION_BLOCKED -> go(VoiceState.PERMISSION_BLOCKED)
                    PolicyOutcome.LOCKED -> go(VoiceState.LOCKED_UNAVAILABLE)
                    PolicyOutcome.SPEAKER_REJECTED -> go(VoiceState.REJECTED_UNKNOWN_VOICE)
                    PolicyOutcome.NOT_ALLOWED -> go(VoiceState.UNSUPPORTED)
                }
                else -> reject
            }
            VoiceState.CONFIRMING -> when (event) {
                VoiceEvent.UserConfirmed -> go(VoiceState.EXECUTING)
                VoiceEvent.UserDeclined -> go(VoiceState.CANCELLED)
                else -> reject
            }
            VoiceState.EXECUTING -> when (event) {
                VoiceEvent.ExecutionFinished -> go(VoiceState.REPORTING)
                else -> reject
            }
            VoiceState.REPORTING -> when (event) {
                VoiceEvent.ReportFinished -> go(current.idleState)
                else -> reject
            }
            // Blocked states can be retried by re-enabling.
            VoiceState.PERMISSION_BLOCKED, VoiceState.ROLE_MISSING -> when (event) {
                VoiceEvent.EnableListening -> go(VoiceState.STANDBY, enabled = true)
                else -> reject
            }
            else -> reject
        }
    }
}
