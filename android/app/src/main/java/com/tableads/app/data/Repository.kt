package com.tableads.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class DeviceState(
    val payload: DevicePayload? = null,
    /** Local files for ads, keyed by ad id. Only ads whose media is downloaded are playable. */
    val adFiles: Map<String, File> = emptyMap(),
    val menuFile: File? = null,
    val online: Boolean = false,
    val lastSyncMillis: Long = 0,
    val lastError: String? = null,
    val configError: String? = null,
)

/**
 * Talks to Supabase, keeps a cached copy of the last payload + media on disk so the
 * tablet keeps working when Wi-Fi drops, and exposes everything as a [StateFlow].
 */
class Repository(private val context: Context, val identity: DeviceIdentity, private val api: Api) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    private val payloadFile = File(context.filesDir, "payload.json")
    private val syncNow = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null

    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    /** Emits remote commands that need the UI (e.g. restart). */
    val commands = Channel<String>(Channel.BUFFERED)

    fun start() {
        if (loop != null) return
        if (!api.isConfigured) {
            _state.update { it.copy(configError = "App is missing SUPABASE_URL / SUPABASE_ANON_KEY build config.") }
            return
        }
        loop = scope.launch {
            loadCached()
            var registered = false
            while (true) {
                try {
                    val payload = if (registered) api.sync() else api.register().also { registered = true }
                    applyPayload(payload)
                } catch (e: Exception) {
                    Log.w(TAG, "sync failed", e)
                    if (e is InvalidCredentialsException) registered = false
                    _state.update { it.copy(online = false, lastError = e.message) }
                }
                val linked = _state.value.payload?.linked == true
                val waitMs = if (linked) LINKED_POLL_MS else UNLINKED_POLL_MS
                withTimeoutOrNull(waitMs) { syncNow.receive() }
            }
        }
    }

    fun requestSync() {
        syncNow.trySend(Unit)
    }

    fun clearCache() {
        scope.launch {
            mediaDir.listFiles()?.forEach { it.delete() }
            _state.update { it.copy(adFiles = emptyMap(), menuFile = null) }
            requestSync()
        }
    }

    fun verifyPin(pin: String): Boolean {
        val p = _state.value.payload ?: return false
        val hash = sha256("${identity.deviceId}:$pin")
        return hash == p.pinHash || hash == p.masterPinHash
    }

    private suspend fun loadCached() {
        val cached = runCatching {
            if (payloadFile.exists()) api.json.decodeFromString<DevicePayload>(payloadFile.readText()) else null
        }.getOrNull() ?: return
        _state.update { it.copy(payload = cached.copy(command = null)) }
        resolveMedia(cached, download = false)
    }

    private suspend fun applyPayload(payload: DevicePayload) {
        withContext(Dispatchers.IO) {
            payloadFile.writeText(api.json.encodeToString(DevicePayload.serializer(), payload.copy(command = null)))
        }
        _state.update {
            it.copy(
                payload = payload,
                online = true,
                lastSyncMillis = System.currentTimeMillis(),
                lastError = null,
            )
        }
        when (payload.command) {
            "clear_cache" -> {
                mediaDir.listFiles()?.forEach { it.delete() }
                _state.update { it.copy(adFiles = emptyMap(), menuFile = null) }
            }
            "restart_app" -> commands.trySend("restart_app")
        }
        resolveMedia(payload, download = true)
    }

    private suspend fun resolveMedia(payload: DevicePayload, download: Boolean) {
        val keep = mutableSetOf<String>()
        val adFiles = linkedMapOf<String, File>()

        for (ad in payload.ads) {
            val file = File(mediaDir, cacheName("ad", ad.id, ad.updatedAt, ad.storagePath))
            keep += file.name
            if (!file.exists() && download) {
                runCatching { api.download(api.publicMediaUrl(ad.storagePath), file) }
                    .onFailure { Log.w(TAG, "ad download failed ${ad.id}", it) }
            }
            if (file.exists()) {
                adFiles[ad.id] = file
                // Publish progressively so the first ads show while the rest download.
                _state.update { it.copy(adFiles = LinkedHashMap(adFiles)) }
            }
        }

        var menuFile: File? = null
        payload.menu?.let { menu ->
            val file = File(mediaDir, cacheName("menu", "menu", menu.updatedAt, menu.storagePath))
            keep += file.name
            if (!file.exists() && download) {
                runCatching { api.download(api.publicMediaUrl(menu.storagePath), file) }
                    .onFailure { Log.w(TAG, "menu download failed", it) }
            }
            if (file.exists()) menuFile = file
        }

        _state.update { it.copy(adFiles = adFiles, menuFile = menuFile) }

        if (download) {
            mediaDir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
    }

    private fun cacheName(prefix: String, id: String, version: String, path: String): String {
        val ext = path.substringAfterLast('.', "bin").lowercase().take(5)
        return "${prefix}_${id}_${sha256(version + path).take(12)}.$ext"
    }

    companion object {
        private const val TAG = "TableAdsRepo"
        private const val UNLINKED_POLL_MS = 3_000L
        private const val LINKED_POLL_MS = 30_000L

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
