package com.serkantken.secuasist.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.serkantken.secuasist.models.Contact
import kotlinx.coroutines.flow.Flow // Flow için import eklendi

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(contacts: List<Contact>): List<Long>

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("DELETE FROM Contacts WHERE contactId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM Contacts")
    suspend fun deleteAll()

    @Query("SELECT * FROM Contacts ORDER BY contactName ASC")
    fun getAllContacts(): LiveData<List<Contact>>

    @Query("SELECT * FROM Contacts WHERE contactId IN (:contactIds)")
    suspend fun getContactsByIds(contactIds: List<String>): List<Contact>

    @Query("SELECT * FROM Contacts WHERE contactId IN (:contactIds)")
    fun getContactsByIdsAsFlow(contactIds: List<String>): Flow<List<Contact>>

    @Query("SELECT * FROM Contacts ORDER BY lastCallTimestamp DESC")
    suspend fun getAllContactsAsList(): List<Contact>

    @Query("SELECT * FROM Contacts ORDER BY lastCallTimestamp DESC")
    fun getAllContactsAsFlow(): Flow<List<Contact>>

    @Query("SELECT * FROM Contacts WHERE contactId = :contactId")
    suspend fun getContactById(contactId: String): Contact?

    @Query("SELECT * FROM Contacts WHERE contactPhone = :phoneNumber")
    suspend fun getContactByPhoneNumber(phoneNumber: String): Contact?

    @Query("SELECT * FROM Contacts WHERE contactName = :name AND contactPhone = :phone LIMIT 1")
    suspend fun getContactByNameAndPhone(name: String, phone: String): Contact?

    @Query("SELECT * FROM Contacts WHERE contactPhone = :phone LIMIT 1")
    fun getContactByPhone(phone: String): Contact?

    @Query("SELECT * FROM Contacts WHERE replace(replace(replace(replace(contactPhone, ' ', ''), '-', ''), '(', ''), ')', '') LIKE '%' || :phone || '%' LIMIT 1")
    suspend fun findContactByPhoneLike(phone: String): Contact?

    @Query("SELECT * FROM Contacts WHERE contactName LIKE :query OR contactPhone LIKE :query ORDER BY contactName ASC")
    fun searchContacts(query: String): Flow<List<Contact>>

    @Query("SELECT * FROM Contacts WHERE replace(replace(replace(replace(contactPhone, ' ', ''), '-', ''), '(', ''), ')', '') LIKE '%' || :query || '%' ORDER BY contactName ASC")
    fun searchContactsByPhoneDigits(query: String): Flow<List<Contact>>
}