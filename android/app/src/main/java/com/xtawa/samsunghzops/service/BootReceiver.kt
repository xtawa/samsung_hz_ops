package com.xtawa.samsunghzops.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.xtawa.samsunghzops.HzOpsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pendingResult = goAsync()
        val application = context.applicationContext as HzOpsApplication
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (application.container.preferences.automationEnabled.first()) {
                    ContextCompat.startForegroundService(
                        application,
                        Intent(application, AutomationService::class.java),
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
