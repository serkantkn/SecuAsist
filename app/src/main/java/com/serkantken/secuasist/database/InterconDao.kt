package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.serkantken.secuasist.models.Intercom
import com.serkantken.secuasist.models.IntercomWithVillaInfo
import kotlinx.coroutines.flow.Flow


@Dao
interface IntercomDao {
    /**
     * İnterkom listesi ekranı için tüm interkomları, ait oldukları villanın
     * bilgileriyle birleştirerek getirir. Uygulamanın en önemli sorgularından biri budur.
     */
    @Query("""
        SELECT 
            intercoms.*, 
            villas.villaNo, 
            villas.isVillaEmpty, 
            villas.isVillaUnderConstruction 
        FROM intercoms 
        INNER JOIN villas ON intercoms.villaId = villas.villaId 
        ORDER BY villas.villaNo ASC, intercoms.location ASC
    """)
    fun getAllIntercomsWithVillaInfo(): Flow<List<IntercomWithVillaInfo>>

    /**
     * Yeni bir interkom eklemek veya mevcut birini güncellemek için.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(intercom: Intercom)

    /**
     * ID'ye göre bir interkomu silmek için.
     */
    @Query("DELETE FROM intercoms WHERE id = :intercomId")
    suspend fun deleteById(intercomId: Int)
}