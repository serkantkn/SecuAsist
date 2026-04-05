package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.serkantken.secuasist.models.Intercom
import kotlinx.coroutines.flow.Flow

@Dao
interface IntercomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(intercom: Intercom): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(intercoms: List<Intercom>): List<Long>

    @Update
    suspend fun update(intercom: Intercom)

    @Delete
    suspend fun delete(intercom: Intercom)
    
    @Query("DELETE FROM Intercoms")
    suspend fun deleteAll()

    @Query("SELECT * FROM Intercoms WHERE villaId = :villaId")
    fun getIntercomsForVilla(villaId: Int): Flow<List<Intercom>>
    
    @Query("SELECT * FROM Intercoms WHERE intercomId = :id")
    suspend fun getIntercomById(id: String): Intercom?

    @Query("SELECT * FROM Intercoms")
    suspend fun getAllSync(): List<Intercom>

    @Query("DELETE FROM Intercoms WHERE intercomId = :id")
    suspend fun deleteById(id: String)
}
