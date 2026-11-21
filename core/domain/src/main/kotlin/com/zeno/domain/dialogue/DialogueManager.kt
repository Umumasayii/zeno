package com.zeno.domain.dialogue

import com.zeno.domain.intent.AssistantIntent
import com.zeno.domain.intent.IntentKind
import com.zeno.domain.intent.ParsedCommand
import com.zeno.domain.nlu.CommandParser
import com.zeno.domain.nlu.ParseOutcome
import com.zeno.domain.policy.AuthorizationGate
import com.zeno.domain.policy.AuthorizedIntent
import com.zeno.domain.policy.DenyReason
import com.zeno.domain.policy.PolicyContext
import com.zeno.domain.policy.PolicyDecision
import com.zeno.domain.policy.PolicyEngine
import com.zeno.domain.policy.UserApproval
import com.zeno.domain.security.Capability

/** A slot the dialogue is asking for. The next utterance fills it verbatim (docs/03 free-form SMS). */
enum class Slot { SMS_BODY, REPLY_BODY, DICTATION_TEXT, REMINDER_TEXT }

sealed interface DialogueState {
    data object Idle : DialogueState
    data class AwaitingSlot(val pending: AssistantIntent, val slot: Slot) : DialogueState
    data class AwaitingConfirmation(val intent: AssistantIntent, val decision: PolicyDecision.Confirm, val retries: Int = 0) : DialogueState
}

enum class TurnOutcome { EXECUTE, ASKING, CONFIRMING, CANCELLED, DENIED, NEEDS_UNLOCK, NOT_UNDERSTOOD, INFO }

/**
 * One exchange. [authorized] is non-null only when the command passed policy and any confirmation;
 * it is the only thing the caller may hand to the action router.
 */
data class DialogueTurn(
    val reply: String,
    val outcome: TurnOutcome,
    val state: DialogueState,
    val authorized: AuthorizedIntent? = null,
)

/**
 * Multi-turn dialogue (docs/03 "Dialogue principles"): ask for missing slots, read back, wait for an
 * explicit confirmation, allow "change it to …", and let "cancel"/"stop" interrupt from any state.
 *
 * Safety properties (tested in DialogueManagerTest):
 *  - A confirmation only authorizes the exact intent that was read back; editing re-asks.
 *  - While confirming, a different command is NOT accepted as an answer (no command switching).
 *  - Nothing is executed for NotUnderstood/Unsupported input; nothing is guessed.
 *  - Context lives only in this active dialogue; [reset] clears it (app calls it on timeout).
 *
 * [notBuilt] lets a build say honestly that an understood command has no action yet. It is consulted
 * only after policy (so lock-screen and other refusals still win) and before any confirmation, so the
 * owner is never asked to confirm something that cannot happen. It receives the read-back text.
 */
