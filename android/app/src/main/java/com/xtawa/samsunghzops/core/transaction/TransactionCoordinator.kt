package com.xtawa.samsunghzops.core.transaction

import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingMutation
import com.xtawa.samsunghzops.core.model.SettingSpec
import com.xtawa.samsunghzops.core.model.TransactionRecord
import com.xtawa.samsunghzops.data.settings.SettingsBackend
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies multi-key settings changes as an all-or-rollback transaction. The
 * The coordinator keeps a bounded in-memory stream for the live UI and can
 * mirror each completed record to a Room-backed journal sink.
 */
class TransactionCoordinator(
    private val backend: SettingsBackend,
    private val journalSink: suspend (TransactionRecord) -> Unit = {},
    private val managedSnapshotSink: suspend (SettingSpec, String?) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private val _journal = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val journal: StateFlow<List<TransactionRecord>> = _journal.asStateFlow()

    suspend fun apply(
        operation: String,
        requestedMutations: List<SettingMutation>,
    ): OperationResult<Unit> = mutex.withLock {
        if (requestedMutations.isEmpty()) return@withLock OperationResult.Success(Unit)

        val id = UUID.randomUUID().toString()
        val startedAt = Instant.now()
        val prepared = mutableListOf<SettingMutation>()
        for (requested in requestedMutations) {
            when (val read = backend.read(requested.spec)) {
                is OperationResult.Success -> prepared += requested.copy(previousValue = read.value)
                is OperationResult.Failure -> {
                    val record = TransactionRecord(
                        id = id,
                        startedAt = startedAt,
                        completedAt = Instant.now(),
                        operation = operation,
                        mutations = requestedMutations,
                        error = read.message,
                    )
                    append(record)
                    return@withLock OperationResult.Failure(
                        message = read.message,
                        recoverable = read.recoverable,
                        missingCapabilities = read.missingCapabilities,
                    )
                }
            }
        }

        prepared.firstOrNull { mutation -> !backend.canWrite(mutation.spec) }?.let { blocked ->
            val message = "没有写入 ${blocked.spec.key} 所需的权限"
            append(
                TransactionRecord(
                    id = id,
                    startedAt = startedAt,
                    completedAt = Instant.now(),
                    operation = operation,
                    mutations = prepared,
                    error = message,
                ),
            )
            return@withLock OperationResult.Failure(message)
        }

        for (mutation in prepared) {
            try {
                managedSnapshotSink(mutation.spec, mutation.previousValue)
            } catch (error: Throwable) {
                val message = "无法保存 ${mutation.spec.key} 的恢复快照：${error.message ?: error.javaClass.simpleName}"
                append(
                    TransactionRecord(
                        id = id,
                        startedAt = startedAt,
                        completedAt = Instant.now(),
                        operation = operation,
                        mutations = prepared,
                        error = message,
                    ),
                )
                return@withLock OperationResult.Failure(message)
            }
        }

        val applied = mutableListOf<SettingMutation>()
        for (mutation in prepared) {
            when (val write = writeAndVerify(mutation)) {
                is OperationResult.Success -> applied += mutation
                is OperationResult.Failure -> {
                    val rollbackWarnings = rollback(applied + mutation)
                    val message = buildString {
                        append(write.message)
                        if (rollbackWarnings.isNotEmpty()) {
                            append("；回滚警告：")
                            append(rollbackWarnings.joinToString("、"))
                        }
                    }
                    append(
                        TransactionRecord(
                            id = id,
                            startedAt = startedAt,
                            completedAt = Instant.now(),
                            operation = operation,
                            mutations = prepared,
                            rollbackAttempted = applied.isNotEmpty(),
                            error = message,
                        ),
                    )
                    return@withLock write.copy(message = message)
                }
            }
        }

        append(
            TransactionRecord(
                id = id,
                startedAt = startedAt,
                completedAt = Instant.now(),
                operation = operation,
                mutations = prepared,
                committed = true,
            ),
        )
        OperationResult.Success(Unit)
    }

    suspend fun reset(
        operation: String,
        specs: List<SettingSpec>,
    ): OperationResult<Unit> = apply(
        operation = operation,
        requestedMutations = specs.map { spec ->
            // A null value maps to the platform's "unset" behavior. The
            // transaction still reads the old value and can roll it back.
            SettingMutation(spec = spec, value = null)
        },
    )

    private suspend fun rollback(applied: List<SettingMutation>): List<String> {
        val warnings = mutableListOf<String>()
        for (mutation in applied.asReversed()) {
            when (val result = writeAndVerify(mutation.copy(value = mutation.previousValue))) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> warnings += "${mutation.spec.key}: ${result.message}"
            }
        }
        return warnings
    }

    private suspend fun writeAndVerify(mutation: SettingMutation): OperationResult<Unit> {
        var lastFailure: OperationResult.Failure? = null
        repeat(MAX_WRITE_ATTEMPTS) { attempt ->
            when (val write = backend.write(mutation.spec, mutation.value)) {
                is OperationResult.Failure -> {
                    lastFailure = write
                    return@repeat
                }

                is OperationResult.Success -> {
                    when (val readBack = backend.read(mutation.spec)) {
                        is OperationResult.Success -> {
                            if (valuesEquivalent(mutation.value, readBack.value)) {
                                return OperationResult.Success(Unit)
                            }
                            lastFailure = OperationResult.Failure(
                                "写入 ${mutation.spec.key} 后读回为 ${readBack.value ?: "<未设置>"}，期望 ${mutation.value ?: "<未设置>"}（第 ${attempt + 1} 次）",
                            )
                        }

                        is OperationResult.Failure -> {
                            lastFailure = readBack.copy(
                                message = "写入 ${mutation.spec.key} 后无法读回确认：${readBack.message}",
                            )
                        }
                    }
                }
            }
        }
        return lastFailure ?: OperationResult.Failure("写入 ${mutation.spec.key} 未确认")
    }

    private fun valuesEquivalent(expected: String?, actual: String?): Boolean {
        if (expected == actual) return true
        if (expected == null || actual == null) return false
        val expectedNumber = expected.toDoubleOrNull()
        val actualNumber = actual.toDoubleOrNull()
        return expectedNumber != null &&
            actualNumber != null &&
            kotlin.math.abs(expectedNumber - actualNumber) < NUMERIC_EPSILON
    }

    private suspend fun append(record: TransactionRecord) {
        _journal.value = (_journal.value + record).takeLast(50)
        try {
            journalSink(record)
        } catch (_: Throwable) {
            // A diagnostics sink must never turn a committed system mutation
            // into a false failure; the in-memory journal remains available.
        }
    }

    private companion object {
        const val MAX_WRITE_ATTEMPTS = 3
        const val NUMERIC_EPSILON = 0.001
    }
}
