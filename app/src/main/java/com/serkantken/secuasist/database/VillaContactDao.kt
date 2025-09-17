package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Update
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.VillaContact
import kotlinx.coroutines.flow.Flow

@Dao
interface VillaContactDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(villaContact: VillaContact): Long

    @Delete
    suspend fun delete(villaContact: VillaContact)

    @Query("DELETE FROM VillaContacts WHERE villaId = :villaId AND contactId = :contactId")
    suspend fun deleteByVillaIdAndContactId(villaId: Int, contactId: Int)

    // Belirli bir villanın tüm ilgili kişilerini çekmek için
    @Query("SELECT C.* FROM Contacts C JOIN VillaContacts VC ON C.contactId = VC.contactId WHERE VC.villaId = :villaId ORDER BY VC.orderIndex ASC")
    fun getContactsForVilla(villaId: Int): Flow<List<Contact>>

    @Query("SELECT C.* FROM Contacts C JOIN VillaContacts VC ON C.contactId = VC.contactId WHERE VC.villaId = :villaId")
    suspend fun getContactsForVillaNonFlow(villaId: Int): List<Contact> // Flow olmayan versiyon

    // Belirli bir villanın gerçek sahiplerini çekmek için
    @Query("SELECT C.* FROM Contacts C JOIN VillaContacts VC ON C.contactId = VC.contactId WHERE VC.villaId = :villaId AND VC.isRealOwner = 1")
    fun getRealOwnersForVilla(villaId: Int): Flow<List<Contact>>

    @Update
    suspend fun updateVillaContacts(villaContacts: List<VillaContact>)

    @Query("SELECT * FROM VillaContacts WHERE villaId = :villaId")
    suspend fun getVillaContactRelations(villaId: Int): List<VillaContact>

    @Query("SELECT C.* FROM Contacts C JOIN VillaContacts VC ON C.contactId = VC.contactId WHERE VC.villaId = :villaId AND VC.isRealOwner = 1")
    suspend fun getRealOwnersForVillaNonFlow(villaId: Int): List<Contact> // Flow olmayan versiyon

    // Belirli bir villanın belirli bir contactType'a sahip kişilerini çekmek için
    @Query("SELECT C.* FROM Contacts C JOIN VillaContacts VC ON C.contactId = VC.contactId WHERE VC.villaId = :villaId AND VC.contactType = :contactType")
    fun getContactsByVillaIdAndType(villaId: Int, contactType: String): Flow<List<Contact>>

    // Bir bağlantının varlığını kontrol etmek için (örneğin UNIQUE kısıtlaması nedeniyle)
    @Query("SELECT * FROM VillaContacts WHERE villaId = :villaId AND contactId = :contactId AND contactType = :contactType")
    suspend fun getVillaContact(villaId: Int, contactId: Int, contactType: String): VillaContact?

    // Belirli bir contactId'nin kaç tane villa ilişkisi olduğunu sayar
    @Query("SELECT COUNT(*) FROM VillaContacts WHERE contactId = :contactId")
    suspend fun getVillaAssociationsCount(contactId: Int): Int
}