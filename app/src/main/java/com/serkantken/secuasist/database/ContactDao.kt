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

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("DELETE FROM Contacts WHERE contactId = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM Contacts ORDER BY contactName ASC")
    fun getAllContacts(): LiveData<List<Contact>>

    @Query("SELECT * FROM Contacts WHERE contactId IN (:contactIds)")
    suspend fun getContactsByIds(contactIds: List<Int>): List<Contact>

    @Query("SELECT * FROM Contacts ORDER BY lastCallTimestamp DESC")
    fun getAllContactsAsList(): List<Contact>

    @Query("SELECT * FROM Contacts ORDER BY lastCallTimestamp DESC")
    fun getAllContactsAsFlow(): Flow<List<Contact>>

    @Query("SELECT * FROM Contacts WHERE contactId = :contactId")
    suspend fun getContactById(contactId: Int): Contact?

    @Query("SELECT * FROM Contacts WHERE contactName = :name AND contactPhone = :phone LIMIT 1")
    suspend fun getContactByNameAndPhone(name: String, phone: String): Contact?

    @Query("SELECT * FROM Contacts WHERE contactPhone = :phone LIMIT 1")
    fun getContactByPhone(phone: String): Contact?
}