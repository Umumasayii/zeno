package com.zeno.domain

import com.zeno.domain.automation.ApprovalStatus
import com.zeno.domain.automation.RoutineJson
import com.zeno.domain.intent.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps code and the documentation package's machine-readable specs in sync.
 * These fixtures are synthetic (no real contacts or messages).
 */
class SpecFixturesTest {

    private val specs = File(System.getProperty("zeno.specsDir") ?: error("zeno.specsDir not set"))

    @Test fun `every expected intent in the utterance corpus exists with the same risk level`() {
        val rows = File(specs, "voice_command_examples.csv").readLines().drop(1).filter { it.isNotBlank() }
        assertTrue("corpus unexpectedly small", rows.size >= 300)
        for (row in rows) {
            val cols = row.split(",")
            val id = cols[0]
            val intent = cols[cols.size - 3]
            val risk = cols[cols.size - 2]
            val kind = IntentKind.entries.firstOrNull { it.name == intent }
                ?: throw AssertionError("$id: intent $intent missing from IntentKind")
            assertEquals("$id risk for $intent", risk, kind.risk.name)
        }
    }

    @Test fun `all example routines parse and none is enabled or approved`() {
        val files = File(specs, "examples").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertEquals(10, files.size)
        for (f in files) {
            val routine = RoutineJson.decode(f.readText())
            assertFalse("${f.name} must be disabled", routine.enabled)
            assertNotEquals("${f.name} must not be pre-approved", ApprovalStatus.APPROVED, routine.approval.status)
            assertEquals("${f.name} approvedVersion", null, routine.approval.approvedVersion)
            // Round-trip keeps the contract stable.
            assertEquals(routine, RoutineJson.decode(RoutineJson.encode(routine)))
        }
    }

    @Test fun `unknown fields are rejected like the schema's additionalProperties false`() {
        val text = File(specs, "examples/03_Battery_saver_notification.json").readText()
            .replaceFirst("\"version\"", "\"surprise\": 1, \"version\"")
        val failed = runCatching { RoutineJson.decode(text) }.isFailure
        assertTrue(failed)
    }
}
