package dev.droidprobe.runner

import dev.droidprobe.core.*

/** Development fixtures only. Never passed to exploration planners. */
object FixtureScenarios {
    fun checkout(longer: Boolean = false): Scenario {
        val actions = mutableListOf(ActionIR("launch", ActionType.LAUNCH, timeoutMs = 30_000))
        if (longer) actions += listOf(ActionIR("extra-orders", ActionType.TAP, selector = Selector("home_orders")), ActionIR("extra-home", ActionType.TAP, selector = Selector("navigate_home")))
        actions += listOf(
            ActionIR("product", ActionType.TAP, selector = Selector("home_product")),
            ActionIR("add", ActionType.TAP, selector = Selector("product_add")),
            ActionIR("checkout", ActionType.TAP, selector = Selector("cart_checkout")),
            ActionIR("submit", ActionType.TAP, selector = Selector("checkout_submit")),
            ActionIR("persisted", ActionType.WAIT, condition = Condition(ConditionType.ORDER_PERSISTED, operationId = "checkout-1"), dependsOn = listOf("submit")),
            ActionIR("rotate", ActionType.ROTATE, orientation = Orientation.LANDSCAPE, preconditions = listOf(Condition(ConditionType.ORDER_PERSISTED, operationId = "checkout-1")), dependsOn = listOf("persisted")),
            ActionIR("release", ActionType.RELEASE_FAULT, dependsOn = listOf("rotate")),
            ActionIR("ack", ActionType.WAIT, condition = Condition(ConditionType.ACKNOWLEDGED), dependsOn = listOf("release")))
        return Scenario(name = "Checkout lifecycle fixture", faults = FaultConfig(holdAcknowledgement = true), actions = actions,
            assertion = AssertionSpec(Oracles.UNIQUE_ORDER, "checkout-1"), source = "scripted reproduction fixture")
    }
    fun draft() = Scenario(name = "Draft recreation fixture", source = "scripted reproduction fixture", actions = listOf(
        ActionIR("launch", ActionType.LAUNCH, timeoutMs = 30_000), ActionIR("draft", ActionType.TAP, selector = Selector("home_draft")),
        ActionIR("edit", ActionType.ENTER_TEXT, selector = Selector("draft_content"), text = "Acknowledged field notes"),
        ActionIR("save", ActionType.TAP, selector = Selector("draft_save")),
        ActionIR("saved", ActionType.WAIT, condition = Condition(ConditionType.DRAFT_ACKNOWLEDGED), dependsOn = listOf("save")),
        ActionIR("rotate", ActionType.ROTATE, orientation = Orientation.LANDSCAPE, dependsOn = listOf("saved"))), assertion = AssertionSpec(Oracles.DRAFT))
}
