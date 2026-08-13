package com.xtawa.samsunghzops.data.settings

import android.content.Context
import android.provider.Settings
import com.xtawa.samsunghzops.core.model.Capability
import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingMutation
import com.xtawa.samsunghzops.core.model.SettingNamespace
import com.xtawa.samsunghzops.core.model.SettingSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The smallest interface the policy layer needs for a settings transaction. */
interface SettingsBackend {
    fun read(spec: SettingSpec): OperationResult<String?>

    suspend fun write(spec: SettingSpec, value: String?): OperationResult<Unit>

    fun canWrite(spec: SettingSpec): Boolean
}

/**
 * Optional bridge for settings that must be mutated as shell/root or by an
 * already privileged app process. Implementations may use Shizuku, root, or a
 * future companion process.
 */
interface PrivilegedSettingsWriter {
    fun isAvailable(): Boolean

    /** Whether this backend can mutate a specific Settings namespace. */
    fun canWrite(namespace: SettingNamespace): Boolean = isAvailable()

    fun statusSnapshot(): PrivilegedBackendStatus = PrivilegedBackendStatus()

    suspend fun put(namespace: SettingNamespace, key: String, value: String?): OperationResult<Unit>
}

class AndroidSettingsBackend(
    private val context: Context,
    private val privilegedWriter: PrivilegedSettingsWriter,
) : SettingsBackend {
    private val resolver = context.contentResolver

    override fun read(spec: SettingSpec): OperationResult<String?> = runCatching {
        val value = when (spec.namespace) {
            SettingNamespace.SYSTEM -> Settings.System.getString(resolver, spec.key)
            SettingNamespace.SECURE -> Settings.Secure.getString(resolver, spec.key)
            SettingNamespace.GLOBAL -> Settings.Global.getString(resolver, spec.key)
            // SYSFS is deliberately not guessed or parsed by the generic backend.
            SettingNamespace.SYSFS -> return OperationResult.Failure(
                "sysfs 读取必须由经过设备校准的后端提供",
                recoverable = false,
            )
        }
        OperationResult.Success(value)
    }.getOrElse { error ->
        OperationResult.Failure("读取 ${spec.key} 失败：${error.message ?: error.javaClass.simpleName}")
    }

    override fun canWrite(spec: SettingSpec): Boolean {
        if (spec.namespace == SettingNamespace.SYSFS) return false

        // Hidden System keys such as min_refresh_rate/peak_refresh_rate are
        // rejected for modern third-party UIDs even when WRITE_SETTINGS is
        // granted. Respect the registry flag and require the privileged bridge.
        if (spec.requiresPrivilegedWrite) {
            return privilegedWriter.canWrite(spec.namespace)
        }

        return when (spec.namespace) {
            SettingNamespace.SYSTEM ->
                Settings.System.canWrite(context) || privilegedWriter.canWrite(SettingNamespace.SYSTEM)
            SettingNamespace.SECURE,
            SettingNamespace.GLOBAL,
            -> privilegedWriter.canWrite(spec.namespace)
            SettingNamespace.SYSFS -> false
        }
    }

    override suspend fun write(spec: SettingSpec, value: String?): OperationResult<Unit> {
        if (!canWrite(spec)) {
            val capability = when {
                spec.namespace == SettingNamespace.SYSTEM && spec.requiresPrivilegedWrite -> Capability.SHIZUKU
                spec.namespace == SettingNamespace.SYSTEM -> Capability.WRITE_SYSTEM_SETTINGS
                spec.namespace == SettingNamespace.SECURE -> Capability.WRITE_SECURE_SETTINGS
                spec.namespace == SettingNamespace.GLOBAL -> Capability.WRITE_GLOBAL_SETTINGS
                else -> Capability.SHIZUKU
            }
            val hint = if (spec.namespace == SettingNamespace.SYSTEM && spec.requiresPrivilegedWrite) {
                "；该 System 私有键必须通过 Shizuku shell/root 身份写入"
            } else {
                ""
            }
            return OperationResult.Failure(
                "没有写入 ${spec.key} 所需的权限$hint",
                missingCapabilities = listOf(capability),
            )
        }

        // Never fall back to the app UID for a field explicitly marked as
        // privileged. That fallback is what caused SettingsProvider to throw
        // "You cannot keep your settings in the secure settings" for
        // min_refresh_rate on modern targetSdk builds.
        if (spec.requiresPrivilegedWrite) {
            return privilegedWriter.put(spec.namespace, spec.key, value)
        }

        return when (spec.namespace) {
            SettingNamespace.SYSTEM -> {
                if (Settings.System.canWrite(context)) {
                    writeSystemDirect(spec, value)
                } else {
                    privilegedWriter.put(spec.namespace, spec.key, value)
                }
            }

            SettingNamespace.SECURE,
            SettingNamespace.GLOBAL,
            -> privilegedWriter.put(spec.namespace, spec.key, value)

            SettingNamespace.SYSFS -> OperationResult.Failure(
                "sysfs 写入必须由经过设备校准的后端提供",
                recoverable = false,
            )
        }
    }

    private suspend fun writeSystemDirect(
        spec: SettingSpec,
        value: String?,
    ): OperationResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(Settings.System.putString(resolver, spec.key, value))
            OperationResult.Success(Unit)
        }.getOrElse { error ->
            val message = if (
                error is IllegalArgumentException &&
                error.message?.contains("secure settings", ignoreCase = true) == true
            ) {
                "系统拒绝普通应用写入 ${spec.key}；请启用 Shizuku 特权后端"
            } else {
                "写入 ${spec.key} 失败：${error.message ?: "系统拒绝"}"
            }
            OperationResult.Failure(message)
        }
    }

    suspend fun writeMutation(mutation: SettingMutation): OperationResult<Unit> =
        write(mutation.spec, mutation.value)
}
