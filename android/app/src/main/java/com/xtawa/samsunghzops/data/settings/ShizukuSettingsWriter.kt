package com.xtawa.samsunghzops.data.settings

import android.content.pm.PackageManager
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingNamespace
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings CLI bridge used when the user explicitly grants Shizuku access.
 * It intentionally accepts only the namespace/key/value supplied by the
 * registry and shell-quotes all user-controlled text.
 */
class ShizukuSettingsWriter : PrivilegedSettingsWriter {
    override fun isAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override suspend fun put(
        namespace: SettingNamespace,
        key: String,
        value: String?,
    ): OperationResult<Unit> {
        if (namespace != SettingNamespace.SECURE && namespace != SettingNamespace.GLOBAL) {
            return OperationResult.Failure("Shizuku 仅用于 Secure/Global 设置")
        }
        if (!isAvailable()) {
            return OperationResult.Failure("Shizuku 未连接或未授权")
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

        return withContext(Dispatchers.IO) {
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
                    OperationResult.Failure("settings 命令失败（exit=$exitCode）")
                } else {
                    OperationResult.Success(Unit)
                }
            }.getOrElse { error ->
                OperationResult.Failure("Shizuku 写入失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
