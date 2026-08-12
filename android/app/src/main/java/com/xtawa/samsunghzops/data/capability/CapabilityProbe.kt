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
    fun snapshot(): List<CapabilityStatus> {
        val privilege = privilegedWriter.statusSnapshot()
        val privilegedExplanation = when {
            privilege.writeSecureSettingsGranted -> "已获得 WRITE_SECURE_SETTINGS，可直接写入 Secure/Global 设置"
            !privilege.binderAlive -> "Shizuku 未运行；请先启动 Shizuku 服务"
            !privilege.shizukuPermissionGranted -> "Shizuku 已连接；需要授权 Samsung Hz Ops"
            else -> "Shizuku 已授权；点击授予 WRITE_SECURE_SETTINGS"
        }
        val privilegedAction = when {
            privilege.writeSecureSettingsGranted -> null
            !privilege.binderAlive -> "打开 Shizuku"
            !privilege.shizukuPermissionGranted -> "授权"
            else -> "授予安全设置"
        }
        return listOf(
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
                if (privilege.writeSecureSettingsGranted) CapabilityState.GRANTED
                else CapabilityState.USER_ACTION_REQUIRED,
                privilegedExplanation,
                actionLabel = privilegedAction,
            ),
            status(
                Capability.WRITE_GLOBAL_SETTINGS,
                if (privilege.canWriteSecureOrGlobal) CapabilityState.GRANTED
                else CapabilityState.USER_ACTION_REQUIRED,
                if (privilege.canWriteSecureOrGlobal) "可以写入 Global 设置"
                else privilegedExplanation,
                actionLabel = if (privilege.canWriteSecureOrGlobal) null else privilegedAction,
            ),
            status(
                Capability.SHIZUKU,
                if (privilege.canUseShizukuShell) CapabilityState.GRANTED
                else CapabilityState.USER_ACTION_REQUIRED,
                when {
                    privilege.canUseShizukuShell -> "Shizuku 已连接并授权"
                    !privilege.binderAlive -> "未检测到 Shizuku binder"
                    else -> "Shizuku 已连接，等待 App 授权"
                },
                actionLabel = if (privilege.canUseShizukuShell) null else privilegedAction,
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
    }

    private fun status(
        capability: Capability,
        state: CapabilityState,
        explanation: String,
        actionLabel: String? = null,
    ) = CapabilityStatus(capability, state, explanation, actionLabel)

    private fun isAccessibilityEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(
            context.packageName,
            "${context.packageName}.service.HzAccessibilityService",
        )
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