package com.zeno.domain.status

import com.zeno.domain.settings.WakePhraseCheck
import com.zeno.domain.settings.WakePhrasePolicy
import com.zeno.domain.settings.ZenoSettings
import com.zeno.testing.InMemorySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatusAndWakePhraseTest {

    private val on = ZenoSettings(listeningEnabled = true)
    private val allGood = PlatformSnapshot(
        userUnlocked = true, microphoneGranted = true, assistantRoleHeld = true,
        wakeWordEngineReady = true, listeningServiceRunning = true,
    )

    @Test fun `listening off is paused even if everything else is fine`() {
        assertEquals(ListeningStatus.PAUSED, ListeningStatusResolver.resolve(ZenoSettings(), allGood))
    }

    @Test fun `active only when the service reports running`() {
        assertEquals(ListeningStatus.ACTIVE, ListeningStatusResolver.resolve(on, allGood))
        assertEquals(ListeningStatus.NOT_RUNNING, ListeningStatusResolver.resolve(on, allGood.copy(listeningServiceRunning = false)))
    }

    @Test fun `revoked microphone shows permission blocked, never active`() {
        val s = ListeningStatusResolver.resolve(on, allGood.copy(microphoneGranted = false))
        assertEquals(ListeningStatus.PERMISSION_BLOCKED, s)
        assertFalse(ListeningStatusResolver.mayShowListeningIndicator(s))
    }

    @Test fun `precedence is unlock, mic, role, engine, service`() {
        val nothing = PlatformSnapshot(false, false, false, false, false)
        assertEquals(ListeningStatus.WAITING_FOR_UNLOCK, ListeningStatusResolver.resolve(on, nothing))
        assertEquals(ListeningStatus.PERMISSION_BLOCKED, ListeningStatusResolver.resolve(on, nothing.copy(userUnlocked = true)))
        assertEquals(ListeningStatus.ROLE_MISSING, ListeningStatusResolver.resolve(on, nothing.copy(userUnlocked = true, microphoneGranted = true)))
        assertEquals(
            ListeningStatus.ENGINE_UNAVAILABLE,
            ListeningStatusResolver.resolve(on, nothing.copy(userUnlocked = true, microphoneGranted = true, assistantRoleHeld = true)),
        )
    }

    @Test fun `only active may show the listening indicator`() {
        ListeningStatus.entries.forEach {
            assertEquals(it == ListeningStatus.ACTIVE, ListeningStatusResolver.mayShowListeningIndicator(it))
        }
    }

    @Test fun `wake phrase validation`() {
        assertTrue(WakePhrasePolicy.check("   ", emptySet()) is WakePhraseCheck.Invalid)
        assertTrue(WakePhrasePolicy.check("Zeno", emptySet()) is WakePhraseCheck.Invalid)
        assertTrue(WakePhrasePolicy.check("hey " + "a".repeat(40), emptySet()) is WakePhraseCheck.Invalid)
        assertEquals(WakePhraseCheck.NeedsCompatibleModel(emptySet()), WakePhrasePolicy.check("Hey Zeno", emptySet()))
        assertEquals(WakePhraseCheck.Supported, WakePhrasePolicy.check("  hey   ZENO ", setOf("Hey Zeno")))
        assertTrue(WakePhrasePolicy.check("Hello Friday", setOf("Hey Zeno")) is WakePhraseCheck.NeedsCompatibleModel)
    }

    @Test fun `reset restores safe defaults with listening off`() = runTest {
        val repo = InMemorySettingsRepository(ZenoSettings(listeningEnabled = true, assistantName = "Ada"))
        repo.resetToDefaults()
        val s = repo.settings.first()
        assertEquals(ZenoSettings(), s)
        assertFalse(s.listeningEnabled)
        assertFalse(s.learningEnabled)
        assertTrue(s.enabledRootOperations.isEmpty())
    }
}
