package com.xtawa.samsunghzops.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal object NotificationSupport {
    const val CHANNEL_ID = "refresh_monitor"
    const val NOTIFICATION_ID = 42

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "刷新率监控",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Samsung Hz Ops 自动化与刷新率状态"
            },
        )
    }

    fun build(
        context: Context,
        title: String,
        text: String,
    ): Notification {
        createChannel(context)
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .build()
        }
    }
}
