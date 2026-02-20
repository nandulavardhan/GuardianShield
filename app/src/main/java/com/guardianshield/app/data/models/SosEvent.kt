package com.guardianshield.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SosEvent(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("trigger_type") val triggerType: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("battery_level") val batteryLevel: Int? = null,
    val status: String = "active",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("resolved_at") val resolvedAt: String? = null
)
