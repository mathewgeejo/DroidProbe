package dev.droidprobe.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SAMPLE_PACKAGE = "dev.droidprobe.sample"
const val PROTOCOL_VERSION = 1
val ProbeJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

@Serializable enum class AppMode { FAULTY, CORRECTED }
@Serializable enum class Orientation { PORTRAIT, LANDSCAPE }
@Serializable enum class ActionType { LAUNCH, TAP, ENTER_TEXT, BACK, ROTATE, BACKGROUND, REOPEN, WAIT, CONFIGURE_FAULT, RELEASE_FAULT, EVALUATE }
@Serializable enum class ConditionType { SCREEN, FOREGROUND, ORDER_PERSISTED, ACKNOWLEDGED, DRAFT_ACKNOWLEDGED, REQUEST_ERROR, RECREATED }
@Serializable data class Condition(val type: ConditionType, val value: String? = null, val operationId: String? = null, val minimum: Int = 1)
@Serializable data class FaultConfig(val holdAcknowledgement: Boolean = false, val requestError: Boolean = false)
@Serializable data class Selector(val key: String, val packageName: String = SAMPLE_PACKAGE)
@Serializable data class ActionIR(
    val id: String,
    val type: ActionType,
    val schemaVersion: Int = PROTOCOL_VERSION,
    val packageName: String = SAMPLE_PACKAGE,
    val selector: Selector? = null,
    val text: String? = null,
    val orientation: Orientation? = null,
    val condition: Condition? = null,
    val fault: FaultConfig? = null,
    val invariant: String? = null,
    val preconditions: List<Condition> = emptyList(),
    val dependsOn: List<String> = emptyList(),
    val timeoutMs: Long = 5_000,
    val rationale: String? = null,
) {
    fun key(): String = listOf(type.name, selector?.key, text, orientation, condition, fault, invariant).joinToString("|")
}
@Serializable data class UiElement(val key: String, val label: String, val enabled: Boolean, val editable: Boolean = false, val value: String? = null)
@Serializable data class AppEvent(val sequence: Long, val runId: String, val type: String, val operationId: String? = null, val value: String? = null, val elapsedMs: Long)
@Serializable data class Order(val operationId: String, val recordId: String)
@Serializable data class AppState(
    val runId: String = "manual",
    val mode: AppMode = AppMode.CORRECTED,
    val screen: String = "Home",
    val phase: String = "idle",
    val operationId: String? = null,
    val orders: List<Order> = emptyList(),
    val draftInput: String = "",
    val acknowledgedDraft: String? = null,
    val visibleDraft: String = "",
    val faults: FaultConfig = FaultConfig(),
    val events: List<AppEvent> = emptyList(),
    val generation: Int = 0,
    val foreground: Boolean = false,
    val operationCounter: Int = 0,
    val cartItems: Int = 0,
) {
    fun orderCount(id: String?) = orders.count { it.operationId == id }
}
@Serializable data class Observation(
    val runId: String,
    val sequence: Long,
    val packageName: String,
    val activity: String? = null,
    val screen: String? = null,
    val orientation: Orientation,
    val foreground: Boolean,
    val elements: List<UiElement>,
    val recentActions: List<String> = emptyList(),
    val sdk: AppState? = null,
    val screenshot: String? = null,
    val elapsedMs: Long,
    val synchronizedThroughEvent: Long = 0,
    val limitations: List<String> = emptyList(),
)
@Serializable data class Fixtures(val name: String = "clean-store-v1", val initialDraft: String = "", val initialCartItems: Int = 0)
@Serializable data class AssertionSpec(val invariantId: String, val operationId: String? = null)
@Serializable data class Scenario(
    val schemaVersion: Int = PROTOCOL_VERSION,
    val harnessVersion: String = "0.1.0",
    val name: String,
    val targetPackage: String = SAMPLE_PACKAGE,
    val mode: AppMode = AppMode.FAULTY,
    val fixtures: Fixtures = Fixtures(),
    val initialOrientation: Orientation = Orientation.PORTRAIT,
    val faults: FaultConfig = FaultConfig(),
    val actions: List<ActionIR>,
    val assertion: AssertionSpec,
    val source: String = "recorded",
)
@Serializable data class InvariantResult(val id: String, val passed: Boolean, val applicable: Boolean, val expected: String, val observed: String, val operationId: String? = null) {
    // Logical operation tokens vary across episodes; distinct invariants never merge.
    fun fingerprint(): String = "$id|$expected|${if (id == Oracles.UNIQUE_ORDER) "logical-checkout" else "saved-draft"}"
}
object Oracles {
    const val UNIQUE_ORDER = "checkout.at-most-one-order"
    const val DRAFT = "draft.acknowledged-content-restored"
    const val ERROR = "checkout.error-is-not-success"
    val approved = setOf(UNIQUE_ORDER, DRAFT, ERROR)
    fun evaluate(spec: AssertionSpec, state: AppState): InvariantResult = when (spec.invariantId) {
        UNIQUE_ORDER -> {
            val id = spec.operationId ?: state.operationId
            val count = state.orderCount(id)
            InvariantResult(UNIQUE_ORDER, count <= 1, id != null, "orderCount <= 1", "orderCount = $count", id)
        }
        DRAFT -> {
            val applicable = state.acknowledgedDraft != null && state.events.any { it.type == "draftRestored" }
            InvariantResult(DRAFT, state.acknowledgedDraft == state.visibleDraft, applicable,
                state.acknowledgedDraft ?: "no acknowledged draft", state.visibleDraft)
        }
        ERROR -> {
            val applicable = state.events.any { it.type == "requestError" && it.operationId == state.operationId }
            InvariantResult(ERROR, state.phase != "completed", applicable, "failed request has no success confirmation", state.phase, state.operationId)
        }
        else -> error("Unapproved invariant ${spec.invariantId}")
    }
}
fun Condition.matches(state: AppState?, foreground: Boolean): Boolean = when (type) {
    ConditionType.FOREGROUND -> foreground == (value != "false")
    ConditionType.SCREEN -> state?.screen == value
    ConditionType.ORDER_PERSISTED -> state != null && state.orderCount(operationId ?: state.operationId) >= minimum
    ConditionType.ACKNOWLEDGED -> state?.phase == "completed"
    ConditionType.DRAFT_ACKNOWLEDGED -> state?.acknowledgedDraft != null
    ConditionType.REQUEST_ERROR -> state?.phase == "error"
    ConditionType.RECREATED -> state != null && state.generation >= minimum
}
