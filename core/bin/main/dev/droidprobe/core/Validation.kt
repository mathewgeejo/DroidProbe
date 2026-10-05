package dev.droidprobe.core

data class ValidationContext(val observation: Observation, val remainingActions: Int, val executedIds: Set<String> = emptySet(), val repetitions: Map<String, Int> = emptyMap(), val maxRepetitions: Int = 4)
data class Validation(val valid: Boolean, val reason: String? = null)
object ActionValidator {
    fun validate(action: ActionIR, context: ValidationContext): Validation {
        fun reject(reason: String) = Validation(false, reason)
        if (action.schemaVersion != PROTOCOL_VERSION) return reject("Unsupported ActionIR version")
        if (action.id.isBlank() || action.id.length > 100 || action.id in context.executedIds) return reject("Invalid or repeated action ID")
        if (action.packageName != context.observation.packageName || action.packageName != SAMPLE_PACKAGE) return reject("Target package is outside the configured allowlist")
        if (context.remainingActions <= 0) return reject("Action budget exhausted")
        if (action.timeoutMs !in 100..30_000) return reject("Timeout outside supported bounds")
        if ((context.repetitions[action.key()] ?: 0) >= context.maxRepetitions) return reject("Action repetition limit")
        if (!context.executedIds.containsAll(action.dependsOn)) return reject("Missing event/action dependency")
        if (action.preconditions.any { !it.matches(context.observation.sdk, context.observation.foreground) }) return reject("Precondition failed")
        if (action.text != null && (action.type != ActionType.ENTER_TEXT || action.text.length > 2048)) return reject("Invalid text parameter")
        if (action.selector != null && action.type !in setOf(ActionType.TAP, ActionType.ENTER_TEXT)) return reject("Unexpected selector")
        if (action.orientation != null && action.type != ActionType.ROTATE) return reject("Unexpected orientation")
        if (action.condition != null && action.type != ActionType.WAIT) return reject("Unexpected wait condition")
        if (action.fault != null && action.type != ActionType.CONFIGURE_FAULT) return reject("Unexpected fault configuration")
        if (action.invariant != null && action.type != ActionType.EVALUATE) return reject("Unexpected invariant")
        if (action.type in setOf(ActionType.TAP, ActionType.ENTER_TEXT)) {
            if (!context.observation.foreground) return reject("Target is not foreground")
            val selector = action.selector ?: return reject("Missing selector")
            if (selector.packageName != action.packageName) return reject("Selector targets another package")
            val node = context.observation.elements.singleOrNull { it.key == selector.key } ?: return reject("Selector absent or ambiguous")
            if (!node.enabled) return reject("Target disabled")
            if (action.type == ActionType.ENTER_TEXT && (!node.editable || action.text == null)) return reject("Target is not editable or input absent")
        }
        if (action.type == ActionType.ROTATE && (action.orientation == null || !context.observation.foreground)) return reject("Rotation needs an orientation and foreground target")
        if (action.type == ActionType.WAIT && action.condition == null) return reject("Typed wait required")
        if (action.condition?.minimum?.let { it !in 1..100 } == true) return reject("Invalid condition threshold")
        if (action.type == ActionType.CONFIGURE_FAULT && action.fault == null) return reject("Fault required")
        if (action.type == ActionType.EVALUATE && action.invariant !in Oracles.approved) return reject("Invariant not approved")
        if (action.type in setOf(ActionType.BACK, ActionType.BACKGROUND) && !context.observation.foreground) return reject("Target must be foreground")
        return Validation(true)
    }
    fun scenario(scenario: Scenario): Validation {
        if (scenario.schemaVersion != PROTOCOL_VERSION || scenario.harnessVersion != "0.1.0") return Validation(false, "Unsupported scenario/harness version")
        if (scenario.targetPackage != SAMPLE_PACKAGE || scenario.assertion.invariantId !in Oracles.approved) return Validation(false, "Unapproved target/assertion")
        if (scenario.actions.size !in 1..500) return Validation(false, "Invalid sequence length")
        val ids = mutableSetOf<String>()
        for (action in scenario.actions) {
            if (!ids.containsAll(action.dependsOn) || !ids.add(action.id)) return Validation(false, "Invalid dependency ordering or duplicate ID")
        }
        return Validation(true)
    }
}
