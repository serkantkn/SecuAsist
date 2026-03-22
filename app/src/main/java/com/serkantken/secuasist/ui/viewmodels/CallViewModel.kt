package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import android.telecom.Call
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaContact
import com.serkantken.secuasist.services.CallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CallUiState(
    val call: Call? = null,
    val callState: Int = Call.STATE_NEW,
    val contact: Contact? = null,
    val villa: Villa? = null,
    val contactType: String? = null,
    val phoneNumber: String = "Bilinmeyen Numara",
    val isSearching: Boolean = true,
    val missedCargoCompanies: List<String> = emptyList()
)

class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    
    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            CallManager.currentCall.collect { callInfo ->
                if (callInfo == null) {
                    _uiState.update { 
                        it.copy(
                            call = null, 
                            callState = Call.STATE_DISCONNECTED,
                            isSearching = false
                        ) 
                    }
                    return@collect
                }

                val call = callInfo.call
                val state = callInfo.state
                val number = callInfo.number ?: "Bilinmeyen Numara"
                
                // If number changed, we need to fetch contact
                if (number != _uiState.value.phoneNumber) {
                    _uiState.update { it.copy(call = call, callState = state, phoneNumber = number, isSearching = true) }
                    
                    if (number != "Bilinmeyen Numara") {
                        fetchContactAndVilla(number)
                    } else {
                        _uiState.update { it.copy(isSearching = false) }
                    }
                } else {
                    // Update state to trigger flow collection updates in UI
                    _uiState.update { it.copy(call = call, callState = state) }
                }

                // Check for Call.STATE_ACTIVE to clear missed cargos
                if (state == Call.STATE_ACTIVE) {
                    val currentVilla = _uiState.value.villa
                    if (currentVilla != null && _uiState.value.missedCargoCompanies.isNotEmpty()) {
                        // Launch in Coroutine
                        launch(Dispatchers.IO) {
                            db.cargoDao().clearMissedCargosForVillaToday(currentVilla.villaId)
                            _uiState.update { it.copy(missedCargoCompanies = emptyList()) }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchContactAndVilla(phoneNumber: String) {
        // Clean phone number for better matching (strip all non-digits)
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        // Exact matches first
        var contact = db.contactDao().getContactByPhoneNumber(phoneNumber) 
            ?: db.contactDao().getContactByPhoneNumber(cleanNumber)
            ?: db.contactDao().getContactByPhoneNumber("+$cleanNumber")
            ?: db.contactDao().getContactByPhoneNumber("0$cleanNumber")

        // Loose match if exact fails and number is long enough
        if (contact == null && cleanNumber.length >= 10) {
            val searchNumber = if (cleanNumber.startsWith("90")) cleanNumber.substring(2) 
                else if (cleanNumber.startsWith("0")) cleanNumber.substring(1) 
                else cleanNumber
            
            contact = db.contactDao().findContactByPhoneLike(searchNumber)
        }

        var villa: Villa? = null
        var contactType: String? = null
        
        if (contact != null) {
            val villas = db.villaContactDao().getVillasForContact(contact.contactId)
            if (villas.isNotEmpty()) {
                villa = villas.first()
                // Simple workaround to get contact type from db directly using a query or mapping
                // For now, we don't have a direct getVillaContact(villaId, contactId) without contactType in DAO,
                // so we can just query all relations for this villa and filter.
                val relations = db.villaContactDao().getVillaContactRelations(villa.villaId)
                contactType = relations.find { it.contactId == contact.contactId }?.contactType
            }
        }

        _uiState.update { 
            it.copy(
                contact = contact, 
                villa = villa,
                contactType = contactType,
                missedCargoCompanies = if (villa != null) db.cargoDao().getMissedCargoCompanyNamesToday(villa.villaId) else emptyList(),
                isSearching = false
            ) 
        }
    }
}
