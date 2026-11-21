package com.zeno.domain.intent

/**
 * Typed command produced by the parser. This is the ONLY shape a spoken request may take on its way
 * to execution: there is deliberately no "raw text" or "shell" variant (docs/02, "No text-to-shell").
 *
 * Entity references (contacts, apps, UI targets) are *unresolved* spoken references. Resolution happens
 * later and must never fabricate a number, package or element (Z-009).
 */
sealed interface AssistantIntent {
    val kind: IntentKind

    // --- Messages ---
    data class SendSms(val recipient: ContactRef, val body: String?) : AssistantIntent {
        override val kind get() = IntentKind.SEND_SMS
    }
    data class ReadSms(val sender: ContactRef? = null, val count: Int = 1, val unreadOnly: Boolean = true) : AssistantIntent {
        override val kind get() = IntentKind.READ_SMS
    }
    data class ReplySms(val conversation: ContactRef?, val body: String?) : AssistantIntent {
        override val kind get() = IntentKind.REPLY_SMS
    }

    // --- Calls / contacts ---
    data class StartCall(val callee: ContactRef) : AssistantIntent {
        override val kind get() = IntentKind.START_CALL
    }
    data object EndCall : AssistantIntent {
        override val kind get() = IntentKind.END_CALL
    }
    data class FindContact(val query: ContactRef) : AssistantIntent {
        override val kind get() = IntentKind.FIND_CONTACT
    }

    // --- Apps ---
    data class OpenApp(val app: AppRef) : AssistantIntent {
        override val kind get() = IntentKind.OPEN_APP
    }
    data class SearchInApp(val app: AppRef, val query: String) : AssistantIntent {
        override val kind get() = IntentKind.SEARCH_IN_APP
    }

    // --- Navigation / input / screen ---
    data object Home : AssistantIntent {
        override val kind get() = IntentKind.HOME
    }
    data object Back : AssistantIntent {
        override val kind get() = IntentKind.BACK
    }
    data object Recents : AssistantIntent {
        override val kind get() = IntentKind.RECENTS
    }
    data class Scroll(val direction: Direction, val times: Int = 1) : AssistantIntent {
        override val kind get() = IntentKind.SCROLL
        init { require(times in 1..MAX_REPEAT) { "times must be 1..$MAX_REPEAT" } }
    }
    data class Tap(val target: UiTarget) : AssistantIntent {
        override val kind get() = IntentKind.TAP
    }
    data class DictateText(val text: String) : AssistantIntent {
        override val kind get() = IntentKind.DICTATE_TEXT
    }
    data object TakeScreenshot : AssistantIntent {
        override val kind get() = IntentKind.TAKE_SCREENSHOT
    }
    data object ReadScreen : AssistantIntent {
        override val kind get() = IntentKind.READ_SCREEN
    }

    // --- Phone ---
    data object LockScreen : AssistantIntent {
        override val kind get() = IntentKind.LOCK_SCREEN
    }
    data object PowerMenu : AssistantIntent {
        override val kind get() = IntentKind.POWER_MENU
    }
    data object RequestUnlock : AssistantIntent {
        override val kind get() = IntentKind.REQUEST_UNLOCK
    }
    data object Reboot : AssistantIntent {
        override val kind get() = IntentKind.REBOOT
    }
    data object Shutdown : AssistantIntent {
        override val kind get() = IntentKind.SHUTDOWN
    }

    // --- System ---
    data class SetVolume(val stream: AudioStream, val level: Level) : AssistantIntent {
        override val kind get() = IntentKind.SET_VOLUME
    }
    data class SetBrightness(val level: Level) : AssistantIntent {
        override val kind get() = IntentKind.SET_BRIGHTNESS
    }
    data class SetFlashlight(val on: Boolean) : AssistantIntent {
        override val kind get() = IntentKind.SET_FLASHLIGHT
    }
    data class SetDnd(val on: Boolean) : AssistantIntent {
        override val kind get() = IntentKind.SET_DND
    }
    data class ReadNotifications(val app: AppRef? = null) : AssistantIntent {
        override val kind get() = IntentKind.READ_NOTIFICATIONS
    }

