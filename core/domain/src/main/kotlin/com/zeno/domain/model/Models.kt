package com.zeno.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest

/**
 * Local model management (docs/08 "Model license and security work", docs/02 "ModelManager").
 * Models are sideloaded by the owner (e.g. `adb push`) together with a manifest. Zeno never downloads
 * them. Every entry must state its licence and source, and is used only if size and SHA-256 match.
 */
@Serializable
enum class ModelKind {
    @SerialName("wake_word") WAKE_WORD,
    @SerialName("asr") ASR,
    @SerialName("speaker_verification") SPEAKER_VERIFICATION,
    @SerialName("tts") TTS,
}

@Serializable
data class ModelDescriptor(
    val id: String,
    val kind: ModelKind,
    val version: String,
    /** File name relative to the models directory. No paths: prevents directory traversal. */
    val file: String,
    val sha256: String,
    val sizeBytes: Long,
    val license: String,
    val source: String,
    /** Wake phrases a wake-word model supports (empty for other kinds). */
    val phrases: List<String> = emptyList(),
)

@Serializable
data class ModelManifest(val version: Int, val models: List<ModelDescriptor>)

sealed interface ModelStatus {
    data object Verified : ModelStatus
    data object Missing : ModelStatus
    data class SizeMismatch(val expected: Long, val actual: Long) : ModelStatus
    data object ChecksumMismatch : ModelStatus
    data class Rejected(val reason: String) : ModelStatus
}

data class ModelReport(val descriptor: ModelDescriptor, val status: ModelStatus)

/** Read-only access to the owner-controlled models directory. */
interface ModelFileStore {
    fun exists(name: String): Boolean
    fun size(name: String): Long
    fun open(name: String): InputStream
}

object ModelManifestParser {
    private val json = Json { ignoreUnknownKeys = false }
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val SAFE_FILE = Regex("^[A-Za-z0-9._-]{1,128}$")

    sealed interface Result {
        data class Ok(val manifest: ModelManifest, val rejected: List<ModelReport>) : Result
        data class Invalid(val reason: String) : Result
    }

    /** Parses and validates; entries breaking the rules are returned as Rejected, not silently dropped. */
    fun parse(text: String): Result {
        val manifest = runCatching { json.decodeFromString(ModelManifest.serializer(), text) }
            .getOrElse { return Result.Invalid("Manifest is not valid JSON for this format: ${it.message?.take(120)}") }
        if (manifest.version != 1) return Result.Invalid("Unsupported manifest version ${manifest.version}")
        val rejected = mutableListOf<ModelReport>()
        val ok = mutableListOf<ModelDescriptor>()
        val seen = mutableSetOf<String>()
        for (d in manifest.models) {
            val reason = when {
                d.id.isBlank() -> "missing id"
                !seen.add(d.id) -> "duplicate id"
                !SAFE_FILE.matches(d.file) || d.file == "." || d.file == ".." -> "file must be a plain file name"
                !SHA256.matches(d.sha256) -> "sha256 must be 64 lowercase hex characters"
                d.sizeBytes <= 0 -> "sizeBytes must be positive"
                d.license.isBlank() -> "licence is required (docs/08)"
                d.source.isBlank() -> "source is required (docs/08)"
                d.kind == ModelKind.WAKE_WORD && d.phrases.isEmpty() -> "wake-word model must list its phrases"
                else -> null
            }
            if (reason == null) ok += d else rejected += ModelReport(d, ModelStatus.Rejected(reason))
        }
        return Result.Ok(manifest.copy(models = ok), rejected)
    }
}

object ModelVerifier {
    fun verify(d: ModelDescriptor, store: ModelFileStore): ModelStatus {
        if (!store.exists(d.file)) return ModelStatus.Missing
        val size = store.size(d.file)
        if (size != d.sizeBytes) return ModelStatus.SizeMismatch(d.sizeBytes, size)
        val digest = MessageDigest.getInstance("SHA-256")
        store.open(d.file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return if (hex == d.sha256) ModelStatus.Verified else ModelStatus.ChecksumMismatch
    }
}

/** Summary used by status UI and the listening resolver. */
data class ModelInventory(val reports: List<ModelReport>, val manifestProblem: String?) {
    fun verified(kind: ModelKind) = reports.filter { it.descriptor.kind == kind && it.status == ModelStatus.Verified }
    fun isReady(kind: ModelKind) = verified(kind).isNotEmpty()
    val supportedWakePhrases: Set<String> get() = verified(ModelKind.WAKE_WORD).flatMap { it.descriptor.phrases }.toSet()

    companion object {
        val EMPTY = ModelInventory(emptyList(), manifestProblem = null)

        fun scan(manifestText: String?, store: ModelFileStore): ModelInventory {
            if (manifestText == null) return EMPTY
            return when (val r = ModelManifestParser.parse(manifestText)) {
                is ModelManifestParser.Result.Invalid -> ModelInventory(emptyList(), r.reason)
                is ModelManifestParser.Result.Ok ->
                    ModelInventory(r.manifest.models.map { ModelReport(it, ModelVerifier.verify(it, store)) } + r.rejected, null)
            }
        }
    }
}
