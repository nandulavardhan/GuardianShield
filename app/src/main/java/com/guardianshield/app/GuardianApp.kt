package com.guardianshield.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.guardianshield.app.data.PreferencesManager

class GuardianApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesManager = PreferencesManager(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // SOS Active channel — high importance
            val sosChannel = NotificationChannel(
                CHANNEL_SOS, "SOS Active",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification shown when SOS is active"
                setShowBadge(true)
            }

            // Background service channel — low importance
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE, "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for background monitoring"
                setShowBadge(false)
            }

            nm.createNotificationChannel(sosChannel)
            nm.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_SOS = "sos_channel"
        const val CHANNEL_SERVICE = "service_channel"

        lateinit var instance: GuardianApp
            private set
    }
}
