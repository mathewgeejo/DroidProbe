package dev.droidprobe.sample

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import dev.droidprobe.core.*
import kotlinx.serialization.encodeToString

class SampleStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("probe-store", Context.MODE_PRIVATE)
    private val engine = SampleEngine(prefs.getString("state", null)?.let { ProbeJson.decodeFromString<AppState>(it) } ?: AppState(), SystemClock::elapsedRealtime)
    val observed = mutableStateOf(engine.state)
    val state get() = engine.state
    /** All callers use Android's main thread; one immutable snapshot is committed per mutation. */
    fun change(block: SampleEngine.() -> Unit) {
        engine.block()
        check(prefs.edit().putString("state", ProbeJson.encodeToString(engine.state)).commit()) { "Fixture persistence failed" }
        observed.value = engine.state
    }
    companion object {
        @Volatile private var instance: SampleStore? = null
        fun get(context: Context): SampleStore = instance ?: synchronized(this) { instance ?: SampleStore(context).also { instance = it } }
    }
}
