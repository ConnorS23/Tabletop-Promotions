package com.tableads.app.data

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import java.util.UUID

/** Persistent per-install device credentials. The ID is shown to managers; the secret never leaves the API calls. */
class DeviceIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)

    val deviceId: String
    val secret: String

    init {
        val existingId = prefs.getString(KEY_ID, null)
        val existingSecret = prefs.getString(KEY_SECRET, null)
        if (existingId != null && existingSecret != null) {
            deviceId = existingId
            secret = existingSecret
        } else {
            deviceId = UUID.randomUUID().toString()
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            secret = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
            prefs.edit().putString(KEY_ID, deviceId).putString(KEY_SECRET, secret).apply()
        }
    }

    private companion object {
        const val KEY_ID = "device_id"
        const val KEY_SECRET = "device_secret"
    }
}
