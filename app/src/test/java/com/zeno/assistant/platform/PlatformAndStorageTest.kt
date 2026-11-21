package com.zeno.assistant.platform

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeno.assistant.data.BootFlags
import com.zeno.assistant.data.DeviceProtectedBootFlagStore
import com.zeno.assistant.model.DirectoryModelFileStore
import com.zeno.assistant.model.ModelScanner
import com.zeno.domain.model.ModelInventory
import com.zeno.domain.model.ModelKind
import com.zeno.domain.model.ModelStatus
import com.zeno.domain.security.Capability
import com.zeno.domain.settings.ZenoSettings
import com.zeno.domain.status.ListeningStatus
import com.zeno.domain.status.ListeningStatusResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowRoleManager
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PlatformAndStorageTest {

    @get:Rule val tmp = TemporaryFolder()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val platform = AndroidPlatformStatus(app)

    @Test fun `microphone revoked is reported and blocks listening`() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertTrue(platform.snapshot(false, false).microphoneGranted)

        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val snap = platform.snapshot(wakeWordEngineReady = true, listeningServiceRunning = true)
        assertFalse(snap.microphoneGranted)
        assertEquals(
            ListeningStatus.PERMISSION_BLOCKED,
            ListeningStatusResolver.resolve(ZenoSettings(listeningEnabled = true), snap),
        )
        assertEquals(CapabilityState.NOT_GRANTED, platform.capabilities().single { it.capability == Capability.MICROPHONE }.state)
    }

    @Test fun `assistant role is read from RoleManager`() {
        val roles = shadowOf(app.getSystemService(RoleManager::class.java)) as ShadowRoleManager
        assertFalse(platform.snapshot(false, false).assistantRoleHeld)
        roles.addHeldRole(RoleManager.ROLE_ASSISTANT)
        assertTrue(platform.snapshot(false, false).assistantRoleHeld)
        roles.removeHeldRole(RoleManager.ROLE_ASSISTANT)
        assertFalse(platform.snapshot(false, false).assistantRoleHeld)
    }

    @Test fun `every capability is listed, only the microphone is used by this build, root is never probed`() {
        val caps = platform.capabilities()
        assertEquals(Capability.entries.toSet(), caps.map { it.capability }.toSet())
        assertEquals(setOf(Capability.MICROPHONE), caps.filter { it.usedByThisBuild }.map { it.capability }.toSet())
        assertEquals(CapabilityState.NOT_CHECKED, caps.single { it.capability == Capability.ROOT }.state)
        // This build declares none of these permissions, so none can be granted.
        listOf(Capability.CONTACTS, Capability.SMS_SEND, Capability.SMS_READ, Capability.CALL_PHONE).forEach { c ->
            assertEquals(c.name, CapabilityState.NOT_GRANTED, caps.single { it.capability == c }.state)
        }
    }

    @Test fun `boot flags round-trip through device-protected storage`() = runBlocking {
        val store = DeviceProtectedBootFlagStore(app)
        assertEquals(null, store.read())
        store.write(BootFlags(listeningEnabled = true, startListeningAfterBoot = false))
        assertEquals(BootFlags(true, false), DeviceProtectedBootFlagStore(app).read())
    }

    // ------------------------------------------------------------ model directory

    private val bytes = "synthetic-model".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun manifest(file: String = "wake.bin") = """
        {"version":1,"models":[{"id":"w","kind":"wake_word","version":"1","file":"$file","sha256":"$sha",
        "sizeBytes":${bytes.size},"license":"Apache-2.0","source":"official release","phrases":["Hey Zeno"]}]}
    """.trimIndent()

    @Test fun `no models directory content means nothing installed`() {
        assertEquals(ModelInventory.EMPTY, ModelScanner(tmp.newFolder("models")).scan())
        assertNotNull(ModelScanner(null).scan().manifestProblem)
    }

    @Test fun `installed model is verified, then detected as corrupt`() {
        val dir = tmp.newFolder("models")
        File(dir, "manifest.json").writeText(manifest())
        val model = File(dir, "wake.bin").apply { writeBytes(bytes) }
        val inv = ModelScanner(dir).scan()
        assertEquals(ModelStatus.Verified, inv.reports.single().status)
        assertTrue(inv.isReady(ModelKind.WAKE_WORD))

        model.writeBytes(bytes.reversedArray())
        assertEquals(ModelStatus.ChecksumMismatch, ModelScanner(dir).scan().reports.single().status)
        model.delete()
        assertEquals(ModelStatus.Missing, ModelScanner(dir).scan().reports.single().status)
    }

    @Test fun `file store refuses paths outside the models directory`() {
        val dir = tmp.newFolder("models")
        File(tmp.root, "secret.txt").writeText("x")
        val store = DirectoryModelFileStore(dir)
        listOf("../secret.txt", "..", "a/b", "a\\b", "").forEach { name ->
            assertTrue(name, runCatching { store.exists(name) }.isFailure)
        }
    }

    @Test fun `oversized manifest is reported`() {
        val dir = tmp.newFolder("models")
        File(dir, "manifest.json").writeText(" ".repeat((ModelScanner.MAX_MANIFEST_BYTES + 1).toInt()))
        assertNotNull(ModelScanner(dir).scan().manifestProblem)
    }
}
