package com.fyiplayer.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// domain+name PK is the isolation: a lookup is always scoped to one domain, never a shared jar
@Entity(tableName = "cookies", primaryKeys = ["domain", "name"])
data class CookieEntity(
    val domain: String,
    val name: String,
    val value: String,
    val path: String,
    val expiresAt: Long?,
)

@Dao
interface CookieDao {
    @Query("SELECT * FROM cookies WHERE domain = :domain")
    suspend fun forDomain(domain: String): List<CookieEntity>

    @Query("SELECT * FROM cookies ORDER BY domain ASC")
    fun observeAll(): Flow<List<CookieEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cookie: CookieEntity)

    @Query("DELETE FROM cookies WHERE domain = :domain")
    suspend fun clearDomain(domain: String)

    @Query("DELETE FROM cookies")
    suspend fun clearAll()
}
