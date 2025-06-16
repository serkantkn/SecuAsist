package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import com.serkantken.secuasist.models.CompanyContact
import com.serkantken.secuasist.models.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyContactDao {
    @Insert
    suspend fun insert(companyContact: CompanyContact): Long

    @Delete
    suspend fun delete(companyContact: CompanyContact)

    // Belirli bir şirketin tüm ilgili kişilerini çekmek için
    @Query("SELECT C.* FROM Contacts C JOIN CompanyContacts CC ON C.contactId = CC.contactId WHERE CC.companyId = :companyId")
    fun getContactsForCompany(companyId: Int): Flow<List<Contact>>

    // Belirli bir şirketin birincil iletişim kişisini çekmek için
    @Query("SELECT C.* FROM Contacts C JOIN CompanyContacts CC ON C.contactId = CC.contactId WHERE CC.companyId = :companyId AND CC.isPrimaryContact = 1 LIMIT 1")
    fun getPrimaryContactForCompany(companyId: Int): Flow<Contact?>

    // Bir bağlantının varlığını kontrol etmek için
    @Query("SELECT * FROM CompanyContacts WHERE companyId = :companyId AND contactId = :contactId")
    suspend fun getCompanyContact(companyId: Int, contactId: Int): CompanyContact?
}