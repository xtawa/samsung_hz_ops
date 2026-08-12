package com.xtawa.samsunghzops.data.capability

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.xtawa.samsunghzops.core.model.Capability
import com.xtawa.samsunghzops.core.model.CapabilityState
import com.xtawa.samsunghzops.core.model.CapabilityStatus
import com.xtawa.samsunghzops.data.settings.PrivilegedSettingsWriter

class CapabilityProbe(
    private val context: Context,
    private val privilegedWriter: PrivilegedSettingsWriter,
) {
    fun snapshot(): List<CapabilityStatus> = listOf(
        status(
            Capability.READ_DISPLAY,
            CapabilityState.GRANTED,
            "可读取屏幕支持的刷新率，不修改系统设置",
        ),
        status(
            Capability.WRITE_SYSTEM_SETTINGS,
            if (Settings.System.canWrite(context)) CapabilityState.GRANTED
            else CapabilityState.USER_ACTION_REQUIRED,
            if (Settings.System.canWrite(context)) "可以写入 min/peak_refresh_rate"
            else "需要在系统设置中允许修改系统设置",
            actionLabel = if (Settings.System.canWrite(context)) null else "去授权",
        ),
        status(
            Capability.WRITE_SECURE_SETTINGS,
            if (privilegedWriter.isAvailable()) CapabilityState.GRANTED
            else CapabilityState.USER_ACTION_REQUIRED,
            if (privilegedWriter.isAvailable()) "已连接特权设置后端"
            else "需要 Shizuku 或受信任的特权伴侣才能修改 Secure 设置",
            actionLabel = if (privilegedWriter.isAvailable()) null else "查看权限",
        ),
        status(
            Capability.WRITE_GLOBAL_SETTINGS,
            if (privilegedWriter.isAvailable()) CapabilityState.GRANTED
            else CapabilityState.USER_ACTION_REQUIRED,
            if (privilegedWriter.isAvailable()) "已连接特权设置后端"
            else "需要 Shizuku 或受信任的特权伴侣才能修改 Global 设置",
            actionLabel = if (privilegedWriter.isAvailable()) null else "查看权限",
        ),
        status(
            Capability.SHIZUKU,
            if (privilegedWriter.isAvailable()) CapabilityState.GRANTED
            else CapabilityState.USER_ACTION_REQUIRED,
            if (privilegedWriter.isAvailable()) "Shizuku 已授权"
            else "用于 Secure/Global 设置的可撤销授权",
            actionLabel = if (privilegedWriter.isAvailable()) null else "打开 Shizuku",
        ),
        status(
            Capability.ACCESSIBILITY,
            if (isAccessibilityEnabled()) CapabilityState.GRANTED
            else CapabilityState.USER_ACTION_REQUIRED,
            if (isAccessibilityEnabled()) "可接收前台应用与交互事件"
            else "Adaptive/Per-App 规则需要辅助功能事件",
            actionLabel = if (isAccessibilityEnabled()) null else "去开启",
        ),
        status(
            Capability.OVERLAY,
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)) {
                CapabilityState.GRANTED
            } else {
                CapabilityState.USER_ACTION_REQUIRED
            },
            "仅网络速度浮层等可选功能需要",
            actionLabel = if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)) null else "去授权",
        ),
        status(
            Capability.NOTIFICATIONS,
            if (areNotificationsEnabled()) CapabilityState.GRANTED
            else CapabilityState.USER_ACTION_REQUIRED,
            "用于前台服务状态、失败恢复和自动化通知",
            actionLabel = if (areNotificationsEnabled()) null else "去开启",
        ),
        status(
            Capability.IGNORE_BATTERY_OPTIMIZATIONS,
            if (isIgnoringBatteryOptimizations()) CapabilityState.GRANTED
            else CapabilityState.USER_ACTION_REQUIRED,
            "可选；避免自动化监控被系统休眠",
            actionLabel = if (isIgnoringBatteryOptimizations()) null else "去设置",
        ),
        status(
            Capability.BATTERY_MANAGER,
            CapabilityState.GRANTED,
            "PowerManager 可读取省电状态",
        ),
    )

    private fun status(
        capability: Capability,
        state: CapabilityState,
        explanation: String,
        actionLabel: String? = null,
    ) = CapabilityStatus(capability, state, explanation, actionLabel)

    private fun isAccessibilityEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(context, "${context.packageName}.service.HzAccessibilityService")
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
            }
    }

    private fun areNotificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 23) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }
}
