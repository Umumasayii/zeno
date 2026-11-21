package com.zeno.assistant.model

import com.zeno.domain.model.ModelFileStore
import com.zeno.domain.model.ModelInventory
import java.io.File
import java.io.InputStream

/**
 * Owner-installed models in the app-specific external directory, e.g.
 * `/sdcard/Android/data/<package>/files/models/` (copied with `adb push`). Zeno never downloads models
 * and has no INTERNET permission. Integrity is checked by size + SHA-256 from `manifest.json`.
 */
class DirectoryModelFileStore(private val dir: File) : ModelFileStore {
    private fun resolve(name: String): File {
        require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") { "plain file names only" }
        val f = File(dir, name)
        require(f.canonicalFile.parentFile == dir.canonicalFile) { "file must be inside the models directory" }
        return f
    }

    override fun exists(name: String) = resolve(name).isFile
    override fun size(name: String) = resolve(name).length()
    override fun open(name: String): InputStream = resolve(name).inputStream().buffered()
}

class ModelScanner(private val dir: File?) {
    /** Display path for the owner, without any personal data. */
    val location: String? get() = dir?.absolutePath

    /** Blocking (hashes files). Call off the main thread. */
    fun scan(): ModelInventory {
        if (dir == null) return ModelInventory(emptyList(), "Shared storage is unavailable right now.")
        val manifest = File(dir, MANIFEST)
        if (!manifest.isFile) return ModelInventory.EMPTY
        if (manifest.length() > MAX_MANIFEST_BYTES) return ModelInventory(emptyList(), "manifest.json is larger than 256 KB.")
        val text = runCatching { manifest.readText() }.getOrElse { return ModelInventory(emptyList(), "manifest.json could not be read.") }
        return ModelInventory.scan(text, DirectoryModelFileStore(dir))
    }

    companion object {
        const val MANIFEST = "manifest.json"
        const val MAX_MANIFEST_BYTES = 256 * 1024L
    }
}
