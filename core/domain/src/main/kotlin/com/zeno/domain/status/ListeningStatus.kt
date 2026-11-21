package com.zeno.domain.status

import com.zeno.domain.settings.ZenoSettings

/**
 * What the UI and notification may claim about listening (Z-029, docs/07 "persistent listening status").
 * Derived from facts reported by Android; never from what we hope is happening.
 */
enum class ListeningStatus {
    /** Owner switched listening off. Microphone not in use. */
    PAUSED,
    /** Device rebooted and has not been unlocked yet; credential storage unavailable (SPIKE-02). */
    WAITING_FOR_UNLOCK,
    /** RECORD_AUDIO not granted (or revoked). */
    PERMISSION_BLOCKED,
    /** Zeno is not the default digital assistant; needed for background start (SPIKE-01). */
    ROLE_MISSING,
    /** No verified wake-word model installed (M1: always, until M2b). */
    ENGINE_UNAVAILABLE,
    /** Everything is in place but the listening service is not running (killed, starting, or failed). */
    NOT_RUNNING,
    /** The listening service reports it is capturing audio for wake-word detection. */
    ACTIVE,
}

/** Platform facts sampled by the Android layer. */
data class PlatformSnapshot(
    val userUnlocked: Boolean,
    val microphoneGranted: Boolean,
    val assistantRoleHeld: Boolean,
    val wakeWordEngineReady: Boolean,
    val listeningServiceRunning: Boolean,
)

object ListeningStatusResolver {
    /**
     * Fixed precedence: the owner's choice first, then hard platform blocks, then Zeno's own readiness.
     * ACTIVE is returned only when the service itself reports it is running.
     */
    fun resolve(settings: ZenoSettings, p: PlatformSnapshot): ListeningStatus = when {
        !settings.listeningEnabled -> ListeningStatus.PAUSED
        !p.userUnlocked -> ListeningStatus.WAITING_FOR_UNLOCK
        !p.microphoneGranted -> ListeningStatus.PERMISSION_BLOCKED
        !p.assistantRoleHeld -> ListeningStatus.ROLE_MISSING
        !p.wakeWordEngineReady -> ListeningStatus.ENGINE_UNAVAILABLE
        !p.listeningServiceRunning -> ListeningStatus.NOT_RUNNING
        else -> ListeningStatus.ACTIVE
    }

    /** Only ACTIVE may show a listening animation / "Always listening" text. */
    fun mayShowListeningIndicator(status: ListeningStatus): Boolean = status == ListeningStatus.ACTIVE
}
