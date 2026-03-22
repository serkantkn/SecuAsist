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

    suspend fun getVillasForContact(contactId: String) = villaContactDao.getVillasForContact(contactId)

    sealed class HomeSearchResult {
        data class VillaResult(val villa: Villa) : HomeSearchResult()
        data class ContactResult(val contact: Contact) : HomeSearchResult()
    }

    // Expose WebSocket Connection State
    val connectionState = app.wsClient.connectionState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.serkantken.secuasist.network.ConnectionState.DISCONNECTED
    )

    // Arama metni
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredResults = _searchQuery.flatMapLatest { query ->
        if (query.isEmpty() || !query.any { it.isLetter() }) {
            val dbFlow = if (query.isEmpty()) villaDao.getAllVillas() else villaDao.searchVillas("%$query%")
            dbFlow.map { villas -> villas.map { HomeSearchResult.VillaResult(it) } }
        } else {
            contactDao.searchContacts("%$query%").map { contacts ->
                contacts.map { HomeSearchResult.ContactResult(it) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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
            // Sync to Server
            // If ID is 0, Room generates it. We should use the returned ID.
            val villaToSend = villa.copy(villaId = id.toInt())
            app.wsClient.sendData("ADD_VILLA", villaToSend)
            
            // Add Default Intercom Fault Item
            val intercom = com.serkantken.secuasist.models.Intercom(
                villaId = id.toInt(),
                intercomName = "Dış Kapı" // Default name for manual add too
            )
            intercomDao.insert(intercom)
            app.wsClient.sendData("ADD_INTERCOM", intercom)
        }
    }
    
    fun updateVilla(villa: Villa) {
        viewModelScope.launch {
            villaDao.update(villa)
            val updatedVilla = villa.copy(updatedAt = System.currentTimeMillis())
            app.wsClient.sendData("UPDATE_VILLA", updatedVilla)
        }
    }

    fun deleteVilla(villa: Villa) {
        viewModelScope.launch {
            villaDao.delete(villa)
            // Sync Delete
            val payload = mapOf("villaId" to villa.villaId)
            app.wsClient.sendData("DELETE_VILLA", payload)
        }
    }

    // All available contacts for selection
    val allContacts = contactDao.getAllContactsAsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun getContactsForVilla(villaId: Int) = villaContactDao.getContactsForVilla(villaId)

    fun addContactToVilla(villaId: Int, contact: com.serkantken.secuasist.models.Contact, isOwner: Boolean, type: String) {
        viewModelScope.launch {
            val link = com.serkantken.secuasist.models.VillaContact(
                villaId = villaId,
                contactId = contact.contactId,
                isRealOwner = isOwner,
                contactType = type,
                notes = null
            )
            villaContactDao.insert(link)
            app.wsClient.sendData("ADD_VILLA_CONTACT", link)
        }
    }

    fun removeContactFromVilla(villaId: Int, contactId: String) {
        viewModelScope.launch {
            villaContactDao.deleteByVillaIdAndContactId(villaId, contactId)
            val payload = mapOf("villaId" to villaId, "contactId" to contactId)
            app.wsClient.sendData("DELETE_VILLA_CONTACT", payload)
        }
    }



    // ...

    fun importVillasFromCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                app.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val villas = com.serkantken.secuasist.utils.CsvUtils.parseVillasFromCsv(inputStream)
                    if (villas.isNotEmpty()) {
                        val ids = villaDao.insertAll(villas)
                        // Sync each imported villa
                        villas.forEachIndexed { index, villa ->
                            val villaId = ids[index].toInt()
                            // 1. Sync Villa
                            val villaToSend = villa.copy(villaId = villaId)
                            app.wsClient.sendData("ADD_VILLA", villaToSend)
                            
                            // 2. Add Default Intercom Fault Item
                            val intercom = com.serkantken.secuasist.models.Intercom(
                                villaId = villaId,
                                intercomName = "Dış Kapı" // Default name
                            )
                            intercomDao.insert(intercom)
                            app.wsClient.sendData("ADD_INTERCOM", intercom)
                        }
                        
                        // Show success message
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(app, "${villas.size} villa başarıyla eklendi.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Empty or parse error
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(app, "CSV dosyasından villa okunamadı veya dosya boş.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(app, "Hata oluştu: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
