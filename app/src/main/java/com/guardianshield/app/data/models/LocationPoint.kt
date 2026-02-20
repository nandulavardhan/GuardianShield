package com.guardianshield.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationPoint(
    val id: String = "",
    @SerialName("sos_event_id") val sosEventId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float? = null,
    val speed: Float? = null,
    @SerialName("battery_level") val batteryLevel: Int? = null,
    @SerialName("recorded_at") val recordedAt: String = ""
)
