package com.zeno.domain.automation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Device facts sampled at execution time (conditions are evaluated when the run starts, docs/04). */
data class DeviceSnapshot(
    val batteryPercent: Int,
    val charging: Boolean,
    val screenUnlocked: Boolean,
    val weekday: Weekday,
)

sealed interface ConditionResult {
    data object Pass : ConditionResult
    data object Fail : ConditionResult
    /** Malformed clause: treated as NOT passing, and surfaced to the user, never silently ignored. */
    data class Invalid(val reason: String) : ConditionResult
}

object ConditionEvaluator {
    fun evaluate(group: ConditionGroup?, device: DeviceSnapshot): ConditionResult {
        if (group == null || group.clauses.isEmpty()) return ConditionResult.Pass
        val results = group.clauses.map { evaluate(it, device) }
        results.firstOrNull { it is ConditionResult.Invalid }?.let { return it }
        val passes = results.map { it == ConditionResult.Pass }
        val ok = when (group.op) {
            ConditionOp.ALL -> passes.all { it }
            ConditionOp.ANY -> passes.any { it }
        }
        return if (ok) ConditionResult.Pass else ConditionResult.Fail
    }

    private fun evaluate(c: Clause, d: DeviceSnapshot): ConditionResult {
        fun bool(b: Boolean) = if (b) ConditionResult.Pass else ConditionResult.Fail
        fun invalid() = ConditionResult.Invalid("${c.field} does not support ${c.comparator} with ${c.value}")
        return when (c.field) {
            ConditionField.BATTERY_PERCENT -> {
                val v = (c.value as? JsonPrimitive)?.intOrNull ?: return invalid()
                when (c.comparator) {
                    Comparator.EQ -> bool(d.batteryPercent == v)
                    Comparator.LT -> bool(d.batteryPercent < v)
                    Comparator.GT -> bool(d.batteryPercent > v)
                    Comparator.IN -> invalid()
                }
            }
            ConditionField.CHARGING, ConditionField.SCREEN_UNLOCKED -> {
                val v = (c.value as? JsonPrimitive)?.booleanOrNull ?: return invalid()
                if (c.comparator != Comparator.EQ) return invalid()
                bool((if (c.field == ConditionField.CHARGING) d.charging else d.screenUnlocked) == v)
            }
            ConditionField.WEEKDAY -> {
                val days = (c.value as? JsonArray)?.map { (it as? JsonPrimitive)?.content } ?: return invalid()
                if (c.comparator != Comparator.IN) return invalid()
                bool(d.weekday.name in days)
            }
        }
    }
}
