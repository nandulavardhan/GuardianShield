package com.guardianshield.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.guardianshield.app.R
import com.guardianshield.app.camera.BurstCaptureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class NoNetworkEmergencyService : LifecycleService() {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var strobeJob: Job? = null
    private var isTorchOn = false

    companion object {
        private const val TAG = "NoNetworkSOS"
        private const val CHANNEL_ID = "no_network_sos_channel"
        private const val NOTIFICATION_ID = 912
        private const val ACTION_STOP = "com.guardianshield.STOP_NO_NETWORK_SOS"

        fun start(context: Context) {
            val intent = Intent(context, NoNetworkEmergencyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NoNetworkEmergencyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (intent?.action == ACTION_STOP) {
            stopEmergencyMode()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startEmergencyMode()
        
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Offline Emergency Mode",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Siren, Flashing, and Recording Active"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val stopIntent = Intent(this, NoNetworkEmergencyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 OFFLINE EMERGENCY ACTIVE")
            .setContentText("No network! Siren and recording started.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop SOS", stopPending)
            
        return builder.build()
    }

    private fun startEmergencyMode() {
        startSiren()
        startFlashlightStrobe()
        startAudioRecording()
        
        // Start Burst Capture
        lifecycleScope.launch {
            try {
                val burstManager = BurstCaptureManager(applicationContext)
                burstManager.captureBurstSilent(this@NoNetworkEmergencyService)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start burst capture", e)
            }
        }
    }

    private fun startSiren() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.siren)?.apply {
                isLooping = true
                setVolume(1.0f, 1.0f) // Max volume
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start siren", e)
        }
    }

    private fun startFlashlightStrobe() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Find a camera with a flash
            cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId != null) {
                strobeJob = lifecycleScope.launch(Dispatchers.IO) {
                    while (true) {
                        try {
                            isTorchOn = !isTorchOn
                            cameraManager?.setTorchMode(cameraId!!, isTorchOn)
                            delay(200) // Fast strobe
                        } catch (e: Exception) {
                            Log.e(TAG, "Torch mode failed", e)
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight unavailable", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun startAudioRecording() {
        try {
            val audioFile = File(filesDir, "evidence/offline_audio_${System.currentTimeMillis()}.3gp")
            audioFile.parentFile?.mkdirs()

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
        }
    }

    private fun stopEmergencyMode() {
        Log.d(TAG, "Stopping Offline Emergency Mode")
        
        // Stop Siren
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        // Stop Strobe
        strobeJob?.cancel()
        try {
            if (cameraId != null) {
                cameraManager?.setTorchMode(cameraId!!, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable torch on stop", e)
        }
        cameraManager = null

        // Stop Audio Recording
        mediaRecorder?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                Log.e(TAG, "MediaRecorder stop failed", e)
            } finally {
                it.release()
            }
        }
        mediaRecorder = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopEmergencyMode()
        super.onDestroy()
    }
}
