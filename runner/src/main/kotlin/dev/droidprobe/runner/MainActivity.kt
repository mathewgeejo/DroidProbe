package dev.droidprobe.runner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.droidprobe.core.RunReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)
    override fun onResume() { super.onResume(); refresh++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = RunStore(this)
        setContent {
            ProbeTheme {
                val reports by produceState<List<RunReport>?>(null, refresh) {
                    value = withContext(Dispatchers.IO) { store.history() }
                }
                var destination by rememberSaveable { mutableStateOf("Overview") }
                var selected by rememberSaveable { mutableStateOf<String?>(null) }
                val report = reports?.find { it.runId == selected }
                BackHandler(selected != null) { selected = null }
                Scaffold(containerColor = Ink.Background, bottomBar = {
                    if (selected == null) NavigationBar(containerColor = Ink.Panel, tonalElevation = 0.dp) {
                        listOf("Overview" to Glyph.Radar, "Runs" to Glyph.Runs, "Setup" to Glyph.Settings).forEach { (name, icon) ->
                            NavigationBarItem(selected = destination == name, onClick = { destination = name },
                                icon = { ProbeIcon(icon) }, label = { Text(name) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink.Mint,
                                    selectedTextColor = Ink.Mint, indicatorColor = Ink.Mint.copy(alpha = .12f),
                                    unselectedIconColor = Ink.Muted, unselectedTextColor = Ink.Muted))
                        }
                    }
                }) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 880.dp).fillMaxSize()) {
                            if (selected != null && report != null) {
                                RunDetails(report, store, onBack = { selected = null }, onShare = ::share)
                            } else {
                                AppHeader(onRefresh = { refresh++ })
                                val items = reports
                                if (items == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Ink.Mint)
                                } else key(destination) {
                                    when (destination) {
                                        "Overview" -> OverviewScreen(items, { selected = it.runId }, { destination = "Runs" }, { destination = "Setup" })
                                        "Runs" -> RunsScreen(items) { selected = it.runId }
                                        else -> SetupScreen(store, this@MainActivity) { command ->
                                            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DroidProbe launch command", command))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "dev.droidprobe.runner.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share regression bundle"))
    }
}
