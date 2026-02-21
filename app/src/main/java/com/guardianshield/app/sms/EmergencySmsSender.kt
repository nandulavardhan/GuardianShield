package com.guardianshield.app.sms

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.guardianshield.app.BuildConfig
import com.guardianshield.app.data.SupabaseProvider
import com.guardianshield.app.data.models.EmergencyContact
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object EmergencySmsSender {

    private const val TAG = "EmergencySMS"

    // Twilio credentials from BuildConfig (read from local.properties)
    private val ACCOUNT_SID = BuildConfig.TWILIO_ACCOUNT_SID
    private val AUTH_TOKEN = BuildConfig.TWILIO_AUTH_TOKEN
    private val FROM_NUMBER = BuildConfig.TWILIO_PHONE_NUMBER

    // Ktor HTTP client for Twilio API calls
    private val httpClient = HttpClient(Android)
    private val smsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    /**
     * Send SOS alerts to all emergency contacts and PCR via Twilio.
     */
    suspend fun sendSosAlerts(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        triggerType: String,
        pcrNumber: String
    ) {
        val message = buildSosMessage(context, latitude, longitude, triggerType)

        // Send to PCR first
        if (pcrNumber.isNotBlank()) {
            sendViaTwilio(pcrNumber, message)
        }

        // Send to all emergency contacts from Supabase
        try {
            SupabaseProvider.client.auth.awaitInitialization()
            val user = SupabaseProvider.client.auth.currentUserOrNull()
            if (user == null) {
                Log.e(TAG, "Cannot fetch contacts: User session is empty (null). SMS aborted!")
                return
            }
            
            val contacts = SupabaseProvider.client.postgrest["emergency_contacts"]
                .select {
                    filter { eq("user_id", user.id) }
                }
                .decodeList<EmergencyContact>()

            contacts.forEach { contact ->
                sendViaTwilio(contact.phone, message)
            }
            Log.d(TAG, "SOS alerts sent to ${contacts.size} contacts + PCR via Twilio")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch contacts for SMS", e)
        }
    }

    /**
     * Send a breadcrumb alert (low battery) via Twilio.
     */
    suspend fun sendBreadcrumbAlert(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        batteryLevel: Int,
        pcrNumber: String
    ) {
        val locationStr = if (latitude != null && longitude != null)
            "https://maps.google.com/maps?q=$latitude,$longitude"
        else "Location unavailable"

        val message = """
             BATTERY BREADCRUMB
            Battery: $batteryLevel%
            Location: $locationStr
            Time: ${getCurrentTime()}
        """.trimIndent()

        if (pcrNumber.isNotBlank()) {
            sendViaTwilio(pcrNumber, message)
        }
    }

    /**
     * Send periodic location update via Twilio.
     */
    suspend fun sendLocationUpdate(
        pcrNumber: String,
        latitude: Double,
        longitude: Double,
        batteryLevel: Int
    ) {
        val message = """
             LOCATION UPDATE
            Location: https://maps.google.com/maps?q=$latitude,$longitude
            Battery: $batteryLevel%
            Time: ${getCurrentTime()}
        """.trimIndent()

        if (pcrNumber.isNotBlank()) {
            sendViaTwilio(pcrNumber, message)
        }
    }

    /**
     * Build a compact SOS message that fits within SMS length limits.
     */
    private fun buildSosMessage(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        triggerType: String
    ): String {
        val locationStr = if (latitude != null && longitude != null)
            "https://maps.google.com/?q=$latitude,$longitude"
        else "Location unavailable"

        return "SOS ALERT\nLocation:\n$locationStr\n${getCurrentTime()}"
    }

    /**
     * Send an SMS via Twilio REST API.
     * Uses HTTP Basic Auth: AccountSID:AuthToken
     * POST to https://api.twilio.com/2010-04-01/Accounts/{SID}/Messages.json
     */
    private fun sendViaTwilio(toNumber: String, message: String) {
        if (ACCOUNT_SID.isBlank() || AUTH_TOKEN.isBlank() || FROM_NUMBER.isBlank()) {
            Log.e(TAG, "Twilio credentials not configured. Check local.properties.")
            return
        }

        // Twilio requires E.164 format: +countrycode followed by number
        val formattedNumber = formatToE164(toNumber)
        Log.d(TAG, "Sending SMS to: $formattedNumber (original: $toNumber)")

        smsScope.launch {
            try {
                val url = "https://api.twilio.com/2010-04-01/Accounts/$ACCOUNT_SID/Messages.json"

                val response: HttpResponse = httpClient.post(url) {
                    basicAuth(ACCOUNT_SID, AUTH_TOKEN)

                    setBody(FormDataContent(Parameters.build {
                        append("To", formattedNumber)
                        append("From", FROM_NUMBER)
                        append("Body", message)
                    }))
                }

                val status = response.status.value
                val body = response.bodyAsText()

                if (status in 200..299) {
                    Log.d(TAG, "Twilio SMS sent to $formattedNumber (status=$status)")
                } else {
                    Log.e(TAG, "Twilio SMS FAILED to $formattedNumber — status=$status")
                    Log.e(TAG, "Twilio response: $body")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Twilio API call failed for $formattedNumber: ${e.message}", e)
            }
        }
    }

    /**
     * Format phone number to E.164 format required by Twilio.
     * If number already starts with +, use as-is.
     * Otherwise prepend +91 (India country code).
     */
    private fun formatToE164(phone: String): String {
        val cleaned = phone.replace("[^+\\d]".toRegex(), "") // Remove spaces, dashes, parens
        return if (cleaned.startsWith("+")) cleaned else "+91$cleaned"
    }

    private fun getBatteryLevel(context: Context): Int {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return (level * 100 / scale)
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    }
}