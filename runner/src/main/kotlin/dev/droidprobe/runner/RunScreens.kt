package dev.droidprobe.runner

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.droidprobe.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable internal fun OverviewScreen(reports: List<RunReport>, open: (RunReport) -> Unit, allRuns: () -> Unit, setup: () -> Unit) {
    LazyColumn(contentPadding=PaddingValues(20.dp,8.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Your testing workspace",style=MaterialTheme.typography.headlineSmall)
            Text("Explore behavior. Keep the evidence.",color=Ink.Muted)
        } }
        item { Panel {
            Eyebrow("WORKSPACE SNAPSHOT")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Metric(reports.size.toString(),"Stored runs",Modifier.weight(1f))
                Metric(reports.count { it.findings.isNotEmpty() }.toString(),"With findings",Modifier.weight(1f),Ink.Orange)
                Metric(reports.count { it.exportedBundle != null }.toString(),"Exports",Modifier.weight(1f),Ink.Mint)
            }
        } }
        val latest = reports.firstOrNull()
        if(latest != null) item {
            Panel(color=Ink.Raised) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Eyebrow("LATEST RUN",Ink.Mint); Spacer(Modifier.weight(1f)); Pill(latest.statusLabel(),latest.statusColor())
                }
                Text(latest.plannerLabel(),style=MaterialTheme.typography.headlineSmall)
                Text("${latest.totalExecutedActions} actions · ${latest.graph.nodes.size} observed states · ${duration(latest.elapsedMs)}",color=Ink.Muted)
                if(latest.config.planner == "local" && latest.plannerStats.modelAccepted == 0) Text("Local model unavailable or no proposals accepted. This run used the graph baseline.",style=MaterialTheme.typography.bodySmall,color=Ink.Muted)
                Button(onClick={ open(latest) },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
                    Text("Inspect run"); Spacer(Modifier.weight(1f)); ProbeIcon(Glyph.Arrow)
                }
            }
        } else item { EmptyPanel("Your first run starts here", "Choose a planner and configure the checks you want to run. Completed results will appear in this workspace.") }
        item { SectionTitle("Recent activity","View all",allRuns) }
        items(reports.take(3),key={it.runId}) { RunCard(it) { open(it) } }
        item { Surface(onClick=setup,shape=RoundedCornerShape(16.dp),color=Ink.Panel,border=BorderStroke(1.dp,Ink.Border)) {
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                ProbeIcon(Glyph.Settings,Ink.Mint)
                Column(Modifier.weight(1f)) { Text("Prepare the next run",style=MaterialTheme.typography.titleMedium); Text("Planner, budgets and assertions",color=Ink.Muted,style=MaterialTheme.typography.bodySmall) }
                ProbeIcon(Glyph.Arrow,Ink.Muted)
            }
        } }
    }
}

@Composable internal fun RunsScreen(reports: List<RunReport>, open: (RunReport) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All runs") }
    val visible = reports.filter { r ->
        (filter != "With findings" || r.findings.isNotEmpty()) &&
            (filter != "Exports" || r.exportedBundle != null) &&
            (query.isBlank() || "${r.runId} ${r.plannerLabel()} ${r.status} ${r.findings.joinToString { it.assertion.id }}".contains(query,ignoreCase=true))
    }
    LazyColumn(contentPadding=PaddingValues(20.dp,8.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("Run history",style=MaterialTheme.typography.headlineSmall) }
        item { OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
            placeholder={Text("Search runs or assertions")},leadingIcon={ProbeIcon(Glyph.Search,Ink.Muted)},shape=RoundedCornerShape(14.dp)) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("All runs","With findings","Exports").forEach { name -> FilterChip(filter==name,{filter=name},label={Text(name)}) }
        } }
        item { Eyebrow("${visible.size} ${if(visible.size==1) "RUN" else "RUNS"}") }
        if(visible.isEmpty()) item { EmptyPanel("No matching runs",if(reports.isEmpty()) "Configure your first run in Setup." else "Try another search or filter.",Glyph.Search) }
        items(visible,key={it.runId}) { RunCard(it) { open(it) } }
    }
}

