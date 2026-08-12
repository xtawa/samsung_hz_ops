package com.xtawa.samsunghzops.data.profile

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.xtawa.samsunghzops.core.model.AppProfile
import com.xtawa.samsunghzops.core.model.RefreshMode
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

@Database(entities = [ProfileEntity::class, TransactionEntity::class], version = 1, exportSchema = false)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun transactionDao(): TransactionDao
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
