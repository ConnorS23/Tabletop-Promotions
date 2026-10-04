package com.tableads.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DevicePayload(
    @SerialName("device_id") val deviceId: String,
    @SerialName("pair_code") val pairCode: String,
    val name: String = "",
    val linked: Boolean = false,
    val command: String? = null,
    @SerialName("pin_hash") val pinHash: String? = null,
    @SerialName("master_pin_hash") val masterPinHash: String? = null,
    val restaurant: Restaurant? = null,
    val ads: List<Ad> = emptyList(),
    val menu: Menu? = null,
)

@Serializable
data class Restaurant(
    val id: String,
    val name: String,
    @SerialName("slide_seconds") val slideSeconds: Int = 8,
    @SerialName("idle_timeout_seconds") val idleTimeoutSeconds: Int = 60,
)

@Serializable
data class Ad(
    val id: String,
    val title: String = "",
    @SerialName("media_type") val mediaType: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("updated_at") val updatedAt: String = "",
) {
    val isVideo: Boolean get() = mediaType == "video"
}

@Serializable
data class Menu(
    @SerialName("storage_path") val storagePath: String,
    @SerialName("file_name") val fileName: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class ApiError(
    val code: String? = null,
    val message: String? = null,
)