@Composable private fun RunCard(report: RunReport, onClick: () -> Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(18.dp),color=Ink.Panel,border=BorderStroke(1.dp,Ink.Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp).background(Ink.Blue.copy(alpha=.10f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) { ProbeIcon(Glyph.Graph,Ink.Blue) }
                Column(Modifier.weight(1f)) {
                    Text(report.plannerLabel(),style=MaterialTheme.typography.titleMedium)
                    Text(report.dateLabel(),style=MaterialTheme.typography.bodySmall,color=Ink.Muted)
                }
                ProbeIcon(Glyph.Arrow,Ink.Muted)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Pill(report.statusLabel(),report.statusColor())
                Spacer(Modifier.weight(1f))
                Text("${report.totalExecutedActions} actions",color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
                Text(duration(report.elapsedMs),color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
            }
            Text(report.runId,color=Ink.Muted,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun RunDetails(report: RunReport, store: RunStore, onBack: () -> Unit, onShare: (File) -> Unit) {
    var tab by rememberSaveable(report.runId) { mutableStateOf("Evidence") }
    Row(Modifier.fillMaxWidth().padding(8.dp,8.dp,20.dp,6.dp),verticalAlignment=Alignment.CenterVertically) {
        IconAction("Back to runs",Glyph.Back,onBack)
        Column(Modifier.weight(1f)) { Text(report.plannerLabel(),style=MaterialTheme.typography.titleMedium); Text(report.dateLabel(),color=Ink.Muted,style=MaterialTheme.typography.bodySmall) }
        Box(Modifier.size(8.dp).background(report.statusColor(),RoundedCornerShape(4.dp)))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf("Evidence" to Glyph.Alert,"Graph" to Glyph.Graph,"Reduce" to Glyph.Reduce,"Export" to Glyph.Export).forEach { (name,icon) ->
            Surface(onClick={tab=name},modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),
                color=if(tab==name) Ink.Mint.copy(alpha=.12f) else Ink.Panel,
                contentColor=if(tab==name) Ink.Mint else Ink.Muted,
                border=BorderStroke(1.dp,if(tab==name) Ink.Mint.copy(alpha=.4f) else Ink.Border)) {
                Column(Modifier.padding(horizontal=2.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    ProbeIcon(icon,modifier=Modifier.size(20.dp)); Text(name,style=MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    key(report.runId,tab) {
        LazyColumn(contentPadding=PaddingValues(20.dp,14.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            when(tab) {
                "Evidence" -> {
                    item { Panel {
                        Pill(report.statusLabel(),report.statusColor())
                        Text("Run overview",style=MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Metric(report.totalExecutedActions.toString(),"Actions",Modifier.weight(1f))
                            Metric(report.graph.nodes.size.toString(),"States",Modifier.weight(1f))
                            Metric(duration(report.elapsedMs),"Explore time",Modifier.weight(1f))
                        }
                        HorizontalDivider(color=Ink.Border)
                        Text(report.status,color=report.statusColor(),style=MaterialTheme.typography.bodySmall)
                        Text(report.plannerIdentity,color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
                        Text(report.modelStatus,color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
                        SelectionContainer { Text(report.runId,color=Ink.Muted,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace) }
                    } }
                    item { SectionTitle("Assertion evidence") }
                    if(report.findings.isEmpty()) item { EmptyPanel("No confirmed violations","No approved business assertion failed in the recorded observations. Review the run status for any execution limits.",Glyph.Check) }
                    items(report.findings) { finding -> FindingCard(finding,report.runId,store) }
                    if(report.replays.isNotEmpty()) item { SectionTitle("Replay results") }
                    items(report.replays) { replay ->
                        var expanded by remember { mutableStateOf(false) }
                        Panel {
                            Eyebrow("${replay.mode} MODE")
                            val passed = replay.status == ReplayStatus.FAILURE_NOT_OBSERVED && replay.assertion?.passed == true && replay.assertion?.applicable == true
                            val label = when { passed -> "Assertion passed"; replay.status==ReplayStatus.REPRODUCED -> "Failure reproduced"; else -> replay.status.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() } }
                            Pill(label,if(passed) Ink.Mint else Ink.Orange)
                            Text("${replay.records.size} actions · ${duration(replay.elapsedMs)}",color=Ink.Muted)
                            replay.assertion?.let { Text(it.observed,style=MaterialTheme.typography.bodySmall) }
                            replay.detail?.let { detail -> TextButton({expanded=!expanded}) { Text(if(expanded) "Hide diagnostics" else "Show diagnostics") }; if(expanded) SelectionContainer { Text(detail,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace) } }
                        }
                    }
                    item { SectionTitle("Executed sequence") }
                    if(report.previousEpisodes.isNotEmpty()) item { Text("Showing the final episode (${report.records.size} actions). ${report.previousEpisodes.size} earlier episodes are retained in the report.",color=Ink.Muted,style=MaterialTheme.typography.bodySmall) }
                    itemsIndexed(report.records) { index,record -> ActionRow(index,record.action,record.events.joinToString { it.type }) }
                    if(report.rejections.isNotEmpty()) item { Panel { Eyebrow("REJECTED ACTIONS",Ink.Orange); report.rejections.forEach { Text(it,style=MaterialTheme.typography.bodySmall,color=Ink.Muted) } } }
                }
                "Graph" -> {
                    item { Text("Observed behavior",style=MaterialTheme.typography.headlineSmall); Text("Follow the path from action to outcome.",color=Ink.Muted) }
                    item { Panel { Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Metric(report.graph.nodes.size.toString(),"Observed states",Modifier.weight(1f))
                        Metric(report.graph.transitions.size.toString(),"Transitions",Modifier.weight(1f),Ink.Blue)
                    }; ObservedGraph(report.graph) } }
                    item { SectionTitle("State directory") }
                    items(report.graph.nodes.values.toList(),key={it.signature}) { node -> Panel {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(node.screen ?: "Unknown screen",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium); Pill("${node.visits} visits",if(node.failures.isNotEmpty()) Ink.Orange else Ink.Blue) }
                        Text(node.phase ?: "No SDK phase",color=Ink.Muted)
                        Text("${node.availableActions.size} available actions · ${node.failures.size} failure associations",style=MaterialTheme.typography.bodySmall,color=Ink.Muted)
                        Text(node.signature.take(12),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall,color=Ink.Muted)
                    } }
                    item { SectionTitle("Transitions") }
                    items(report.graph.transitions) { edge -> Panel {
                        Text("${edge.from.take(6)} → ${edge.to.take(6)}",fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall,color=Ink.Blue)
                        Text(edge.actionKey,style=MaterialTheme.typography.bodySmall,color=Ink.Muted)
                    } }
                }
                "Reduce" -> {
                    val reduction = report.minimization
                    item { Text("A smaller reproduction",style=MaterialTheme.typography.headlineSmall) }
                    if(reduction == null) item { EmptyPanel("No reduction recorded","A reproducible failure can be reduced into a shorter regression scenario.",Glyph.Reduce) }
                    else {
                        item { Panel {
                            Eyebrow("REPRODUCTION LENGTH",Ink.Mint)
                            Text("${reduction.originalLength} → ${reduction.scenario.actions.size}",style=MaterialTheme.typography.headlineLarge,fontFamily=FontFamily.Monospace)
                            Text("${reduction.originalLength-reduction.scenario.actions.size} actions removed · ${reduction.replayCount} candidate replays",color=Ink.Muted)
                            Pill(if(reduction.budgetExpired) "Search budget reached" else "Search completed",if(reduction.budgetExpired) Ink.Orange else Ink.Mint)
                            Text(reduction.description,style=MaterialTheme.typography.bodySmall,color=Ink.Muted)
                            HorizontalDivider(color=Ink.Border)
                            Eyebrow("SCENARIO SOURCE")
                            Text(reduction.scenario.source,style=MaterialTheme.typography.bodySmall,color=Ink.Muted)
                        } }
                        item { SectionTitle("Reduced sequence") }
                        itemsIndexed(reduction.scenario.actions) { index,action -> ActionRow(index,action) }
                        item { SectionTitle("Reduction attempts") }
                        itemsIndexed(reduction.attempts) { index,attempt -> Panel {
                            Eyebrow("ATTEMPT ${index+1}")
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("${attempt.length} actions",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium); Pill(if(attempt.kept) "Kept" else "Discarded",if(attempt.kept) Ink.Mint else Ink.Muted) }
                            Text("${attempt.successes} / ${attempt.attempts} reproduced",color=Ink.Muted)
                            Text(attempt.statuses.joinToString { it.name.lowercase().replace('_',' ') },style=MaterialTheme.typography.bodySmall,color=Ink.Muted)
                        } }
                    }
                }
                "Export" -> {
                    val file = File(store.directory(report.runId),"regression.zip")
                    val exists = file.isFile
                    item { Text("Take the evidence with you",style=MaterialTheme.typography.headlineSmall) }
                    item { Panel {
                        Box(Modifier.size(52.dp).background(Ink.Mint.copy(alpha=.10f),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) { ProbeIcon(Glyph.Export,Ink.Mint,Modifier.size(28.dp)) }
                        Text("Regression bundle",style=MaterialTheme.typography.titleLarge)
                        Text(if(exists) "A recorded scenario, a runnable test, and the evidence behind the finding." else "Run the export workflow to create a bundle for this run.",color=Ink.Muted)
                        if(exists) {
                            Pill(String.format(Locale.getDefault(),"%.2f MB · ZIP archive",file.length()/1_000_000.0))
                            Button(onClick={onShare(file)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) { ProbeIcon(Glyph.Export); Spacer(Modifier.width(10.dp)); Text("Share regression bundle") }
                        }
                    } }
                    item { SectionTitle("Inside the bundle") }
                    item { Panel {
                        listOf("scenario.json" to "Actions, fixtures, faults and the assertion", "Kotlin regression test" to "Replay the scenario against faulty or corrected mode", "Evidence & screenshots" to "Recorded observations and captured screens", "Execution guide" to "Harness version and commands to run the test").forEach { (title,subtitle) ->
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { ProbeIcon(Glyph.Runs,Ink.Blue); Column { Text(title,style=MaterialTheme.typography.titleMedium); Text(subtitle,color=Ink.Muted,style=MaterialTheme.typography.bodySmall) } }
                        }
                    } }
                    item { SelectionContainer { Text(report.runId,color=Ink.Muted,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}

@Composable private fun FindingCard(finding: Finding, runId: String, store: RunStore) {
    val bitmap by produceState<ImageBitmap?>(null,runId,finding.screenshot) {
        value = withContext(Dispatchers.IO) {
            finding.screenshot?.let { name -> BitmapFactory.decodeFile(File(store.directory(runId),"screenshots/$name").absolutePath,BitmapFactory.Options().apply { inSampleSize=2 })?.asImageBitmap() }
        }
    }
    var expanded by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { ProbeIcon(Glyph.Alert,Ink.Orange); Eyebrow("CONFIRMED VIOLATION",Ink.Orange) }
        Text(finding.assertion.id,style=MaterialTheme.typography.titleMedium)
        Surface(color=Ink.Background,shape=RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Eyebrow("EXPECTED"); Text(finding.assertion.expected,color=Ink.Mint,fontFamily=FontFamily.Monospace)
                HorizontalDivider(color=Ink.Border)
                Eyebrow("OBSERVED"); Text(finding.assertion.observed,color=Ink.Orange,fontFamily=FontFamily.Monospace)
            }
        }
        bitmap?.let { image ->
            TextButton({expanded=!expanded}) { Text(if(expanded) "Collapse screenshot" else "View captured screenshot") }
            if(expanded) Image(image,"Captured screen for ${finding.assertion.id}",Modifier.fillMaxWidth().height(360.dp))
        }
        SelectionContainer { Text("Fingerprint · ${finding.fingerprint}",style=MaterialTheme.typography.bodySmall,color=Ink.Muted) }
    }
}

@Composable private fun ActionRow(index: Int, action: ActionIR, detail: String = "") {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(Ink.Raised,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) { Text("${index+1}",color=Ink.Blue,style=MaterialTheme.typography.labelLarge,fontFamily=FontFamily.Monospace) }
        Column(Modifier.weight(1f).padding(bottom=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(action.type.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() },style=MaterialTheme.typography.titleMedium)
            val target = action.selector?.key ?: action.orientation?.name ?: action.condition?.type?.name ?: action.id
            Text(target,color=Ink.Muted,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)
            if(detail.isNotBlank()) Text(detail,color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
        }
    }
}
