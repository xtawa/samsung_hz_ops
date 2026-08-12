package com.xtawa.samsunghzops.data.profile

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xtawa.samsunghzops.core.model.AppProfile
import com.xtawa.samsunghzops.core.model.ManagedSettingSnapshot
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.SettingNamespace
import com.xtawa.samsunghzops.core.model.SettingSpec
import com.xtawa.samsunghzops.core.model.TransactionRecord
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "app_profiles")
data class ProfileEntity(
    @androidx.room.PrimaryKey val packageName: String,
    val appLabel: String,
    val enabled: Boolean,
    val preferredMode: String,
    val minHz: Float?,
    val maxHz: Float?,
    val pauseWhenScreenOff: Boolean,
    val pauseWhenKeyguard: Boolean,
    val updatedAtEpochMs: Long,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM app_profiles ORDER BY appLabel COLLATE NOCASE")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM app_profiles")
    suspend fun deleteAll()
}

@Database(
    entities = [ProfileEntity::class, TransactionEntity::class, ManagedSettingSnapshotEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun transactionDao(): TransactionDao

    abstract fun managedSettingSnapshotDao(): ManagedSettingSnapshotDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS managed_setting_snapshots (
                        namespace TEXT NOT NULL,
                        settingKey TEXT NOT NULL,
                        description TEXT NOT NULL,
                        requiresPrivilegedWrite INTEGER NOT NULL,
                        originalValue TEXT,
                        capturedAtEpochMs INTEGER NOT NULL,
                        lastTouchedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(namespace, settingKey)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}

fun ProfileEntity.toDomain(): AppProfile = AppProfile(
    packageName = packageName,
    appLabel = appLabel,
    enabled = enabled,
    preferredMode = runCatching { RefreshMode.valueOf(preferredMode) }
        .getOrDefault(RefreshMode.ADAPTIVE),
    minHz = minHz,
    maxHz = maxHz,
    pauseWhenScreenOff = pauseWhenScreenOff,
    pauseWhenKeyguard = pauseWhenKeyguard,
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun AppProfile.toEntity(): ProfileEntity = ProfileEntity(
    packageName = packageName,
    appLabel = appLabel,
    enabled = enabled,
    preferredMode = preferredMode.name,
    minHz = minHz,
    maxHz = maxHz,
    pauseWhenScreenOff = pauseWhenScreenOff,
    pauseWhenKeyguard = pauseWhenKeyguard,
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

class AppProfileRepository(
    private val dao: ProfileDao,
) {
    val profiles: Flow<List<AppProfile>> = dao.observeAll().map { entities ->
        entities.map(ProfileEntity::toDomain)
    }

    suspend fun save(profile: AppProfile) = dao.upsert(profile.toEntity())

    suspend fun delete(profile: AppProfile) = dao.delete(profile.toEntity())

    suspend fun clear() = dao.deleteAll()
}

@Entity(tableName = "transaction_journal")
data class TransactionEntity(
    @androidx.room.PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val operation: String,
    val mutationSummary: String,
    val committed: Boolean,
    val rollbackAttempted: Boolean,
    val error: String?,
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transaction_journal ORDER BY startedAtEpochMs DESC LIMIT 50")
    fun observeRecent(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity)
}

fun TransactionRecord.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    startedAtEpochMs = startedAt.toEpochMilli(),
    completedAtEpochMs = completedAt?.toEpochMilli(),
    operation = operation,
    mutationSummary = mutations.joinToString("; ") { mutation ->
        "${mutation.spec.namespace}.${mutation.spec.key}=${mutation.value ?: "<delete>"}"
    },
    committed = committed,
    rollbackAttempted = rollbackAttempted,
    error = error,
)

class TransactionJournalRepository(
    private val dao: TransactionDao,
) {
    val recent: Flow<List<TransactionEntity>> = dao.observeRecent()

    suspend fun append(record: TransactionRecord) = dao.insert(record.toEntity())
}

@Entity(
    tableName = "managed_setting_snapshots",
    primaryKeys = ["namespace", "settingKey"],
)
data class ManagedSettingSnapshotEntity(
    val namespace: String,
    val settingKey: String,
    val description: String,
    val requiresPrivilegedWrite: Boolean,
    val originalValue: String?,
    val capturedAtEpochMs: Long,
    val lastTouchedAtEpochMs: Long,
)

@Dao
interface ManagedSettingSnapshotDao {
    @Query("SELECT * FROM managed_setting_snapshots ORDER BY namespace, settingKey")
    fun observeAll(): Flow<List<ManagedSettingSnapshotEntity>>

    @Query("SELECT * FROM managed_setting_snapshots ORDER BY namespace, settingKey")
    suspend fun getAll(): List<ManagedSettingSnapshotEntity>

    @Query("SELECT * FROM managed_setting_snapshots WHERE namespace = :namespace AND settingKey = :settingKey LIMIT 1")
    suspend fun find(namespace: String, settingKey: String): ManagedSettingSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: ManagedSettingSnapshotEntity)

    @Query("UPDATE managed_setting_snapshots SET lastTouchedAtEpochMs = :lastTouchedAtEpochMs WHERE namespace = :namespace AND settingKey = :settingKey")
    suspend fun touch(namespace: String, settingKey: String, lastTouchedAtEpochMs: Long)

    @Query("DELETE FROM managed_setting_snapshots WHERE namespace = :namespace AND settingKey = :settingKey")
    suspend fun delete(namespace: String, settingKey: String)

    @Query("DELETE FROM managed_setting_snapshots")
    suspend fun deleteAll()
}

fun ManagedSettingSnapshotEntity.toDomain(): ManagedSettingSnapshot = ManagedSettingSnapshot(
    spec = SettingSpec(
        namespace = SettingNamespace.valueOf(namespace),
        key = settingKey,
        description = description,
        requiresPrivilegedWrite = requiresPrivilegedWrite,
    ),
    originalValue = originalValue,
    capturedAt = Instant.ofEpochMilli(capturedAtEpochMs),
    lastTouchedAt = Instant.ofEpochMilli(lastTouchedAtEpochMs),
)

fun ManagedSettingSnapshot.toEntity(): ManagedSettingSnapshotEntity = ManagedSettingSnapshotEntity(
    namespace = spec.namespace.name,
    settingKey = spec.key,
    description = spec.description,
    requiresPrivilegedWrite = spec.requiresPrivilegedWrite,
    originalValue = originalValue,
    capturedAtEpochMs = capturedAt.toEpochMilli(),
    lastTouchedAtEpochMs = lastTouchedAt.toEpochMilli(),
)

class ManagedSettingSnapshotRepository(
    private val dao: ManagedSettingSnapshotDao,
) {
    val snapshots: Flow<List<ManagedSettingSnapshot>> = dao.observeAll().map { entities ->
        entities.map(ManagedSettingSnapshotEntity::toDomain)
    }

    suspend fun captureIfMissing(spec: SettingSpec, originalValue: String?) {
        val now = Instant.now()
        val existing = dao.find(spec.namespace.name, spec.key)
        if (existing == null) {
            dao.upsert(
                ManagedSettingSnapshot(
                    spec = spec,
                    originalValue = originalValue,
                    capturedAt = now,
                    lastTouchedAt = now,
                ).toEntity(),
            )
        } else {
            dao.touch(spec.namespace.name, spec.key, now.toEpochMilli())
        }
    }

    suspend fun all(): List<ManagedSettingSnapshot> = dao.getAll().map(ManagedSettingSnapshotEntity::toDomain)

    suspend fun forget(spec: SettingSpec) = dao.delete(spec.namespace.name, spec.key)

    suspend fun clear() = dao.deleteAll()
}
