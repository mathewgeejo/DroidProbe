package dev.droidprobe.core

/** Application-controlled backend and fixtures. Android persists each mutation atomically. */
class SampleEngine(initial: AppState = AppState(), private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    var state: AppState = initial
        private set
    private fun event(type: String, id: String? = state.operationId, value: String? = null) {
        state = state.copy(events = state.events + AppEvent((state.events.lastOrNull()?.sequence ?: 0) + 1, state.runId, type, id, value, clock()))
    }
    fun reset(runId: String, mode: AppMode, fixtures: Fixtures, faults: FaultConfig) {
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        require(fixtures.name == "clean-store-v1" && fixtures.initialDraft.length <= 2048 && fixtures.initialCartItems in 0..1)
        state = AppState(runId = runId, mode = mode, faults = faults, draftInput = fixtures.initialDraft,
            visibleDraft = fixtures.initialDraft, cartItems = fixtures.initialCartItems)
        event("fixturesReset", id = null, value = fixtures.name)
    }
    fun configure(faults: FaultConfig) { state = state.copy(faults = faults); event("faultConfigured", value = faults.toString()) }
    fun navigate(screen: String) { require(screen in setOf("Home", "Product", "Cart", "Checkout", "Orders", "Draft")); state = state.copy(screen = screen); event("screen", value = screen) }
    fun addToCart() { state = state.copy(cartItems = 1); navigate("Cart") }
    fun submit() {
        require(state.screen == "Checkout" && state.cartItems > 0 && state.phase in setOf("idle", "error"))
        state = state.copy(operationCounter = state.operationCounter + 1, operationId = "checkout-${state.operationCounter + 1}", phase = "submitted")
        persistRequest()
    }
    private fun persistRequest() {
        event("requestAccepted")
        if (state.faults.requestError) { state = state.copy(phase = "error"); event("requestError"); return }
        val id = requireNotNull(state.operationId)
        if (state.mode == AppMode.FAULTY || state.orders.none { it.operationId == id }) {
            state = state.copy(orders = state.orders + Order(id, "order-${state.orders.size + 1}"))
            event("orderPersisted", value = state.orders.last().recordId)
        } else event("idempotencyHit")
        state = state.copy(phase = "ackPending")
        if (!state.faults.holdAcknowledgement) release()
    }
    fun retryPending() { require(state.phase == "ackPending"); persistRequest() }
    fun release() {
        state = state.copy(faults = state.faults.copy(holdAcknowledgement = false))
        event("acknowledgementReleased")
        if (state.phase == "ackPending") { state = state.copy(phase = "completed"); event("acknowledged") }
    }
    fun editDraft(text: String) { require(text.length <= 2048); state = state.copy(draftInput = text, visibleDraft = text) }
    fun saveDraft() { state = state.copy(acknowledgedDraft = state.draftInput, visibleDraft = state.draftInput); event("draftAcknowledged", value = state.draftInput) }
    fun created(recreated: Boolean) {
        state = state.copy(generation = state.generation + 1)
        event(if (recreated) "activityRecreated" else "activityCreated")
        if (recreated && state.phase == "ackPending") {
            if (state.mode == AppMode.FAULTY) { event("lifecycleResubmission"); persistRequest() }
            else event("pendingOperationRetained")
        }
        if (recreated && state.acknowledgedDraft != null) {
            val content = if (state.mode == AppMode.FAULTY) "" else state.acknowledgedDraft!!
            state = state.copy(draftInput = content, visibleDraft = content)
            event("draftRestored", value = content)
        }
    }
    fun foreground(value: Boolean) { state = state.copy(foreground = value); event(if (value) "resumed" else "backgrounded") }
}
