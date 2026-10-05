package dev.droidprobe.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import dev.droidprobe.core.*

class MainActivity : ComponentActivity() {
    private val store by lazy { SampleStore.get(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store.change { created(savedInstanceState != null) }
        setContent { StoreUi(store) }
    }
    override fun onResume() { super.onResume(); store.change { foreground(true) } }
    override fun onPause() { store.change { foreground(false) }; super.onPause() }
}
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable private fun StoreUi(store: SampleStore) {
    val s = store.observed.value
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7DE2C6), background = Color(0xFF10171F), surface = Color(0xFF18232F))) {
        BackHandler(s.screen != "Home") { store.change { navigate("Home") } }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PROBE STORE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(s.screen, style = MaterialTheme.typography.headlineLarge)
                Text("${s.mode} · resettable developer fixture", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                when (s.screen) {
                    "Home" -> {
                        Text("A small store. A real testing surface.", style = MaterialTheme.typography.titleLarge)
                        Text("Explore a product, submit a checkout, or write a saved draft.")
                        ActionButton("home_product", "View product") { store.change { navigate("Product") } }
                        ActionButton("home_cart", "Cart (${s.cartItems})") { store.change { navigate("Cart") } }
                        ActionButton("home_orders", "Order history") { store.change { navigate("Orders") } }
                        ActionButton("home_draft", "Draft editor") { store.change { navigate("Draft") } }
                    }
                    "Product" -> {
                        Text("Field Notes", style = MaterialTheme.typography.headlineMedium)
                        Text("One notebook · ₹240\nA repeatable product fixture.")
                        ActionButton("product_add", "Add product to cart") { store.change { addToCart() } }
                    }
                    "Cart" -> {
                        Text(if (s.cartItems > 0) "Field Notes × 1\nTotal ₹240" else "Your cart is empty.")
                        ActionButton("cart_checkout", "Checkout", s.cartItems > 0) { store.change { navigate("Checkout") } }
                        ActionButton("cart_product", "View product") { store.change { navigate("Product") } }
                    }
                    "Checkout" -> {
                        Text("Total ₹240", style = MaterialTheme.typography.headlineMedium)
                        Text(when (s.phase) { "ackPending" -> "Submitting… awaiting acknowledgement"; "completed" -> "Order confirmed"; "error" -> "Request failed. No order confirmed."; else -> "Ready to submit" }, Modifier.testTag("checkout_status"))
                        ActionButton("checkout_submit", "Submit checkout", s.phase in setOf("idle", "error")) { store.change { submit() } }
                        ActionButton("checkout_orders", "Order history") { store.change { navigate("Orders") } }
                    }
                    "Orders" -> {
                        Text("${s.orders.size} persisted orders", style = MaterialTheme.typography.titleLarge)
                        s.orders.forEach { Text("${it.recordId} · ${it.operationId}") }
                        if (s.orders.isEmpty()) Text("No orders yet.")
                    }
                    "Draft" -> {
                        OutlinedTextField(s.draftInput, { text -> store.change { editDraft(text) } }, label = { Text("Draft content") }, modifier = Modifier.fillMaxWidth().testTag("draft_content"))
                        ActionButton("draft_save", "Save draft", s.draftInput.isNotEmpty()) { store.change { saveDraft() } }
                        Text(if (s.acknowledgedDraft != null) "Draft save acknowledged" else "No acknowledged save", Modifier.testTag("draft_status"))
                    }
                }
                if (s.screen != "Home") ActionButton("navigate_home", "Home") { store.change { navigate("Home") } }
            }
        }
    }
}
@Composable private fun ActionButton(key: String, label: String, enabled: Boolean = true, click: () -> Unit) {
    Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(key)) { Text(label) }
}
