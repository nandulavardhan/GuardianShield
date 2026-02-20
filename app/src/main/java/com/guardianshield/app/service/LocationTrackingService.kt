package com.guardianshield.app.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.MainActivity
import com.guardianshield.app.R
import com.guardianshield.app.data.SupabaseProvider
import com.guardianshield.app.data.models.LocationPoint
import com.guardianshield.app.sms.EmergencySmsSender
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sosEventId: String = ""
    private var lastSmsTime: Long = 0
    private val SMS_INTERVAL_MS = 60_000L // 1 minute

    companion object {
        private const val TAG = "LocationTracking"
        private const val NOTIFICATION_ID = 2001
        const val EXTRA_SOS_EVENT_ID = "sos_event_id"

        fun start(context: Context, sosEventId: String) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                putExtra(EXTRA_SOS_EVENT_ID, sosEventId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sosEventId = intent?.getStringExtra(EXTRA_SOS_EVENT_ID) ?: ""
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).apply {
            setMinUpdateIntervalMillis(3000L)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    serviceScope.launch {
                        // Stream to Supabase
                        try {
                            SupabaseProvider.client.postgrest["location_trail"].insert(
                                LocationPoint(
                                    sosEventId = sosEventId,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracy = location.accuracy,
                                    speed = location.speed,
                                    batteryLevel = getBatteryLevel()
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to upload location", e)
                        }

                        // Send SMS update every 60 seconds
                        val now = System.currentTimeMillis()
                        if (now - lastSmsTime >= SMS_INTERVAL_MS) {
                            lastSmsTime = now
                            try {
                                val pcrNumber = GuardianApp.instance.preferencesManager.pcrNumber.first()
                                EmergencySmsSender.sendLocationUpdate(
                                    pcrNumber,
                                    location.latitude,
                                    location.longitude,
                                    getBatteryLevel()
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send SMS update", e)
                            }
                        }
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, GuardianApp.CHANNEL_SOS)
            .setContentTitle("🚨 SOS Active — Location Tracking")
            .setContentText("Streaming your location to emergency contacts")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }
}
