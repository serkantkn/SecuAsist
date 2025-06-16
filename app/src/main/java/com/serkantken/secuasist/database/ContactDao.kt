package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.serkantken.secuasist.models.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM Contacts")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM Contacts WHERE contactId = :contactId")
    suspend fun getContactById(contactId: Int): Contact?
}