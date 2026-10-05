package dev.droidprobe.runner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.droidprobe.core.*
import dev.droidprobe.model.LocalModel
import java.io.File

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)
    override fun onResume() { super.onResume(); refresh++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = RunStore(this)
        setContent {
            val reports = remember(refresh) { store.history() }
            var selected by remember { mutableStateOf<String?>(null) }
            val report = reports.find { it.runId == selected } ?: reports.firstOrNull()
            var tab by remember { mutableStateOf("Runs") }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF75E1C2), secondary = Color(0xFF9CACFF),
                background = Color(0xFF0B111B), surface = Color(0xFF151F2D), onSurface = Color(0xFFE3EAF5))) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                        Text("DROIDPROBE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp))
                        Text("Observed. Asserted. Replayable.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Runs", "Config", "Graph", "Failure", "Minimize", "Export").forEach { name -> FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) }) }
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (tab) {
                                "Config" -> Configuration(store)
                                "Runs" -> {
                                    Text("Run history", style = MaterialTheme.typography.titleLarge)
                                    Text("Instrumentation runs in the target workflow. Return here to review completed evidence.")
                                    OutlinedButton(onClick = { refresh++ }) { Text("Refresh stored runs") }
                                    if (reports.isEmpty()) InfoCard("No completed runs", "Install the sample and run the documented instrumentation command. Results appear after execution.")
                                    reports.forEach { r -> Card(Modifier.fillMaxWidth().clickable { selected = r.runId; tab = "Failure" }) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(r.runId, style = MaterialTheme.typography.titleMedium)
                                            Text(r.status, color = if (r.findings.isNotEmpty()) Color(0xFFFFAB8B) else MaterialTheme.colorScheme.primary)
                                            Text(r.plannerIdentity)
                                            Text("${r.records.size} actions · ${r.findings.size} findings · ${r.elapsedMs / 1000.0}s")
                                        }
                                    } }
                                }
                                "Graph" -> {
                                    Text("Graph of observed behavior", style = MaterialTheme.typography.titleLarge)
                                    if (report == null) Text("No observations stored.") else {
                                        Text("${report.graph.nodes.size} states · ${report.graph.transitions.size} transitions")
                                        report.graph.nodes.values.forEach { n -> InfoCard("${n.screen} / ${n.phase}", "${n.signature.take(10)} · ${n.visits} visits\n${n.availableActions.size} available actions\n${n.failures.size} failure associations") }
                                        report.graph.transitions.forEach { t -> Text("${t.from.take(6)} → ${t.to.take(6)}\n${t.actionKey}", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                                "Failure" -> {
                                    Text("Assertion & evidence", style = MaterialTheme.typography.titleLarge)
                                    if (report == null) Text("No evidence stored.") else {
                                        Text(report.runId); Text(report.status); Text("Planner: ${report.plannerIdentity}\n${report.modelStatus}")
                                        report.findings.forEach { f -> InfoCard(f.assertion.id, "${f.classification}\nExpected: ${f.assertion.expected}\nObserved: ${f.assertion.observed}\nFingerprint: ${f.fingerprint}\nScreenshot: ${f.screenshot ?: "unavailable"}") }
                                        if (report.findings.isEmpty()) Text("No confirmed business invariant violations in this run.")
                                        report.replays.forEach { r -> InfoCard("Replay ${r.mode}: ${r.status}", "${r.records.size} actions · ${r.elapsedMs}ms\n${r.detail ?: r.assertion?.observed ?: ""}") }
                                        Text("Executed sequence", style = MaterialTheme.typography.titleMedium)
                                        report.records.forEachIndexed { i, r -> Text("${i + 1}. ${r.action.type} ${r.action.selector?.key ?: r.action.orientation ?: ""}\n${r.events.joinToString { it.type }}", style = MaterialTheme.typography.bodySmall) }
                                        report.rejections.forEach { Text(it, color = Color(0xFFFFAB8B)) }
                                    }
                                }
                                "Minimize" -> {
                                    val m = report?.minimization
                                    Text("Reproduction reduction", style = MaterialTheme.typography.titleLarge)
                                    if (m == null) Text("No minimization stored.") else {
                                        InfoCard("${m.originalLength} → ${m.scenario.actions.size} actions", "${m.description}\n${m.replayCount} replays · budget expired: ${m.budgetExpired}")
                                        m.scenario.actions.forEach { Text("${it.id}: ${it.type} ${it.selector?.key ?: ""}") }
                                        m.attempts.forEach { Text("${it.length} actions · ${it.successes}/${it.attempts} reproduced · kept: ${it.kept}\n${it.statuses}", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                                "Export" -> {
                                    Text("Regression bundle", style = MaterialTheme.typography.titleLarge)
                                    Text("scenario.json · fixtures and faults · invariant · Kotlin test · evidence · screenshots · execution README")
                                    val file = report?.let { File(store.directory(it.runId), "regression.zip") }
                                    if (file?.isFile == true) {
                                        Text("${file.length()} bytes · ${report.runId}")
                                        Button(onClick = {
                                            val uri = FileProvider.getUriForFile(this@MainActivity, "dev.droidprobe.runner.files", file)
                                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share regression bundle"))
                                        }) { Text("Share bundle") }
                                    } else Text("Run the demo/export instrumentation workflow to create a bundle.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @Composable private fun Configuration(store: RunStore) {
        var config by remember { mutableStateOf(store.config()) }
        var budget by remember { mutableStateOf(config.actionBudget.toString()) }
        var message by remember { mutableStateOf("") }
        val model = LocalModel.status(this)
        Text("Run configuration", style = MaterialTheme.typography.titleLarge)
        InfoCard("Target package", config.targetPackage)
        OutlinedTextField(config.goal, { config = config.copy(goal = it.take(1024)) }, label = { Text("Developer goal") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("graph", "random", "local").forEach { p -> FilterChip(config.planner == p, { config = config.copy(planner = p) }, label = { Text(p) }) } }
        InfoCard("Local model", model.description)
        OutlinedTextField(budget, { budget = it }, label = { Text("Action budget (1–500)") })
        Text("Wall-clock budget: ${config.wallClockMs / 1000}s · seed ${config.seed}")
        Row { AppMode.entries.forEach { m -> FilterChip(config.mode == m, { config = config.copy(mode = m) }, label = { Text(m.name) }) } }
        Text("Approved invariants")
        Oracles.approved.sorted().forEach { id -> Row { Checkbox(id in config.invariants, { checked -> config = config.copy(invariants = if (checked) config.invariants + id else config.invariants - id) }); Text(id, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall) } }
        Button(onClick = {
            val value = budget.toIntOrNull()
            if (value == null || value !in 1..500 || config.invariants.isEmpty()) message = "Choose a valid budget and at least one invariant."
            else { config = config.copy(actionBudget = value); store.saveConfig(config); message = "Configuration saved for the next instrumented run." }
        }) { Text("Save configuration") }
        Text(message)
        Text("Launch from an authorized ADB terminal:")
        SelectionContainer { Text("adb shell am instrument -w -e class dev.droidprobe.runner.ExplorationTest dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner", style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodySmall) } }
}
