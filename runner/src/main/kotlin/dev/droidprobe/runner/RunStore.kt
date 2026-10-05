package dev.droidprobe.runner

import android.content.Context
import android.util.AtomicFile
import dev.droidprobe.core.*
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RunStore(private val context: Context) {
    private val root = File(context.filesDir, "runs").apply { mkdirs() }
    fun directory(id: String): File { require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))); return File(root, id).apply { mkdirs() } }
    private fun atomic(file: File, content: String) {
        val atomic = AtomicFile(file); val out = atomic.startWrite()
        try { out.write(content.toByteArray()); atomic.finishWrite(out) } catch (e: Exception) { atomic.failWrite(out); throw e }
    }
    fun save(report: RunReport) = atomic(File(directory(report.runId), "run.json"), ProbeJson.encodeToString(report))
    fun history(): List<RunReport> = root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { File(it, "run.json").lastModified() }
        ?.mapNotNull { dir -> runCatching { ProbeJson.decodeFromString<RunReport>(File(dir, "run.json").readText()) }.getOrNull() } ?: emptyList()
    fun config(): RunConfig = File(context.filesDir, "config.json").takeIf { it.exists() }?.let { ProbeJson.decodeFromString<RunConfig>(it.readText()) } ?: RunConfig()
    fun saveConfig(config: RunConfig) { require(config.targetPackage == SAMPLE_PACKAGE && config.actionBudget in 1..500); atomic(File(context.filesDir, "config.json"), ProbeJson.encodeToString(config)) }
    fun export(report: RunReport, scenario: Scenario): File {
        val dir = File(directory(report.runId), "export").apply { mkdirs() }
        ScenarioExport.textFiles(scenario).forEach { (name, content) -> atomic(File(dir, name), content) }
        atomic(File(dir, "evidence.json"), ProbeJson.encodeToString(report))
        val screenshotNames = (report.observations + report.replays.flatMap { it.observations }).mapNotNull { it.screenshot }.distinct()
        val screenshots = File(dir, "screenshots").apply { mkdirs() }
        screenshotNames.forEach { name -> File(directory(report.runId), "screenshots/$name").takeIf { it.isFile }?.copyTo(File(screenshots, name), overwrite = true) }
        val zip = File(directory(report.runId), "regression.zip")
        ZipOutputStream(zip.outputStream()).use { out -> dir.walkTopDown().filter { it.isFile }.forEach { file ->
            out.putNextEntry(ZipEntry(file.relativeTo(dir).invariantSeparatorsPath)); file.inputStream().use { it.copyTo(out) }; out.closeEntry()
        } }
        return zip
    }
}
