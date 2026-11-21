package com.zeno.domain.nlu

import com.zeno.domain.intent.AppRef
import com.zeno.domain.intent.AssistantIntent
import com.zeno.domain.intent.AudioStream
import com.zeno.domain.intent.ContactRef
import com.zeno.domain.intent.Direction
import com.zeno.domain.intent.IntentKind
import com.zeno.domain.intent.Level
import com.zeno.domain.intent.MediaCommand
import com.zeno.domain.intent.UiTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommandParserTest {

    private val parser = RuleBasedCommandParser()

    private fun intent(u: String): AssistantIntent {
        val o = parser.parse(u)
        assertTrue("'$u' -> $o", o is ParseOutcome.Understood)
        return (o as ParseOutcome.Understood).command.intent
    }

    private data class Row(val id: String, val utterance: String, val expected: IntentKind)

    private val rows: List<Row> by lazy {
        val f = File(System.getProperty("zeno.specsDir"), "voice_command_examples.csv")
        f.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val c = line.split(',')
            Row(c[0], c[1], IntentKind.valueOf(c[2]))
        }
    }

    /**
     * Every synthetic fixture must parse to its expected intent GROUP (docs/03 "Test corpus"). The CSV labels
     * some rows at group level (e.g. "pause the song" as PLAY_MEDIA, "remind me" as CREATE_ALARM).
     */
    @Test fun `all fixtures map to their expected intent group`() {
        val failures = rows.mapNotNull { r ->
            when (val o = parser.parse(r.utterance)) {
                is ParseOutcome.Understood -> if (o.command.intent.kind.group != r.expected.group) "${r.id} '${r.utterance}' -> ${o.command.intent.kind} (expected ${r.expected})" else null
                else -> "${r.id} '${r.utterance}' -> $o (expected ${r.expected})"
            }
        }
        assertEquals("Fixture failures:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    @Test fun `fixtures match the exact intent except group-level media and clock labels`() {
        val groupLevel = setOf(IntentKind.PLAY_MEDIA, IntentKind.CREATE_ALARM)
        val misses = rows.filter { it.expected !in groupLevel }.mapNotNull { r ->
            val k = (parser.parse(r.utterance) as? ParseOutcome.Understood)?.command?.intent?.kind
            if (k != r.expected) "${r.id} '${r.utterance}' -> $k (expected ${r.expected})" else null
        }
        assertEquals("Exact-intent misses:\n" + misses.joinToString("\n"), 0, misses.size)
    }

    @Test fun `sms slots keep spoken references and never invent a body`() {
        assertEquals(AssistantIntent.SendSms(ContactRef("Mum"), "hello"), intent("text Mum hello"))
        assertEquals(AssistantIntent.SendSms(ContactRef("Mum"), "good morning"), intent("send an SMS to Mum saying good morning"))
        assertEquals(AssistantIntent.SendSms(ContactRef("John"), "I am coming"), intent("message John that I am coming"))
        assertEquals(AssistantIntent.SendSms(ContactRef("Grace"), "I am outside"), intent("send this text to Grace: I am outside"))
        assertEquals(AssistantIntent.SendSms(ContactRef("my mother"), "hello"), intent("Hey Zeno, please text my mother hello"))
        assertEquals(AssistantIntent.SendSms(ContactRef("Mum"), "I will call later"), intent("tell Mum by SMS I will call later"))
        assertEquals(AssistantIntent.SendSms(ContactRef("John"), "I arrived"), intent("send John the message I arrived"))
        // Missing body stays null so the dialogue asks "What should I say?" (docs/03 free-form SMS).
        assertEquals(AssistantIntent.SendSms(ContactRef("my mother"), null), intent("text my mother"))
        assertEquals(AssistantIntent.SendSms(ContactRef("Mary"), null), intent("send a message to the contact called Mary"))
    }

    @Test fun `routine phrasing creates a routine instead of acting now`() {
        assertEquals(IntentKind.CREATE_ROUTINE, intent("every weekday at 7 send a message to Mum saying good morning").kind)
        assertEquals(IntentKind.CREATE_ROUTINE, intent("schedule DND at ten PM").kind)
        assertEquals(IntentKind.CREATE_ALARM, intent("schedule a phone alarm for six").kind)
    }

    @Test fun `system slots`() {
        assertEquals(AssistantIntent.SetVolume(AudioStream.MEDIA, Level.Percent(50)), intent("set media volume to fifty percent"))
        assertEquals(AssistantIntent.SetVolume(AudioStream.RING, Level.Step(-1)), intent("reduce ringing volume"))
        assertEquals(AssistantIntent.SetVolume(AudioStream.MEDIA, Level.Min), intent("mute media audio"))
        assertEquals(AssistantIntent.SetBrightness(Level.Percent(80)), intent("raise brightness to eighty percent"))
        assertEquals(AssistantIntent.SetBrightness(Level.Percent(50)), intent("adjust brightness to half"))
        assertEquals(AssistantIntent.SetFlashlight(false), intent("switch off the torch"))
        assertEquals(AssistantIntent.SetFlashlight(true), intent("turn on the rear flash"))
        assertEquals(AssistantIntent.SetDnd(false), intent("turn off do not disturb"))
        assertEquals(AssistantIntent.SetDnd(true), intent("silence notifications"))
        assertEquals(AssistantIntent.MediaControl(MediaCommand.PAUSE), intent("pause the song"))
        assertEquals(AssistantIntent.MediaControl(MediaCommand.NEXT), intent("skip the music"))
    }

    @Test fun `navigation and ui targets are semantic, never coordinates`() {
        assertEquals(AssistantIntent.Scroll(Direction.DOWN, 2), intent("scroll down twice"))
        assertEquals(AssistantIntent.Scroll(Direction.UP, AssistantIntent.MAX_REPEAT), intent("scroll to the top"))
        assertEquals(AssistantIntent.Tap(UiTarget(ordinal = 2)), intent("choose the second item"))
        assertEquals(AssistantIntent.Tap(UiTarget(label = "save")), intent("tap the save button"))
        assertEquals(AssistantIntent.Tap(UiTarget(label = "next")), intent("select the button labeled next"))
        assertEquals(AssistantIntent.OpenApp(AppRef("Messages")), intent("open the Messages app"))
        assertEquals(AssistantIntent.SearchInApp(AppRef("Maps"), "coffee near me"), intent("search for coffee near me in Maps"))
        assertEquals(AssistantIntent.StartCall(ContactRef("my brother")), intent("call my brother on speaker"))
        assertEquals(AssistantIntent.StartCall(ContactRef("John")), intent("dial the saved contact John"))
    }

    @Test fun `interrupts win and unknown input is not guessed`() {
        assertEquals(AssistantIntent.Cancel, intent("never mind"))
        assertEquals(AssistantIntent.Cancel, intent("stop"))
        assertEquals(AssistantIntent.StopListening, intent("Hey Zeno stop listening"))
        listOf("", "   ", "banana helicopter", "rm -rf /", "what is the meaning of life").forEach {
            assertEquals("'$it'", ParseOutcome.NotUnderstood, parser.parse(it))
        }
        assertTrue(parser.parse("open WhatsApp and then text Mum hello") is ParseOutcome.Unsupported)
    }

    @Test fun `number words`() {
        assertEquals(25, NumberWords.parse("twenty five"))
        assertEquals(100, NumberWords.parse("hundred"))
        assertEquals(3, NumberWords.ordinal("third"))
        assertEquals(null, NumberWords.parse("lots"))
    }
}
