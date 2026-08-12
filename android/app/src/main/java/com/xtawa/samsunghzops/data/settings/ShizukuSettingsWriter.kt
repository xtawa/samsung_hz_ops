package com.xtawa.samsunghzops.data.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingNamespace
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class PrivilegedBackendStatus(
    val binderAlive: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val writeSecureSettingsGranted: Boolean = false,
    val lastMessage: String? = null,
) {
    val canUseShizukuShell: Boolean get() = binderAlive && shizukuPermissionGranted
    val canWriteSecureOrGlobal: Boolean get() = writeSecureSettingsGranted || canUseShizukuShell
}

/**
 * Settings CLI bridge used when the user explicitly grants Shizuku access.
 * It intentionally accepts only the namespace/key/value supplied by the
 * registry and shell-quotes all user-controlled text.
 */
class ShizukuSettingsWriter(
    context: Context,
) : PrivilegedSettingsWriter {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingGrantPackage = AtomicReference<String?>(null)
    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<PrivilegedBackendStatus> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus("Shizuku 已连接")
        pendingGrantPackage.get()?.let { packageName ->
            scope.launch { ensureWriteSecureSettingsGranted(packageName) }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshStatus("Shizuku 服务已断开")
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                refreshStatus("Shizuku 授权已允许")
                pendingGrantPackage.get()?.let { packageName ->
                    scope.launch { ensureWriteSecureSettingsGranted(packageName) }
                }
            } else {
                pendingGrantPackage.set(null)
                refreshStatus("Shizuku 授权被拒绝")
            }
        }
    }

    init {
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
        runCatching { Shizuku.addRequestPermissionResultListener(requestPermissionResultListener) }
    }

    override fun isAvailable(): Boolean = readStatus().canWriteSecureOrGlobal

    override fun statusSnapshot(): PrivilegedBackendStatus = readStatus()

    suspend fun ensureWriteSecureSettingsGranted(packageName: String): OperationResult<Unit> {
        refreshStatus()
        val current = readStatus()
        if (current.writeSecureSettingsGranted) {
            pendingGrantPackage.set(null)
            return OperationResult.Success(Unit, listOf("安全设置权限已就绪"))
        }
        if (!current.binderAlive) {
            pendingGrantPackage.set(packageName)
            return OperationResult.Failure("Shizuku 未运行；请先打开 Shizuku 并启动服务")
        }
        if (!current.shizukuPermissionGranted) {
            pendingGrantPackage.set(packageName)
            val requested = requestShizukuPermission()
            return when (requested) {
                is OperationResult.Success -> OperationResult.Success(Unit, listOf("已请求 Shizuku 授权，请在弹窗中允许"))
                is OperationResult.Failure -> requested
            }
        }

        pendingGrantPackage.set(packageName)
        val command = "pm grant ${shellQuote(packageName)} android.permission.WRITE_SECURE_SETTINGS"
        return when (val result = runShell(command)) {
            is OperationResult.Success -> {
                refreshStatus("已通过 Shizuku 授予安全设置权限")
                pendingGrantPackage.set(null)
                if (hasWriteSecureSettingsPermission()) {
                    OperationResult.Success(Unit, listOf("安全设置权限已授予"))
                } else {
                    OperationResult.Failure("pm grant 已执行，但系统未确认 WRITE_SECURE_SETTINGS")
                }
            }

            is OperationResult.Failure -> {
                refreshStatus(result.message)
                result
            }
        }
    }

    override suspend fun put(
        namespace: SettingNamespace,
        key: String,
        value: String?,
    ): OperationResult<Unit> {
        if (namespace != SettingNamespace.SECURE && namespace != SettingNamespace.GLOBAL) {
            return OperationResult.Failure("Shizuku 仅用于 Secure/Global 设置")
        }
        if (!isAvailable()) return OperationResult.Failure("未获得安全设置权限；请先完成 Shizuku 授权")

        if (hasWriteSecureSettingsPermission()) {
            return withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = appContext.contentResolver
                    val ok = when (namespace) {
                        SettingNamespace.SECURE -> Settings.Secure.putString(resolver, key, value)
                        SettingNamespace.GLOBAL -> Settings.Global.putString(resolver, key, value)
                        else -> false
                    }
                    if (ok) OperationResult.Success(Unit)
                    else OperationResult.Failure("系统拒绝写入 $key")
                }.getOrElse { error ->
                    OperationResult.Failure("写入 $key 失败：${error.message ?: error.javaClass.simpleName}")
                }
            }
        }

        val table = when (namespace) {
            SettingNamespace.SECURE -> "secure"
            SettingNamespace.GLOBAL -> "global"
            else -> error("validated above")
        }
        val command = if (value == null) {
            "settings delete $table ${shellQuote(key)}"
        } else {
            "settings put $table ${shellQuote(key)} ${shellQuote(value)}"
        }

        return runShell(command)
    }

    private fun requestShizukuPermission(): OperationResult<Unit> = runCatching {
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
        refreshStatus("已请求 Shizuku 授权")
        OperationResult.Success(Unit)
    }.getOrElse { error ->
        OperationResult.Failure("请求 Shizuku 授权失败：${error.message ?: error.javaClass.simpleName}")
    }

    private suspend fun runShell(command: String): OperationResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Shizuku 13 exposes this bridge with different visibility
            // across API artifacts. Resolve it only after the user has
            // granted Shizuku; an unavailable method is a safe failure.
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as? Process
                ?: return@runCatching OperationResult.Failure("Shizuku newProcess 不可用")
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                OperationResult.Failure("Shizuku 命令失败（exit=$exitCode）")
            } else {
                OperationResult.Success(Unit)
            }
        }.getOrElse { error ->
            OperationResult.Failure("Shizuku 执行失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun readStatus(message: String? = null): PrivilegedBackendStatus {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuPermissionGranted = if (binderAlive) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        } else {
            false
        }
        return PrivilegedBackendStatus(
            binderAlive = binderAlive,
            shizukuPermissionGranted = shizukuPermissionGranted,
            writeSecureSettingsGranted = hasWriteSecureSettingsPermission(),
            lastMessage = message,
        )
    }

    private fun refreshStatus(message: String? = _status.value.lastMessage) {
        _status.value = readStatus(message ?: _status.value.lastMessage)
    }

    private fun hasWriteSecureSettingsPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val REQUEST_CODE_SHIZUKU_PERMISSION = 4201
    }
}