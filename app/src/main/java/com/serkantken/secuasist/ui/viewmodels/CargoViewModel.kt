package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CargoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SecuAsistApplication
    private val cargoDao = app.db.cargoDao()
    private val cargoCompanyDao = app.db.cargoCompanyDao()
    private val villaDao = app.db.villaDao()
    private val villaContactDao = app.db.villaContactDao()

    // --- New Entry Tab State ---
    val companies = cargoCompanyDao.getAllCargoCompanies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (cargoCompanyDao.getAllCompaniesAsList().isEmpty()) {
                val defaults = listOf(
                    "Yurtiçi Kargo", "Aras Kargo", "MNG Kargo", "Sürat Kargo", 
                    "PTT Kargo", "UPS Kargo", "DHL", "HepsiJet", 
                    "Trendyol Express", "Kolay Gelsin"
                ).map { CargoCompany(companyName = it, isCargoInOperation = 1) }
                cargoCompanyDao.insertAll(defaults)
            }
        }
    }

    fun addCompany(name: String) {
        viewModelScope.launch {
            val newCompany = CargoCompany(companyName = name, isCargoInOperation = 1)
            // 1. Local
            val id = cargoCompanyDao.insert(newCompany)
            // 2. Sync (Assuming local ID is temporary or syncing by name if ID=0, but here let's assume simple sync)
            // If ID is autogen, we send it.
            app.wsClient.sendData("ADD_COMPANY", newCompany.copy(companyId = id.toInt()))
        }
    }

    fun updateCompany(company: CargoCompany) {
        viewModelScope.launch {
            cargoCompanyDao.update(company)
            app.wsClient.sendData("UPDATE_COMPANY", company)
        }
    }

    fun deleteCompany(company: CargoCompany) {
        viewModelScope.launch {
            cargoCompanyDao.delete(company)
            app.wsClient.sendData("DELETE_COMPANY", mapOf("companyId" to company.companyId))
        }
    }
    
    // Deliverer Management
    private val companyDelivererDao = app.db.companyDelivererDao()
    
    fun getDeliverersForCompany(companyId: Int) = companyDelivererDao.getDeliverersForCompanyFlow(companyId)

    fun addDelivererToCompany(companyId: Int, contact: Contact, isPrimary: Boolean = false) {
        viewModelScope.launch {
            val crossRef = com.serkantken.secuasist.models.CompanyDelivererCrossRef(
                companyId = companyId,
                contactId = contact.contactId,
                isPrimaryContact = if (isPrimary) 1 else 0
            )
            companyDelivererDao.addDelivererToCompany(crossRef)
            app.wsClient.sendData("ADD_COMPANY_CONTACT", mapOf(
                "companyId" to companyId,
                "contactId" to contact.contactId,
                "isPrimaryContact" to (if (isPrimary) 1 else 0)
            ))
        }
    }
    
    // Pass entire Contact object to make it easier for UI or just ID
    fun removeDelivererFromCompany(companyId: Int, contactId: String) {
        viewModelScope.launch {
            companyDelivererDao.removeDelivererFromCompany(companyId, contactId)
            app.wsClient.sendData("DELETE_COMPANY_CONTACT", mapOf(
                "companyId" to companyId,
                "contactId" to contactId
            ))
        }
    }

    private val _selectedVillas = MutableStateFlow<List<Villa>>(emptyList())
    val selectedVillas = _selectedVillas.asStateFlow()

    fun addVillaToSelection(villa: Villa) {
        val current = _selectedVillas.value
        if (!current.any { it.villaId == villa.villaId }) {
            _selectedVillas.value = current + villa
        }
    }

    fun removeVillaFromSelection(villa: Villa) {
        _selectedVillas.value = _selectedVillas.value.filter { it.villaId != villa.villaId }
    }
    
    fun clearSelection() {
        _selectedVillas.value = emptyList()
    }

    fun saveCargos(companyId: Int) {
        viewModelScope.launch {
            val villas = _selectedVillas.value
            if (villas.isEmpty()) return@launch

            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            
            val newCargos = villas.map { villa ->
                Cargo(
                    companyId = companyId,
                    villaId = villa.villaId,
                    whoCalled = null,
                    isCalled = 0, // Not called yet
                    isMissed = 0,
                    date = now,
                    callDate = null
                )
            }
            // 1. Save Local
            val ids = cargoDao.insertAll(newCargos)
            
            // 2. Sync to Server (Assign generated IDs if possible, or reload)
            // Ideally we need the IDs generated by Room. insertAll returns List<Long>.
            // We can iterate and send.
            ids.forEachIndexed { index, id ->
                val cargoToSend = newCargos[index].copy(cargoId = id.toInt())
                app.wsClient.sendData("ADD_CARGO", cargoToSend)
            }

            clearSelection() // Reset UI
        }
    }

    // --- Villa Picker State ---
    private val _villaSearchQuery = MutableStateFlow("")
    val villaSearchQuery = _villaSearchQuery.asStateFlow()
    
    // Convert to StateFlow to access .value for text import
    val allVillas = villaDao.getAllVillas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val filteredVillasToPick = combine(allVillas, _villaSearchQuery) { villas, query ->
        if (query.isEmpty()) {
            villas
        } else {
            villas.filter {
                it.villaNo.toString().contains(query) ||
                (it.villaStreet?.contains(query, ignoreCase = true) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateVillaSearchQuery(query: String) {
        _villaSearchQuery.value = query
    }

    // --- Operations / Distribution Tab State ---
    // Pending cargos: Not called yet (isCalled=0) OR Missed (isMissed=1 - retrying?)
    // Requirement says "ordered for search operation". PendingCargos query is ordered by date ASC usually to handle oldest first.


    // Call Management
    fun getContactsForVilla(villaId: Int) = villaContactDao.getContactsForVilla(villaId)
    
    // For Deliverer Picker
    val allContacts: kotlinx.coroutines.flow.StateFlow<List<Contact>> = app.db.contactDao().getAllContactsAsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- REPORTING ---
    private val _reportFilterDateStart = MutableStateFlow<String?>(null)
    private val _reportFilterDateEnd = MutableStateFlow<String?>(null)
    private val _reportFilterCompanyId = MutableStateFlow<Int?>(null)
    private val _reportFilterVillaId = MutableStateFlow<Int?>(null)

    // Helper to format Date for query if needed, keeping simple string for now (YYYY-MM-DD)
    
    fun setReportFilters(startDate: String?, endDate: String?, companyId: Int?, villaId: Int?) {
        _reportFilterDateStart.value = startDate
        _reportFilterDateEnd.value = endDate
        _reportFilterCompanyId.value = companyId
        _reportFilterVillaId.value = villaId
    }

    val cargoReports = combine(
        _reportFilterDateStart, _reportFilterDateEnd, _reportFilterCompanyId, _reportFilterVillaId
    ) { start, end, company, villa ->
        // Return Flow
        cargoDao.getCargoReportDetailsFiltered(start, end, company, villa)
    }.flatMapLatest { it } // flattening Flow<Flow<List>> to Flow<List>
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- VILLA CHECKS ---
    suspend fun hasContacts(villaId: Int): Boolean {
        // Simple check, or expose a flow. Since this is for alert logic (one-off), suspend is fine.
        return villaContactDao.getContactCountForVilla(villaId) > 0
    }
    
    // Auto-advance logic implies UI observing the list.
    // If we update status, the list (pendingCargos) automatically updates because it's filtered (isCalled=0).
    // So "Next" just happens.
    // "Skip" means? Maybe move to end of list? Or just ignore for now and user manually picks next?
    // User asked "Atla" (Skip) button. If clicked, maybe we just hide it temporarily or do nothing but proceed UI focus?
    // Re-reading: "Order by date ASC". Skipping means it stays at top. 
    // If "Skip" means "Don't call now, do it later", then we should probably just proceed to NEXT item in UI list without updating DB (effectively valid since list is list).
    // BUT the requirement Says "Sırada bir villa yoksa sayfa kapansın". This implies "Next" logic is strict.
    // If I skip, it remains in pending list. I should probably just show the next one in the list?
    // The UI `ActiveCallSection` gets `cargos.firstOrNull()`.
    // If I skip, I want the SECOND one to be `nextCargo`.
    // So I need a local "skippedIds" list in UI or ViewModel to filter them out temporarily.
    
    // SKIPPED CARGOS STATE
    private val _skippedCargoIds = MutableStateFlow<Set<Int>>(emptySet())
    val skippedCargoIds = _skippedCargoIds.asStateFlow()

    fun skipCargo(cargoId: Int) {
        _skippedCargoIds.value = _skippedCargoIds.value + cargoId
    }

    // Update pendingCargos to exclude skipped ones
    val pendingCargos = combine(
        cargoDao.getPendingCargosWithDetails(),
        _skippedCargoIds
    ) { cargos, skipped ->
        cargos.filter { it.cargo.cargoId !in skipped }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateCargoStatus(cargo: Cargo, isCalled: Boolean, isMissed: Boolean, whoCalledId: String?) {
        viewModelScope.launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val updatedCargo = cargo.copy(
                isCalled = if (isCalled) 1 else 0,
                isMissed = if (isMissed) 1 else 0,
                callDate = now,
                callAttemptCount = cargo.callAttemptCount + 1,
                whoCalled = whoCalledId
            )
            
            // 1. Local Update
            cargoDao.updateCargoCallStatus(
                cargoId = cargo.cargoId, 
                isCalled = if (isCalled) 1 else 0, 
                isMissed = if (isMissed) 1 else 0, 
                callDate = now, 
                callAttemptCount = cargo.callAttemptCount + 1, 
                whoCalledId = whoCalledId
            )
            
            
            // 2. Sync Update
            app.wsClient.sendData("UPDATE_CARGO_STATUS", updatedCargo)
        }
    }

    // --- TEXT IMPORT LOGIC ---
    
    data class TextImportResult(
        val foundVillas: List<Villa>,
        val notFoundNumbers: List<String>,
        val alreadyAdded: List<Villa>,
        val foundByNames: Map<Int, String> = emptyMap(), // Verified matches
        val unverifiedContext: Map<Int, String> = emptyMap() // Unverified hints (text found near number)
    )
    
    // Returns a result summary for the UI to display
    suspend fun parseAndAddVillasFromText(text: String): TextImportResult {
        // 1. Regex to find numbers and potential following words
        // Matches: "12", "12 Ahmet", "Villa 12", "12. Ahmet"
        // We catch the number and up to 2 words after it
        val numberContextRegex = Regex("(?<number>\\d+)(?:\\s+|\\.|\\-)+((?:[a-zA-ZğüşıöçĞÜŞİÖÇ]+\\s*){1,2})?")
        
        val matches = numberContextRegex.findAll(text)
        val potentialNumbers = mutableMapOf<Int, String?>() // Number -> Potential Name
        
        matches.forEach { match ->
             val numStr = match.groups["number"]?.value
             val context = match.groups[2]?.value?.trim()
             numStr?.toIntOrNull()?.let { num ->
                 potentialNumbers[num] = context
             }
        }
        
        // Also ensure we catch standalone numbers that regex might have missed if complex (though regex above is greedy on number part)
        // Actually, simple regex \\d+ might find more. Let's merge.
        val simpleNumbers = Regex("\\b\\d+\\b").findAll(text).mapNotNull { it.value.toIntOrNull() }
        simpleNumbers.forEach { if (!potentialNumbers.containsKey(it)) potentialNumbers[it] = null }

        // Start processing
        // Ensure villas are loaded
        var villas = allVillas.value
        if (villas.isEmpty()) {
             villas = villaDao.getAllVillasAsList()
        }
        val allVillasMap = villas.associateBy { it.villaNo } 
        
        val found = mutableListOf<Villa>()
        val notFoundNumbers = mutableListOf<String>()
        val already = mutableListOf<Villa>()
        val currentSelectionIds = _selectedVillas.value.map { it.villaId }.toSet()
        val foundByNamesMap = mutableMapOf<Int, String>()
        val unverifiedContextMap = mutableMapOf<Int, String>()

        val textLower = text.lowercase()

        // 2. Process Numbers
        potentialNumbers.forEach { (number, contextText) ->
            val villa = allVillasMap[number]
            if (villa != null) {
                // Check if already selected
                if (currentSelectionIds.contains(villa.villaId)) {
                    already.add(villa)
                } else {
                    // Check Verification
                    val contacts = villaContactDao.getContactsForVillaNonFlow(villa.villaId)
                    var verifiedName: String? = null
                    
                    // A. Check against captured context specific to this number (High confidence)
                    if (contextText != null && contextText.length > 2) {
                        // Check if this context matches any contact
                        for (contact in contacts) {
                            val cName = contact.contactName ?: ""
                            if (cName.contains(contextText, ignoreCase = true) || contextText.contains(cName, ignoreCase = true)) {
                                verifiedName = cName
                                break
                            }
                        }
                    }
                    
                    // B. Fallback: Check if ANY contact name exists ANYWHERE in text (Legacy logic, helpful for "Ahmet Villa 12")
                    if (verifiedName == null) {
                         for (contact in contacts) {
                             val fullName = contact.contactName ?: ""
                             if (fullName.isNotBlank()) {
                                 val parts = fullName.split("\\s+".toRegex()).filter { it.length > 2 }
                                 val matchedPart = parts.find { part -> textLower.contains(part.lowercase()) }
                                 if (matchedPart != null) {
                                     verifiedName = fullName
                                     break
                                 }
                             }
                        }
                    }
                    
                    found.add(villa)
                    if (verifiedName != null) {
                        foundByNamesMap[villa.villaId] = verifiedName
                    } else if (contextText != null && contextText.isNotBlank()) {
                        // Unverified but has context
                        unverifiedContextMap[villa.villaId] = contextText
                    }
                }
            } else {
                notFoundNumbers.add(number.toString())
            }
        }
        
        return TextImportResult(found, notFoundNumbers, already, foundByNamesMap, unverifiedContextMap)
    }
    
    fun confirmImport(villas: List<Villa>) {
        val current = _selectedVillas.value
        val newSelection = (current + villas).distinctBy { it.villaId }
        _selectedVillas.value = newSelection
    }
}
