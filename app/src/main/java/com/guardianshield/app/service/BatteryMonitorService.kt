package com.guardianshield.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.R
import com.guardianshield.app.data.SupabaseProvider
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

class BatteryMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batteryReceiver: BroadcastReceiver? = null
    private var breadcrumbSent = false

    companion object {
        private const val TAG = "BatteryMonitor"
        private const val CHANNEL_ID = "battery_monitor_channel"
        private const val NOTIFICATION_ID = 202
        private const val LOW_BATTERY_THRESHOLD = 5

        fun start(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryMonitorService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        registerBatteryReceiver()
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Battery Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors battery for digital breadcrumbs"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian Shield Active")
            .setContentText("Battery monitoring enabled")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val percentage = (level * 100) / scale

                if (percentage <= LOW_BATTERY_THRESHOLD && !breadcrumbSent) {
                    breadcrumbSent = true
                    Log.w(TAG, "Battery critically low ($percentage%). Sending breadcrumb.")
                    sendDigitalBreadcrumb(percentage)
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    @Suppress("MissingPermission")
    private fun sendDigitalBreadcrumb(batteryLevel: Int) {
        serviceScope.launch {
            try {
                val prefs = GuardianApp.instance.preferencesManager
                val pcrNumber = prefs.pcrNumber.first()

                // 1. Get last known location
                val fusedClient = LocationServices.getFusedLocationProviderClient(this@BatteryMonitorService)
                val location = try {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Location failed", e)
                    null
                }

                // 2. Record 5-second audio clip
                val audioFile = recordAudioClip()

                // 3. Send SMS alert
                EmergencySmsSender.sendBreadcrumbAlert(
                    this@BatteryMonitorService,
                    location?.latitude, location?.longitude,
                    batteryLevel, pcrNumber
                )

                // 4. Upload audio to Supabase Storage
                if (audioFile != null && audioFile.exists()) {
                    try {
                        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                        if (userId == null) {
                            Log.e(TAG, "Audio upload skipped — user not authenticated")
                        } else {
                            val storagePath = "breadcrumbs/${userId}/${audioFile.name}"
                            val bucket = SupabaseProvider.client.storage["evidence-audio"]
                            bucket.upload(storagePath, audioFile.readBytes())
                            Log.d(TAG, "Audio uploaded: $storagePath")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "AUDIO UPLOAD FAILED: ${e.message}", e)
                        Log.e(TAG, "Exception class: ${e::class.simpleName}")
                    }
                }

                // 5. Log breadcrumb event
                try {
                    val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: ""
                    SupabaseProvider.client.postgrest["sos_events"].insert(
                        mapOf(
                            "user_id" to userId,
                            "trigger_type" to "breadcrumb_low_battery",
                            "latitude" to (location?.latitude),
                            "longitude" to (location?.longitude),
                            "battery_level" to batteryLevel
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Breadcrumb log failed", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Digital breadcrumb failed", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun recordAudioClip(): File? = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(filesDir, "breadcrumb_audio_${System.currentTimeMillis()}.3gp")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            delay(5000) // Record for 5 seconds

            recorder.apply {
                stop()
                release()
            }
            Log.d(TAG, "Audio recorded: ${audioFile.name}")
            audioFile
        } catch (e: Exception) {
            Log.e(TAG, "Audio recording failed", e)
            null
        }
    }

    override fun onDestroy() {
        batteryReceiver?.let { unregisterReceiver(it) }
        serviceScope.cancel()
        super.onDestroy()
    }
}
