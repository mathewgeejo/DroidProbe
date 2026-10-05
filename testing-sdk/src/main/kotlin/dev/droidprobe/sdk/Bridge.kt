package dev.droidprobe.sdk

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import dev.droidprobe.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

const val PROBE_PERMISSION = "dev.droidprobe.sample.permission.PROBE"
val PROBE_URI: Uri = Uri.parse("content://dev.droidprobe.sample.probe")
@Serializable enum class BridgeCommand { READ, RESET, CONFIGURE_FAULT, RELEASE_FAULT }
@Serializable data class BridgeRequest(val command: BridgeCommand, val runId: String, val mode: AppMode = AppMode.FAULTY,
    val fixtures: Fixtures = Fixtures(), val faults: FaultConfig = FaultConfig(), val version: Int = PROTOCOL_VERSION)

/** ContentProvider.call does not imply read/write enforcement: check every exchange explicitly. */
abstract class DebugProbeProvider : ContentProvider() {
    protected abstract fun exchange(request: BridgeRequest): AppState
    override fun onCreate() = true
    final override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = requireNotNull(context)
        ctx.enforceCallingOrSelfPermission(PROBE_PERMISSION, "DroidProbe debug bridge requires matching development signer")
        require(ctx.packageManager.checkSignatures(ctx.applicationInfo.uid, Binder.getCallingUid()) == PackageManager.SIGNATURE_MATCH) { "Caller signature mismatch" }
        require(method == "exchange")
        val raw = requireNotNull(extras?.getString("json"))
        require(raw.length <= 16_384)
        val request = ProbeJson.decodeFromString<BridgeRequest>(raw)
        require(request.version == PROTOCOL_VERSION)
        return Bundle().apply { putString("json", ProbeJson.encodeToString(exchange(request))) }
    }
    final override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException("Use typed exchange")
    final override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    final override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    final override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    final override fun getType(uri: Uri) = "application/vnd.droidprobe.v1+json"
}
class ProbeClient(private val resolver: ContentResolver) {
    fun exchange(request: BridgeRequest): AppState {
        val bundle = Bundle().apply { putString("json", ProbeJson.encodeToString(request)) }
        val result = resolver.call(PROBE_URI, "exchange", null, bundle) ?: error("Debug SDK bridge unavailable")
        return ProbeJson.decodeFromString(requireNotNull(result.getString("json")))
    }
}
