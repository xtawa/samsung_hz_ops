package com.xtawa.samsunghzops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.xtawa.samsunghzops.ui.HzOpsApp
import com.xtawa.samsunghzops.ui.MainViewModel
import com.xtawa.samsunghzops.core.model.RefreshMode

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HzOpsApp(viewModel) }
        applyShortcutIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyShortcutIntent(intent)
    }

    private fun applyShortcutIntent(intent: android.content.Intent?) {
        intent?.getStringExtra("mode")
            ?.let { runCatching { RefreshMode.valueOf(it) }.getOrNull() }
            ?.let(viewModel::applyMode)
    }
}
