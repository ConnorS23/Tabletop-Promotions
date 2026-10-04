package com.tableads.app.data

import com.tableads.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class InvalidCredentialsException(message: String) : IOException(message)

class Api(private val identity: DeviceIdentity, private val appVersion: String) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean get() = baseUrl.isNotEmpty() && anonKey.isNotEmpty()

    fun publicMediaUrl(storagePath: String): String =
        "$baseUrl/storage/v1/object/public/media/" +
            storagePath.split('/').joinToString("/") { android.net.Uri.encode(it) }

    suspend fun register(): DevicePayload = rpc("register_device")

    suspend fun sync(): DevicePayload = rpc("device_sync")

    private suspend fun rpc(name: String): DevicePayload = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_device_id", identity.deviceId)
            put("p_secret", identity.secret)
            put("p_app_version", appVersion)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$name")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = runCatching { json.decodeFromString<ApiError>(text) }.getOrNull()
                if (err?.code == "28000") throw InvalidCredentialsException(err.message ?: "invalid credentials")
                throw IOException("HTTP ${response.code}: ${err?.message ?: text.take(200)}")
            }
            json.decodeFromString<DevicePayload>(text)
        }
    }

    /** Downloads [url] to [dest] atomically (via a temp file). */
    suspend fun download(url: String, dest: File) = withContext(Dispatchers.IO) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (${response.code}): $url")
            response.body!!.byteStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            throw IOException("Could not save ${dest.name}")
        }
    }
}
