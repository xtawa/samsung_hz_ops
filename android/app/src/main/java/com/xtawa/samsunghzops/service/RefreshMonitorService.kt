package com.xtawa.samsunghzops.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.xtawa.samsunghzops.HzOpsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Read-only monitor used by the optional notification/diagnostics feature. */
class RefreshMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val container by lazy { (application as HzOpsApplication).container }

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(
            NotificationSupport.build(this, "Samsung Hz Ops 监视器", "正在读取当前刷新率"),
        )
        serviceScope.launch {
            container.refreshRates.snapshot.collect { snapshot ->
                val hz = snapshot.activeRefreshRateHz?.let { "%.0f Hz".format(it) } ?: "未知"
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager?.notify(
                    NotificationSupport.NOTIFICATION_ID + 1,
                    NotificationSupport.build(this@RefreshMonitorService, "当前刷新率 $hz", snapshot.activeMode.name),
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NotificationSupport.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationSupport.NOTIFICATION_ID, notification)
        }
    }
}
