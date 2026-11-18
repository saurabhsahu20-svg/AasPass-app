package com.aaspass.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AasPassApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel("messages", "Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Private chat, groups and study notifications"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel("calls", "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Voice and video call invitations"
                }
            )
        }
    }
}
