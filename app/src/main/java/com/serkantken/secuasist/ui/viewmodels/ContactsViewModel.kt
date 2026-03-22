package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ImportSummary(
    val totalProcessed: Int,
    val newAdded: Int,
    val alreadyExists: Int,
    val duplicatesMerged: Int,
    val villasLinked: Int
)

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactDao = (application as SecuAsistApplication).db.contactDao()
    private val app = application as SecuAsistApplication

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress = _importProgress.asStateFlow()

    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary = _importSummary.asStateFlow()

    fun dismissImportSummary() {
        _importSummary.value = null
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredContacts = _searchQuery.flatMapLatest { query ->
        if (query.isEmpty()) {
            contactDao.getAllContactsAsFlow()
        } else {
            contactDao.searchContacts("%$query%")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addContact(name: String, phone: String) {
        viewModelScope.launch {
            val newContact = Contact(
                contactName = name,
                contactPhone = phone
            )
            contactDao.insert(newContact)
            // contactId is already generated in newContact
            app.syncManager.sendData("ADD_CONTACT", newContact)
        }
    }
    
    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactDao.delete(contact)
            val payload = mapOf("contactId" to contact.contactId)
            app.syncManager.sendData("DELETE_CONTACT", payload)
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            val updatedContact = contact.copy(updatedAt = System.currentTimeMillis())
            contactDao.update(updatedContact)
            app.syncManager.sendData("UPDATE_CONTACT", updatedContact)
        }
    }

    // New dependency needed for Villa lookup
    private val villaDao = (application as SecuAsistApplication).db.villaDao()
    private val villaContactDao = (application as SecuAsistApplication).db.villaContactDao()

    fun importContactsFromDevice(context: android.content.Context) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgress.value = 0f
            _importSummary.value = null
            
            var newAdded = 0
            var alreadyExists = 0
            var villasLinked = 0
            var merged = 0

            try {
                val deviceContacts = com.serkantken.secuasist.utils.ContactUtils.fetchDeviceContacts(context)
                val total = deviceContacts.size
                
                deviceContacts.forEachIndexed { index, deviceContact ->
                    val normalized = com.serkantken.secuasist.utils.ContactUtils.normalizePhoneNumber(deviceContact.phone)

                    // 1. Insert Contact
                    var existingContact = contactDao.getContactByPhoneNumber(deviceContact.phone)
                    if (existingContact == null && normalized.length >= 10) {
                        existingContact = contactDao.findContactByPhoneLike(normalized)
                    }
                    
                    val contactId = if (existingContact != null) {
                        alreadyExists++
                        existingContact.contactId
                    } else {
                        val newContact = Contact(
                            contactName = deviceContact.name,
                            contactPhone = deviceContact.phone
                        )
                        contactDao.insert(newContact)
                        app.syncManager.sendData("ADD_CONTACT", newContact)
                        newAdded++
                        newContact.contactId
                    }

                    // 2. Link to Villa if villaNo detected
                    if (deviceContact.potentialVillaNo != null) {
                        val villa = villaDao.getVillaByNo(deviceContact.potentialVillaNo)
                        if (villa != null) {
                            val existingLink = villaContactDao.getContactsForVillaNonFlow(villa.villaId).find { it.contactId == contactId }
                            if (existingLink == null) {
                                val link = com.serkantken.secuasist.models.VillaContact(
                                    villaId = villa.villaId,
                                    contactId = contactId,
                                    isRealOwner = false,
                                    contactType = "Tenant",
                                    notes = "Otomatik İçe Aktarıldı"
                                )
                                villaContactDao.insert(link)
                                app.syncManager.sendData("ADD_VILLA_CONTACT", link)
                                villasLinked++
                            }
                        }
                    }
                    
                    _importProgress.value = (index + 1).toFloat() / total
                }
                
                // 3. Post-import cleanup: Merge any existing duplicates
                merged = mergeDatabaseContactsInternal()
                
                _importSummary.value = ImportSummary(
                    totalProcessed = total,
                    newAdded = newAdded,
                    alreadyExists = alreadyExists,
                    villasLinked = villasLinked,
                    duplicatesMerged = merged
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }

    suspend fun getLinkedVillas(contactId: String): List<Villa> {
        return villaContactDao.getVillasForContact(contactId)
    }

    suspend fun getAllVillas(): List<Villa> {
        val villasFlow = villaDao.getAllVillas()
        return try {
            villasFlow.first()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Helper to get villas as Flow if needed for UI state
    val allVillasFlow = villaDao.getAllVillas()

    fun linkContactToVilla(contactId: String, villaId: Int, isOwner: Boolean, type: String) {
        viewModelScope.launch {
            // Check if already linked
            val existingLinks = villaContactDao.getContactsForVillaNonFlow(villaId)
            if (existingLinks.none { it.contactId == contactId }) {
                val link = com.serkantken.secuasist.models.VillaContact(
                    villaId = villaId,
                    contactId = contactId,
                    isRealOwner = isOwner,
                    contactType = type,
                    notes = null
                )
                villaContactDao.insert(link)
                app.syncManager.sendData("ADD_VILLA_CONTACT", link)
            }
        }
    }

    fun updateLastCall(contact: Contact) {
        viewModelScope.launch {
            val updated = contact.copy(lastCallTimestamp = System.currentTimeMillis())
            contactDao.update(updated)
            app.syncManager.sendData("UPDATE_CONTACT", updated)
        }
    }

    private suspend fun mergeDatabaseContactsInternal(): Int {
        var mergedCount = 0
        val allContacts = contactDao.getAllContactsAsList()
        val grouped = allContacts.groupBy { com.serkantken.secuasist.utils.ContactUtils.normalizePhoneNumber(it.contactPhone ?: "") }
        
        grouped.forEach { (normalized, contacts) ->
            if (contacts.size > 1 && normalized.length >= 10) {
                val primary = contacts.maxByOrNull { contact -> 
                    villaContactDao.getVillaAssociationsCount(contact.contactId)
                } ?: contacts.first()
                
                val duplicates = contacts.filter { it.contactId != primary.contactId }
                
                duplicates.forEach { duplicate ->
                    val relationships = villaContactDao.getRelationshipsByContactId(duplicate.contactId)
                    relationships.forEach { rel ->
                        val existingRelations = villaContactDao.getRelationshipsByContactId(primary.contactId)
                        val isAlreadyLinked = existingRelations.any { it.villaId == rel.villaId }
                        
                        if (!isAlreadyLinked) {
                            val newRel = rel.copy(contactId = primary.contactId, updatedAt = System.currentTimeMillis())
                            villaContactDao.insert(newRel)
                            app.syncManager.sendData("ADD_VILLA_CONTACT", newRel)
                        }
                    }
                    contactDao.delete(duplicate)
                    app.syncManager.sendData("DELETE_CONTACT", mapOf("contactId" to duplicate.contactId))
                    mergedCount++
                }
            }
        }
        return mergedCount
    }
}
