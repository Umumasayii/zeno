package com.zeno.assistant.voice

import com.zeno.domain.action.ActionAdapter
import com.zeno.domain.action.ActionResult
import com.zeno.domain.action.Availability
import com.zeno.domain.action.ExecutionRoute
import com.zeno.domain.intent.IntentGroup
import com.zeno.domain.intent.IntentKind
import com.zeno.domain.policy.AuthorizedIntent
import com.zeno.domain.settings.SettingsRepository

/**
 * The only action Zeno performs in M2a: the listening kill switch (Z-004). It is a real adapter behind
 * the normal router, so the same path is used when more adapters arrive in M3+.
 */
class AssistantControlAdapter(private val settings: SettingsRepository) : ActionAdapter {
    override val route = ExecutionRoute.ANDROID_API
    override fun supports(kind: IntentKind) = kind == IntentKind.STOP_LISTENING
    override fun availability(kind: IntentKind) = Availability.Available
    override suspend fun execute(authorized: AuthorizedIntent): ActionResult {
        settings.update { it.copy(listeningEnabled = false) }
        return ActionResult.Succeeded("Listening is off. Turn it back on with the switch on Home.")
    }
}

/** Honest "not built yet" wording: which milestone delivers each command (docs/10). */
object Roadmap {
    fun milestoneFor(kind: IntentKind): String = when {
        kind == IntentKind.REBOOT || kind == IntentKind.SHUTDOWN -> "M7 (optional root)"
        kind.group == IntentGroup.MESSAGES || kind.group == IntentGroup.CALLS || kind.group == IntentGroup.CONTACTS -> "M4"
        kind.group == IntentGroup.ROUTINES -> "M5"
        kind.group == IntentGroup.LEARNING -> "M8"
        else -> "M3"
    }

    fun notBuiltYet(kind: IntentKind, what: String): String {
        val nothing = when (kind.group) {
            IntentGroup.MESSAGES -> "Nothing was sent."
            IntentGroup.CALLS -> "No call was made."
            else -> "Nothing was changed."
        }
        return "I understood: $what. That action is built in ${milestoneFor(kind)}. $nothing"
    }
}
