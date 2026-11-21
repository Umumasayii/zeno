package com.zeno.domain.automation

import com.zeno.domain.policy.ApprovalCheck
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.security.MessageDigest

/**
 * Approval binds routine ID + version + exact side effects + target + schedule (docs/04 "Approval model").
 * The fingerprint covers trigger, conditions, steps and execution policy; editing the name or
 * description does not invalidate approval, editing a recipient, message or schedule does (T14).
 */
data class RoutineApprovalRecord(
    val routineId: String,
    val version: Int,
    val fingerprint: String,
    val approvedAtEpochMs: Long,
)

object ApprovalFingerprint {
    fun of(routine: Routine): String {
        val f = RoutineJson.format
        val material = JsonObject(
            mapOf(
                "trigger" to f.encodeToJsonElement(routine.trigger),
                "conditions" to (routine.conditions?.let { f.encodeToJsonElement(it) } ?: JsonObject(emptyMap())),
                "steps" to f.encodeToJsonElement(routine.steps),
                "executionPolicy" to f.encodeToJsonElement(routine.executionPolicy),
            ),
        )
        val canonical = canonicalize(material).toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Sort object keys recursively so semantically equal JSON hashes identically. */
    private fun canonicalize(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.toSortedMap().mapValues { canonicalize(it.value) })
        is JsonArray -> JsonArray(e.map(::canonicalize))
        else -> e
    }
}

object ApprovalVerifier {
    fun check(routine: Routine, record: RoutineApprovalRecord?): ApprovalCheck = when {
        record == null || record.routineId != routine.id -> ApprovalCheck.MISSING
        routine.approval.status != ApprovalStatus.APPROVED -> ApprovalCheck.MISSING
        record.version != routine.version || routine.approval.approvedVersion != routine.version -> ApprovalCheck.STALE
        record.fingerprint != ApprovalFingerprint.of(routine) -> ApprovalCheck.STALE
        else -> ApprovalCheck.APPROVED_CURRENT
    }
}
