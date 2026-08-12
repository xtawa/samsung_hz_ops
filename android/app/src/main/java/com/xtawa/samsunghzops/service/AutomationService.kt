package com.xtawa.samsunghzops.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.xtawa.samsunghzops.HzOpsApplication
import com.xtawa.samsunghzops.core.model.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Applies one policy decision whenever the unified device state changes. */
class AutomationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val container by lazy { (application as HzOpsApplication).container }

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(
            NotificationSupport.build(
                this,
                "Samsung Hz Ops 自动化",
                "正在监听刷新率规则",
            ),
        )
        serviceScope.launch {
            combine(
                container.preferences.automationEnabled,
                container.policy.decision,
                container.powerSave.state,
            ) { enabled, decision, psm -> Triple(enabled, decision, psm) }
                .distinctUntilChanged { old, new ->
                    old.first == new.first &&
                        old.second == new.second &&
                        old.third.powerSaveMode == new.third.powerSaveMode &&
                        old.third.keepHighRefresh == new.third.keepHighRefresh
                }
                .collect { (enabled, decision, psm) ->
                    if (!enabled) return@collect
                    if (psm.powerSaveMode && !psm.keepHighRefresh) return@collect
                    when (val result = container.refreshRates.applyDecision(decision)) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> Unit // surfaced in the UI snapshot
                    }
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
