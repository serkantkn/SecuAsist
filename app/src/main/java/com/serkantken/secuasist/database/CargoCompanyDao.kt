package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.serkantken.secuasist.models.CargoCompany
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoCompanyDao {
    @Insert
    suspend fun insert(company: CargoCompany): Long

    @Update
    suspend fun update(company: CargoCompany)

    @Delete
    suspend fun delete(company: CargoCompany)

    @Query("DELETE FROM CargoCompanies WHERE companyId = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM CargoCompanies")
    fun getAllCargoCompanies(): Flow<List<CargoCompany>>

    @Query("SELECT * FROM CargoCompanies")
    suspend fun getAllCompaniesAsList(): List<CargoCompany>

    @Query("SELECT * FROM CargoCompanies WHERE companyId = :companyId")
    suspend fun getCargoCompanyById(companyId: Int): CargoCompany?

    @Query("SELECT * FROM CargoCompanies WHERE companyName = :companyName")
    suspend fun getCargoCompanyByName(companyName: String): CargoCompany?
}