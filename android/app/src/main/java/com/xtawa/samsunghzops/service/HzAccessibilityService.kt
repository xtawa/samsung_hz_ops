package com.xtawa.samsunghzops.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.app.KeyguardManager
import com.xtawa.samsunghzops.HzOpsApplication

/**
 * Receives only package/window event metadata. Window text and node content
 * are not requested, so Per-App rules do not need to inspect user content.
 */
class HzAccessibilityService : AccessibilityService() {
    private val container by lazy { (application as HzOpsApplication).container }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()?.takeIf { it.isNotBlank() }
        if (packageName != null) container.deviceState.setForegroundPackage(packageName)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        container.deviceState.setKeyguardShowing(keyguardManager?.isKeyguardLocked == true)
    }

    override fun onInterrupt() = Unit
}
