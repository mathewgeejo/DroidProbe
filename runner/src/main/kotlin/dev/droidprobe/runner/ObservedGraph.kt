package dev.droidprobe.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.droidprobe.core.StateGraph
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable fun ObservedGraph(graph: StateGraph) {
    val nodes = graph.nodes.values.take(24)
    var selected by remember(graph) { mutableStateOf<String?>(null) }
    var positions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val accent = MaterialTheme.colorScheme.primary
    if (nodes.isEmpty()) return
    Canvas(Modifier.fillMaxWidth().height((maxOf(3, (nodes.size + 3) / 4) * 64).dp).pointerInput(nodes) {
        detectTapGestures { point -> selected = positions.minByOrNull { (_, position) -> (position - point).getDistance() }?.key }
    }) {
        val points = nodes.mapIndexed { i, n -> n.signature to Offset(size.width * ((i % 4) + 0.5f) / 4, 32.dp.toPx() + (i / 4) * 64.dp.toPx()) }.toMap()
        positions = points
        graph.transitions.forEach { t ->
            val start = points[t.from]; val end = points[t.to]
            if (start != null && end != null && start != end) {
                val color = if (t.from == selected || t.to == selected) accent else Color(0xFF61748D)
                drawLine(color, start, end, 2.dp.toPx())
                val angle = atan2(end.y - start.y, end.x - start.x)
                val tip = end - Offset(cos(angle), sin(angle)) * 14.dp.toPx()
                val arrow = Path().apply { moveTo(tip.x, tip.y)
                    lineTo(tip.x - cos(angle - 0.5f) * 10.dp.toPx(), tip.y - sin(angle - 0.5f) * 10.dp.toPx())
                    lineTo(tip.x - cos(angle + 0.5f) * 10.dp.toPx(), tip.y - sin(angle + 0.5f) * 10.dp.toPx()); close() }
                drawPath(arrow, color)
            }
        }
        nodes.forEach { n -> drawCircle(if (n.failures.isNotEmpty()) Color(0xFFFFAB8B) else if (n.signature == selected) accent else Color(0xFF9CACFF), 12.dp.toPx(), points.getValue(n.signature)) }
    }
    Text(if (graph.nodes.size > 24) "First 24 observed states shown. Tap a node; all states are listed below." else "Tap a state to inspect its observed context.", style = MaterialTheme.typography.bodySmall)
    selected?.let { key -> graph.nodes[key]?.let { n -> Text("${n.screen} / ${n.phase} · ${n.visits} visits\n${n.recentActions.joinToString("\n")}", style = MaterialTheme.typography.bodySmall) } }
}
