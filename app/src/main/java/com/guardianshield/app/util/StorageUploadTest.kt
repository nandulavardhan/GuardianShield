package com.guardianshield.app.util

import android.util.Log
import com.guardianshield.app.data.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StorageUploadTest {
    private const val TAG = "StorageUploadTest"

    suspend fun runTest() = withContext(Dispatchers.IO) {
        // Step 1: Check authentication
        val session = SupabaseProvider.client.auth.currentSessionOrNull()
        if (session == null) {
            Log.e(TAG, "FAIL: No active session. User must log in first.")
            return@withContext
        }
        Log.d(TAG, "Auth OK - user=${session.user?.id}")

        // Step 2: Upload to evidence-photos bucket
        try {
            val bytes = "Hello from GuardianShield".encodeToByteArray()
            val path = "test/photo_test_${System.currentTimeMillis()}.txt"

            SupabaseProvider.client.storage["evidence-photos"].upload(path, bytes)

            Log.d(TAG, "Photo bucket upload OK - path=$path")
        } catch (e: Exception) {
            Log.e(TAG, "Photo bucket upload FAILED: ${e.message}", e)
        }

        // Step 3: Upload to evidence-audio bucket
        try {
            val bytes = "Audio test from GuardianShield".encodeToByteArray()
            val path = "test/audio_test_${System.currentTimeMillis()}.txt"

            SupabaseProvider.client.storage["evidence-audio"].upload(path, bytes)

            Log.d(TAG, "Audio bucket upload OK - path=$path")
        } catch (e: Exception) {
            Log.e(TAG, "Audio bucket upload FAILED: ${e.message}", e)
        }
    }
}
