package com.zeno.domain.nlu

import com.zeno.domain.intent.AppRef
import com.zeno.domain.intent.AssistantIntent
import com.zeno.domain.intent.AssistantIntent.Back
import com.zeno.domain.intent.AssistantIntent.Cancel
import com.zeno.domain.intent.AssistantIntent.CreateAlarm
import com.zeno.domain.intent.AssistantIntent.CreateReminder
import com.zeno.domain.intent.AssistantIntent.CreateRoutine
import com.zeno.domain.intent.AssistantIntent.DeleteRoutine
import com.zeno.domain.intent.AssistantIntent.DictateText
import com.zeno.domain.intent.AssistantIntent.DisableLearning
import com.zeno.domain.intent.AssistantIntent.EnableLearning
import com.zeno.domain.intent.AssistantIntent.FindContact
import com.zeno.domain.intent.AssistantIntent.Help
import com.zeno.domain.intent.AssistantIntent.Home
import com.zeno.domain.intent.AssistantIntent.ListRoutines
import com.zeno.domain.intent.AssistantIntent.LockScreen
import com.zeno.domain.intent.AssistantIntent.MediaControl
import com.zeno.domain.intent.AssistantIntent.OpenApp
import com.zeno.domain.intent.AssistantIntent.PauseRoutine
import com.zeno.domain.intent.AssistantIntent.PowerMenu
import com.zeno.domain.intent.AssistantIntent.ReadNotifications
import com.zeno.domain.intent.AssistantIntent.ReadScreen
import com.zeno.domain.intent.AssistantIntent.ReadSms
import com.zeno.domain.intent.AssistantIntent.Reboot
import com.zeno.domain.intent.AssistantIntent.Recents
import com.zeno.domain.intent.AssistantIntent.Repeat
import com.zeno.domain.intent.AssistantIntent.ReplySms
import com.zeno.domain.intent.AssistantIntent.RequestUnlock
import com.zeno.domain.intent.AssistantIntent.ResumeRoutine
import com.zeno.domain.intent.AssistantIntent.ReviewProposals
import com.zeno.domain.intent.AssistantIntent.Scroll
import com.zeno.domain.intent.AssistantIntent.SearchInApp
import com.zeno.domain.intent.AssistantIntent.SendSms
import com.zeno.domain.intent.AssistantIntent.SetBrightness
import com.zeno.domain.intent.AssistantIntent.SetDnd
import com.zeno.domain.intent.AssistantIntent.SetFlashlight
import com.zeno.domain.intent.AssistantIntent.SetVolume
import com.zeno.domain.intent.AssistantIntent.Shutdown
import com.zeno.domain.intent.AssistantIntent.StartCall
import com.zeno.domain.intent.AssistantIntent.StartListening
import com.zeno.domain.intent.AssistantIntent.StopListening
import com.zeno.domain.intent.AssistantIntent.Tap
import com.zeno.domain.intent.AssistantIntent.TakeScreenshot
import com.zeno.domain.intent.AudioStream
import com.zeno.domain.intent.ContactRef
import com.zeno.domain.intent.Direction
import com.zeno.domain.intent.Level
import com.zeno.domain.intent.MediaCommand
import com.zeno.domain.intent.ParsedCommand
import com.zeno.domain.intent.RoutineRef
import com.zeno.domain.intent.UiTarget

sealed interface ParseOutcome {
    data class Understood(val command: ParsedCommand) : ParseOutcome
    /** Recognised shape that Zeno deliberately does not handle yet (e.g. several steps in one sentence). */
    data class Unsupported(val reason: String) : ParseOutcome
    data object NotUnderstood : ParseOutcome
}

/** Port so a model-based parser (docs/08) can replace or back up the rules later. */
fun interface CommandParser {
    fun parse(utterance: String): ParseOutcome
}

/**
 * Offline, rule-based intent and slot parser (docs/03 "Intent/slot-based understanding").
 *
 * - Output is always a typed [AssistantIntent]; there is no free-text or shell path (docs/02).
 * - Slots are spoken references only. Nothing is resolved or invented here (Z-009): an unknown
 *   contact stays a [ContactRef], a missing message body stays `null` so the dialogue asks for it.
 * - Rules are ordered from most specific to most generic. Fixture coverage is measured by
 *   `CommandParserTest` against specs/voice_command_examples.csv, which is synthetic and not proof
 *   of understanding arbitrary English (docs/03).
 */
