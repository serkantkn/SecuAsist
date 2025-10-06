package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.CargoReport
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cargo: Cargo): Long

    @Update
    suspend fun update(cargo: Cargo)

    @Delete
    suspend fun delete(cargo: Cargo)

    @Query("SELECT * FROM Cargos ORDER BY date DESC")
    fun getAllCargos(): Flow<List<Cargo>>

    @Query("SELECT * FROM Cargos WHERE cargoId = :cargoId")
    suspend fun getCargoById(cargoId: Int): Cargo?

    @Query("SELECT * FROM Cargos WHERE cargoId IN (:cargoIds)")
    suspend fun getCargosByIds(cargoIds: List<Int>): List<Cargo>

    @Query("SELECT * FROM Cargos WHERE cargoId IN (:cargoIds)")
    fun getCargosByIdsAsFlow(cargoIds: List<Int>): Flow<List<Cargo>>

    // Belirli bir villaya ait kargoları çekmek için
    @Query("SELECT * FROM Cargos WHERE villaId = :villaId ORDER BY date DESC")
    fun getCargosForVilla(villaId: Int): Flow<List<Cargo>>

    // Teslim edilmemiş kargoları çekmek için
    @Query("SELECT * FROM Cargos WHERE isCalled = 0 OR isMissed = 1 ORDER BY date ASC")
    fun getPendingCargos(): Flow<List<Cargo>>

    @Query("SELECT * FROM cargos WHERE isCalled = 0 OR isMissed = 1 ORDER BY date ASC")
    suspend fun getPendingCargosAsList(): List<Cargo>

    // Kargo teslim durumunu güncelleme
    @Query("UPDATE Cargos SET isCalled = :isCalled, isMissed = :isMissed, callDate = :callDate, callAttemptCount = :callAttemptCount WHERE cargoId = :cargoId")
    suspend fun updateCargoCallStatus(cargoId: Int, isCalled: Int, isMissed: Int, callDate: String?, callAttemptCount: Int)

    @Query("SELECT EXISTS (SELECT 1 FROM Cargos WHERE companyId = :targetCompanyId AND isCalled = 0 LIMIT 1)")
    suspend fun hasUncalledCargosForCompany(targetCompanyId: Int): Boolean

    @Query("SELECT EXISTS (SELECT 1 FROM Cargos WHERE isCalled = 0 LIMIT 1)")
    fun hasAnyUncalledCargosFlow(): Flow<Boolean>

    @Query("UPDATE Cargos SET isCalled = :isCalled, isMissed = :isMissed, callDate = :callDate, callAttemptCount = :callAttemptCount, whoCalled = :whoCalledId WHERE cargoId = :cargoId")
    suspend fun updateCargoCallStatus(cargoId: Int, isCalled: Int, isMissed: Int, callDate: String?, callAttemptCount: Int, whoCalledId: Int?)

    // YENİ METOD: Belirli bir şirkete ait, aranmamış (isCalled = 0) kargoları liste olarak döndürür.
    @Query("SELECT * FROM cargos WHERE companyId = :companyId AND isCalled = 0 ORDER BY date ASC")
    suspend fun getUncalledCargosForCompanyAsList(companyId: Int): List<Cargo>

    @Query("SELECT * FROM cargos WHERE villaId = :villaId AND isCalled = 0 ORDER BY date ASC")
    suspend fun getUncalledCargosForVillaAsList(villaId: Int): List<Cargo>

    @Query("""
        SELECT
            c.cargoId as cargoId,
            cp.companyName as companyName, 
            v.villaNo as villaNo,          
            ct.contactName as whoCalledName, 
            c.callDate as callDate,
            CASE c.isMissed 
                WHEN 0 THEN 'Başarılı' 
                WHEN 1 THEN 'Ulaşılamadı/Atlandı' 
                ELSE 'Bilinmiyor' 
            END as callStatus,
            c.callingDeviceName as callingDeviceName,
            c.callAttemptCount as callAttemptCount
        FROM cargos c
        LEFT JOIN CargoCompanies cp ON c.companyId = cp.companyId 
        LEFT JOIN villas v ON c.villaId = v.villaId             
        LEFT JOIN contacts ct ON c.whoCalled = ct.contactId     
        ORDER BY c.callDate DESC 
    """) // ORDER BY isteğe bağlı, son aramalara göre sıralar
    fun getCargoReportDetails(): Flow<List<CargoReport>> // Asenkron akış için Flow kullanmak iyi bir pratik

    // VEYA Filtreli Rapor Sorgusu (CargoReportActivity'deki mevcut fetchReportData'ya benzer)
    @Query("""
        SELECT
            c.cargoId as cargoId,
            cp.companyName as companyName,
            v.villaNo as villaNo,
            ct.contactName as whoCalledName,
            c.callDate as callDate,
            CASE c.isMissed
                WHEN 0 THEN 'Başarılı'
                WHEN 1 THEN 'Ulaşılamadı/Atlandı'
                ELSE 'Bilinmiyor'
            END as callStatus,
            c.callingDeviceName as callingDeviceName,
            c.callAttemptCount as callAttemptCount
        FROM cargos c
        LEFT JOIN CargoCompanies cp ON c.companyId = cp.companyId
        LEFT JOIN villas v ON c.villaId = v.villaId
        LEFT JOIN contacts ct ON c.whoCalled = ct.contactId
        WHERE (:startDate IS NULL OR c.callDate >= :startDate) AND
              (:endDate IS NULL OR c.callDate <= :endDate) AND
              (:companyIdFilter IS NULL OR c.companyId = :companyIdFilter) AND
              (:villaIdFilter IS NULL OR c.villaId = :villaIdFilter)
        ORDER BY c.callDate DESC
    """)
    fun getCargoReportDetailsFiltered(
        startDate: String?,
        endDate: String?,
        companyIdFilter: Int?,
        villaIdFilter: Int?
    ): Flow<List<CargoReport>>
}