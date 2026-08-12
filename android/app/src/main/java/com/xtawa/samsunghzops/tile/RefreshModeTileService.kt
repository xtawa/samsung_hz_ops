package com.xtawa.samsunghzops.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xtawa.samsunghzops.HzOpsApplication
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.RefreshRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Quick Settings tile that cycles the three core modes. */
class RefreshModeTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private val container by lazy { (application as HzOpsApplication).container }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val current = container.refreshRates.snapshot.value.activeMode
        val next = when (current) {
            RefreshMode.STANDARD -> RefreshMode.ADAPTIVE
            RefreshMode.ADAPTIVE -> RefreshMode.MAXIMUM
            RefreshMode.MAXIMUM -> RefreshMode.STANDARD
        }
        job?.cancel()
        job = scope.launch {
            val modes = container.refreshRates.snapshot.value.supportedModes
            val values = modes.map { it.refreshRateHz }
            if (values.isEmpty()) return@launch
            val range = when (next) {
                RefreshMode.STANDARD -> {
                    val value = values.minBy { kotlin.math.abs(it - 60f) }
                    RefreshRange(value, value)
                }
                RefreshMode.ADAPTIVE -> RefreshRange(values.min(), values.max())
                RefreshMode.MAXIMUM -> RefreshRange(values.max(), values.max())
            }
            container.refreshRates.applyMode(next, range, "快捷设置磁贴")
            updateTile()
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val mode = container.refreshRates.snapshot.value.activeMode
        tile.label = when (mode) {
            RefreshMode.STANDARD -> "标准 60"
            RefreshMode.ADAPTIVE -> "自适应"
            RefreshMode.MAXIMUM -> "最高刷新"
        }
        tile.state = Tile.STATE_ACTIVE
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_menu_manage)
        tile.updateTile()
    }
}
