package com.xtawa.samsunghzops.data.settings

import com.xtawa.samsunghzops.core.model.SettingNamespace
import com.xtawa.samsunghzops.core.model.SettingSpec

/**
 * Central registry for every key referenced by the product specification.
 *
 * Keeping keys here prevents UI code from accidentally writing a guessed OEM
 * value. A backend must still prove the capability and read the old value
 * before applying a mutation.
 */
object SettingsFieldRegistry {
    val minRefreshRate = SettingSpec(
        SettingNamespace.SYSTEM,
        "min_refresh_rate",
        "系统允许的最低刷新率",
        requiresPrivilegedWrite = false,
    )
    val peakRefreshRate = SettingSpec(
        SettingNamespace.SYSTEM,
        "peak_refresh_rate",
        "系统允许的最高刷新率",
        requiresPrivilegedWrite = false,
    )
    val refreshRateMode = SettingSpec(
        SettingNamespace.SECURE,
        "refresh_rate_mode",
        "三星刷新率模式（主屏）",
        requiresPrivilegedWrite = true,
    )
    val refreshRateModeCover = SettingSpec(
        SettingNamespace.SECURE,
        "refresh_rate_mode_cover",
        "三星刷新率模式（外屏）",
        requiresPrivilegedWrite = true,
    )
    val userRefreshRate = SettingSpec(
        SettingNamespace.SYSTEM,
        "user_refresh_rate",
        "三星用户刷新率档位",
        requiresPrivilegedWrite = true,
    )

    val psmRefreshRateEnabled = SettingSpec(
        SettingNamespace.GLOBAL,
        "pms_settings_refresh_rate_enabled",
        "三星省电模式刷新率开关",
        requiresPrivilegedWrite = true,
    )
    val psmRefreshRateTag = SettingSpec(
        SettingNamespace.GLOBAL,
        "psm_refresh_rate_tag",
        "三星省电模式刷新率标签",
        requiresPrivilegedWrite = true,
    )
    val psmRefreshRateEnabledCover = SettingSpec(
        SettingNamespace.GLOBAL,
        "pms_settings_refresh_rate_cover_enabled",
        "三星省电模式外屏刷新率开关",
        requiresPrivilegedWrite = true,
    )
    val psmRefreshRateTagCover = SettingSpec(
        SettingNamespace.GLOBAL,
        "psm_refresh_rate_cover_tag",
        "三星省电模式外屏刷新率标签",
        requiresPrivilegedWrite = true,
    )
    val restrictedDevicePerformance = SettingSpec(
        SettingNamespace.GLOBAL,
        "restricted_device_performance",
        "系统性能限制状态",
        requiresPrivilegedWrite = true,
    )
    val lowPower = SettingSpec(
        SettingNamespace.GLOBAL,
        "low_power",
        "省电模式状态",
        requiresPrivilegedWrite = true,
    )

    val aodMode = SettingSpec(
        SettingNamespace.SECURE,
        "aod_mode",
        "息屏显示模式",
        requiresPrivilegedWrite = true,
    )
    val deviceIdleConstants = SettingSpec(
        SettingNamespace.GLOBAL,
        "device_idle_constants",
        "Doze 参数",
        requiresPrivilegedWrite = true,
    )
    val windowAnimationScale = SettingSpec(
        SettingNamespace.GLOBAL,
        "window_animation_scale",
        "窗口动画倍率",
        requiresPrivilegedWrite = true,
    )
    val transitionAnimationScale = SettingSpec(
        SettingNamespace.GLOBAL,
        "transition_animation_scale",
        "转场动画倍率",
        requiresPrivilegedWrite = true,
    )
    val animatorDurationScale = SettingSpec(
        SettingNamespace.GLOBAL,
        "animator_duration_scale",
        "Animator 动画倍率",
        requiresPrivilegedWrite = true,
    )
    val forceResizableActivities = SettingSpec(
        SettingNamespace.GLOBAL,
        "force_resizable_activities",
        "强制应用可调整大小",
        requiresPrivilegedWrite = true,
    )
    val qsTiles = SettingSpec(
        SettingNamespace.SECURE,
        "sysui_qs_tiles",
        "快捷设置磁贴列表",
        requiresPrivilegedWrite = true,
    )
    val networkSpeed = SettingSpec(
        SettingNamespace.SYSTEM,
        "network_speed",
        "状态栏网速开关",
        requiresPrivilegedWrite = true,
    )
    val sensorsOff = SettingSpec(
        SettingNamespace.SECURE,
        "sensors_off",
        "传感器隐私开关",
        requiresPrivilegedWrite = true,
    )

    val all: List<SettingSpec> = listOf(
        minRefreshRate,
        peakRefreshRate,
        refreshRateMode,
        refreshRateModeCover,
        userRefreshRate,
        psmRefreshRateEnabled,
        psmRefreshRateTag,
        psmRefreshRateEnabledCover,
        psmRefreshRateTagCover,
        restrictedDevicePerformance,
        lowPower,
        aodMode,
        deviceIdleConstants,
        windowAnimationScale,
        transitionAnimationScale,
        animatorDurationScale,
        forceResizableActivities,
        qsTiles,
        networkSpeed,
        sensorsOff,
    )
}
