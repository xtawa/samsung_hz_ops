package com.xtawa.samsunghzops.data.profile

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xtawa.samsunghzops.core.model.RefreshMode
import com.xtawa.samsunghzops.core.model.SystemProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "system_profiles")
data class SystemProfileEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val mode: String,
    val minHz: Float?,
    val maxHz: Float?,
    val enabled: Boolean,
)

@Dao
interface SystemProfileDao {
    @Query("SELECT * FROM system_profiles")
    fun observeAll(): Flow<List<SystemProfileEntity>>

    @Query("SELECT * FROM system_profiles")
    suspend fun getAll(): List<SystemProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SystemProfileEntity)
}

fun SystemProfileEntity.toDomain(): SystemProfile = SystemProfile(
    id = id,
    title = title,
    mode = runCatching { RefreshMode.valueOf(mode) }.getOrDefault(RefreshMode.ADAPTIVE),
    minHz = minHz,
    maxHz = maxHz,
    enabled = enabled,
)

fun SystemProfile.toEntity(): SystemProfileEntity = SystemProfileEntity(
    id = id,
    title = title,
    mode = mode.name,
    minHz = minHz,
    maxHz = maxHz,
    enabled = enabled,
)

class SystemProfileRepository(
    private val dao: SystemProfileDao,
) {
    val profiles: Flow<List<SystemProfile>> = dao.observeAll().map { entities ->
        ensureDefaults(entities).map(SystemProfileEntity::toDomain)
    }

    suspend fun save(profile: SystemProfile) = dao.upsert(profile.toEntity())

    suspend fun profile(id: String): SystemProfile =
        defaults().first { it.id == id }.let { fallback ->
            dao.getAll().firstOrNull { it.id == id }?.toDomain() ?: fallback
        }

    companion object {
        const val NORMAL = "normal"
        const val PSM = "psm"
        const val LOW_BATTERY = "low_battery"
        const val AOD = "aod"
        const val COVER = "cover"

        fun defaults(): List<SystemProfile> = listOf(
            SystemProfile(NORMAL, "日常模式", RefreshMode.ADAPTIVE, enabled = true),
            SystemProfile(PSM, "省电模式", RefreshMode.ADAPTIVE, enabled = true),
            SystemProfile(LOW_BATTERY, "低电量", RefreshMode.STANDARD, enabled = true),
            SystemProfile(AOD, "息屏与 AOD", RefreshMode.STANDARD, enabled = true),
            SystemProfile(COVER, "折叠外屏", RefreshMode.ADAPTIVE, enabled = true),
        )

        private fun ensureDefaults(existing: List<SystemProfileEntity>): List<SystemProfileEntity> {
            val byId = existing.associateBy { it.id }
            return defaults().map { fallback -> byId[fallback.id] ?: fallback.toEntity() }
        }
    }
}
