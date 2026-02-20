package com.guardianshield.app.util

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Diagnostic utility to test Twilio SMS delivery end-to-end.
 *
 * Usage from any composable:
 *   LaunchedEffect(Unit) { TwilioTest.runFullDiagnostic() }
 *
 * Check Logcat with tag "TwilioTest" for results.
 */
object TwilioTest {
    private const val TAG = "TwilioTest"

    suspend fun runFullDiagnostic() = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== TWILIO DIAGNOSTIC START ==========")

        // Step 1: Check Twilio credentials
        val sid = BuildConfig.TWILIO_ACCOUNT_SID
        val token = BuildConfig.TWILIO_AUTH_TOKEN
        val from = BuildConfig.TWILIO_PHONE_NUMBER

        Log.d(TAG, "Account SID: ${sid.take(8)}... (length=${sid.length})")
        Log.d(TAG, "Auth Token:  ${token.take(4)}... (length=${token.length})")
        Log.d(TAG, "From Number: $from")

        if (sid.isBlank() || token.isBlank() || from.isBlank()) {
            Log.e(TAG, "FAIL: Twilio credentials are empty. Check local.properties and rebuild.")
            return@withContext
        }
        Log.d(TAG, "Step 1 OK: Credentials loaded")

        // Step 2: Check authentication
        val user = SupabaseProvider.client.auth.currentUserOrNull()
        if (user == null) {
            Log.e(TAG, "FAIL: User not authenticated. Log in first.")
            return@withContext
        }
        Log.d(TAG, "Step 2 OK: User authenticated - ${user.id}")

        // Step 3: Fetch emergency contacts
        try {
            val contacts = SupabaseProvider.client.postgrest["emergency_contacts"]
                .select {
                    filter { eq("user_id", user.id) }
                }
                .decodeList<EmergencyContact>()

            Log.d(TAG, "Step 3 OK: Found ${contacts.size} emergency contacts")

            if (contacts.isEmpty()) {
                Log.e(TAG, "FAIL: No emergency contacts saved. Add contacts in the app first.")
                return@withContext
            }

            contacts.forEachIndexed { i, c ->
                Log.d(TAG, "  Contact $i: name='${c.name}', phone='${c.phone}', isPcr=${c.isPcr}")
            }

            // Step 4: Send test SMS to first contact
            val testContact = contacts.first()
            Log.d(TAG, "Step 4: Sending test SMS to ${testContact.name} (${testContact.phone})")

            val toNumber = testContact.phone.let { phone ->
                // Ensure E.164 format
                if (phone.startsWith("+")) phone
                else "+91$phone" // Default to India country code
            }
            Log.d(TAG, "  Formatted number: $toNumber")

            val client = HttpClient(Android)
            val url = "https://api.twilio.com/2010-04-01/Accounts/$sid/Messages.json"

            val response: HttpResponse = client.post(url) {
                basicAuth(sid, token)
                setBody(FormDataContent(Parameters.build {
                    append("To", toNumber)
                    append("From", from)
                    append("Body", "Guardian Shield Test: If you received this, Twilio SMS is working!")
                }))
            }

            val status = response.status.value
            val body = response.bodyAsText()

            Log.d(TAG, "  Twilio Response Status: $status")
            Log.d(TAG, "  Twilio Response Body: $body")

            if (status in 200..299) {
                Log.d(TAG, "SUCCESS: SMS sent to ${testContact.phone}")
            } else {
                Log.e(TAG, "FAIL: Twilio returned status $status")
                Log.e(TAG, "  Common causes:")
                Log.e(TAG, "  - Status 401: Wrong Account SID or Auth Token")
                Log.e(TAG, "  - Status 400: Invalid phone number format (need +countrycode)")
                Log.e(TAG, "  - Status 21608: Trial account - recipient not verified")
                Log.e(TAG, "  - Status 21211: Invalid 'To' phone number")
            }

            client.close()

        } catch (e: Exception) {
            Log.e(TAG, "FAIL: ${e::class.simpleName}: ${e.message}", e)
        }

        Log.d(TAG, "========== TWILIO DIAGNOSTIC END ==========")
    }
}