    // --- Media ---
    data class MediaControl(val command: MediaCommand) : AssistantIntent {
        override val kind get() = when (command) {
            MediaCommand.PLAY -> IntentKind.PLAY_MEDIA
            MediaCommand.PAUSE -> IntentKind.PAUSE_MEDIA
            MediaCommand.NEXT -> IntentKind.NEXT_TRACK
            MediaCommand.PREVIOUS -> IntentKind.PREVIOUS_TRACK
        }
    }

    // --- Clock ---
    /** [spokenTime] stays unresolved text until the clock resolver applies timezone/date rules. */
    data class CreateAlarm(val spokenTime: String, val label: String? = null) : AssistantIntent {
        override val kind get() = IntentKind.CREATE_ALARM
    }
    data class CreateReminder(val spokenTime: String, val text: String) : AssistantIntent {
        override val kind get() = IntentKind.CREATE_REMINDER
    }

    // --- Routines (never executes the routine's steps; creation goes through approval) ---
    data class CreateRoutine(val spokenDefinition: String) : AssistantIntent {
        override val kind get() = IntentKind.CREATE_ROUTINE
    }
    data object ListRoutines : AssistantIntent {
        override val kind get() = IntentKind.LIST_ROUTINES
    }
    data class PauseRoutine(val routine: RoutineRef) : AssistantIntent {
        override val kind get() = IntentKind.PAUSE_ROUTINE
    }
    data class ResumeRoutine(val routine: RoutineRef) : AssistantIntent {
        override val kind get() = IntentKind.RESUME_ROUTINE
    }
    data class DeleteRoutine(val routine: RoutineRef) : AssistantIntent {
        override val kind get() = IntentKind.DELETE_ROUTINE
    }

    // --- Learning ---
    data object EnableLearning : AssistantIntent {
        override val kind get() = IntentKind.ENABLE_LEARNING
    }
    data object DisableLearning : AssistantIntent {
        override val kind get() = IntentKind.DISABLE_LEARNING
    }
    data object ReviewProposals : AssistantIntent {
        override val kind get() = IntentKind.REVIEW_PROPOSALS
    }

    // --- Assistant control ---
    data object StopListening : AssistantIntent {
        override val kind get() = IntentKind.STOP_LISTENING
    }
    data object StartListening : AssistantIntent {
        override val kind get() = IntentKind.START_LISTENING
    }
    data object Cancel : AssistantIntent {
        override val kind get() = IntentKind.CANCEL
    }
    data object Repeat : AssistantIntent {
        override val kind get() = IntentKind.REPEAT
    }
    data object Help : AssistantIntent {
        override val kind get() = IntentKind.HELP
    }

    companion object {
        /** Bound on repeated UI actions so one utterance cannot cause unbounded taps/scrolls. */
        const val MAX_REPEAT = 10
    }
}

/** A spoken contact reference such as "Mum" or "John". Unresolved; may match 0, 1 or many contacts. */
@JvmInline value class ContactRef(val spoken: String)

/** A spoken app name such as "WhatsApp". Resolved only against installed apps. */
@JvmInline value class AppRef(val spoken: String)

/** A spoken routine reference such as "bedtime". */
@JvmInline value class RoutineRef(val spoken: String)

/**
 * Semantic UI target for accessibility actions. Never screen coordinates (CLAUDE.md).
 * "select the second option" → UiTarget(label = null, ordinal = 2).
 */
data class UiTarget(val label: String? = null, val ordinal: Int? = null) {
    init {
        require(label != null || ordinal != null) { "UiTarget needs a label or an ordinal" }
        require(ordinal == null || ordinal >= 1) { "ordinal is 1-based" }
    }
}

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class AudioStream { MEDIA, RING, ALARM, NOTIFICATION, CALL }
enum class MediaCommand { PLAY, PAUSE, NEXT, PREVIOUS }

sealed interface Level {
    data class Percent(val value: Int) : Level {
        init { require(value in 0..100) { "percent must be 0..100" } }
    }
    data class Step(val delta: Int) : Level
    data object Max : Level
    data object Min : Level
}

/** Parser output: the typed intent plus how sure the parser is (0.0–1.0). */
data class ParsedCommand(val intent: AssistantIntent, val confidence: Double) {
    init { require(confidence in 0.0..1.0) { "confidence must be within 0..1" } }
}
