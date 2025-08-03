package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.serkantken.secuasist.models.Cargo
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoDao {
    @Insert
    suspend fun insert(cargo: Cargo): Long

    @Update
    suspend fun update(cargo: Cargo)

    @Delete
    suspend fun delete(cargo: Cargo)

    @Query("SELECT * FROM Cargos ORDER BY date DESC")
    fun getAllCargos(): Flow<List<Cargo>>

    @Query("SELECT * FROM Cargos WHERE cargoId = :cargoId")
    suspend fun getCargoById(cargoId: Int): Cargo?

    // Belirli bir villaya ait kargoları çekmek için
    @Query("SELECT * FROM Cargos WHERE villaId = :villaId ORDER BY date DESC")
    fun getCargosForVilla(villaId: Int): Flow<List<Cargo>>

    // Teslim edilmemiş kargoları çekmek için
    @Query("SELECT * FROM Cargos WHERE isCalled = 0 OR isMissed = 1 ORDER BY date ASC")
    fun getPendingCargos(): Flow<List<Cargo>>

    // Kargo teslim durumunu güncelleme
    @Query("UPDATE Cargos SET isCalled = :isCalled, isMissed = :isMissed, callDate = :callDate, callAttemptCount = :callAttemptCount WHERE cargoId = :cargoId")
    suspend fun updateCargoCallStatus(cargoId: Int, isCalled: Int, isMissed: Int, callDate: String?, callAttemptCount: Int)

    @Query("SELECT EXISTS (SELECT 1 FROM Cargos WHERE companyId = :targetCompanyId AND isCalled = 0 LIMIT 1)")
    suspend fun hasUncalledCargosForCompany(targetCompanyId: Int): Boolean
}