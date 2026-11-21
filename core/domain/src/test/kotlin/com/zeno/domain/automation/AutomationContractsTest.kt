package com.zeno.domain.automation

import com.zeno.domain.policy.ApprovalCheck
import com.zeno.testing.InMemoryRunLedger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AutomationContractsTest {

    private val policy = ExecutionPolicy(requiresConfirmationAtRun = false, missedRun = MissedRunMode.SKIP, graceMinutes = 5, maxRetries = 0, notifyOnFailure = true)

    private fun smsRoutine(message: String = "Good morning (synthetic)", recipient: String = "Test Contact A", at: String = "07:00") = Routine(
        id = "r1", name = "Morning", version = 1, origin = RoutineOrigin.MANUAL, enabled = true,
        trigger = Trigger.WeeklyTime("Africa/Kigali", at, Weekday.entries.toSet()),
        steps = listOf(Step(StepType.SEND_SMS, JsonObject(mapOf("contactAlias" to JsonPrimitive(recipient), "message" to JsonPrimitive(message))))),
        executionPolicy = policy,
        approval = Approval(ApprovalStatus.APPROVED, approvedVersion = 1),
    )

    // --- Idempotency (Z-020, T12) ---
    @Test fun `same occurrence can be attempted only once, even after a simulated reboot`() = runTest {
        val ledger = InMemoryRunLedger()
        val occurrence = ZonedDateTime.of(2026, 11, 22, 7, 0, 0, 0, ZoneId.of("Africa/Kigali"))
        val key = IdempotencyKey.forScheduled("r1", 1, occurrence)
        val now = Instant.parse("2026-11-22T05:00:00Z")

        assertEquals(BeginResult.STARTED, ledger.tryBegin(key, now))
        // "Reboot": the alarm is re-delivered and the key recomputed from the same occurrence.
        val replayed = IdempotencyKey.forScheduled("r1", 1, occurrence)
        assertEquals(BeginResult.DUPLICATE, ledger.tryBegin(replayed, now.plusSeconds(60)))
        assertEquals(1, ledger.attempts)
    }

    @Test fun `next day and new version are different occurrences`() {
        val zone = ZoneId.of("Africa/Kigali")
        val d1 = IdempotencyKey.forScheduled("r1", 1, ZonedDateTime.of(2026, 11, 22, 7, 0, 0, 0, zone))
        val d2 = IdempotencyKey.forScheduled("r1", 1, ZonedDateTime.of(2026, 11, 23, 7, 0, 0, 0, zone))
        val v2 = IdempotencyKey.forScheduled("r1", 2, ZonedDateTime.of(2026, 11, 22, 7, 0, 0, 0, zone))
        assertNotEquals(d1.value, d2.value)
        assertNotEquals(d1.value, v2.value)
    }

    // --- Recurrence and DST (CLAUDE.md scheduler rule) ---
    @Test fun `weekday schedule skips weekends in Kigali`() {
        val t = Trigger.WeeklyTime("Africa/Kigali", "07:00", setOf(Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR))
        // Friday 2026-11-20 08:00 local → next is Monday 23rd.
        val after = ZonedDateTime.of(2026, 11, 20, 8, 0, 0, 0, ZoneId.of("Africa/Kigali")).toInstant()
        val next = WeeklySchedule.nextOccurrences(t, after, 3)
        assertEquals(listOf(23, 24, 25), next.map { it.dayOfMonth })
        next.forEach { assertEquals(7, it.hour) }
    }

    @Test fun `spring forward gap runs once at the shifted time`() {
        // US DST starts 2026-03-08 02:00 → 03:00. 02:30 does not exist that day.
        val t = Trigger.WeeklyTime("America/New_York", "02:30", Weekday.entries.toSet())
        val after = Instant.parse("2026-03-07T12:00:00Z")
        val next = WeeklySchedule.nextOccurrences(t, after, 3)
        // 02:30 on the 7th already passed at 07:00 local, so the next three days are 8, 9, 10.
        assertEquals(listOf(8, 9, 10), next.map { it.dayOfMonth })
        val gapDay = next.single { it.dayOfMonth == 8 }
        assertEquals(3, gapDay.hour)
        assertEquals(30, gapDay.minute)
    }

    @Test fun `fall back overlap runs once at the earlier instant`() {
        // US DST ends 2026-11-01 02:00 → 01:00. 01:30 happens twice; we must schedule it once.
        val t = Trigger.WeeklyTime("America/New_York", "01:30", Weekday.entries.toSet())
        val after = Instant.parse("2026-10-31T12:00:00Z")
        val next = WeeklySchedule.nextOccurrences(t, after, 2)
        val overlapDay = next.single { it.dayOfMonth == 1 }
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), overlapDay.toInstant()) // EDT, UTC-4
        assertEquals(1, next.count { it.dayOfMonth == 1 })
    }

    // --- Missed runs (T13) ---
    @Test fun `skip policy runs a slightly late alarm but skips a long-missed one`() {
        val scheduled = Instant.parse("2026-11-22T05:00:00Z")
        assertEquals(MissedRunDecision.RUN, MissedRunPolicy.decide(scheduled, scheduled.plus(Duration.ofMinutes(4)), policy))
        assertEquals(MissedRunDecision.SKIP_MISSED, MissedRunPolicy.decide(scheduled, scheduled.plus(Duration.ofMinutes(6)), policy))
    }

    @Test fun `grace policy honours the configured window`() {
        val p = policy.copy(missedRun = MissedRunMode.RUN_IF_WITHIN_GRACE, graceMinutes = 60)
        val scheduled = Instant.parse("2026-11-22T05:00:00Z")
        assertEquals(MissedRunDecision.RUN, MissedRunPolicy.decide(scheduled, scheduled.plus(Duration.ofMinutes(59)), p))
        assertEquals(MissedRunDecision.SKIP_MISSED, MissedRunPolicy.decide(scheduled, scheduled.plus(Duration.ofMinutes(61)), p))
    }

    @Test fun `phone off for three days never causes a catch-up burst`() {
        val p = policy.copy(missedRun = MissedRunMode.RUN_IF_WITHIN_GRACE, graceMinutes = 120)
        val now = Instant.parse("2026-11-25T05:30:00Z")
        val missed = (22..25).map { Instant.parse("2026-11-${it}T05:00:00Z") }
        assertEquals(Instant.parse("2026-11-25T05:00:00Z"), MissedRunPolicy.selectAfterDowntime(missed, now, p))
        assertNull(MissedRunPolicy.selectAfterDowntime(missed.dropLast(1), now, p))
    }

    // --- Approval binding (Z-021, T14) ---
    @Test fun `approval is current until a side effect changes`() {
        val r = smsRoutine()
        val record = RoutineApprovalRecord("r1", 1, ApprovalFingerprint.of(r), 0)
        assertEquals(ApprovalCheck.APPROVED_CURRENT, ApprovalVerifier.check(r, record))
        assertEquals(ApprovalCheck.APPROVED_CURRENT, ApprovalVerifier.check(r.copy(name = "Renamed"), record))
        assertEquals(ApprovalCheck.STALE, ApprovalVerifier.check(smsRoutine(recipient = "Test Contact B"), record))
        assertEquals(ApprovalCheck.STALE, ApprovalVerifier.check(smsRoutine(message = "changed"), record))
        assertEquals(ApprovalCheck.STALE, ApprovalVerifier.check(smsRoutine(at = "07:05"), record))
    }

    @Test fun `new version needs new approval and drafts are never approved`() {
        val r = smsRoutine()
        val record = RoutineApprovalRecord("r1", 1, ApprovalFingerprint.of(r), 0)
        assertEquals(ApprovalCheck.STALE, ApprovalVerifier.check(r.copy(version = 2), record))
        assertEquals(ApprovalCheck.MISSING, ApprovalVerifier.check(r.copy(approval = Approval(ApprovalStatus.DRAFT, null)), record))
        assertEquals(ApprovalCheck.MISSING, ApprovalVerifier.check(r, null))
    }

    // --- Conditions ---
    @Test fun `conditions evaluate all and any, and malformed clauses fail visibly`() {
        val device = DeviceSnapshot(batteryPercent = 30, charging = true, screenUnlocked = false, weekday = Weekday.MO)
        val all = ConditionGroup(
            ConditionOp.ALL,
            listOf(
                Clause(ConditionField.CHARGING, Comparator.EQ, JsonPrimitive(true)),
                Clause(ConditionField.BATTERY_PERCENT, Comparator.LT, JsonPrimitive(40)),
                Clause(ConditionField.WEEKDAY, Comparator.IN, JsonArray(listOf(JsonPrimitive("MO"), JsonPrimitive("TU")))),
            ),
        )
        assertEquals(ConditionResult.Pass, ConditionEvaluator.evaluate(all, device))
        assertEquals(ConditionResult.Fail, ConditionEvaluator.evaluate(all, device.copy(batteryPercent = 50)))
        val any = all.copy(op = ConditionOp.ANY)
        assertEquals(ConditionResult.Pass, ConditionEvaluator.evaluate(any, device.copy(batteryPercent = 50)))
        val bad = ConditionGroup(ConditionOp.ALL, listOf(Clause(ConditionField.CHARGING, Comparator.GT, JsonPrimitive(true))))
        assertTrue(ConditionEvaluator.evaluate(bad, device) is ConditionResult.Invalid)
    }
}
