package dev.droidprobe.model

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dev.droidprobe.core.LocalInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class ModelStatus(val available: Boolean, val description: String, val identity: String? = null)
class LocalModel private constructor(private val engine: Engine, override val modelIdentity: String) : LocalInference, AutoCloseable {
    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        // No tools registered: model text can only be proposed to the core validator.
        engine.createConversation(ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 0.9, temperature = 0.1, seed = 1)))
            .use { it.sendMessage(prompt).toString() }
    }
    override fun close() = engine.close()
    companion object {
        const val RUNTIME = "LiteRT-LM 0.10.2 / CPU(2) / context 2048 / topK 1 / seed 1"
        fun status(context: Context): ModelStatus {
            val file = File(context.filesDir, "models/model.litertlm")
            val hash = File(context.filesDir, "models/model.sha256")
            if (!file.isFile) return ModelStatus(false, "Model absent. Graph and random baselines are available.")
            if (!hash.isFile || !hash.readText().trim().matches(Regex("[a-fA-F0-9]{64}"))) return ModelStatus(false, "Model checksum missing/invalid; provide model.sha256.")
            if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) return ModelStatus(false, "Physical ARM64 device required for the selected local inference validation path.")
            return ModelStatus(true, "Artifact present; runtime compatibility and memory must be verified at initialization.", "${file.name}: ${hash.readText().trim()} / $RUNTIME")
        }
        suspend fun open(context: Context): Pair<LocalModel?, ModelStatus> = withContext(Dispatchers.Default) {
            val state = status(context)
            if (!state.available) return@withContext null to state
            var engine: Engine? = null
            try {
                val file = File(context.filesDir, "models/model.litertlm")
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream -> val buffer = ByteArray(64 * 1024); var n = stream.read(buffer); while (n >= 0) { if (n > 0) digest.update(buffer, 0, n); n = stream.read(buffer) } }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(File(context.filesDir, "models/model.sha256").readText().trim(), ignoreCase = true)) { "Model checksum mismatch" }
                engine = Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(numOfThreads = 2), maxNumTokens = 2048, cacheDir = context.cacheDir.absolutePath))
                engine.initialize()
                val identity = "$actual / $RUNTIME"
                LocalModel(engine, identity) to ModelStatus(true, "Local runtime initialized; inference performed only for validated proposals.", identity)
            } catch (e: Exception) { engine?.close(); null to ModelStatus(false, "Runtime initialization failed: ${e.javaClass.simpleName}: ${e.message}") }
            catch (e: LinkageError) { null to ModelStatus(false, "Native runtime incompatible: ${e.message}") }
        }
    }
}
