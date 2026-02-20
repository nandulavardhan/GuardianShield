package com.guardianshield.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmergencyContact(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val relationship: String = "",
    @SerialName("is_pcr") val isPcr: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)