class RuleBasedCommandParser(private val assistantName: String = "Zeno") : CommandParser {

    private class Rule(val name: String, val regex: Regex, val build: (MatchResult, String) -> AssistantIntent?)

    override fun parse(utterance: String): ParseOutcome {
        val text = normalize(utterance)
        if (text.isEmpty()) return ParseOutcome.NotUnderstood
        if (MULTI_STEP.containsMatchIn(text) && !ROUTINE_START.containsMatchIn(text)) {
            return ParseOutcome.Unsupported("I can do one thing at a time for now. Please say each step separately.")
        }
        for (rule in rules) {
            val m = rule.regex.find(text) ?: continue
            val intent = runCatching { rule.build(m, text) }.getOrNull() ?: continue
            return ParseOutcome.Understood(ParsedCommand(intent, CONFIDENCE))
        }
        return ParseOutcome.NotUnderstood
    }

    /** Lower-noise text with original casing kept for slot values. */
    internal fun normalize(raw: String): String {
        var t = raw.replace(Regex("[\"“”,.!?;:]+"), " ").replace(Regex("\\s+"), " ").trim()
        val name = Regex.escape(assistantName)
        t = t.replace(Regex("^(hey|hi|ok|okay)?\\s*$name\\b\\s*", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("^(please|can you|could you|would you|will you)\\s+", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("^(please)\\s+", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\s+please$", RegexOption.IGNORE_CASE), "")
        return t.trim()
    }

    // ------------------------------------------------------------------ rules (order matters)

    private fun r(name: String, pattern: String, build: (MatchResult, String) -> AssistantIntent?) =
        Rule(name, Regex(pattern, RegexOption.IGNORE_CASE), build)

    private val rules: List<Rule> = listOf(
        // Global interrupts first, so they always win (docs/03 "global interrupt").
        r("cancel", "^(no )?(cancel( it| that| this operation| the current command)?|never ?mind|forget it|abort|stop that( action)?|stop now|stop|do not send|don't send)( it)?$") { _, _ -> Cancel },
        r("stop_listening", "^(stop|pause|disable|turn off|switch off|mute)\\b.*\\b(listening|microphone|mic|voice activation|wake word|voice monitoring|${Regex.escape(assistantName)})\\b|^switch listening off$") { _, _ -> StopListening },
        r("start_listening", "^(start|resume|enable|turn on|switch on)\\s+(listening|the microphone|voice activation|(the )?wake word)$") { _, _ -> StartListening },
        r("help", "^(help|help me|what can (you|i) (do|say)|what commands (are there|can i use|do you know))$") { _, _ -> Help },
        r("repeat", "^(repeat( that)?|say (that|it) again|what did you say)$") { _, _ -> Repeat },
        r("unlock", "\\bunlock\\b") { _, _ -> RequestUnlock },
        r("learning_on", "^(enable|turn on|start|switch on)\\s+(adaptive )?learning$") { _, _ -> EnableLearning },
        r("learning_off", "^(disable|turn off|stop|switch off)\\s+(adaptive )?learning$") { _, _ -> DisableLearning },

        // Learning proposals before routines: "review suggested routines" is a proposal review.
        r("review", "\\b(suggest\\w*|proposals?|habits?|ideas|patterns?|learning)\\b|\\blearned (suggestions|routines)\\b") { _, _ -> ReviewProposals },

        // Routines before SMS/DND/alarms: "every morning send a text" creates a routine, it doesn't text now.
        r("create_routine", "$ROUTINE_START_PATTERN|^(create|make|set up|add|build)\\b.*\\b(routine|automation|alert|recurring)\\b") { _, t -> CreateRoutine(t) },
        r("pause_routine", "^(pause|disable|turn off|switch off|stop|suspend|temporarily stop|deactivate)\\s+(.*\\b$ROUTINE_NOUN\\b.*)$") { m, _ -> PauseRoutine(RoutineRef(cleanRef(m.groupValues[2]))) },
        r("resume_routine", "^(resume|enable|turn on|switch on|restart|reactivate|re-enable|unpause)\\s+(.*\\b$ROUTINE_NOUN\\b.*)$") { m, _ -> ResumeRoutine(RoutineRef(cleanRef(m.groupValues[2]))) },
        r("resume_routine_back_on", "^switch (.*\\b$ROUTINE_NOUN\\b.*) back on$") { m, _ -> ResumeRoutine(RoutineRef(cleanRef(m.groupValues[1]))) },
        r("delete_routine", "^(delete|remove|erase|discard|get rid of)\\s+(.*\\b$ROUTINE_NOUN\\b.*)$") { m, _ ->
            DeleteRoutine(RoutineRef(cleanRef(m.groupValues[2].replace(Regex("\\s+permanently$", RegexOption.IGNORE_CASE), ""))))
        },
        r("list_routines", "^(show|list|display|open|which|my|what)\\b.*\\b(automations|routines|rules|scheduled (actions|tasks)|automation (library|center))\\b") { _, _ -> ListRoutines },

        // Clock.
        r("reminder", "^(remind me|set a reminder)\\b(.*)$") { m, _ -> CreateReminder(spokenTime = m.groupValues[2].trim().ifEmpty { "unspecified" }, text = "") },
        r("alarm", "\\balarm\\b|^wake me\\b") { _, t -> CreateAlarm(spokenTime = alarmTime(t)) },

        // Power and lock.
        r("power_menu", "\\b(power|shutdown|restart) (menu|options|controls|screen)\\b") { _, _ -> PowerMenu },
        r("reboot", "^(restart|reboot|power cycle)\\b") { _, _ -> Reboot },
        r("shutdown", "\\b(shut ?down|power (off|down)|shut the (phone|device) down)\\b|^(turn|switch) (off )?(my |the )?(phone|device|pixel)( off)?( completely)?$") { _, _ -> Shutdown },
        r("lock", "\\block\\w*\\b|^(sleep|put)\\b.*\\b(sleep|phone|screen)\\b|^turn (off )?the screen( off)?$|^secure my (phone|device)") { _, _ -> LockScreen },

        // Screen.
        r("screenshot", "\\bscreen ?shot\\b|\\bscreen capture\\b|^capture (the|a picture of the) (screen|display)|^grab the screen|^save what is (displayed|on (the )?screen)") { _, _ -> TakeScreenshot },
        r("read_screen", "^(read|speak|describe)\\b.*\\b(screen|page|display|visible|paragraph|card|selected)\\b|^what (does|is on) (this|the|my) (screen|page)|^what are the available buttons|^tell me what (the|this) (page|screen) says") { _, _ -> ReadScreen },

        // System toggles. DND before notifications ("silence notifications" is DND).
        r("dnd", "do not disturb|\\bdnd\\b|silence notifications|quiet (mode|notifications)|focus silence|mute interruptions") { _, t -> SetDnd(on = !OFF_WORDS.containsMatchIn(t)) },
        r("notifications", "\\b(notifications?|alerts)\\b|^what did i miss$") { _, t -> ReadNotifications(app = notificationApp(t)) },
        r("flashlight", "\\b(flashlight|torch|flash)\\b|\\bthe light\\b") { _, t -> SetFlashlight(on = !OFF_WORDS.containsMatchIn(t)) },
        r("brightness", "\\b(brightness|dim|brighten|less bright|screen light)\\b") { _, t -> SetBrightness(brightnessLevel(t)) },
        r("volume", "\\b(volume|quieter|louder|mute media|mute (the )?(music|audio|sound))\\b|^turn (up|down) (the )?(music|sound)\\b") { _, t -> SetVolume(stream(t), volumeLevel(t)) },
        r("media", "^(play|resume playback|continue (the )?(song|music|podcast|playback|video)|skip|next track|previous track|stop playback|pause (the )?(song|music|media|playback|podcast|video))\\b") { _, t -> MediaControl(mediaCommand(t)) },

        // Messages. Reply and read before send ("text back ..." replies; "tell me ..." reads).
        r("reply", "^(reply|respond|answer)\\b|^text back\\b|^send a response\\b") { _, t -> replySms(t) },
        r("read_sms", "^(read|show|any|tell me|what was)\\b.*\\b(texts?|sms|messages?|inbox)\\b|^open my inbox\\b") { _, t -> readSms(t) },
        r("send_sms", "^(text|sms|message|tell|send|compose|write to)\\b") { _, t -> sendSms(t) },

        // Calls, contacts, search. Calls first: "dial the saved contact John" is a call.
        r("call", "^(call|phone|dial|ring|telephone|make a (phone )?call to|start a call (to|with))\\s+(.+)$") { m, _ -> StartCall(ContactRef(callee(m.groupValues[4]))) },
        r("find_contact", "\\b(contacts?|address book|phone number|number belongs to)\\b") { _, t -> FindContact(ContactRef(contactQuery(t))) },
        r("search_in_app", "^(search|find|look up)\\b.+\\b(in|on|for)\\b.+") { _, t -> searchInApp(t) },
        r("look_up_contact", "^look up (my \\S+|\\S+)$") { m, _ -> FindContact(ContactRef(m.groupValues[1])) },

        // Navigation.
        r("home", "\\bhome\\b|\\blauncher\\b|\\bmain screen\\b") { _, _ -> Home },
        r("recents", "\\brecents?\\b|app switcher|apps are open|multitasking|switch applications|\\boverview\\b") { _, _ -> Recents },
        r("back", "^((go|move|step|navigate) )?back(ward)?\\b|^navigate backward|^previous (screen|view|page)$|^go to (the )?(last|previous) (page|view|screen)$|^return one page$|^leave this screen$") { _, _ -> Back },
        r("scroll", "^scroll\\b|^move (down|up)\\b|^go (a bit )?(lower|higher|further (down|up)|down|up)\\b|^(next|previous) page$|^page (down|up)$") { _, t -> scroll(t) },

        // Input and taps.
        r("dictate", "^(type|write|enter|dictate|insert|put)\\b") { _, t -> DictateText(dictation(t)) },
        r("tap", "^(tap|press|click|choose|select|open the highlighted)\\b") { _, t -> Tap(uiTarget(t)) },

        // Apps last: "open"/"start" is the most generic verb.
        r("open_app", "^(open|launch|start)\\s+(.+)$") { m, _ -> OpenApp(AppRef(appName(m.groupValues[2]))) },
    )

    // ------------------------------------------------------------------ slot helpers

    private fun cleanRef(s: String) = s.replace(Regex("^(the|my|this|that|all)\\s+", RegexOption.IGNORE_CASE), "").trim()

    private fun alarmTime(t: String): String {
        val m = Regex("\\b(for|at|on|tomorrow|in the)\\b.*$", RegexOption.IGNORE_CASE).find(t)
        return m?.value?.trim() ?: t.replace(Regex("^(create|set|schedule|make)( an?| a phone)?\\s*", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun notificationApp(t: String): AppRef? =
        Regex("\\bfrom (\\S+)$", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.let(::AppRef)

    private fun brightnessLevel(t: String): Level {
        percent(t)?.let { return Level.Percent(it) }
        if (Regex("\\bhalf\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return Level.Percent(50)
        if (Regex("\\b(max|maximum|full)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return Level.Max
        return if (Regex("\\b(dim|less|reduce|lower|low|down|decrease)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) Level.Step(-1) else Level.Step(+1)
    }

    private fun volumeLevel(t: String): Level {
        if (Regex("\\bmute\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return Level.Min
        if (Regex("\\b(max|maximum|full)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return Level.Max
        percent(t)?.let { return Level.Percent(it) }
        // "set the volume to ten": a bare number is read as percent; the action layer maps it to the stream's steps.
        Regex("\\bto (\\w+( \\w+)?)( percent|%)?$", RegexOption.IGNORE_CASE).find(t)?.let { m -> NumberWords.parse(m.groupValues[1])?.takeIf { it in 0..100 }?.let { return Level.Percent(it) } }
        return if (Regex("\\b(lower|quieter|reduce|down|decrease|softer)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) Level.Step(-1) else Level.Step(+1)
    }

    private fun percent(t: String): Int? =
        Regex("\\b(?:to )?(\\w+(?: \\w+)?)\\s*(?:percent|%)", RegexOption.IGNORE_CASE).find(t)
            ?.groupValues?.get(1)?.let { phrase ->
                NumberWords.parse(phrase) ?: NumberWords.parse(phrase.substringAfter(' '))
            }?.takeIf { it in 0..100 }

    private fun stream(t: String): AudioStream = when {
        Regex("\\bring(ing|er|tone)?\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> AudioStream.RING
        Regex("\\balarm\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> AudioStream.ALARM
        Regex("\\bnotification\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> AudioStream.NOTIFICATION
        Regex("\\bcall\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> AudioStream.CALL
        else -> AudioStream.MEDIA
    }

    private fun mediaCommand(t: String): MediaCommand = when {
        Regex("\\b(next|skip)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> MediaCommand.NEXT
        Regex("\\bprevious\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> MediaCommand.PREVIOUS
        Regex("^(pause|stop)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> MediaCommand.PAUSE
        else -> MediaCommand.PLAY
    }

    private fun scroll(t: String): Scroll {
        val dir = when {
            Regex("\\b(up|top|higher|previous)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> Direction.UP
            Regex("\\bleft\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> Direction.LEFT
            Regex("\\bright\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> Direction.RIGHT
            else -> Direction.DOWN
        }
        val times = when {
            Regex("\\b(top|bottom)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> AssistantIntent.MAX_REPEAT
            Regex("\\btwice\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 2
            Regex("\\bthree times\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 3
            else -> Regex("\\b(\\w+) times\\b", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.let(NumberWords::parse)
                ?.coerceIn(1, AssistantIntent.MAX_REPEAT) ?: 1
        }
        return Scroll(dir, times)
    }

    // --- messages

    /** Splits "<contact> <rest>" where the contact is "my <relation>", "the contact called X" or one word. */
    private fun takeContact(s: String): Pair<String, String> {
        val t = s.trim()
        Regex("^(the (contact|person) (called|named) )(\\S+)\\s*(.*)$", RegexOption.IGNORE_CASE).find(t)?.let { return it.groupValues[4] to it.groupValues[5] }
        Regex("^(my \\S+)\\s*(.*)$", RegexOption.IGNORE_CASE).find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        Regex("^(\\S+)\\s*(.*)$").find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        return t to ""
    }

    private fun body(rest: String): String? = rest
        .replace(Regex("^(by (sms|text)|via (sms|text))\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^(saying|that|the message|with|to say)\\s+", RegexOption.IGNORE_CASE), "")
        .trim().ifEmpty { null }

    private fun sendSms(t: String): SendSms? {
        var s = t
        // "send|compose|write ... to <contact> ..." forms.
        Regex("^(send|compose|write)( an?| this| the| a new)?( sms| text| message| text message)?( to)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val (c, rest) = takeContact(m.groupValues[5]); return SendSms(ContactRef(c), body(rest))
        }
        // "send John the message I arrived"
        Regex("^send\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val (c, rest) = takeContact(m.groupValues[1]); return SendSms(ContactRef(c), body(rest))
        }
        s = s.replace(Regex("^(text|sms|message|tell)\\s+", RegexOption.IGNORE_CASE), "")
        if (s.isBlank()) return null
        val (c, rest) = takeContact(s)
        return SendSms(ContactRef(c), body(rest))
    }

    private fun replySms(t: String): ReplySms {
        var s = t.replace(Regex("^(reply|respond|answer|text back|send a response)\\s*", RegexOption.IGNORE_CASE), "")
        var conversation: ContactRef? = null
        Regex("^to (the |this |that )?(selected |last |newest |latest )?(sms|text|message|conversation)\\s*(.*)$", RegexOption.IGNORE_CASE).find(s)?.let { s = it.groupValues[4] }
            ?: Regex("^(the |this |that )?(selected |last |newest |latest )?(sms|text|message|conversation)\\s*(.*)$", RegexOption.IGNORE_CASE).find(s)?.let { s = it.groupValues[4] }
            ?: Regex("^to (\\S+)\\s*(.*)$", RegexOption.IGNORE_CASE).find(s)?.let { conversation = ContactRef(it.groupValues[1]); s = it.groupValues[2] }
        return ReplySms(conversation, body(s))
    }

    private fun readSms(t: String): ReadSms {
        val sender = Regex("\\bfrom (my \\S+|\\S+)", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.let(::ContactRef)
        val unread = Regex("\\bunread\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)
        return ReadSms(sender = sender, count = 1, unreadOnly = unread)
    }

    private fun contactQuery(t: String): String {
        val patterns = listOf(
            "^(search|look in) (my )?contacts for (?<n>.+)$",
            "number belongs to (?<n>.+)$",
            "^(display|show|open) (the )?contact (called |named )?(?<n>.+)$",
            "^find (?<n>.+?) (in (my )?contacts|contact)$",
            "^look up (?<n>.+?) (phone )?number$",
            "address book (to|for) (?<n>.+)$",
        )
        for (p in patterns) Regex(p, RegexOption.IGNORE_CASE).find(t)?.let { return it.groups["n"]!!.value.trim() }
        return t.replace(Regex("\\b(find|search|show|display|open|my|the|contacts?|in|for|address book|phone number)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim().ifEmpty { "unspecified" }
    }

    private fun searchInApp(t: String): SearchInApp? {
        Regex("^search in (?<app>.+?) for (?<q>.+)$", RegexOption.IGNORE_CASE).find(t)?.let {
            return SearchInApp(AppRef(appName(it.groups["app"]!!.value)), it.groups["q"]!!.value)
        }
        Regex("^(search|find|look up)( for)? (?<q>.+) (in|on) (?<app>.+)$", RegexOption.IGNORE_CASE).find(t)?.let {
            return SearchInApp(AppRef(appName(it.groups["app"]!!.value)), it.groups["q"]!!.value.trim())
        }
        Regex("^search (?<app>\\S+) for (?<q>.+)$", RegexOption.IGNORE_CASE).find(t)?.let {
            return SearchInApp(AppRef(appName(it.groups["app"]!!.value)), it.groups["q"]!!.value)
        }
        return null
    }

    private fun callee(s: String): String = s
        .replace(Regex("\\s+on speaker( ?phone)?$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^(the )?(saved )?(contact|person)( called| named)?\\s+", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun dictation(t: String): String {
        var s = t.replace(Regex("^(type|write|enter|dictate|insert|put)\\s*", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("^(the words|this text|the text)\\s*", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("\\s*\\b(into|in) (the|this) (focused )?(field|box|text editor|editor)$", RegexOption.IGNORE_CASE), "")
        // Placeholders mean "start dictation" rather than literal text.
        return if (Regex("^(a paragraph|this sentence|my message|something)?$", RegexOption.IGNORE_CASE).matches(s.trim())) "" else s.trim()
    }

    private fun uiTarget(t: String): UiTarget {
        val s = t.replace(Regex("^(tap|press|click|choose|select|open)\\s+(on\\s+)?", RegexOption.IGNORE_CASE), "")
        Regex("^the (\\w+) (item|option|button|result|entry|link)$", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            NumberWords.ordinal(m.groupValues[1])?.let { return UiTarget(ordinal = it) }
        }
        val label = s
            .replace(Regex("^(the )?(button|item|option)( labeled| labelled| called| named)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^the\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(button|icon|option|link)$", RegexOption.IGNORE_CASE), "")
            .trim()
        return UiTarget(label = label)
    }

    private fun appName(s: String): String = s
        .replace(Regex("^(the|my)\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+app$", RegexOption.IGNORE_CASE), "")
        .trim()

    companion object {
        /** Rules either match a known shape or don't; uncertainty is handled by asking (dialogue), not by scores. */
        const val CONFIDENCE = 0.9

        private const val ROUTINE_NOUN = "(routines?|automations?|rules?|mode|scheduled (tasks?|actions?|reminders?|sms routine))"
        private const val ROUTINE_START_PATTERN = "^(every|each|when|whenever|automatically|schedule\\b(?!.*\\balarm\\b))"
        private val ROUTINE_START = Regex(ROUTINE_START_PATTERN, RegexOption.IGNORE_CASE)
        private val MULTI_STEP = Regex("\\b(and then|then|after that)\\b", RegexOption.IGNORE_CASE)
        private val OFF_WORDS = Regex("\\b(off|disable|deactivate|stop|end)\\b", RegexOption.IGNORE_CASE)
    }
}

/** English number words for slot values ("fifty", "twenty five", "second"). */
object NumberWords {
    private val units = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val ordinals = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")

    fun parse(s: String): Int? {
        val w = s.trim().lowercase().replace('-', ' ')
        w.toIntOrNull()?.let { return it }
        if (w == "hundred" || w == "one hundred" || w == "a hundred") return 100
        units.indexOf(w).takeIf { it >= 0 }?.let { return it }
        tens[w]?.let { return it }
        val parts = w.split(' ')
        if (parts.size == 2) {
            val t = tens[parts[0]] ?: return null
            val u = units.indexOf(parts[1]).takeIf { it in 1..9 } ?: return null
            return t + u
        }
        return null
    }

    fun ordinal(s: String): Int? = ordinals.indexOf(s.lowercase()).takeIf { it >= 0 }?.plus(1)
        ?: Regex("^(\\d+)(st|nd|rd|th)$").find(s)?.groupValues?.get(1)?.toIntOrNull()
}
