package com.zeno.assistant.platform

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zeno.domain.security.Capability
import com.zeno.domain.status.PlatformSnapshot

enum class CapabilityState { GRANTED, NOT_GRANTED, NOT_CHECKED }

/**
 * Live grant state of one capability (Z-033 checklist, screen 11). [usedFrom] is the milestone that
 * first needs it; capabilities not used by this build are shown as "not needed yet".
 */
data class CapabilityStatus(
    val capability: Capability,
    val state: CapabilityState,
    val usedFrom: String,
    val usedByThisBuild: Boolean,
)

interface PlatformStatusSource {
    fun snapshot(wakeWordEngineReady: Boolean, listeningServiceRunning: Boolean): PlatformSnapshot
    fun capabilities(): List<CapabilityStatus>
}

object CapabilityPlan {
    /** Milestone that first uses each capability (docs/10). */
    val usedFrom: Map<Capability, String> = mapOf(
        Capability.MICROPHONE to "M2a",
        Capability.ASSISTANT_ROLE to "M2b",
        Capability.POST_NOTIFICATIONS to "M2b",
        Capability.ACCESSIBILITY to "M3",
        Capability.NOTIFICATION_LISTENER to "M3",
        Capability.DND_ACCESS to "M3",
        Capability.WRITE_SETTINGS to "M3",
        Capability.CONTACTS to "M4",
        Capability.SMS_SEND to "M4",
        Capability.SMS_READ to "M4",
        Capability.CALL_PHONE to "M4",
        Capability.EXACT_ALARM to "M5",
        Capability.ROOT to "M7",
        Capability.USAGE_ACCESS to "M8",
    )
    val usedByThisBuild: Set<Capability> = setOf(Capability.MICROPHONE)
}

/** Reads facts from Android. Never requests anything, never opens the microphone, never runs `su`. */
class AndroidPlatformStatus(private val context: Context) : PlatformStatusSource {

    override fun snapshot(wakeWordEngineReady: Boolean, listeningServiceRunning: Boolean) = PlatformSnapshot(
        userUnlocked = safe { context.getSystemService(UserManager::class.java).isUserUnlocked },
        microphoneGranted = granted(Manifest.permission.RECORD_AUDIO),
        assistantRoleHeld = assistantRoleHeld(),
        wakeWordEngineReady = wakeWordEngineReady,
        listeningServiceRunning = listeningServiceRunning,
    )

    override fun capabilities(): List<CapabilityStatus> = Capability.entries.map { c ->
        CapabilityStatus(
            capability = c,
            state = check(c),
            usedFrom = CapabilityPlan.usedFrom.getValue(c),
            usedByThisBuild = c in CapabilityPlan.usedByThisBuild,
        )
    }

    private fun check(c: Capability): CapabilityState = when (c) {
        Capability.MICROPHONE -> state(granted(Manifest.permission.RECORD_AUDIO))
        Capability.ASSISTANT_ROLE -> state(assistantRoleHeld())
        Capability.CONTACTS -> state(granted(Manifest.permission.READ_CONTACTS))
        Capability.SMS_SEND -> state(granted(Manifest.permission.SEND_SMS))
        Capability.SMS_READ -> state(granted(Manifest.permission.READ_SMS))
        Capability.CALL_PHONE -> state(granted(Manifest.permission.CALL_PHONE))
        Capability.POST_NOTIFICATIONS -> state(granted(Manifest.permission.POST_NOTIFICATIONS))
        Capability.NOTIFICATION_LISTENER -> state(safe {
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        })
        Capability.ACCESSIBILITY -> state(safe {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            enabled.split(':').any { it.substringBefore('/') == context.packageName }
        })
        Capability.DND_ACCESS -> state(safe { context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted })
        Capability.EXACT_ALARM -> state(safe { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() })
        Capability.WRITE_SETTINGS -> state(safe { Settings.System.canWrite(context) })
        Capability.USAGE_ACCESS -> state(safe {
            context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        })
        // Root is optional and off; probing it would mean running `su`, which M1 never does (Z-015).
        Capability.ROOT -> CapabilityState.NOT_CHECKED
    }

    private fun assistantRoleHeld() = safe { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT) }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun state(ok: Boolean) = if (ok) CapabilityState.GRANTED else CapabilityState.NOT_GRANTED

    /** A failed check is reported as "not granted", never as granted. */
    private inline fun safe(block: () -> Boolean): Boolean = runCatching(block).getOrDefault(false)
}
