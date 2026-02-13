package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.serkantken.secuasist.models.SyncLog

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(log: SyncLog)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp ASC")
    suspend fun getAllPendingLogs(): List<SyncLog>

    @Query("DELETE FROM sync_logs WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM sync_logs")
    suspend fun deleteAll()
}
