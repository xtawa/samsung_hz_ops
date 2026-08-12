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
 * Optional bridge for Settings.Secure/Global writes. Implementations may use
 * Shizuku, root, or a future companion process. The app never assumes that a
 * privileged bridge exists.
 */
interface PrivilegedSettingsWriter {
    fun isAvailable(): Boolean

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

    override fun canWrite(spec: SettingSpec): Boolean = when (spec.namespace) {
        SettingNamespace.SYSTEM -> Settings.System.canWrite(context)
        SettingNamespace.SECURE, SettingNamespace.GLOBAL -> privilegedWriter.isAvailable()
        SettingNamespace.SYSFS -> false
    }

    override suspend fun write(spec: SettingSpec, value: String?): OperationResult<Unit> {
        if (!canWrite(spec)) {
            val capability = when (spec.namespace) {
                SettingNamespace.SYSTEM -> Capability.WRITE_SYSTEM_SETTINGS
                SettingNamespace.SECURE -> Capability.WRITE_SECURE_SETTINGS
                SettingNamespace.GLOBAL -> Capability.WRITE_GLOBAL_SETTINGS
                SettingNamespace.SYSFS -> Capability.SHIZUKU
            }
            return OperationResult.Failure(
                "没有写入 ${spec.key} 所需的权限",
                missingCapabilities = listOf(capability),
            )
        }

        return when (spec.namespace) {
            SettingNamespace.SYSTEM -> withContext(Dispatchers.IO) {
                runCatching {
                    check(Settings.System.putString(resolver, spec.key, value))
                    OperationResult.Success(Unit)
                }.getOrElse { error ->
                    OperationResult.Failure("写入 ${spec.key} 失败：${error.message ?: "系统拒绝"}")
                }
            }

            SettingNamespace.SECURE,
            SettingNamespace.GLOBAL,
            SettingNamespace.SYSFS,
            -> privilegedWriter.put(spec.namespace, spec.key, value)
        }
    }

    suspend fun writeMutation(mutation: SettingMutation): OperationResult<Unit> =
        write(mutation.spec, mutation.value)
}