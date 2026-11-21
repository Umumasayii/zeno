package com.zeno.domain.settings

import kotlinx.coroutines.flow.Flow

/** Port for persisted preferences (DataStore adapter in :app). */
interface SettingsRepository {
    val settings: Flow<ZenoSettings>

    /** Atomically read-modify-write. Invalid results (failing ZenoSettings.init) are rejected. */
    suspend fun update(transform: (ZenoSettings) -> ZenoSettings)

    /** Restore safe defaults (Z-031). Listening ends up OFF. */
    suspend fun resetToDefaults()
}

sealed interface WakePhraseCheck {
    data object Supported : WakePhraseCheck
    /** The phrase can be saved as a display name, but a compatible wake-word model is required (Z-001). */
    data class NeedsCompatibleModel(val supported: Set<String>) : WakePhraseCheck
    data class Invalid(val reason: String) : WakePhraseCheck
}

object WakePhrasePolicy {
    const val MAX_LENGTH = 40

    fun normalize(phrase: String): String = phrase.trim().replace(Regex("\\s+"), " ").lowercase()

    /** [supported] comes from installed, verified wake-word models (empty until M2b). */
    fun check(phrase: String, supported: Set<String>): WakePhraseCheck {
        val n = normalize(phrase)
        if (n.isEmpty()) return WakePhraseCheck.Invalid("The wake phrase can't be empty.")
        if (n.length > MAX_LENGTH) return WakePhraseCheck.Invalid("Keep the wake phrase under $MAX_LENGTH characters.")
        if (n.split(' ').size < 2) return WakePhraseCheck.Invalid("Use at least two words, like \"Hey Zeno\", to reduce false triggers.")
        return if (supported.any { normalize(it) == n }) WakePhraseCheck.Supported
        else WakePhraseCheck.NeedsCompatibleModel(supported)
    }
}
