package com.zeno.domain.root

import com.zeno.domain.action.ActionResult
import com.zeno.domain.intent.IntentKind

/**
 * The COMPLETE allowlist of privileged operations (docs/06 "Root execution design", ADR-003).
 *
 * Deliberately an enum without parameters: no parser output, transcript, file path or string can
 * reach a root command. Adding an operation means adding an enum constant, a hardcoded audited
 * mapping in the Android RootBridge (M7) and tests — never a generic "run" entry point.
 */
enum class RootOperation(val intent: IntentKind) {
    POWER_OFF(IntentKind.SHUTDOWN),
    REBOOT(IntentKind.REBOOT),
    ;

    companion object {
        fun forIntent(kind: IntentKind): RootOperation? = entries.firstOrNull { it.intent == kind }
    }
}

/**
 * Port implemented by the Magisk adapter in M7. Takes only the enum: there is intentionally no method
 * accepting a String/command. A reflection test fails the build if one is ever added.
 */
interface RootBridge {
    /** True only if `su` is present AND the user granted Zeno in Magisk. Never assumed. */
    suspend fun isGranted(): Boolean

    /** Runs one allowlisted operation with a timeout. Caller must already hold policy approval. */
    suspend fun perform(operation: RootOperation): ActionResult
}
