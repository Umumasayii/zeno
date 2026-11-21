package com.zeno.assistant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zeno.domain.root.RootOperation
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.ZenoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeBootFlags : BootFlagStore {
        var flags: BootFlags? = null
        var writes = 0
        override suspend fun read() = flags
        override suspend fun write(flags: BootFlags) { this.flags = flags; writes++ }
    }

    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File by lazy { File(tmp.root, "zeno_settings.preferences_pb") }

    private fun open(): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    /** Simulates a process restart: the old DataStore is shut down before a new one opens the same file. */
    private fun restart(): DataStore<Preferences> {
        scopes.forEach { it.cancel() }
        scopes.clear()
        return open()
    }

    @After fun tearDown() = scopes.forEach { it.cancel() }

    @Test fun `empty store yields safe defaults`() = runBlocking {
        val repo = DataStoreSettingsRepository(open(), FakeBootFlags())
        val s = repo.settings.first()
        assertEquals(ZenoSettings(), s)
        assertFalse(s.listeningEnabled)
        assertEquals(SmsPolicy.ALWAYS_CONFIRM, s.smsPolicy)
    }

    @Test fun `changes survive a restart`() = runBlocking {
        val repo = DataStoreSettingsRepository(open(), FakeBootFlags())
        repo.update { it.copy(assistantName = "Ada", smsPolicy = SmsPolicy.SMART, retentionDays = 7, listeningEnabled = true, automationsPaused = true) }

        val reopened = DataStoreSettingsRepository(restart(), FakeBootFlags()).settings.first()
        assertEquals("Ada", reopened.assistantName)
        assertEquals(SmsPolicy.SMART, reopened.smsPolicy)
        assertEquals(7, reopened.retentionDays)
        assertTrue(reopened.listeningEnabled)
        assertTrue(reopened.automationsPaused)
    }

    @Test fun `boot flags are mirrored on every update and on reset`() = runBlocking {
        val boot = FakeBootFlags()
        val repo = DataStoreSettingsRepository(open(), boot)
        repo.update { it.copy(listeningEnabled = true, startListeningAfterBoot = false) }
        assertEquals(BootFlags(listeningEnabled = true, startListeningAfterBoot = false), boot.flags)

        repo.resetToDefaults()
        assertEquals(BootFlags(listeningEnabled = false, startListeningAfterBoot = true), boot.flags)
        assertEquals(ZenoSettings(), repo.settings.first())
    }

    @Test fun `invalid update writes nothing`() = runBlocking {
        val boot = FakeBootFlags()
        val repo = DataStoreSettingsRepository(open(), boot)
        repo.update { it.copy(assistantName = "Ada") }
        val writesBefore = boot.writes
        val failed = runCatching { repo.update { it.copy(assistantName = " ") } }
        assertTrue(failed.isFailure)
        assertEquals("Ada", repo.settings.first().assistantName)
        assertEquals(writesBefore, boot.writes)
    }

    @Test fun `corrupt or unknown values fall back to safe defaults`() = runBlocking {
        val store = open()
        store.edit {
            it[stringPreferencesKey("sms_policy")] = "SEND_WITHOUT_ASKING"
            it[intPreferencesKey("retention_days")] = 0
            it[stringPreferencesKey("assistant_name")] = "   "
            it[stringSetPreferencesKey("enabled_root_operations")] = setOf("FORMAT_DISK", "REBOOT")
            it[intPreferencesKey("learning_min_repetitions")] = 1
        }
        val s = DataStoreSettingsRepository(store, FakeBootFlags()).settings.first()
        assertEquals(SmsPolicy.ALWAYS_CONFIRM, s.smsPolicy)
        assertEquals(30, s.retentionDays)
        assertEquals("Zeno", s.assistantName)
        assertEquals(5, s.learningMinRepetitions)
        // Only typed allowlist members survive (Z-015).
        assertEquals(setOf(RootOperation.REBOOT), s.enabledRootOperations)
    }

    @Test fun `sync repairs a missing or stale mirror`() = runBlocking {
        val boot = FakeBootFlags()
        val repo = DataStoreSettingsRepository(open(), FakeBootFlags())
        repo.update { it.copy(listeningEnabled = true) }

        val repaired = DataStoreSettingsRepository(restart(), boot)
        assertEquals(null, boot.flags)
        repaired.syncBootFlags()
        assertEquals(BootFlags(listeningEnabled = true, startListeningAfterBoot = true), boot.flags)
        val writes = boot.writes
        repaired.syncBootFlags()
        assertEquals("no rewrite when already in sync", writes, boot.writes)
    }
}
