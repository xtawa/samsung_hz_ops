package com.xtawa.samsunghzops.core.state

import com.xtawa.samsunghzops.core.model.DeviceState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared event sink for accessibility, display, camera and cast observers. */
class DeviceStateRepository {
    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    fun update(transform: (DeviceState) -> DeviceState) {
        _state.value = transform(_state.value).copy(eventTimestamp = Instant.now())
    }

    fun setForegroundPackage(packageName: String?) = update {
        it.copy(foregroundPackage = packageName)
    }

    fun setInteractive(interactive: Boolean) = update {
        it.copy(isInteractive = interactive)
    }

    fun setKeyguardShowing(showing: Boolean) = update {
        it.copy(isKeyguardShowing = showing)
    }

    fun setCameraActive(active: Boolean) = update {
        it.copy(isCameraActive = active)
    }

    fun setCasting(casting: Boolean) = update {
        it.copy(isCasting = casting)
    }

    fun setBrightness(fraction: Float?) = update {
        it.copy(brightnessFraction = fraction?.coerceIn(0f, 1f))
    }

    fun setFolded(folded: Boolean) = update {
        it.copy(isFolded = folded)
    }
}
