package com.zeno.assistant.voiceid

import com.zeno.domain.voiceid.VerificationThresholds
import com.zeno.domain.voiceid.VoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The Android Keystore isn't available on the JVM, so a software AES key stands in for it here. */
class EncryptedVoiceProfileStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private class SoftwareKeys : ProfileKeyProvider {
        var key: SecretKey? = null
        override fun existing() = key
        override fun getOrCreate() = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        override fun delete() { key = null }
    }

    private val profile = VoiceProfile(
        modelId = "fake-speaker", modelVersion = "1",
        embeddings = listOf(listOf(0.1f, 0.2f, 0.3f), listOf(0.3f, 0.2f, 0.1f), listOf(0.2f, 0.2f, 0.2f)),
        thresholds = VerificationThresholds(), createdAtMillis = 1234,
    )

    private fun file(dir: File) = File(dir, "voice_profile.bin")

    @Test fun `profile round-trips and is not stored in plain text`() {
        val dir = tmp.newFolder("voice")
        val keys = SoftwareKeys()
        val store = EncryptedVoiceProfileStore(dir, keys)
        assertNull(store.load())
        store.save(profile)
        assertEquals(profile, EncryptedVoiceProfileStore(dir, keys).load())
        val raw = file(dir).readBytes().decodeToString()
        assertFalse(raw.contains("fake-speaker"))
        assertFalse(raw.contains("embeddings"))
    }

    @Test fun `each save uses a fresh IV`() {
        val dir = tmp.newFolder("voice")
        val store = EncryptedVoiceProfileStore(dir, SoftwareKeys())
        store.save(profile)
        val first = file(dir).readBytes()
        store.save(profile)
        assertFalse(first.contentEquals(file(dir).readBytes()))
    }

    @Test fun `delete erases the file and the key, so the old voice can never load again`() {
        val dir = tmp.newFolder("voice")
        val keys = SoftwareKeys()
        val store = EncryptedVoiceProfileStore(dir, keys)
        store.save(profile)
        store.delete()
        assertFalse(file(dir).exists())
        assertNull(keys.key)
        assertNull(store.load())
    }

    @Test fun `tampered, truncated or wrongly keyed files are discarded`() {
        val dir = tmp.newFolder("voice")
        val keys = SoftwareKeys()
        val store = EncryptedVoiceProfileStore(dir, keys)

        store.save(profile)
        val bytes = file(dir).readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file(dir).writeBytes(bytes)
        assertNull(store.load())
        assertFalse(file(dir).exists())

        store.save(profile)
        file(dir).writeBytes(ByteArray(5))
        assertNull(store.load())

        store.save(profile)
        keys.key = null
        keys.getOrCreate() // a different key, e.g. after the Keystore was reset
        assertNull(store.load())
        assertFalse(file(dir).exists())

        store.save(profile)
        keys.key = null // key gone entirely
        assertNull(store.load())
        assertTrue(!file(dir).exists())
    }
}
