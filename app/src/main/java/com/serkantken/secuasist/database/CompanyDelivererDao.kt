package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.serkantken.secuasist.models.CompanyDelivererCrossRef
import com.serkantken.secuasist.models.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDelivererDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDelivererToCompany(crossRef: CompanyDelivererCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<CompanyDelivererCrossRef>): List<Long>

    @Query("DELETE FROM CompanyContacts")
    suspend fun deleteAll()

    @Query("SELECT * FROM CompanyContacts")
    suspend fun getAllSync(): List<CompanyDelivererCrossRef>

    @Query("DELETE FROM CompanyContacts WHERE companyId = :companyId AND contactId = :contactId")
    suspend fun removeDelivererFromCompany(companyId: Int, contactId: String)

    @Query("DELETE FROM CompanyContacts WHERE companyId = :companyId")
    suspend fun removeAllDeliverersFromCompany(companyId: Int)

    // Belirli bir şirkete atanmış tüm dağıtıcı Contact ID'lerini getirir
    @Query("SELECT contactId FROM CompanyContacts WHERE companyId = :companyId")
    fun getDelivererContactIdsForCompanyFlow(companyId: Int): Flow<List<String>>

    @Transaction
    @Query("""
        SELECT * FROM Contacts 
        INNER JOIN CompanyContacts ON Contacts.contactId = CompanyContacts.contactId 
        WHERE CompanyContacts.companyId = :companyId
    """)
    fun getDeliverersForCompanyFlow(companyId: Int): Flow<List<Contact>>

    @Query("SELECT EXISTS (SELECT 1 FROM CompanyContacts WHERE companyId = :companyId AND contactId = :contactId LIMIT 1)")
    fun isContactDelivererForCompanyFlow(companyId: Int, contactId: String): Flow<Boolean>
}