package com.xtawa.samsunghzops.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.xtawa.samsunghzops.HzOpsApplication
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.RefreshReason
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
                container.preferences.masterEnabled,
                container.policy.decision,
                container.powerSave.state,
            ) { automation, master, decision, psm ->
                AutomationTick(automation, master, decision, psm.powerSaveMode, psm.keepHighRefresh)
            }
                .distinctUntilChanged()
                .collect { tick ->
                    if (!tick.automationEnabled || !tick.masterEnabled) return@collect
                    if (!tick.decision.shouldApply) return@collect
                    if (tick.decision.reasonCode == RefreshReason.THERMAL) return@collect
                    if (tick.decision.reasonCode == RefreshReason.EMERGENCY_OR_MASTER_OFF) return@collect
                    if (tick.powerSaveMode && !tick.keepHighRefresh &&
                        tick.decision.reasonCode != RefreshReason.POWER_SAVE_OR_LOW_BATTERY
                    ) {
                        return@collect
                    }
                    when (container.refreshRates.applyDecision(tick.decision)) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> Unit
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

    private data class AutomationTick(
        val automationEnabled: Boolean,
        val masterEnabled: Boolean,
        val decision: com.xtawa.samsunghzops.core.model.RefreshPolicyDecision,
        val powerSaveMode: Boolean,
        val keepHighRefresh: Boolean,
    )
}
