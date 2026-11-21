package com.zeno.domain.action

import com.zeno.domain.intent.IntentKind
import com.zeno.domain.policy.AuthorizedIntent

/** Execution routes in order of preference: normal API first, root last (ADR-003, CLAUDE.md). */
enum class ExecutionRoute { ANDROID_API, ACCESSIBILITY, ROOT }

sealed interface Availability {
    data object Available : Availability
    data class Unavailable(val reason: String) : Availability
}

/** Structured outcome; never an optimistic "success" for something not confirmed (docs/02). */
sealed interface ActionResult {
    data class Succeeded(val message: String) : ActionResult
    data class Failed(val reason: String, val retryable: Boolean) : ActionResult
    /** e.g. SMS handed to the radio with no carrier callback yet. Must NOT be blindly retried. */
    data class OutcomeUnknown(val reason: String) : ActionResult
    data class Unavailable(val reasons: List<String>) : ActionResult
}

/** One platform implementation (Android API, Accessibility or Root) for some intents. */
interface ActionAdapter {
    val route: ExecutionRoute
    fun supports(kind: IntentKind): Boolean
    fun availability(kind: IntentKind): Availability
    suspend fun execute(authorized: AuthorizedIntent): ActionResult
}

fun interface ActionRouter {
    suspend fun route(authorized: AuthorizedIntent): ActionResult
}

/**
 * Picks the first AVAILABLE adapter by route preference. Does not fall through to the next route after
 * an adapter has executed and failed: a failed SMS must not be re-sent by another path.
 */
class OrderedActionRouter(private val adapters: List<ActionAdapter>) : ActionRouter {
    override suspend fun route(authorized: AuthorizedIntent): ActionResult {
        val kind = authorized.intent.kind
        val candidates = adapters.filter { it.supports(kind) }.sortedBy { it.route.ordinal }
        if (candidates.isEmpty()) return ActionResult.Unavailable(listOf("No adapter supports $kind yet."))
        val reasons = mutableListOf<String>()
        for (adapter in candidates) {
            when (val a = adapter.availability(kind)) {
                Availability.Available -> return adapter.execute(authorized)
                is Availability.Unavailable -> reasons += "${adapter.route}: ${a.reason}"
            }
        }
        return ActionResult.Unavailable(reasons)
    }
}
