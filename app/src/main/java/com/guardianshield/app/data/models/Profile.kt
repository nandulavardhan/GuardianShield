package com.guardianshield.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String = "",
    @SerialName("full_name") val fullName: String = "",
    val phone: String? = null,
    @SerialName("pin_hash") val pinHash: String = "",
    @SerialName("ghost_mode") val ghostMode: String = "default",
    @SerialName("created_at") val createdAt: String = ""
)
