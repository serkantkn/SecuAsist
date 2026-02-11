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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactDao = (application as SecuAsistApplication).db.contactDao()
    private val app = application as SecuAsistApplication

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _contacts = contactDao.getAllContactsAsFlow()

    val filteredContacts = combine(_contacts, _searchQuery) { contacts, query ->
        if (query.isEmpty()) {
            contacts.sortedBy { it.contactName }
        } else {
            contacts.filter {
                (it.contactName?.contains(query, ignoreCase = true) == true) ||
                (it.contactPhone?.contains(query) == true)
            }.sortedBy { it.contactName }
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
            app.wsClient.sendData("ADD_CONTACT", newContact)
        }
    }
    
    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactDao.delete(contact)
            val payload = mapOf("contactId" to contact.contactId)
            app.wsClient.sendData("DELETE_CONTACT", payload)
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            val updatedContact = contact.copy(updatedAt = System.currentTimeMillis())
            contactDao.update(updatedContact)
            app.wsClient.sendData("UPDATE_CONTACT", updatedContact)
        }
    }

    // New dependency needed for Villa lookup
    private val villaDao = (application as SecuAsistApplication).db.villaDao()
    private val villaContactDao = (application as SecuAsistApplication).db.villaContactDao()

    fun importContactsFromDevice(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val deviceContacts = com.serkantken.secuasist.utils.ContactUtils.fetchDeviceContacts(context)
                
                deviceContacts.forEach { deviceContact ->
                    // 1. Insert Contact
                    // Check if exists
                    val existingContact = contactDao.getContactByPhoneNumber(deviceContact.phone)
                    
                    val contactId = if (existingContact != null) {
                        existingContact.contactId
                    } else {
                        val newContact = Contact(
                            contactName = deviceContact.name, // Use parsed name (without villa no prefix)
                            contactPhone = deviceContact.phone
                        )
                        contactDao.insert(newContact)
                        
                        // Sync with Server
                        app.wsClient.sendData("ADD_CONTACT", newContact)
                        
                        newContact.contactId
                    }

                    // 2. Link to Villa if villaNo detected
                    if (deviceContact.potentialVillaNo != null) {
                        val villa = villaDao.getVillaByNo(deviceContact.potentialVillaNo)
                        if (villa != null) {
                            // Check if already linked
                            val existingLink = villaContactDao.getContactsForVillaNonFlow(villa.villaId).find { it.contactId == contactId }
                            
                            if (existingLink == null) {
                                val link = com.serkantken.secuasist.models.VillaContact(
                                    villaId = villa.villaId,
                                    contactId = contactId,
                                    isRealOwner = false, // Default assumption
                                    contactType = "Tenant", // Default
                                    notes = "Otomatik İçe Aktarıldı"
                                )
                                villaContactDao.insert(link)
                                app.wsClient.sendData("ADD_VILLA_CONTACT", link)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getLinkedVillas(contactId: String): List<Villa> {
        return villaContactDao.getVillasForContact(contactId)
    }

    fun updateLastCall(contact: Contact) {
        viewModelScope.launch {
            val updated = contact.copy(lastCallTimestamp = System.currentTimeMillis())
            contactDao.update(updated)
            app.wsClient.sendData("UPDATE_CONTACT", updated)
        }
    }
}
