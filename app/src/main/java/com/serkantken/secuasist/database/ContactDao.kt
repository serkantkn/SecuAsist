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
    fun getAllContacts(): LiveData<List<Contact>> // Bu metod korundu

    @Query("SELECT * FROM Contacts ORDER BY contactName ASC") // Aynı sorgu, farklı dönüş tipi
    fun getAllContactsAsFlow(): Flow<List<Contact>> // Yeni Flow tabanlı metod

    @Query("SELECT * FROM Contacts WHERE contactId = :contactId")
    suspend fun getContactById(contactId: Int): Contact?

    @Query("SELECT * FROM Contacts WHERE contactName = :name AND contactPhone = :phone LIMIT 1")
    suspend fun getContactByNameAndPhone(name: String, phone: String): Contact?
}