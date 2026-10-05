package dev.droidprobe.sample

import android.os.Handler
import android.os.Looper
import dev.droidprobe.core.*
import dev.droidprobe.sdk.*
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class SampleProbeProvider : DebugProbeProvider() {
    override fun exchange(request: BridgeRequest): AppState {
        val task = FutureTask {
            val store = SampleStore.get(requireNotNull(context))
            if (request.command != BridgeCommand.RESET) require(store.state.runId == request.runId) { "Stale run identifier" }
            when (request.command) {
                BridgeCommand.READ -> Unit
                BridgeCommand.RESET -> store.change { reset(request.runId, request.mode, request.fixtures, request.faults) }
                BridgeCommand.CONFIGURE_FAULT -> store.change { configure(request.faults) }
                BridgeCommand.RELEASE_FAULT -> store.change { release() }
            }
            store.state
        }
        if (Looper.myLooper() == Looper.getMainLooper()) task.run() else Handler(Looper.getMainLooper()).post(task)
        return task.get(5, TimeUnit.SECONDS)
    }
}
