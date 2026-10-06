package dev.droidprobe.runner

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.droidprobe.core.*
import dev.droidprobe.model.LocalModel

@Composable internal fun SetupScreen(store: RunStore, context: Context, copyCommand: (String) -> Unit) {
    var config by remember { mutableStateOf(store.config()) }
    var budget by remember { mutableStateOf(config.actionBudget.toString()) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val model = remember { LocalModel.status(context) }
    val command = "adb shell am instrument -w -e class dev.droidprobe.runner.ExplorationTest dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner"
    LazyColumn(contentPadding=PaddingValues(20.dp,8.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Set up your next run",style=MaterialTheme.typography.headlineSmall)
            Text("Choose how to explore and what to check.",color=Ink.Muted)
        } }
        item { Panel {
            Eyebrow("TARGET APPLICATION")
            Text("DroidProbe sample",style=MaterialTheme.typography.titleMedium)
            Text(config.targetPackage,color=Ink.Muted,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(config.goal,{config=config.copy(goal=it.take(1024));message=""},modifier=Modifier.fillMaxWidth(),
                label={Text("Exploration goal")},minLines=2,shape=RoundedCornerShape(12.dp))
        } }
        item { SectionTitle("Exploration strategy") }
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf(Triple("graph","Graph explorer","Prioritize new states and lifecycle transitions."),
                Triple("random","Random explorer","Choose from supported actions using a repeatable seed."),
                Triple("local","Local AI","Use model proposals with a graph fallback.")).forEach { (id,title,description) ->
                Surface(onClick={config=config.copy(planner=id);message=""},shape=RoundedCornerShape(16.dp),
                    color=if(config.planner==id) Ink.Mint.copy(alpha=.08f) else Ink.Panel,
                    border=androidx.compose.foundation.BorderStroke(1.dp,if(config.planner==id) Ink.Mint.copy(alpha=.6f) else Ink.Border)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected=config.planner==id,onClick=null)
                        Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium); Text(description,color=Ink.Muted,style=MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        } }
        if(config.planner=="local") item { Panel { Eyebrow("MODEL STATUS",Ink.Blue); Text(model.description,color=Ink.Muted,style=MaterialTheme.typography.bodyMedium) } }
        item { Panel {
            Eyebrow("RUN LIMITS")
            OutlinedTextField(budget,{budget=it;message=""},label={Text("Action budget")},supportingText={Text("Between 1 and 500 actions")},
                modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=RoundedCornerShape(12.dp),
                isError=budget.toIntOrNull()?.let { it !in 1..500 } ?: true)
            Text("Time limit ${config.wallClockMs/1000}s · Seed ${config.seed}",color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider(color=Ink.Border)
            Eyebrow("SAMPLE BEHAVIOR")
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                AppMode.entries.forEach { mode -> FilterChip(config.mode==mode,{config=config.copy(mode=mode);message=""},
                    label={Text(if(mode==AppMode.FAULTY) "Faulty" else "Corrected")}) }
            }
        } }
        item { SectionTitle("Business assertions") }
        item { Panel {
            listOf(Triple(Oracles.UNIQUE_ORDER,"One checkout, one order","A checkout must not create duplicate orders."),
                Triple(Oracles.DRAFT,"Keep saved drafts","Acknowledged content survives recreation."),
                Triple(Oracles.ERROR,"Respect request failures","A failed request must not show success.")).forEach { (id,title,description) ->
                Row(Modifier.fillMaxWidth().toggleable(value=id in config.invariants,role=Role.Checkbox,onValueChange={checked ->
                    config=config.copy(invariants=if(checked) config.invariants+id else config.invariants-id);message=""
                }).padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(id in config.invariants,onCheckedChange=null)
                    Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium); Text(description,color=Ink.Muted,style=MaterialTheme.typography.bodySmall) }
                }
            }
        } }
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick={
                val value=budget.toIntOrNull()
                error=value==null || value !in 1..500 || config.invariants.isEmpty()
                if(error) message="Choose 1–500 actions and at least one assertion."
                else { config=config.copy(actionBudget=value!!); store.saveConfig(config); message="Configuration saved. Your next run will use these settings." }
            },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(16.dp)) { ProbeIcon(Glyph.Check); Spacer(Modifier.width(10.dp)); Text("Save configuration") }
            if(message.isNotBlank()) Text(message,color=if(error) Ink.Orange else Ink.Mint,style=MaterialTheme.typography.bodySmall)
        } }
        item { Panel {
            Eyebrow("START FROM YOUR COMPUTER",Ink.Blue)
            Text("Launch an instrumented run",style=MaterialTheme.typography.titleMedium)
            Text("Connect with ADB, then run this command. Return here after it finishes to review the results.",color=Ink.Muted)
            SelectionContainer { Text(command,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace,color=Ink.Muted) }
            OutlinedButton(onClick={copyCommand(command);copied=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
                ProbeIcon(if(copied) Glyph.Check else Glyph.Copy); Spacer(Modifier.width(10.dp)); Text(if(copied) "Command copied" else "Copy launch command")
            }
        } }
    }
}
