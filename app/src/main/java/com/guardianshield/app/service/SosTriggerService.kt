package com.guardianshield.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.R
import com.guardianshield.app.data.SupabaseProvider
import com.guardianshield.app.data.models.SosEvent
import com.guardianshield.app.sms.EmergencySmsSender
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File

class SosTriggerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "SosTrigger"
        private const val CHANNEL_ID = "sos_channel"
        private const val NOTIFICATION_ID = 911
        private const val ACTION_CANCEL = "com.guardianshield.CANCEL_SOS"

        fun triggerSos(context: Context, triggerType: String) {
            val intent = Intent(context, SosTriggerService::class.java).apply {
                putExtra("trigger_type", triggerType)
            }
            context.startForegroundService(intent)
        }

        fun cancelSos(context: Context) {
            val intent = Intent(context, SosTriggerService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelSos()
            return START_NOT_STICKY
        }

        val triggerType = intent?.getStringExtra("trigger_type") ?: "unknown"
        startForeground(NOTIFICATION_ID, buildNotification())
        executeSos(triggerType)
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SOS Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Active SOS emergency alert"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val cancelIntent = Intent(this, SosTriggerService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 SOS ACTIVE")
            .setContentText("Emergency alert sent. Location being tracked.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel SOS", cancelPending)
            .build()
    }

    private fun executeSos(triggerType: String) {
        serviceScope.launch {
            try {
                if (!NoNetworkEmergencyService.isNetworkAvailable(this@SosTriggerService)) {
                    Log.w(TAG, "NO NETWORK DETECTED: triggering offline emergency mode")
                    NoNetworkEmergencyService.start(this@SosTriggerService)
                }

                // IMPORTANT: Ensure backend login session is recovered from secure storage
                // since this is often launched from a background/cold service state.
                SupabaseProvider.client.auth.awaitInitialization()

                val prefs = GuardianApp.instance.preferencesManager
                prefs.setSosActive(true)

                // 1. Get current location
                val location = getCurrentLocation()

                // 2. Create SOS event in Supabase
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: ""
                val sosEvent = SosEvent(
                    userId = userId,
                    triggerType = triggerType,
                    latitude = location?.first,
                    longitude = location?.second,
                    batteryLevel = getBatteryLevel()
                )

                try {
                    val result = SupabaseProvider.client.postgrest["sos_events"]
                        .insert(sosEvent) {
                            select()
                            single()
                        }
                        .decodeSingle<SosEvent>()
                    prefs.setActiveSosEventId(result.id)

                    // 3. Send emergency SMS
                    val pcrNumber = prefs.pcrNumber.first()
                    EmergencySmsSender.sendSosAlerts(
                        this@SosTriggerService,
                        location?.first, location?.second,
                        triggerType, pcrNumber
                    )

                    // 4. Start location tracking
                    LocationTrackingService.start(this@SosTriggerService, result.id)

                    // 5. Record audio clip and upload to evidence-audio bucket
                    launch {
                        try {
                            val audioFile = File(filesDir, "evidence/audio_${result.id}.3gp")
                            audioFile.parentFile?.mkdirs()
                            recordAndUploadAudio(audioFile, result.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Audio evidence failed: ${e.message}", e)
                        }
                    }

                    Log.d(TAG, "SOS executed successfully. Event ID: ${result.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Supabase error, sending SMS anyway", e)
                    // Still send SMS even if Supabase fails
                    val pcrNumber = prefs.pcrNumber.first()
                    EmergencySmsSender.sendSosAlerts(
                        this@SosTriggerService,
                        location?.first, location?.second,
                        triggerType, pcrNumber
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "SOS execution failed", e)
            }
        }
    }

    private fun cancelSos() {
        serviceScope.launch {
            try {
                NoNetworkEmergencyService.stop(this@SosTriggerService)
                
                val prefs = GuardianApp.instance.preferencesManager
                prefs.setSosActive(false)
                val eventId = prefs.activeSosEventId.first()
                if (eventId.isNotBlank()) {
                    SupabaseProvider.client.postgrest["sos_events"]
                        .update({ set("status", "cancelled") }) {
                            filter { eq("id", eventId) }
                        }
                }
                LocationTrackingService.stop(this@SosTriggerService)
            } catch (e: Exception) {
                Log.e(TAG, "Cancel SOS failed", e)
            }
            stopSelf()
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY, null
            ).await()
            location?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            Log.e(TAG, "Location fetch failed", e)
            null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun recordAndUploadAudio(outputFile: File, sosEventId: String) {
        withContext(Dispatchers.IO) {
            try {
                val recorder = android.media.MediaRecorder().apply {
                    setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                    setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                }

                delay(5000) // Record 5 seconds

                recorder.stop()
                recorder.release()
                Log.d(TAG, "Audio recorded: ${outputFile.name}")

                // Upload to Supabase
                val bucket = SupabaseProvider.client.storage["evidence-audio"]
                val storagePath = "sos/$sosEventId/${outputFile.name}"
                bucket.upload(storagePath, outputFile.readBytes())
                Log.d(TAG, "Audio uploaded: $storagePath")
            } catch (e: Exception) {
                Log.e(TAG, "AUDIO RECORD/UPLOAD FAILED: ${e.message}", e)
            }
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return (level * 100 / scale)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
