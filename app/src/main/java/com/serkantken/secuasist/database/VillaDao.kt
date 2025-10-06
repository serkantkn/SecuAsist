package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaWithContacts
import kotlinx.coroutines.flow.Flow // Veri değişikliklerini gözlemlemek için Flow kullanacağız

@Dao
interface VillaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(villa: Villa): Long // Eklenen villanın ID'sini döndürür

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(villas: List<Villa>): List<Long>

    @Update
    suspend fun update(villa: Villa)

    @Delete
    suspend fun delete(villa: Villa)

    @Query("DELETE FROM villas WHERE villaId = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM Villas")
    fun getAllVillas(): Flow<List<Villa>> // Tüm villaları gözlemlenebilir bir şekilde döndürür

    @Query("SELECT * FROM Villas ORDER BY villaNo ASC") // Villa numarasına göre sıralı
    suspend fun getAllVillasAsList(): List<Villa> // Flow olmayan versiyon

    @Query("SELECT * FROM Villas WHERE villaId = :villaId")
    suspend fun getVillaById(villaId: Int): Villa?

    @Query("SELECT * FROM Villas WHERE villaId IN (:villaIds)")
    suspend fun getVillasByIds(villaIds: List<Int>): List<Villa>

    @Query("SELECT * FROM Villas WHERE villaId IN (:villaIds)")
    fun getVillasByIdsAsFlow(villaIds: List<Int>): Flow<List<Villa>>

    @Query("SELECT * FROM Villas WHERE villaNo = :villaNo LIMIT 1")
    suspend fun getVillaByNo(villaNo: Int): Villa?

    // Tüm villaları, ilgili iletişim kişileriyle birlikte getiren metod
    // @Transaction anotasyonu, bu sorgunun atomik olmasını sağlar ve ilişkili verileri doğru çeker.
    @Transaction
    @Query("SELECT * FROM Villas ORDER BY villaNo ASC")
    fun getAllVillasWithContacts(): Flow<List<VillaWithContacts>>

    // Belirli bir villayı, ilgili iletişim kişileriyle birlikte getiren metod
    @Transaction
    @Query("SELECT * FROM Villas WHERE villaId = :villaId")
    suspend fun getVillaWithContactsById(villaId: Int): VillaWithContacts?

    @Query("SELECT DISTINCT villaStreet FROM villas WHERE villaStreet IS NOT NULL AND villaStreet != '' ORDER BY villaStreet ASC")
    fun getUniqueStreetNames(): Flow<List<String>>
}