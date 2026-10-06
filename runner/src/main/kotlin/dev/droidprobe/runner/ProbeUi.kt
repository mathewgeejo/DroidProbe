package dev.droidprobe.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidprobe.core.RunReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object Ink {
    val Background = Color(0xFF0B111A)
    val Panel = Color(0xFF121D2B)
    val Raised = Color(0xFF1A2839)
    val Border = Color(0xFF29394C)
    val Text = Color(0xFFEDF3FA)
    val Muted = Color(0xFF9BAEC3)
    val Mint = Color(0xFF79E2BF)
    val Blue = Color(0xFFA2B6FF)
    val Orange = Color(0xFFFFB69D)
}

@Composable internal fun ProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Ink.Mint, onPrimary = Ink.Background,
        secondary = Ink.Blue, secondaryContainer = Ink.Mint.copy(alpha = .12f), onSecondaryContainer = Ink.Mint,
        primaryContainer = Ink.Raised, onPrimaryContainer = Ink.Mint,
        background = Ink.Background, onBackground = Ink.Text, surface = Ink.Panel,
        surfaceVariant = Ink.Raised, onSurface = Ink.Text, onSurfaceVariant = Ink.Muted,
        outline = Ink.Border, error = Ink.Orange), typography = Typography(
        headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
    ), content = content)
}

internal enum class Glyph { Radar, Runs, Settings, Back, Arrow, Refresh, Check, Alert, Graph, Reduce, Export, Copy, Search }

@Composable internal fun ProbeIcon(glyph: Glyph, color: Color = LocalContentColor.current, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x,y), Offset(x2,y2), 1.7f)
            fun ring(x: Float, y: Float, radius: Float) = drawCircle(color, radius, Offset(x,y), style = Stroke(1.7f))
            when (glyph) {
                Glyph.Radar -> { ring(12f,12f,9f); ring(12f,12f,5f); line(12f,12f,19f,5f); drawCircle(color,1.8f,Offset(12f,12f)) }
                Glyph.Runs -> { for (y in listOf(6f,12f,18f)) { line(9f,y,21f,y); drawCircle(color,1.5f,Offset(4f,y)) } }
                Glyph.Settings -> { for ((x,y) in listOf(5f to 8f,12f to 16f,19f to 9f)) { line(x,3f,x,y-3); line(x,y+3,x,21f); ring(x,y,2.5f) } }
                Glyph.Back -> { line(20f,12f,4f,12f); line(4f,12f,10f,6f); line(4f,12f,10f,18f) }
                Glyph.Arrow -> { line(5f,12f,19f,12f); line(19f,12f,13f,6f); line(19f,12f,13f,18f) }
                Glyph.Refresh -> { drawArc(color,35f,285f,false,Offset(4f,4f),Size(16f,16f),style=Stroke(1.7f)); line(20f,3f,20f,9f); line(20f,9f,14f,9f) }
                Glyph.Check -> { line(5f,12f,10f,17f); line(10f,17f,20f,6f) }
                Glyph.Alert -> { ring(12f,12f,9f); line(12f,6f,12f,13f); drawCircle(color,1f,Offset(12f,17f)) }
                Glyph.Graph -> { line(6f,6f,18f,12f); line(6f,18f,18f,12f); ring(5f,5f,3f); ring(5f,19f,3f); ring(19f,12f,3f) }
                Glyph.Reduce -> { line(3f,6f,10f,6f); line(10f,6f,7f,3f); line(10f,6f,7f,9f); line(21f,18f,14f,18f); line(14f,18f,17f,15f); line(14f,18f,17f,21f); line(12f,2f,12f,22f) }
                Glyph.Export -> { line(12f,16f,12f,3f); line(12f,3f,7f,8f); line(12f,3f,17f,8f); line(4f,14f,4f,21f); line(4f,21f,20f,21f); line(20f,21f,20f,14f) }
                Glyph.Copy -> { drawRoundRect(color,Offset(8f,8f),Size(12f,13f),CornerRadius(2f),style=Stroke(1.7f)); line(16f,4f,4f,4f); line(4f,4f,4f,17f) }
                Glyph.Search -> { ring(10f,10f,6f); line(15f,15f,21f,21f) }
            }
        }
    }
}

@Composable internal fun IconAction(label: String, glyph: Glyph, onClick: () -> Unit) {
    IconButton(onClick, Modifier.semantics { contentDescription = label }) { ProbeIcon(glyph, Ink.Muted) }
}

@Composable internal fun AppHeader(onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).background(Ink.Mint.copy(alpha=.10f), RoundedCornerShape(14.dp)), contentAlignment=Alignment.Center) { ProbeIcon(Glyph.Radar, Ink.Mint) }
        Column(Modifier.weight(1f)) { Text("DroidProbe", style=MaterialTheme.typography.titleLarge); Text("ANDROID TEST LAB", color=Ink.Muted, style=MaterialTheme.typography.labelSmall) }
        IconAction("Refresh stored runs", Glyph.Refresh, onRefresh)
    }
}

@Composable internal fun Panel(modifier: Modifier = Modifier, color: Color = Ink.Panel, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape=RoundedCornerShape(20.dp), color=color, contentColor=Ink.Text, border=BorderStroke(1.dp,Ink.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp), content=content)
    }
}
@Composable internal fun Eyebrow(text: String, color: Color = Ink.Muted) { Text(text.uppercase(), color=color, style=MaterialTheme.typography.labelSmall) }
@Composable internal fun Pill(text: String, color: Color = Ink.Mint) {
    Surface(color=color.copy(alpha=.10f),shape=RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal=9.dp,vertical=5.dp),color=color,style=MaterialTheme.typography.labelLarge)
    }
}
@Composable internal fun Metric(value: String, label: String, modifier: Modifier = Modifier, color: Color = Ink.Text) {
    Column(modifier, verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(value, color=color,style=MaterialTheme.typography.headlineSmall, fontFamily=FontFamily.Monospace)
        Text(label,color=Ink.Muted,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable internal fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
        if(action != null) TextButton(onClick) { Text(action) }
    }
}
@Composable internal fun EmptyPanel(title: String, text: String, glyph: Glyph = Glyph.Radar) {
    Panel { ProbeIcon(glyph,Ink.Mint,Modifier.size(32.dp)); Text(title,style=MaterialTheme.typography.titleMedium); Text(text,color=Ink.Muted) }
}
internal fun RunReport.plannerLabel(): String = when {
    plannerIdentity.startsWith("scripted") -> "Scripted workflow"
    config.planner == "local" && plannerStats.modelAccepted == 0 -> "Graph fallback"
    config.planner == "local" -> "Local AI"
    config.planner == "random" -> "Random exploration"
    else -> "Graph exploration"
}
internal fun RunReport.statusLabel(): String = when {
    status.startsWith("infrastructure") -> "Run error"
    status.startsWith("suspected") -> "Suspected stall"
    findings.isNotEmpty() -> "${findings.size} finding${if(findings.size == 1) "" else "s"}"
    status == "budget exhausted" -> "Budget reached"
    else -> status.replaceFirstChar { it.uppercase() }
}
internal fun RunReport.statusColor() = if(findings.isNotEmpty() || status.startsWith("infrastructure") || status.startsWith("suspected")) Ink.Orange else Ink.Blue
internal fun RunReport.dateLabel(): String = Regex("[0-9]{13}").find(runId)?.value?.toLongOrNull()?.let {
    SimpleDateFormat("MMM d · HH:mm",Locale.getDefault()).format(Date(it))
} ?: "Saved workflow"
internal fun duration(ms: Long) = String.format(Locale.getDefault(),"%.1fs",ms/1000.0)
