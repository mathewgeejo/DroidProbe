package dev.droidprobe.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.droidprobe.core.StateGraph
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable fun ObservedGraph(graph: StateGraph) {
    val nodes=graph.nodes.values.take(24)
    var selected by remember(graph) { mutableStateOf<String?>(null) }
    if(nodes.isEmpty()) { Text("No observed states recorded.",color=Ink.Muted); return }
    val rows=(nodes.size+2)/3
    Box(Modifier.fillMaxWidth().height((rows*88).dp)) {
        Canvas(Modifier.matchParentSize()) {
            val positions=nodes.mapIndexed { index,node ->
                val row=index/3
                val column=if(row%2==0) index%3 else 2-index%3
                node.signature to Offset(size.width*(column+.5f)/3,20.dp.toPx()+row*88.dp.toPx())
            }.toMap()
            graph.transitions.forEach { edge ->
                val from=positions[edge.from]; val to=positions[edge.to]
                if(from!=null && to!=null && from!=to) {
                    val angle=atan2(to.y-from.y,to.x-from.x)
                    val direction=Offset(cos(angle),sin(angle))
                    val start=from+direction*22.dp.toPx(); val end=to-direction*22.dp.toPx()
                    val color=if(edge.from==selected || edge.to==selected) Ink.Mint else Ink.Border
                    drawLine(color,start,end,2.dp.toPx())
                    val arrow=Path().apply {
                        moveTo(end.x,end.y)
                        lineTo(end.x-cos(angle-.5f)*7.dp.toPx(),end.y-sin(angle-.5f)*7.dp.toPx())
                        lineTo(end.x-cos(angle+.5f)*7.dp.toPx(),end.y-sin(angle+.5f)*7.dp.toPx()); close()
                    }
                    drawPath(arrow,color)
                }
            }
        }
        Column {
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth().height(88.dp)) {
                    repeat(3) { column ->
                        val index=row*3+if(row%2==0) column else 2-column
                        val node=nodes.getOrNull(index)
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            if(node!=null) {
                                val color=if(node.failures.isNotEmpty()) Ink.Orange else if(selected==node.signature) Ink.Mint else Ink.Blue
                                Surface(onClick={selected=node.signature},modifier=Modifier.size(40.dp).semantics { contentDescription="State ${index+1}: ${node.screen}, ${node.phase}" },
                                    shape=CircleShape,color=Ink.Panel,border=BorderStroke(if(selected==node.signature) 2.dp else 1.dp,color)) {
                                    Box(contentAlignment=Alignment.Center) { Text("${index+1}",color=color,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.labelLarge) }
                                }
                                Text(node.screen ?: "Unknown",color=Ink.Muted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Pill("Observed",Ink.Blue); Pill("Finding",Ink.Orange) }
    Text(if(graph.nodes.size>24) "First 24 states shown. Tap a state to inspect it; the complete directory is below." else "Tap a numbered state to inspect its context.",color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
    selected?.let { key -> graph.nodes[key]?.let { node ->
        HorizontalDivider(color=Ink.Border)
        Text("${node.screen} / ${node.phase}",style=MaterialTheme.typography.titleMedium)
        Text("${node.visits} visits · ${node.availableActions.size} available actions",color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
        if(node.recentActions.isNotEmpty()) Text(node.recentActions.joinToString("\n"),color=Ink.Muted,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)
    } }
}
