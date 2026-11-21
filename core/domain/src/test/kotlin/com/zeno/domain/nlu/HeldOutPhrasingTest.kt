package com.zeno.domain.nlu

import com.zeno.domain.intent.IntentKind
import com.zeno.domain.intent.IntentKind.*
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phrasings written AFTER the rules and never used to tune them, to measure generalisation honestly.
 * The threshold documents current quality; it is not a target to overfit. Misses are printed.
 */
class HeldOutPhrasingTest {

    private val heldOut: List<Pair<String, IntentKind>> = listOf(
        "shoot a text to Peter saying running late" to SEND_SMS,
        "text Sarah see you at six" to SEND_SMS,
        "send my sister a message" to SEND_SMS,
        "read me my latest text" to READ_SMS,
        "do I have any new messages" to READ_SMS,
        "reply with on my way" to REPLY_SMS,
        "give Mum a call" to START_CALL,
        "call Grace please" to START_CALL,
        "find Paul in my contacts" to FIND_CONTACT,
        "open Spotify" to OPEN_APP,
        "fire up the camera" to OPEN_APP,
        "search YouTube for cooking videos" to SEARCH_IN_APP,
        "go to the home screen" to HOME,
        "go back one screen" to BACK,
        "show me my recent apps" to RECENTS,
        "scroll down a little" to SCROLL,
        "tap OK" to TAP,
        "type see you tomorrow" to DICTATE_TEXT,
        "grab a screenshot" to TAKE_SCREENSHOT,
        "read the screen to me" to READ_SCREEN,
        "lock the phone" to LOCK_SCREEN,
        "show me the power menu" to POWER_MENU,
        "reboot" to REBOOT,
        "power off" to SHUTDOWN,
        "volume up" to SET_VOLUME,
        "make it louder" to SET_VOLUME,
        "turn the brightness down" to SET_BRIGHTNESS,
        "flashlight off" to SET_FLASHLIGHT,
        "turn on do not disturb" to SET_DND,
        "any new notifications" to READ_NOTIFICATIONS,
        "play some music" to PLAY_MEDIA,
        "skip this song" to NEXT_TRACK,
        "set an alarm for 6:30 tomorrow" to CREATE_ALARM,
        "remind me at five to call the bank" to CREATE_REMINDER,
        "every Friday at noon turn on do not disturb" to CREATE_ROUTINE,
        "show all my routines" to LIST_ROUTINES,
        "pause the gym routine" to PAUSE_ROUTINE,
        "delete the gym routine" to DELETE_ROUTINE,
        "stop listening for now" to STOP_LISTENING,
        "forget about it" to CANCEL,
    )

    @Test fun `held-out phrasings generalise reasonably`() {
        val parser = RuleBasedCommandParser()
        val misses = heldOut.filter { (u, k) -> (parser.parse(u) as? ParseOutcome.Understood)?.command?.intent?.kind != k }
            .map { (u, k) -> "'$u' -> ${parser.parse(u)} (expected $k)" }
        val rate = 1.0 - misses.size.toDouble() / heldOut.size
        println("Held-out accuracy: ${"%.0f".format(rate * 100)}% (${heldOut.size - misses.size}/${heldOut.size})\n" + misses.joinToString("\n"))
        assertTrue("Held-out accuracy ${"%.2f".format(rate)} below 0.75:\n" + misses.joinToString("\n"), rate >= 0.75)
    }

    /** Safety property: when the parser is wrong it must say "not understood", never pick a different command. */
    @Test fun `held-out misses are never a different command`() {
        val parser = RuleBasedCommandParser()
        val wrong = heldOut.mapNotNull { (u, k) ->
            val got = (parser.parse(u) as? ParseOutcome.Understood)?.command?.intent?.kind
            if (got != null && got != k) "'$u' -> $got (expected $k)" else null
        }
        assertTrue("Wrong commands:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }
}
