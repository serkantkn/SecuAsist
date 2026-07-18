package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.models.Villa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.serkantken.secuasist.models.Contact

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val villaDao = (application as SecuAsistApplication).db.villaDao()
    private val contactDao = (application as SecuAsistApplication).db.contactDao()
    private val villaContactDao = (application as SecuAsistApplication).db.villaContactDao()
    private val app = application as SecuAsistApplication
    private val intercomDao = app.db.intercomDao()
    // Update State
    val isUpdateAvailable = app.updateManager.isUpdateAvailable.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val latestVersionInfo = app.updateManager.latestVersionInfo.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    suspend fun getVillasForContact(contactId: String) = villaContactDao.getVillasForContact(contactId)

    // Arama metni
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredResults = _searchQuery.flatMapLatest { query ->
        if (query.isEmpty()) villaDao.getAllVillas() else villaDao.searchVillas("%$query%")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        if (query.all { it.isDigit() }) {
            _searchQuery.value = query
        }
    }

    fun refresh() {
        // Disabled since sync is removed
    }

    fun addVilla(villaNo: Int, street: String) {
        // Legacy support or can be removed if unused
        // But better to redirect to saveNewVilla
        val newVilla = Villa(
             villaNo = villaNo,
             villaStreet = street,
             villaNotes = null,
             villaNavigationA = null,
             villaNavigationB = null
        )
        saveNewVilla(newVilla)
    }

    fun saveNewVilla(villa: Villa) {
        viewModelScope.launch {
            val id = villaDao.insert(villa)
            
            // Add Default Intercom Fault Item
            val intercom = com.serkantken.secuasist.models.Intercom(
                villaId = id.toInt(),
                intercomName = "Dış Kapı" // Default name for manual add too
            )
            intercomDao.insert(intercom)
        }
    }
    
    fun updateVilla(villa: Villa) {
        viewModelScope.launch {
            villaDao.update(villa)
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            contactDao.update(contact)
        }
    }

    fun deleteVilla(villa: Villa) {
        viewModelScope.launch {
            villaDao.delete(villa)
        }
    }

    // All available contacts for selection
    val allContacts = contactDao.getAllContactsAsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Contact>()
    )

    fun getContactsForVilla(villaId: Int) = villaContactDao.getContactsForVilla(villaId)

    fun addContactToVilla(villaId: Int, contact: com.serkantken.secuasist.models.Contact, isOwner: Boolean, type: String) {
        viewModelScope.launch {
            val link = com.serkantken.secuasist.models.VillaContact(
                villaId = villaId,
                contactId = contact.contactId,
                isRealOwner = if (isOwner) 1 else 0,
                contactType = type,
                notes = null
            )
            villaContactDao.insert(link)
        }
    }

    fun removeContactFromVilla(villaId: Int, contactId: String) {
        viewModelScope.launch {
            villaContactDao.deleteByVillaIdAndContactId(villaId, contactId)
        }
    }



    // ...


}
