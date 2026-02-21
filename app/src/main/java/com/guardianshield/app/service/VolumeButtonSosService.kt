package com.guardianshield.app.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.VolumeProviderCompat
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.R

class VolumeButtonSosService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    private var lastVolUpTime: Long = 0
    private var lastVolDownTime: Long = 0

    companion object {
        private const val TAG = "VolumeButtonSos"
        private const val CHANNEL_ID = GuardianApp.CHANNEL_SERVICE
        private const val NOTIFICATION_ID = 912

        fun start(context: Context) {
            val intent = Intent(context, VolumeButtonSosService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, VolumeButtonSosService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating VolumeButtonSosService")
        startForeground(NOTIFICATION_ID, buildNotification())
        setupSilentMediaPlayer()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting VolumeButtonSosService")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian Shield Protection")
            .setContentText("Hardware SOS trigger is active (Vol Up + Vol Down)")
            .setSmallIcon(R.drawable.ic_launcher_default)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun setupSilentMediaPlayer() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.silent_audio).apply {
                isLooping = true
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                start()
            }
            Log.d(TAG, "Silent MediaPlayer started looping.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start silent MediaPlayer: \${e.message}", e)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG)
        
        // We set the PlaybackState to playing so the OS considers this an active media session
        // This is crucial for intercepting hardware buttons when the screen is off.
        val state = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(state)

        // Intercept Volume keys using VolumeProviderCompat globally
        val volumeProvider = object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                val currentTime = System.currentTimeMillis()
                
                if (direction == AudioManager.ADJUST_RAISE) {
                    Log.d(TAG, "Volume UP pressed")
                    lastVolUpTime = currentTime
                    checkTrigger()
                } else if (direction == AudioManager.ADJUST_LOWER) {
                    Log.d(TAG, "Volume DOWN pressed")
                    lastVolDownTime = currentTime
                    checkTrigger()
                }
            }
        }

        mediaSession.setPlaybackToRemote(volumeProvider)
        mediaSession.isActive = true
        Log.d(TAG, "MediaSession setup complete and active")
    }

    private fun checkTrigger() {
        // If both Volume Up and Volume Down were pressed within 500ms
        if (lastVolUpTime > 0 && lastVolDownTime > 0 && Math.abs(lastVolUpTime - lastVolDownTime) <= 500) {
            Log.d(TAG, "SOS Volume Trigger Detected!")
            
            // Reset times to prevent multiple rapid triggers
            lastVolUpTime = 0
            lastVolDownTime = 0
            
            triggerSos()
        }
    }

    private fun triggerSos() {
        SosTriggerService.triggerSos(this, "hardware_volume_buttons")
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying VolumeButtonSosService")
        
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }
}
