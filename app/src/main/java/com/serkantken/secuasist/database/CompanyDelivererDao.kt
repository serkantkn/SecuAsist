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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addDelivererToCompany(crossRef: CompanyDelivererCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addDeliverersToCompany(crossRefs: List<CompanyDelivererCrossRef>)

    @Query("DELETE FROM CompanyDeliverers WHERE companyId = :companyId AND contactId = :contactId")
    suspend fun removeDelivererFromCompany(companyId: Int, contactId: Int)

    @Query("DELETE FROM CompanyDeliverers WHERE companyId = :companyId")
    suspend fun removeAllDeliverersFromCompany(companyId: Int)

    // Belirli bir şirkete atanmış tüm dağıtıcı Contact ID'lerini getirir
    @Query("SELECT contactId FROM CompanyDeliverers WHERE companyId = :companyId")
    fun getDelivererContactIdsForCompanyFlow(companyId: Int): Flow<List<Int>>

    // Belirli bir şirkete atanmış tüm dağıtıcı Contact nesnelerini getirir
    // Bu, daha karmaşık bir sorgu gerektirir ve genellikle @Relation ile yapılır,
    // ama şimdilik ID'leri alıp sonra Contact'ları çekmek daha basit olabilir.
    // Alternatif olarak, doğrudan Contact'ları getiren bir Transaction metodu yazılabilir.
    // Örneğin:
    @Transaction
    @Query("""
        SELECT * FROM Contacts 
        INNER JOIN CompanyDeliverers ON Contacts.contactId = CompanyDeliverers.contactId 
        WHERE CompanyDeliverers.companyId = :companyId
    """)
    fun getDeliverersForCompanyFlow(companyId: Int): Flow<List<Contact>>

    // Bir kişinin belirli bir şirketin dağıtıcısı olup olmadığını kontrol et
    @Query("SELECT EXISTS (SELECT 1 FROM CompanyDeliverers WHERE companyId = :companyId AND contactId = :contactId LIMIT 1)")
    fun isContactDelivererForCompanyFlow(companyId: Int, contactId: Int): Flow<Boolean>
}