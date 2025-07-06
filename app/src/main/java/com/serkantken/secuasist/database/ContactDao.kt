package com.serkantken.secuasist.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.serkantken.secuasist.models.Contact

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM Contacts ORDER BY contactName ASC")
    fun getAllContacts(): LiveData<List<Contact>>

    @Query("SELECT * FROM Contacts WHERE contactId = :contactId")
    suspend fun getContactById(contactId: Int): Contact?

    @Query("SELECT * FROM Contacts WHERE contactName = :name AND contactPhone = :phone LIMIT 1")
    suspend fun getContactByNameAndPhone(name: String, phone: String): Contact?
}