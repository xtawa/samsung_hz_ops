package com.xtawa.samsunghzops.data.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.media.MediaRouter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.xtawa.samsunghzops.core.state.DeviceStateRepository

/** Bridges platform signals into DeviceStateRepository without applying policy. */
class DeviceSignalMonitor(
    context: Context,
    private val state: DeviceStateRepository,
) {
    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val mediaRouter = appContext.getSystemService(MediaRouter::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val unavailableCameraIds = mutableSetOf<String>()

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            unavailableCameraIds += cameraId
            state.setCameraActive(unavailableCameraIds.isNotEmpty())
        }

        override fun onCameraAvailable(cameraId: String) {
            unavailableCameraIds -= cameraId
            state.setCameraActive(unavailableCameraIds.isNotEmpty())
        }
    }

    private val routeCallback = object : MediaRouter.SimpleCallback() {
        override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) {
            state.setCasting(true)
        }

        override fun onRouteUnselected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) {
            state.setCasting(false)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> state.setInteractive(true)
                Intent.ACTION_SCREEN_OFF -> state.setInteractive(false)
            }
        }
    }

    private val brightnessObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            val value = runCatching {
                Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            }.getOrNull()
            state.setBrightness(value?.div(255f))
        }
    }

    init {
        runCatching { cameraManager?.registerAvailabilityCallback(cameraCallback, mainHandler) }
        mediaRouter?.addCallback(
            MediaRouter.ROUTE_TYPE_LIVE_VIDEO,
            routeCallback,
        )
        ContextCompat.registerReceiver(
            appContext,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        appContext.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            brightnessObserver,
        )
        brightnessObserver.onChange(false)
    }

    fun close() {
        cameraManager?.unregisterAvailabilityCallback(cameraCallback)
        mediaRouter?.removeCallback(routeCallback)
        runCatching { appContext.unregisterReceiver(screenReceiver) }
        appContext.contentResolver.unregisterContentObserver(brightnessObserver)
    }
}
