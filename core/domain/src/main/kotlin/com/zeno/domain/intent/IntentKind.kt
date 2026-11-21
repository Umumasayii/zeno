package com.zeno.domain.intent

import com.zeno.domain.security.Capability
import com.zeno.domain.security.RiskLevel

enum class IntentGroup { MESSAGES, CALLS, CONTACTS, APPS, NAVIGATION, INPUT, SCREEN, PHONE, SYSTEM, NOTIFICATIONS, MEDIA, CLOCK, ROUTINES, LEARNING, ASSISTANT }

/**
 * Canonical intent catalogue (docs/03_VOICE_COMMANDS.md). Every `expected_intent` in
 * specs/voice_command_examples.csv must exist here with the same risk level; a unit test enforces it.
 *
 * [requires] lists capabilities that are needed on EVERY route. Route-specific needs
 * (for example CALL_PHONE vs. a dial-intent fallback) are reported by action adapters instead.
 */
enum class IntentKind(
    val group: IntentGroup,
    val risk: RiskLevel,
    val requires: Set<Capability> = emptySet(),
    /** Reads private content aloud or on screen; gated by lock-screen policy. */
    val revealsPrivateContent: Boolean = false,
) {
    SEND_SMS(IntentGroup.MESSAGES, RiskLevel.R2, setOf(Capability.SMS_SEND)),
    READ_SMS(IntentGroup.MESSAGES, RiskLevel.R2, setOf(Capability.SMS_READ), revealsPrivateContent = true),
    REPLY_SMS(IntentGroup.MESSAGES, RiskLevel.R2, setOf(Capability.SMS_SEND)),

    START_CALL(IntentGroup.CALLS, RiskLevel.R2),
    END_CALL(IntentGroup.CALLS, RiskLevel.R2),

    FIND_CONTACT(IntentGroup.CONTACTS, RiskLevel.R0, setOf(Capability.CONTACTS)),

    OPEN_APP(IntentGroup.APPS, RiskLevel.R1),
    SEARCH_IN_APP(IntentGroup.APPS, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),

    HOME(IntentGroup.NAVIGATION, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),
    BACK(IntentGroup.NAVIGATION, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),
    RECENTS(IntentGroup.NAVIGATION, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),
    SCROLL(IntentGroup.NAVIGATION, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),
    TAP(IntentGroup.NAVIGATION, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),

    DICTATE_TEXT(IntentGroup.INPUT, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),

    TAKE_SCREENSHOT(IntentGroup.SCREEN, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),
    READ_SCREEN(IntentGroup.SCREEN, RiskLevel.R1, setOf(Capability.ACCESSIBILITY), revealsPrivateContent = true),

    LOCK_SCREEN(IntentGroup.PHONE, RiskLevel.R1),
    POWER_MENU(IntentGroup.PHONE, RiskLevel.R1, setOf(Capability.ACCESSIBILITY)),
    /** Asks Android to dismiss Keyguard. Android decides whether PIN/fingerprint is needed (Z-016). */
    REQUEST_UNLOCK(IntentGroup.PHONE, RiskLevel.R2),
    REBOOT(IntentGroup.PHONE, RiskLevel.R3, setOf(Capability.ROOT)),
    SHUTDOWN(IntentGroup.PHONE, RiskLevel.R3, setOf(Capability.ROOT)),

    SET_VOLUME(IntentGroup.SYSTEM, RiskLevel.R1),
    SET_BRIGHTNESS(IntentGroup.SYSTEM, RiskLevel.R1),
    SET_FLASHLIGHT(IntentGroup.SYSTEM, RiskLevel.R1),
    SET_DND(IntentGroup.SYSTEM, RiskLevel.R1, setOf(Capability.DND_ACCESS)),

    READ_NOTIFICATIONS(IntentGroup.NOTIFICATIONS, RiskLevel.R2, setOf(Capability.NOTIFICATION_LISTENER), revealsPrivateContent = true),

    PLAY_MEDIA(IntentGroup.MEDIA, RiskLevel.R1),
    PAUSE_MEDIA(IntentGroup.MEDIA, RiskLevel.R1),
    NEXT_TRACK(IntentGroup.MEDIA, RiskLevel.R1),
    PREVIOUS_TRACK(IntentGroup.MEDIA, RiskLevel.R1),

    CREATE_ALARM(IntentGroup.CLOCK, RiskLevel.R1),
    CREATE_REMINDER(IntentGroup.CLOCK, RiskLevel.R1),

    CREATE_ROUTINE(IntentGroup.ROUTINES, RiskLevel.R2),
    LIST_ROUTINES(IntentGroup.ROUTINES, RiskLevel.R0),
    PAUSE_ROUTINE(IntentGroup.ROUTINES, RiskLevel.R1),
    RESUME_ROUTINE(IntentGroup.ROUTINES, RiskLevel.R1),
    DELETE_ROUTINE(IntentGroup.ROUTINES, RiskLevel.R3),

    ENABLE_LEARNING(IntentGroup.LEARNING, RiskLevel.R2),
    DISABLE_LEARNING(IntentGroup.LEARNING, RiskLevel.R1),
    REVIEW_PROPOSALS(IntentGroup.LEARNING, RiskLevel.R0),

    STOP_LISTENING(IntentGroup.ASSISTANT, RiskLevel.R1),
    START_LISTENING(IntentGroup.ASSISTANT, RiskLevel.R1),
    CANCEL(IntentGroup.ASSISTANT, RiskLevel.R1),
    REPEAT(IntentGroup.ASSISTANT, RiskLevel.R0),
    HELP(IntentGroup.ASSISTANT, RiskLevel.R0),
    ;

    /** Stopping and cancelling must always work, for anyone, immediately (docs/03, Z-004). */
    val isSafeStop: Boolean get() = this == STOP_LISTENING || this == CANCEL || this == DISABLE_LEARNING
}
