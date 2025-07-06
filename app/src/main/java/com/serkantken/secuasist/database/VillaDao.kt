package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaWithContacts
import kotlinx.coroutines.flow.Flow // Veri değişikliklerini gözlemlemek için Flow kullanacağız

@Dao
interface VillaDao {
    @Insert
    suspend fun insert(villa: Villa): Long // Eklenen villanın ID'sini döndürür

    @Update
    suspend fun update(villa: Villa)

    @Delete
    suspend fun delete(villa: Villa)

    @Query("SELECT * FROM Villas")
    fun getAllVillas(): Flow<List<Villa>> // Tüm villaları gözlemlenebilir bir şekilde döndürür

    @Query("SELECT * FROM Villas WHERE villaId = :villaId")
    suspend fun getVillaById(villaId: Int): Villa?

    @Query("SELECT * FROM Villas WHERE villaNo = :villaNo")
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
}