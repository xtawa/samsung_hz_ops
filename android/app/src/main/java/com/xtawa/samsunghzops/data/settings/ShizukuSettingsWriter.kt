package com.xtawa.samsunghzops.data.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingNamespace
import java.util.concurrent.TimeUnit
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
    val shizukuInstalled: Boolean = false,
    val shizukuVersionName: String? = null,
    val binderAlive: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val shizukuUid: Int? = null,
    val writeSecureSettingsGranted: Boolean = false,
    val lastMessage: String? = null,
) {
    val canUseShizukuShell: Boolean get() = binderAlive && shizukuPermissionGranted
    val canWriteSecureOrGlobal: Boolean get() = writeSecureSettingsGranted || canUseShizukuShell
}

/**
 * Privileged Settings bridge.
 *
 * Hidden System keys (including min_refresh_rate/peak_refresh_rate) must be
 * executed as Shizuku's shell/root identity on modern Android. Secure/Global
 * keys prefer the same shell path, with direct WRITE_SECURE_SETTINGS only as a
 * fallback when Shizuku is unavailable.
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

    override fun isAvailable(): Boolean {
        val current = readStatus()
        return current.canUseShizukuShell || current.writeSecureSettingsGranted
    }

    override fun canWrite(namespace: SettingNamespace): Boolean {
        val current = readStatus()
        return when (namespace) {
            // Restricted Settings.System keys must originate from shell/root.
            SettingNamespace.SYSTEM -> current.canUseShizukuShell
            SettingNamespace.SECURE,
            SettingNamespace.GLOBAL,
            -> current.canWriteSecureOrGlobal
            SettingNamespace.SYSFS -> false
        }
    }

    override fun statusSnapshot(): PrivilegedBackendStatus = readStatus()

    suspend fun ensureWriteSecureSettingsGranted(packageName: String): OperationResult<Unit> {
        refreshStatus()
        val current = readStatus()
        if (current.writeSecureSettingsGranted) {
            pendingGrantPackage.set(null)
            // If Shizuku is alive, the shell path is already the preferred
            // backend for all managed keys. The direct probe remains useful as
            // a fallback validation only when shell is unavailable.
            return if (current.canUseShizukuShell) {
                OperationResult.Success(Unit, listOf("Shizuku shell 与安全设置权限已就绪"))
            } else {
                when (val validation = validateDirectSettingsWrite()) {
                    is OperationResult.Success -> OperationResult.Success(Unit, listOf("安全设置权限已就绪"))
                    is OperationResult.Failure -> validation
                }
            }
        }
        if (!current.shizukuInstalled) {
            pendingGrantPackage.set(null)
            return OperationResult.Failure("未安装 Shizuku；请先安装 Shizuku 并启动服务")
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
        return when (
            val result = runShizukuCommand(
                "授予 WRITE_SECURE_SETTINGS",
                "pm",
                "grant",
                packageName,
                Manifest.permission.WRITE_SECURE_SETTINGS,
            )
        ) {
            is OperationResult.Success -> {
                refreshStatus("已通过 Shizuku 授予安全设置权限")
                pendingGrantPackage.set(null)
                if (hasWriteSecureSettingsPermission()) {
                    OperationResult.Success(Unit, listOf("Shizuku shell 与安全设置权限已就绪"))
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
        if (namespace == SettingNamespace.SYSFS) {
            return OperationResult.Failure("通用 Settings 后端不处理 sysfs")
        }

        val current = readStatus()
        if (!canWrite(namespace)) {
            return when (namespace) {
                SettingNamespace.SYSTEM -> OperationResult.Failure(
                    "无法写入 $key：该 System 私有设置必须通过已授权的 Shizuku shell/root 身份写入",
                )
                SettingNamespace.SECURE,
                SettingNamespace.GLOBAL,
                -> OperationResult.Failure("未获得安全设置权限；请先完成 Shizuku 授权")
                SettingNamespace.SYSFS -> OperationResult.Failure("通用 Settings 后端不处理 sysfs")
            }
        }

        // Critical: prefer shell/root identity even when the app has already
        // been granted WRITE_SECURE_SETTINGS. Direct app-UID writes to hidden
        // Settings.System keys are rejected on modern targetSdk versions.
        if (current.canUseShizukuShell) {
            val table = when (namespace) {
                SettingNamespace.SYSTEM -> "system"
                SettingNamespace.SECURE -> "secure"
                SettingNamespace.GLOBAL -> "global"
                SettingNamespace.SYSFS -> error("validated above")
            }
            return if (value == null) {
                runShizukuCommand("删除 $key", "settings", "delete", table, key)
            } else {
                runShizukuCommand("写入 $key", "settings", "put", table, key, value)
            }
        }

        // Fallback for devices where WRITE_SECURE_SETTINGS was granted by ADB
        // but Shizuku is not currently running. This fallback intentionally
        // excludes Settings.System restricted keys.
        if (
            (namespace == SettingNamespace.SECURE || namespace == SettingNamespace.GLOBAL) &&
            hasWriteSecureSettingsPermission()
        ) {
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

        return OperationResult.Failure("没有可用的特权后端写入 $key")
    }

    private fun requestShizukuPermission(): OperationResult<Unit> = runCatching {
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
        refreshStatus("已请求 Shizuku 授权")
        OperationResult.Success(Unit)
    }.getOrElse { error ->
        OperationResult.Failure("请求 Shizuku 授权失败：${error.message ?: error.javaClass.simpleName}")
    }

    private suspend fun runShizukuCommand(label: String, vararg args: String): OperationResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Shizuku 13 exposes this legacy process bridge with different
            // visibility across API artifacts. Resolve it reflectively after
            // permission is granted. All command arguments remain fixed arrays
            // rather than interpolated shell strings.
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
            val process = method.invoke(null, args.toList().toTypedArray(), null, null) as? Process
                ?: return@runCatching OperationResult.Failure("Shizuku shell 进程不可用")
            val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return@runCatching OperationResult.Failure("Shizuku 命令超时：$label")
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val stderr = process.errorStream.bufferedReader().readText().trimForUser()
                val stdout = process.inputStream.bufferedReader().readText().trimForUser()
                OperationResult.Failure(
                    buildString {
                        append("Shizuku 命令失败：$label（exit=$exitCode）")
                        if (stderr.isNotBlank()) append("；$stderr")
                        else if (stdout.isNotBlank()) append("；$stdout")
                    },
                )
            } else {
                OperationResult.Success(Unit)
            }
        }.getOrElse { error ->
            OperationResult.Failure("Shizuku 执行失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun readStatus(message: String? = null): PrivilegedBackendStatus {
        val shizukuPackage = shizukuPackageInfo()
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuPermissionGranted = if (binderAlive) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        } else {
            false
        }
        val shizukuUid = if (binderAlive && shizukuPermissionGranted) {
            runCatching { Shizuku.getUid() }.getOrNull()
        } else {
            null
        }
        return PrivilegedBackendStatus(
            shizukuInstalled = shizukuPackage != null,
            shizukuVersionName = shizukuPackage?.versionName,
            binderAlive = binderAlive,
            shizukuPermissionGranted = shizukuPermissionGranted,
            shizukuUid = shizukuUid,
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

    private fun shizukuPackageInfo() = runCatching {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
    }.getOrNull()

    private fun validateDirectSettingsWrite(): OperationResult<Unit> {
        if (!hasWriteSecureSettingsPermission()) {
            return OperationResult.Failure("系统未确认 WRITE_SECURE_SETTINGS")
        }
        return runCatching {
            val resolver = appContext.contentResolver
            val previous = Settings.Global.getString(resolver, VALIDATION_KEY)
            val probeValue = "probe:${System.currentTimeMillis()}"
            if (!Settings.Global.putString(resolver, VALIDATION_KEY, probeValue)) {
                return OperationResult.Failure("WRITE_SECURE_SETTINGS 已授予，但 Settings 写入测试被系统拒绝")
            }
            val readBack = Settings.Global.getString(resolver, VALIDATION_KEY)
            Settings.Global.putString(resolver, VALIDATION_KEY, previous)
            val restoredValue = Settings.Global.getString(resolver, VALIDATION_KEY)
            if (readBack != probeValue) {
                OperationResult.Failure("WRITE_SECURE_SETTINGS 写入测试读回不一致")
            } else if (restoredValue != previous) {
                OperationResult.Failure("WRITE_SECURE_SETTINGS 写入测试成功，但清理测试键失败")
            } else {
                OperationResult.Success(Unit)
            }
        }.getOrElse { error ->
            OperationResult.Failure("WRITE_SECURE_SETTINGS 写入测试失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun String.trimForUser(limit: Int = 180): String {
        val normalized = replace('\n', ' ').trim()
        return if (normalized.length <= limit) normalized else normalized.take(limit) + "…"
    }

    private companion object {
        const val REQUEST_CODE_SHIZUKU_PERMISSION = 4201
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val COMMAND_TIMEOUT_SECONDS = 15L
        const val VALIDATION_KEY = "samsung_hz_ops_privileged_write_probe"
    }
}
