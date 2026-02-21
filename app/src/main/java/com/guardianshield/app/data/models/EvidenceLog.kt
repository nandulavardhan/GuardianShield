package com.guardianshield.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EvidenceLog(
    val id: String = "",
    @SerialName("sos_event_id") val sosEventId: String = "",
    val type: String = "", // "audio_clip"
    @SerialName("storage_path") val storagePath: String = "",
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("created_at") val createdAt: String = ""
)
