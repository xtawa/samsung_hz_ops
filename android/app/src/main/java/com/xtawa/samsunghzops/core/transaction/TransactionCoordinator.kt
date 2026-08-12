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

        val applied = mutableListOf<SettingMutation>()
        for (mutation in prepared) {
            when (val write = backend.write(mutation.spec, mutation.value)) {
                is OperationResult.Success -> applied += mutation
                is OperationResult.Failure -> {
                    val rollbackWarnings = rollback(applied)
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
            when (val result = backend.write(mutation.spec, mutation.previousValue)) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> warnings += "${mutation.spec.key}: ${result.message}"
            }
        }
        return warnings
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
}
