package com.zeno.domain.model

import com.zeno.testing.InMemoryModelFileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** Synthetic bytes only; no real model weights are used or bundled. */
class ModelInventoryTest {

    private val bytes = "synthetic-wake-word-model".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun manifest(
        file: String = "wake.onnx",
        sha256: String = sha,
        size: Long = bytes.size.toLong(),
        license: String = "Apache-2.0",
        source: String = "https://example.invalid/official",
        kind: String = "wake_word",
        phrases: String = "[\"Hey Zeno\"]",
        id: String = "wake-1",
        extraEntry: String = "",
    ) = """
        {"version":1,"models":[
          {"id":"$id","kind":"$kind","version":"1.0","file":"$file","sha256":"$sha256",
           "sizeBytes":$size,"license":"$license","source":"$source","phrases":$phrases}$extraEntry
        ]}
    """.trimIndent()

    private val store = InMemoryModelFileStore(mutableMapOf("wake.onnx" to bytes))

    @Test fun `no manifest means nothing ready and no problem`() {
        val inv = ModelInventory.scan(null, store)
        assertEquals(ModelInventory.EMPTY, inv)
        assertFalse(inv.isReady(ModelKind.WAKE_WORD))
    }

    @Test fun `verified model exposes its wake phrases`() {
        val inv = ModelInventory.scan(manifest(), store)
        assertNull(inv.manifestProblem)
        assertEquals(ModelStatus.Verified, inv.reports.single().status)
        assertTrue(inv.isReady(ModelKind.WAKE_WORD))
        assertFalse(inv.isReady(ModelKind.ASR))
        assertEquals(setOf("Hey Zeno"), inv.supportedWakePhrases)
    }

    @Test fun `missing file`() {
        val inv = ModelInventory.scan(manifest(), InMemoryModelFileStore())
        assertEquals(ModelStatus.Missing, inv.reports.single().status)
        assertFalse(inv.isReady(ModelKind.WAKE_WORD))
    }

    @Test fun `size mismatch is checked before hashing`() {
        val inv = ModelInventory.scan(manifest(size = 999), store)
        assertEquals(ModelStatus.SizeMismatch(999, bytes.size.toLong()), inv.reports.single().status)
    }

    @Test fun `corrupt file fails checksum`() {
        val corrupt = bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val inv = ModelInventory.scan(manifest(), InMemoryModelFileStore(mutableMapOf("wake.onnx" to corrupt)))
        assertEquals(ModelStatus.ChecksumMismatch, inv.reports.single().status)
        assertTrue(inv.supportedWakePhrases.isEmpty())
    }

    @Test fun `entries without licence or source are rejected`() {
        val noLicence = ModelInventory.scan(manifest(license = " "), store).reports.single().status
        assertTrue(noLicence is ModelStatus.Rejected)
        val noSource = ModelInventory.scan(manifest(source = ""), store).reports.single().status
        assertTrue(noSource is ModelStatus.Rejected)
    }

    @Test fun `path traversal and bad hashes are rejected`() {
        listOf("../wake.onnx", "dir/wake.onnx", "..", "C:\\x.onnx").forEach { f ->
            val r = ModelInventory.scan(manifest(file = f.replace("\\", "\\\\")), store).reports.single().status
            assertTrue("$f should be rejected", r is ModelStatus.Rejected)
        }
        assertTrue(ModelInventory.scan(manifest(sha256 = sha.uppercase()), store).reports.single().status is ModelStatus.Rejected)
        assertTrue(ModelInventory.scan(manifest(size = 0), store).reports.single().status is ModelStatus.Rejected)
    }

    @Test fun `wake word model must list phrases, other kinds need not`() {
        assertTrue(ModelInventory.scan(manifest(phrases = "[]"), store).reports.single().status is ModelStatus.Rejected)
        assertEquals(ModelStatus.Verified, ModelInventory.scan(manifest(kind = "asr", phrases = "[]"), store).reports.single().status)
    }

    @Test fun `duplicate ids are rejected`() {
        val dup = ",{\"id\":\"wake-1\",\"kind\":\"asr\",\"version\":\"1\",\"file\":\"wake.onnx\",\"sha256\":\"$sha\"," +
            "\"sizeBytes\":${bytes.size},\"license\":\"MIT\",\"source\":\"x\"}"
        val reports = ModelInventory.scan(manifest(extraEntry = dup), store).reports
        assertEquals(2, reports.size)
        assertEquals(1, reports.count { it.status is ModelStatus.Rejected })
    }

    @Test fun `malformed or unknown manifests are reported, not crashed`() {
        assertNotNull(ModelInventory.scan("not json", store).manifestProblem)
        assertNotNull(ModelInventory.scan("{\"version\":2,\"models\":[]}", store).manifestProblem)
        assertNotNull(ModelInventory.scan(manifest().replace("\"version\":1,", "\"version\":1,\"evil\":true,"), store).manifestProblem)
        assertNotNull(ModelInventory.scan(manifest(kind = "llm"), store).manifestProblem)
    }
}
