package com.xtawa.samsunghzops.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xtawa.samsunghzops.HzOpsApplication
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.RefreshRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Explicit Tasker/Locale contract. The signature permission keeps arbitrary
 * third-party apps from silently changing system refresh settings.
 */
class AutomationCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        val application = context.applicationContext as HzOpsApplication
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_SET_MODE -> {
                        val mode = intent.getStringExtra(EXTRA_MODE)
                            ?.let { runCatching { RefreshMode.valueOf(it.uppercase()) }.getOrNull() }
                        if (mode != null) {
                            val modes = application.container.refreshRates.snapshot.value.supportedModes
                            val values = modes.map { it.refreshRateHz }
                            if (values.isNotEmpty()) {
                                val range = when (mode) {
                                    RefreshMode.STANDARD -> {
                                        val value = values.minBy { kotlin.math.abs(it - 60f) }
                                        RefreshRange(value, value)
                                    }
                                    RefreshMode.ADAPTIVE -> RefreshRange(values.min(), values.max())
                                    RefreshMode.MAXIMUM -> RefreshRange(values.max(), values.max())
                                }
                                application.container.refreshRates.applyMode(mode, range, "Tasker/Locale")
                            }
                        }
                    }
                    ACTION_SET_RANGE -> {
                        val min = intent.getFloatExtra(EXTRA_MIN_HZ, Float.NaN)
                        val max = intent.getFloatExtra(EXTRA_MAX_HZ, Float.NaN)
                        if (min.isFinite() && max.isFinite() && min <= max) {
                            application.container.refreshRates.applyMode(
                                RefreshMode.ADAPTIVE,
                                RefreshRange(min, max),
                                "Tasker/Locale 自定义范围",
                            )
                        }
                    }
                    ACTION_RESET -> application.container.emergencyReset.resetAll()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_MODE = "com.xtawa.samsunghzops.action.SET_MODE"
        const val ACTION_SET_RANGE = "com.xtawa.samsunghzops.action.SET_RANGE"
        const val ACTION_RESET = "com.xtawa.samsunghzops.action.RESET"
        const val EXTRA_MODE = "mode"
        const val EXTRA_MIN_HZ = "min_hz"
        const val EXTRA_MAX_HZ = "max_hz"
    }
}
