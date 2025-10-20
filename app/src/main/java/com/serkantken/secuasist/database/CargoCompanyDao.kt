package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.serkantken.secuasist.models.CargoCompany
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoCompanyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(company: CargoCompany): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cargoCompanies: List<CargoCompany>): List<Long>

    @Update
    suspend fun update(company: CargoCompany)

    @Delete
    suspend fun delete(company: CargoCompany)

    @Query("DELETE FROM CargoCompanies WHERE companyId = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM CargoCompanies")
    suspend fun deleteAll()

    @Query("SELECT * FROM CargoCompanies")
    fun getAllCargoCompanies(): Flow<List<CargoCompany>>

    @Query("SELECT * FROM CargoCompanies")
    suspend fun getAllCompaniesAsList(): List<CargoCompany>

    @Query("SELECT * FROM CargoCompanies WHERE companyId = :companyId")
    suspend fun getCargoCompanyById(companyId: Int): CargoCompany?

    @Query("SELECT * FROM CargoCompanies WHERE companyName = :companyName")
    suspend fun getCargoCompanyByName(companyName: String): CargoCompany?
}