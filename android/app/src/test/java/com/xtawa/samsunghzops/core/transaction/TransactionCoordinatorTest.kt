package com.xtawa.samsunghzops.core.transaction

import com.xtawa.samsunghzops.core.model.OperationResult
import com.xtawa.samsunghzops.core.model.SettingMutation
import com.xtawa.samsunghzops.core.model.SettingNamespace
import com.xtawa.samsunghzops.core.model.SettingSpec
import com.xtawa.samsunghzops.data.settings.SettingsBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCoordinatorTest {
    private val first = SettingSpec(SettingNamespace.SYSTEM, "first", "", false)
    private val second = SettingSpec(SettingNamespace.SYSTEM, "second", "", false)

    @Test
    fun secondWriteFailureRollsBackFirstWrite() = runTest {
        val values = mutableMapOf<String, String?>(
            first.key to "old-first",
            second.key to "old-second",
        )
        val writes = mutableListOf<Pair<String, String?>>()
        val backend = object : SettingsBackend {
            override fun read(spec: SettingSpec) = OperationResult.Success(values[spec.key])

            override suspend fun write(spec: SettingSpec, value: String?): OperationResult<Unit> {
                writes += spec.key to value
                if (spec == second && value == "new-second") {
                    return OperationResult.Failure("simulated failure")
                }
                values[spec.key] = value
                return OperationResult.Success(Unit)
            }

            override fun canWrite(spec: SettingSpec) = true
        }
        val result = TransactionCoordinator(backend).apply(
            "test",
            listOf(
                SettingMutation(first, "new-first"),
                SettingMutation(second, "new-second"),
            ),
        )
        assertTrue(result is OperationResult.Failure)
        assertEquals("old-first", values[first.key])
        assertEquals(listOf("new-first", "new-second", "old-first"), writes.map { it.second })
    }
}
