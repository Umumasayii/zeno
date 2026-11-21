package com.zeno.domain.automation

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Durable de-duplication key: `routineId + version + occurrenceKey` (docs/02 "Automation orchestration").
 * The run ledger refuses a second insert of the same key, giving at most one local attempt per
 * occurrence across reboots, retries and event replays (Z-020).
 */
data class IdempotencyKey(val routineId: String, val version: Int, val occurrenceKey: String) {
    val value: String get() = "$routineId:v$version:$occurrenceKey"

    companion object {
        /** Occurrence key for a wall-clock schedule: the LOCAL scheduled time plus zone id. */
        fun forScheduled(routineId: String, version: Int, scheduledLocal: ZonedDateTime) =
            IdempotencyKey(routineId, version, "${scheduledLocal.toLocalDateTime()}[${scheduledLocal.zone.id}]")
    }
}

enum class BeginResult { STARTED, DUPLICATE }

enum class RunOutcome { SUCCEEDED, FAILED, UNKNOWN, SKIPPED_CONDITION, SKIPPED_MISSED, CANCELLED }

/** Port for the Room-backed run ledger (M5). Must be transactional: insert-if-absent. */
interface RunLedger {
    suspend fun tryBegin(key: IdempotencyKey, startedAt: Instant): BeginResult
    suspend fun finish(key: IdempotencyKey, outcome: RunOutcome, finishedAt: Instant)
    suspend fun outcomeOf(key: IdempotencyKey): RunOutcome?
}

/**
 * Wall-clock recurrence for [Trigger.WeeklyTime].
 *
 * DST policy (documented, tested):
 *  - Spring-forward gap (local time does not exist): runs at the shifted later instant, once.
 *  - Fall-back overlap (local time occurs twice): runs once, at the EARLIER instant.
 */
object WeeklySchedule {
    fun nextOccurrences(trigger: Trigger.WeeklyTime, after: Instant, count: Int): List<ZonedDateTime> {
        require(count in 1..366) { "count must be 1..366" }
        val zone = ZoneId.of(trigger.timezone)
        val time = LocalTime.parse(trigger.at)
        val isoDays = trigger.days.map { it.isoDay }.toSet()
        val result = mutableListOf<ZonedDateTime>()
        var date: LocalDate = after.atZone(zone).toLocalDate()
        // 8 days of look-ahead per requested result is always enough for a weekly rule.
        val limit = date.plusDays(8L * count)
        while (result.size < count && !date.isAfter(limit)) {
            if (date.dayOfWeek.value in isoDays) {
                // ZonedDateTime.of resolves gaps by shifting forward and overlaps to the earlier offset.
                val candidate = ZonedDateTime.of(date, time, zone)
                if (candidate.toInstant().isAfter(after)) result += candidate
            }
            date = date.plusDays(1)
        }
        return result
    }
}

/** What to do with an occurrence delivered late (phone off, Doze, inexact alarm). */
enum class MissedRunDecision { RUN, SKIP_MISSED }

object MissedRunPolicy {
    /** Small fixed delivery tolerance used by `skip`, so a slightly late alarm still runs. */
    val SKIP_TOLERANCE: Duration = Duration.ofMinutes(5)

    fun decide(scheduledAt: Instant, now: Instant, policy: ExecutionPolicy): MissedRunDecision {
        val lateness = Duration.between(scheduledAt, now)
        if (lateness.isNegative) return MissedRunDecision.RUN
        val allowed = when (policy.missedRun) {
            MissedRunMode.SKIP -> minOf(SKIP_TOLERANCE, Duration.ofMinutes(policy.graceMinutes.toLong()))
            MissedRunMode.RUN_IF_WITHIN_GRACE -> Duration.ofMinutes(policy.graceMinutes.toLong())
        }
        return if (lateness <= allowed) MissedRunDecision.RUN else MissedRunDecision.SKIP_MISSED
    }

    /**
     * After downtime, choose which missed occurrences to run: AT MOST ONE (the latest still within
     * policy). Never a catch-up burst of several SMS (docs/04 "Missed-run and duplicate policies").
     */
    fun selectAfterDowntime(missed: List<Instant>, now: Instant, policy: ExecutionPolicy): Instant? =
        missed.filter { decide(it, now, policy) == MissedRunDecision.RUN }.maxOrNull()
}
