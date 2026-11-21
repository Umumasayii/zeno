package com.zeno.domain.automation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Routine contract mirroring specs/automation.schema.json (planning draft v1). Importing a routine
 * never enables or authorizes it (docs/04 "Routine JSON contract").
 */
@Serializable
data class Routine(
    val id: String,
    val name: String,
    val description: String? = null,
    val version: Int,
    val origin: RoutineOrigin,
    val enabled: Boolean,
    val trigger: Trigger,
    val conditions: ConditionGroup? = null,
    val steps: List<Step>,
    val executionPolicy: ExecutionPolicy,
    val approval: Approval,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(version >= 1) { "version starts at 1" }
        require(steps.isNotEmpty()) { "a routine needs at least one step" }
    }
}

@Serializable
enum class RoutineOrigin {
    @SerialName("manual") MANUAL,
    @SerialName("learned") LEARNED,
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Trigger {
    @Serializable @SerialName("weekly_time")
    data class WeeklyTime(val timezone: String, val at: String, val days: Set<Weekday>) : Trigger {
        init {
            require(TIME.matches(at)) { "at must be HH:mm" }
            require(days.isNotEmpty()) { "at least one day" }
        }
    }

    @Serializable @SerialName("once")
    data class Once(val at: String, val timezone: String) : Trigger

    @Serializable @SerialName("interval")
    data class Interval(val minutes: Int) : Trigger {
        // WorkManager's minimum periodic interval is 15 minutes; the schema mirrors it.
        init { require(minutes >= 15) { "interval must be at least 15 minutes" } }
    }

    @Serializable @SerialName("battery_below")
    data class BatteryBelow(val threshold: Int) : Trigger {
        init { require(threshold in 1..100) { "threshold must be 1..100" } }
    }

    @Serializable @SerialName("device_event")
    data class DeviceEvent(val event: DeviceEventType) : Trigger

    @Serializable @SerialName("app_opened")
    data class AppOpened(@SerialName("package") val packageName: String) : Trigger

    private companion object {
        val TIME = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
    }
}

@Serializable
enum class Weekday(val isoDay: Int) {
    MO(1), TU(2), WE(3), TH(4), FR(5), SA(6), SU(7)
}

@Serializable
enum class DeviceEventType {
    @SerialName("charging_connected") CHARGING_CONNECTED,
    @SerialName("charging_disconnected") CHARGING_DISCONNECTED,
    @SerialName("first_unlock") FIRST_UNLOCK,
    @SerialName("headset_connected") HEADSET_CONNECTED,
}

@Serializable
data class ConditionGroup(val op: ConditionOp, val clauses: List<Clause>)

@Serializable
enum class ConditionOp {
    @SerialName("all") ALL,
    @SerialName("any") ANY,
}

@Serializable
data class Clause(val field: ConditionField, val comparator: Comparator, val value: JsonElement)

@Serializable
enum class ConditionField {
    @SerialName("battery_percent") BATTERY_PERCENT,
    @SerialName("charging") CHARGING,
    @SerialName("screen_unlocked") SCREEN_UNLOCKED,
    @SerialName("weekday") WEEKDAY,
}

@Serializable
enum class Comparator {
    @SerialName("eq") EQ,
    @SerialName("lt") LT,
    @SerialName("gt") GT,
    @SerialName("in") IN,
}

@Serializable
data class Step(
    val type: StepType,
    val parameters: JsonObject,
    val onFailure: OnFailure = OnFailure.STOP,
)

@Serializable
enum class StepType {
    @SerialName("send_sms") SEND_SMS,
    @SerialName("open_app") OPEN_APP,
    @SerialName("set_dnd") SET_DND,
    @SerialName("set_brightness") SET_BRIGHTNESS,
    @SerialName("lock_screen") LOCK_SCREEN,
    @SerialName("set_volume") SET_VOLUME,
    @SerialName("notify") NOTIFY,
    @SerialName("speak") SPEAK,
    @SerialName("set_flashlight") SET_FLASHLIGHT,
    @SerialName("play_media") PLAY_MEDIA,
}

@Serializable
enum class OnFailure {
    @SerialName("stop") STOP,
    @SerialName("continue") CONTINUE,
}

@Serializable
data class ExecutionPolicy(
    val requiresConfirmationAtRun: Boolean,
    val missedRun: MissedRunMode,
    val graceMinutes: Int = 5,
    val maxRetries: Int,
    val notifyOnFailure: Boolean,
) {
    init {
        require(graceMinutes in 0..120) { "graceMinutes must be 0..120" }
        require(maxRetries in 0..3) { "maxRetries must be 0..3" }
    }
}

@Serializable
enum class MissedRunMode {
    @SerialName("skip") SKIP,
    @SerialName("run_if_within_grace") RUN_IF_WITHIN_GRACE,
}

@Serializable
data class Approval(
    val status: ApprovalStatus,
    val approvedVersion: Int?,
    val reviewRequired: Boolean = true,
)

@Serializable
enum class ApprovalStatus {
    @SerialName("draft") DRAFT,
    @SerialName("approved") APPROVED,
    @SerialName("revoked") REVOKED,
}

object RoutineJson {
    /** Strict like the schema (`additionalProperties: false`): unknown keys are rejected. */
    val format: Json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun decode(text: String): Routine = format.decodeFromString(Routine.serializer(), text)
    fun encode(routine: Routine): String = format.encodeToString(Routine.serializer(), routine)
}
