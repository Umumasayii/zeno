package com.zeno.assistant.voiceid

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.zeno.domain.voiceid.VoiceProfile
import com.zeno.domain.voiceid.VoiceProfileCodec
import com.zeno.domain.voiceid.VoiceProfileStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Supplies the profile encryption key. Android Keystore on the phone; a software key in JVM tests. */
interface ProfileKeyProvider {
    fun getOrCreate(): SecretKey
    fun existing(): SecretKey?
    fun delete()
}

/**
 * AES-256-GCM key in the Android Keystore (non-exportable). Not tied to device unlock so that permitted
 * lock-screen voice tasks can still verify the owner; the file itself lives in credential-encrypted storage,
 * so nothing is readable before the first unlock after boot.
 */
class AndroidKeystoreKeyProvider(private val alias: String = "zeno_voice_profile_v1") : ProfileKeyProvider {
    private val ks: KeyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun existing(): SecretKey? = (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    override fun getOrCreate(): SecretKey = existing() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    override fun delete() { runCatching { ks.deleteEntry(alias) } }
}

/**
 * The owner's voice profile (embeddings only) encrypted at rest in `noBackupFilesDir` (Z-005, Z-028, docs/08).
 * File format: 12-byte IV, then AES-GCM ciphertext of the JSON profile. A file that can't be decrypted
 * (key gone, tampered, corrupt) is deleted and reported as "no profile", so the owner re-enrolls.
 */
class EncryptedVoiceProfileStore(
    dir: File,
    private val keys: ProfileKeyProvider,
) : VoiceProfileStore {
    private val file = File(dir, "voice_profile.bin")

    @Synchronized
    override fun load(): VoiceProfile? {
        if (!file.exists()) return null
        val key = keys.existing() ?: return null.also { file.delete() }
        return runCatching {
            val bytes = file.readBytes()
            require(bytes.size > IV_BYTES) { "truncated" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            VoiceProfileCodec.decode(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
        }.getOrElse { file.delete(); null }
    }

    @Synchronized
    override fun save(profile: VoiceProfile) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.getOrCreate()) // provider picks a fresh random IV
        val out = cipher.iv + cipher.doFinal(VoiceProfileCodec.encode(profile))
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(out)
        check(tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) { "could not store the voice profile" }
    }

    @Synchronized
    override fun delete() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
        keys.delete()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