class DialogueManager(
    private val parser: CommandParser,
    private val policy: PolicyEngine,
    private val notBuilt: (intent: AssistantIntent, readBack: String) -> String? = { _, _ -> null },
    private val context: () -> PolicyContext,
) {
    var state: DialogueState = DialogueState.Idle
        private set
    private var lastReply: String = HELP_TEXT
    private var repeated = false

    fun reset() { state = DialogueState.Idle }

    fun handle(utterance: String): DialogueTurn {
        val text = utterance.trim()
        val turn = when (val s = state) {
            DialogueState.Idle -> fresh(text)
            is DialogueState.AwaitingSlot -> fillSlot(s, text)
            is DialogueState.AwaitingConfirmation -> confirm(s, text)
        }
        state = turn.state
        if (!repeated) lastReply = turn.reply
        repeated = false
        return turn
    }

    // ------------------------------------------------------------------ idle

    private fun fresh(text: String): DialogueTurn = when (val o = parser.parse(text)) {
        ParseOutcome.NotUnderstood -> DialogueTurn("Sorry, I didn't understand that. Say \"help\" for examples.", TurnOutcome.NOT_UNDERSTOOD, DialogueState.Idle)
        is ParseOutcome.Unsupported -> DialogueTurn(o.reason, TurnOutcome.NOT_UNDERSTOOD, DialogueState.Idle)
        is ParseOutcome.Understood -> understood(o.command)
    }

    private fun understood(cmd: ParsedCommand): DialogueTurn {
        when (val i = cmd.intent) {
            AssistantIntent.Cancel -> return cancelled("Okay, cancelled.")
            AssistantIntent.Help -> return DialogueTurn(HELP_TEXT, TurnOutcome.INFO, DialogueState.Idle)
            AssistantIntent.Repeat -> { repeated = true; return DialogueTurn(lastReply, TurnOutcome.INFO, DialogueState.Idle) }
            // Turning listening ON is a deliberate UI action, never a spoken one (docs/03 "Assistant").
            AssistantIntent.StartListening -> return DialogueTurn("To turn listening on, use the switch on the Home screen.", TurnOutcome.INFO, DialogueState.Idle)
            is AssistantIntent.SendSms -> if (i.body == null) return ask(i, Slot.SMS_BODY, "What should I say to ${i.recipient.spoken}?")
            is AssistantIntent.ReplySms -> if (i.body == null) return ask(i, Slot.REPLY_BODY, "What should I reply?")
            is AssistantIntent.DictateText -> if (i.text.isBlank()) return ask(i, Slot.DICTATION_TEXT, "What should I type?")
            is AssistantIntent.CreateReminder -> if (i.text.isBlank()) return ask(i, Slot.REMINDER_TEXT, "What should I remind you about?")
            else -> Unit
        }
        return decide(cmd)
    }

    private fun decide(cmd: ParsedCommand): DialogueTurn {
        val ctx = context()
        val d = policy.evaluate(cmd, ctx)
        val explainable = d == PolicyDecision.Allow || d is PolicyDecision.Confirm ||
            (d is PolicyDecision.Deny && d.reason == DenyReason.MISSING_CAPABILITY)
        if (explainable) notBuilt(cmd.intent, describe(cmd.intent).removeSuffix("?"))?.let {
            return DialogueTurn(it, TurnOutcome.INFO, DialogueState.Idle)
        }
        return when (d) {
            PolicyDecision.Allow -> execute(cmd.intent, d, ctx)
            is PolicyDecision.Confirm -> DialogueTurn(
                "${describe(cmd.intent)} ${confirmHint(cmd.intent)}",
                TurnOutcome.CONFIRMING,
                DialogueState.AwaitingConfirmation(cmd.intent, d),
            )
            is PolicyDecision.RequireDeviceCredential -> DialogueTurn(d.reason, TurnOutcome.NEEDS_UNLOCK, DialogueState.Idle)
            is PolicyDecision.Clarify -> DialogueTurn(d.reason, TurnOutcome.NOT_UNDERSTOOD, DialogueState.Idle)
            is PolicyDecision.Deny -> DialogueTurn(denyText(d), TurnOutcome.DENIED, DialogueState.Idle)
        }
    }

    private fun execute(intent: AssistantIntent, d: PolicyDecision, ctx: PolicyContext, approval: UserApproval? = null): DialogueTurn {
        val authorized = AuthorizationGate.authorize(intent, d, ctx.source, approval)
            ?: return DialogueTurn("That isn't allowed right now.", TurnOutcome.DENIED, DialogueState.Idle)
        return DialogueTurn("Okay.", TurnOutcome.EXECUTE, DialogueState.Idle, authorized)
    }

    // ------------------------------------------------------------------ slots

    private fun fillSlot(s: DialogueState.AwaitingSlot, text: String): DialogueTurn {
        if (isInterrupt(text)) return cancelled("Okay, cancelled.")
        if (text.isBlank()) return ask(s.pending, s.slot, "I didn't hear anything. ${questionFor(s)}")
        val filled = when (s.slot) {
            Slot.SMS_BODY -> (s.pending as AssistantIntent.SendSms).copy(body = text)
            Slot.REPLY_BODY -> (s.pending as AssistantIntent.ReplySms).copy(body = text)
            Slot.DICTATION_TEXT -> (s.pending as AssistantIntent.DictateText).copy(text = text)
            Slot.REMINDER_TEXT -> (s.pending as AssistantIntent.CreateReminder).copy(text = text)
        }
        return decide(ParsedCommand(filled, CONFIRMED_SLOT_CONFIDENCE))
    }

    private fun questionFor(s: DialogueState.AwaitingSlot) = when (s.slot) {
        Slot.SMS_BODY -> "What should I say?"
        Slot.REPLY_BODY -> "What should I reply?"
        Slot.DICTATION_TEXT -> "What should I type?"
        Slot.REMINDER_TEXT -> "What should I remind you about?"
    }

    // ------------------------------------------------------------------ confirmation

    private fun confirm(s: DialogueState.AwaitingConfirmation, text: String): DialogueTurn {
        val t = text.lowercase().trim().trimEnd('.', '!')
        if (isInterrupt(t) || DECLINE.matches(t)) return cancelled(if (s.intent.kind in SMS_KINDS) "Okay, I won't send it." else "Okay, cancelled.")
        if (AFFIRM.matches(t)) {
            val ctx = context()
            // Re-check policy at confirmation time: permissions or settings may have changed meanwhile.
            val now = policy.evaluate(ParsedCommand(s.intent, CONFIRMED_SLOT_CONFIDENCE), ctx)
            return when (now) {
                PolicyDecision.Allow, is PolicyDecision.Confirm -> execute(s.intent, now, ctx, UserApproval.Confirmed(s.intent))
                else -> decide(ParsedCommand(s.intent, CONFIRMED_SLOT_CONFIDENCE))
            }
        }
        CHANGE.find(text.trim())?.let { m ->
            val newBody = m.groupValues[2].trim()
            val edited = when (val i = s.intent) {
                is AssistantIntent.SendSms -> i.copy(body = newBody)
                is AssistantIntent.ReplySms -> i.copy(body = newBody)
                else -> null
            }
            if (edited != null && newBody.isNotEmpty()) return decide(ParsedCommand(edited, CONFIRMED_SLOT_CONFIDENCE))
        }
        if (s.retries >= 1) return cancelled("I didn't get a clear answer, so I cancelled it.")
        return DialogueTurn("Please say ${affirmWord(s.intent)} or \"cancel\".", TurnOutcome.CONFIRMING, s.copy(retries = s.retries + 1))
    }

    // ------------------------------------------------------------------ wording

    private fun ask(pending: AssistantIntent, slot: Slot, q: String) =
        DialogueTurn(q, TurnOutcome.ASKING, DialogueState.AwaitingSlot(pending, slot))

    private fun cancelled(msg: String) = DialogueTurn(msg, TurnOutcome.CANCELLED, DialogueState.Idle)

    private fun isInterrupt(t: String): Boolean =
        (parser.parse(t) as? ParseOutcome.Understood)?.command?.intent?.let { it == AssistantIntent.Cancel || it == AssistantIntent.StopListening } == true

    private fun affirmWord(i: AssistantIntent) = when (i.kind) {
        in SMS_KINDS -> "\"send\""
        IntentKind.START_CALL -> "\"call\""
        else -> "\"confirm\""
    }

    private fun confirmHint(i: AssistantIntent) = "Say ${affirmWord(i)} or \"cancel\"."

    /** Read-back text. Shows exactly what will happen, including the full message (docs/01 SMS policy). */
    fun describe(i: AssistantIntent): String = when (i) {
        is AssistantIntent.SendSms -> "Send to ${i.recipient.spoken}: “${i.body}”?"
        is AssistantIntent.ReplySms -> "Reply${i.conversation?.let { " to ${it.spoken}" } ?: ""}: “${i.body}”?"
        is AssistantIntent.StartCall -> "Call ${i.callee.spoken}?"
        AssistantIntent.Reboot -> "Restart the phone?"
        AssistantIntent.Shutdown -> "Turn the phone off?"
        is AssistantIntent.DeleteRoutine -> "Delete the routine “${i.routine.spoken}”? This can't be undone."
        is AssistantIntent.CreateRoutine -> "Create a routine from “${i.spokenDefinition}”? You'll review it before it's turned on."
        AssistantIntent.EnableLearning -> "Turn on adaptive learning?"
        else -> "${label(i.kind)}?"
    }

    private fun denyText(d: PolicyDecision.Deny): String = when (d.reason) {
        DenyReason.MISSING_CAPABILITY -> "I can't do that yet: it needs ${d.missing.joinToString(" and ") { capabilityName(it) }}, which isn't set up."
        DenyReason.ROOT_OPERATION_DISABLED -> "Root controls are off, so I can't do that."
        DenyReason.UNKNOWN_SPEAKER, DenyReason.UNCERTAIN_SPEAKER -> "I couldn't verify it's your voice."
        DenyReason.LOW_CONFIDENCE -> "Sorry, I didn't catch that."
        DenyReason.DISALLOWED_IN_V1 -> "I can't do that."
        DenyReason.AUTOMATIONS_PAUSED -> "Automations are paused."
        else -> "That isn't allowed right now."
    }

    companion object {
        /** A slot answered by the owner in an active dialogue is taken as given, not re-scored. */
        private const val CONFIRMED_SLOT_CONFIDENCE = 0.95
        private val SMS_KINDS = setOf(IntentKind.SEND_SMS, IntentKind.REPLY_SMS)
        private val AFFIRM = Regex("^(yes|yeah|yep|yup|sure|ok|okay|confirm|confirmed|do it|go ahead|send|send it|call|call them|place the call|yes send it|yes please)$")
        private val DECLINE = Regex("^(no|nope|don't|do not|no thanks|don't send it|do not send it)$")
        private val CHANGE = Regex("^(change it to|change the message to|make it|instead say|say instead)\\s+(.+)$", RegexOption.IGNORE_CASE)

        val HELP_TEXT = "Try: \"text Mum hello\", \"call John\", \"open Chrome\", \"turn on the flashlight\", \"set an alarm for seven\", or \"stop listening\"."

        fun label(kind: IntentKind): String = kind.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

        fun capabilityName(c: Capability): String = when (c) {
            Capability.ACCESSIBILITY -> "the accessibility service"
            Capability.SMS_SEND -> "SMS permission"
            Capability.SMS_READ -> "SMS read permission"
            Capability.CONTACTS -> "contacts permission"
            Capability.NOTIFICATION_LISTENER -> "notification access"
            Capability.DND_ACCESS -> "Do Not Disturb access"
            Capability.ROOT -> "root"
            else -> c.name.lowercase().replace('_', ' ')
        }
    }
}
