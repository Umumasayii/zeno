package com.zeno.testing

import com.zeno.domain.action.ActionAdapter
import com.zeno.domain.action.ActionResult
import com.zeno.domain.action.Availability
import com.zeno.domain.action.ExecutionRoute
import com.zeno.domain.automation.BeginResult
import com.zeno.domain.automation.IdempotencyKey
import com.zeno.domain.automation.RunLedger
import com.zeno.domain.automation.RunOutcome
import com.zeno.domain.intent.IntentKind
import com.zeno.domain.policy.AuthorizedIntent
import com.zeno.domain.root.RootBridge
import com.zeno.domain.root.RootOperation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** Configurable fake adapter that records every execution. */
class FakeActionAdapter(
    override val route: ExecutionRoute,
    private val kinds: Set<IntentKind>,
    var availability: Availability = Availability.Available,
    var result: ActionResult = ActionResult.Succeeded("ok"),
) : ActionAdapter {
    val executed = mutableListOf<AuthorizedIntent>()

    override fun supports(kind: IntentKind) = kind in kinds
    override fun availability(kind: IntentKind) = availability
    override suspend fun execute(authorized: AuthorizedIntent): ActionResult {
        executed += authorized
        return result
    }
}

/** In-memory ledger with the same insert-if-absent semantics the Room ledger must provide. */
class InMemoryRunLedger : RunLedger {
    private val mutex = Mutex()
    private val runs = linkedMapOf<String, RunOutcome?>()
    val attempts: Int get() = runs.size

    override suspend fun tryBegin(key: IdempotencyKey, startedAt: Instant): BeginResult = mutex.withLock {
        if (key.value in runs) BeginResult.DUPLICATE else { runs[key.value] = null; BeginResult.STARTED }
    }

    override suspend fun finish(key: IdempotencyKey, outcome: RunOutcome, finishedAt: Instant) = mutex.withLock {
        check(key.value in runs) { "finish() without tryBegin() for ${key.value}" }
        runs[key.value] = outcome
    }

    override suspend fun outcomeOf(key: IdempotencyKey): RunOutcome? = mutex.withLock { runs[key.value] }
}

/** Fake root bridge: records operations, never touches a shell. */
class FakeRootBridge(var granted: Boolean = false) : RootBridge {
    val performed = mutableListOf<RootOperation>()
    override suspend fun isGranted() = granted
    override suspend fun perform(operation: RootOperation): ActionResult {
        if (!granted) return ActionResult.Unavailable(listOf("Root not granted"))
        performed += operation
        return ActionResult.Succeeded("performed $operation")
    }
}
