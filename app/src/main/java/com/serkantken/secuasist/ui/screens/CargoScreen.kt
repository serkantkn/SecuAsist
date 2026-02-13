package com.serkantken.secuasist.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Collections // Use Collections instead
import androidx.compose.material.icons.filled.Search

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.ui.viewmodels.CargoViewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.ContentCopy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun CargoScreen(viewModel: CargoViewModel = viewModel()) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pendingCargos by viewModel.pendingCargos.collectAsState()
    val hasPendingCargos = pendingCargos.isNotEmpty()
    
    val tabs = listOf("Yeni Kargo", "Kargo Arama")
    var showReportScreen by remember { mutableStateOf(false) }

    if (showReportScreen) {
        CargoReportScreen(viewModel = viewModel, onBack = { showReportScreen = false })
    } else {
        Scaffold(
            topBar = {
                Column {
                    // Custom Header with Report Button
                    TopAppBar(
                        title = { Text("Kargo Takip") },
                        actions = {
                            IconButton(onClick = { showReportScreen = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Raporlar")
                            }
                        }
                    )
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(title)
                                        if (index == 1 && hasPendingCargos) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ) {
                                                Text(pendingCargos.size.toString())
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            },
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars)
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                if (selectedTabIndex == 0) {
                    NewCargoEntryScreen(viewModel)
                } else {
                    CargoDistributionScreen(viewModel)
                }
            }
        }
    }
}

// --- TAB 1: NEW CARGO ENTRY ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewCargoEntryScreen(viewModel: CargoViewModel) {
    val companies by viewModel.companies.collectAsState()
    val selectedVillas by viewModel.selectedVillas.collectAsState()
    val scope = rememberCoroutineScope()
    
    var selectedCompany by remember { mutableStateOf<CargoCompany?>(null) }
    var showCompanyDropdown by remember { mutableStateOf(false) }
    
    var showVillaPicker by remember { mutableStateOf(false) }
    
    // Alert logic
    var villaToConfirm by remember { mutableStateOf<Villa?>(null) }
    var confirmationMessage by remember { mutableStateOf("") }
    
    if (villaToConfirm != null) {
        AlertDialog(
            onDismissRequest = { villaToConfirm = null },
            title = { Text("Uyarı") },
            text = { Text(confirmationMessage) },
            confirmButton = {
                TextButton(onClick = {
                    villaToConfirm?.let { viewModel.addVillaToSelection(it) }
                    villaToConfirm = null
                }) { Text("Evet, Ekle") }
            },
            dismissButton = {
                TextButton(onClick = { villaToConfirm = null }) { Text("İptal") }
            }
        )
    }

    var showAddCompanyDialog by remember { mutableStateOf(false) }
    var showManageCompaniesDialog by remember { mutableStateOf(false) }

    if (showManageCompaniesDialog) {
        ManageCompaniesDialog(
            viewModel = viewModel,
            onDismiss = { showManageCompaniesDialog = false }
        )
    }
    
    // Import Dialog State
    var showImportDialog by remember { mutableStateOf(false) }
    
    // Photo Picker for OCR
    var importedText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        importedText = visionText.text
                        showImportDialog = true
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        // Optional: Show Toast
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showImportDialog) {
        ImportTextDialog(
            viewModel = viewModel,
            initialText = importedText,
            onDismiss = { 
                showImportDialog = false 
                importedText = "" 
            },
            onConfirm = { villas ->
                viewModel.confirmImport(villas)
                showImportDialog = false
                importedText = ""
            }
        )
    }

    if (showAddCompanyDialog) {
        var newCompanyName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCompanyDialog = false },
            title = { Text("Yeni Kargo Firması Ekle") },
            text = { 
                OutlinedTextField(
                    value = newCompanyName, 
                    onValueChange = { newCompanyName = it },
                    label = { Text("Firma Adı") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCompanyName.isNotBlank()) {
                         viewModel.addCompany(newCompanyName)
                         showAddCompanyDialog = false
                    }
                }) { Text("Ekle") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCompanyDialog = false }) { Text("İptal") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Select Company
        ExposedDropdownMenuBox(
            expanded = showCompanyDropdown,
            onExpandedChange = { showCompanyDropdown = it }
        ) {
            OutlinedTextField(
                value = selectedCompany?.companyName ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Kargo Firması") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCompanyDropdown) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = showCompanyDropdown,
                onDismissRequest = { showCompanyDropdown = false }
            ) {
                companies.forEach { company ->
                    DropdownMenuItem(
                        text = { Text(company.companyName ?: "") },
                        onClick = {
                            selectedCompany = company
                            showCompanyDropdown = false
                        }
                    )
                }
                Divider()
                DropdownMenuItem(
                    text = { Text("➕ Yeni Firma Ekle") },
                    onClick = {
                        showCompanyDropdown = false
                        showAddCompanyDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("⚙️ Firmaları Yönet") },
                    onClick = {
                        showCompanyDropdown = false
                        showManageCompaniesDialog = true
                    }
                )
            }
        }

        // 2. Select Villas Button, Paste Button, Scan Button (Vertical Stack)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Button(
                onClick = { showVillaPicker = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedCompany != null
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manuel Ekle")
            }
            
            Button(
                onClick = { 
                    importedText = ""
                    showImportDialog = true 
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedCompany != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                 Icon(Icons.Default.Edit, contentDescription = null)
                 Spacer(modifier = Modifier.width(8.dp))
                 Text("Panodan Yapıştır")
            }
            
            Button(
                onClick = { 
                     photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedCompany != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                 Icon(Icons.Default.Collections, contentDescription = null) // Use Collections (Gallery)
                 Spacer(modifier = Modifier.width(8.dp))
                 Text("Galeriden Yükle (OCR)")
            }
        }
        


        // 3. Selected Villas List
        if (selectedVillas.isNotEmpty()) {
            Text("Seçilen Villalar (${selectedVillas.size}):", style = MaterialTheme.typography.titleMedium)
            
            FlowRow(
                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                 verticalArrangement = Arrangement.spacedBy(8.dp),
                 modifier = Modifier.fillMaxWidth()
            ) {
                selectedVillas.forEach { villa ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.removeVillaFromSelection(villa) },
                        label = { Text("Villa ${villa.villaNo}") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Kaldır", modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            
            // 4. Save Button
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    selectedCompany?.let { 
                        viewModel.saveCargos(it.companyId)
                        selectedCompany = null // Reset selection loop if needed, or keep company
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kargoları Kaydet")
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    if (showVillaPicker) {
        VillaPickerSheet(
            viewModel = viewModel,
            onDismiss = { showVillaPicker = false },
            onVillaSelected = { villa ->
                // Check restrictions
                scope.launch {
                    val hasContacts = viewModel.hasContacts(villa.villaId)
                    
                    if (villa.isVillaCallForCargo == 0) {
                        confirmationMessage = "Villa ${villa.villaNo} kargo için aranmak istemiyor. Yine de eklemek ister misiniz?"
                        villaToConfirm = villa
                    } else if (villa.isVillaEmpty == 1) {
                         confirmationMessage = "Villa ${villa.villaNo} BOŞ olarak işaretlenmiş. Yine de eklemek ister misiniz?"
                         villaToConfirm = villa
                    } else if (!hasContacts) {
                         confirmationMessage = "Villa ${villa.villaNo} için kayıtlı kişi (kontak) yok. Yine de eklemek ister misiniz?"
                         villaToConfirm = villa
                    } else {
                        viewModel.addVillaToSelection(villa)
                    }
                }
            }
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VillaPickerSheet(
    viewModel: CargoViewModel,
    onDismiss: () -> Unit,
    onVillaSelected: (Villa) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val villasToPick by viewModel.filteredVillasToPick.collectAsState()
    val searchQuery by viewModel.villaSearchQuery.collectAsState()
    
    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    
    // When opening, maybe clear query?
    LaunchedEffect(Unit) {
        viewModel.updateVillaSearchQuery("")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Villa Seç", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateVillaSearchQuery(it) },
                    label = { Text("Villa Ara (No veya Sokak)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(villasToPick, key = { it.villaId }) { villa ->
                        ListItem(
                            headlineContent = { Text("Villa ${villa.villaNo}") },
                            supportingContent = if (villa.isVillaCallForCargo == 0) {
                                { Text("⚠️ Kargo için aranmak istemiyor", color = MaterialTheme.colorScheme.error) }
                            } else null,
                             modifier = Modifier.clickable { 
                                  // Warning Logic
                                  CoroutineScope(Dispatchers.Main).launch {
                                      val hasContacts = viewModel.hasContacts(villa.villaId)
                                      if (villa.isVillaCallForCargo == 0) {
                                          onVillaSelected(villa)
                                      } else if (villa.isVillaEmpty == 1) {
                                           onVillaSelected(villa) 
                                      } else if (!hasContacts) {
                                          onVillaSelected(villa)
                                      } else {
                                          onVillaSelected(villa)
                                      }
                                  }
                             },
                            trailingContent = {
                                Icon(Icons.Default.Add, contentDescription = "Ekle")
                            }
                        )
                        Divider()
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            com.serkantken.secuasist.ui.components.ScrollToTopButton(
                visible = showScrollToTop,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}

@Composable
fun ManageCompaniesDialog(
    viewModel: CargoViewModel,
    onDismiss: () -> Unit
) {
    val companies by viewModel.companies.collectAsState()
    var editingCompany by remember { mutableStateOf<CargoCompany?>(null) }
    var managingDeliverersFor by remember { mutableStateOf<CargoCompany?>(null) }

    if (managingDeliverersFor != null) {
        ManageDeliverersDialog(
            company = managingDeliverersFor!!,
            viewModel = viewModel,
            onDismiss = { managingDeliverersFor = null }
        )
    }

    if (editingCompany != null) {
        var newName by remember { mutableStateOf(editingCompany?.companyName ?: "") }
        AlertDialog(
            onDismissRequest = { editingCompany = null },
            title = { Text("Şirket Adı Düzenle") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Firma Adı") })
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.updateCompany(editingCompany!!.copy(companyName = newName))
                        editingCompany = null
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { editingCompany = null }) { Text("İptal") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kargo Firmalarını Yönet") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(companies, key = { it.companyId }) { company ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(company.companyName ?: "", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Row {
                                    IconButton(onClick = { managingDeliverersFor = company }) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = "Dağıtıcılar")
                                    }
                                    IconButton(onClick = { editingCompany = company }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                                    }
                                    IconButton(onClick = { viewModel.deleteCompany(company) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}

@Composable
fun ManageDeliverersDialog(
    company: CargoCompany,
    viewModel: CargoViewModel,
    onDismiss: () -> Unit
) {
    val deliverers by viewModel.getDeliverersForCompany(company.companyId).collectAsState(initial = emptyList())
    var showContactPicker by remember { mutableStateOf(false) }

    if (showContactPicker) {
        ContactPickerDialog(
            viewModel = viewModel,
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                viewModel.addDelivererToCompany(company.companyId, contact)
                showContactPicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${company.companyName} - Dağıtıcılar") },
        text = {
            Column {
                Button(
                    onClick = { showContactPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rehberden Kişi Ekle")
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (deliverers.isEmpty()) {
                        item { Text("Henüz dağıtıcı eklenmemiş.", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(deliverers, key = { it.contactId }) { contact ->
                            ListItem(
                                headlineContent = { Text(contact.contactName ?: "İsimsiz") },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeDelivererFromCompany(company.companyId, contact.contactId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Kaldır")
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tamam") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerDialog(
    viewModel: CargoViewModel,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    val contacts by viewModel.allContacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter { 
            it.contactName?.contains(searchQuery, ignoreCase = true) == true || 
            it.contactPhone?.contains(searchQuery) == true 
        }
    }
    
    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Rehberden Kişi Seç", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                ) {
                    items(filteredContacts, key = { it.contactId }) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.contactName ?: "İsimsiz") },
                            supportingContent = { Text(contact.contactPhone ?: "") },
                            modifier = Modifier.clickable { onContactSelected(contact) }
                        )
                        Divider()
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            com.serkantken.secuasist.ui.components.ScrollToTopButton(
                visible = showScrollToTop,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}

// --- TAB 2: DISTRIBUTION ---
@Composable
fun CargoDistributionScreen(viewModel: CargoViewModel) {
    val pendingCargos by viewModel.pendingCargos.collectAsState()
    var selectedCompany by remember { mutableStateOf<CargoCompany?>(null) }

    // Group Key: CargoCompany. We assume distinct objects are equal if IDs match.
    // If not, we might need to group by ID or use a distinct list of companies.
    // CargoWithDetails contains 'company' object.
    val groupedCargos = remember(pendingCargos) {
        pendingCargos.groupBy { it.company }
    }
    
    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // Handle Back Press to clear selection if needed (not implemented here but good to have)

    if (selectedCompany == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Company List View
            if (groupedCargos.isEmpty()) {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Dağıtılacak kargo yok.", style = MaterialTheme.typography.bodyLarge)
                 }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groupedCargos.keys.toList(), key = { it.companyId }) { company ->
                        val count = groupedCargos[company]?.size ?: 0
                        Card(
                            onClick = { selectedCompany = company },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = company.companyName ?: "Bilinmeyen Firma",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Badge { Text(count.toString()) }
                            }
                        }
                    }
                }
            }
            
            com.serkantken.secuasist.ui.components.ScrollToTopButton(
                visible = showScrollToTop,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    } else {
        // 2. Company Detail View
        val companyCargos = groupedCargos[selectedCompany] ?: emptyList()
        // Ensure cargos are sorted by date/ID as per "sırası gelen" requirement
        // pendingCargos from DAO is likely sorted, but let's ensure stability.
        
        CompanyDetailScreen(
            company = selectedCompany!!,
            cargos = companyCargos,
            viewModel = viewModel,
            onBack = { selectedCompany = null }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDetailScreen(
    company: CargoCompany,
    cargos: List<com.serkantken.secuasist.models.CargoWithDetails>,
    viewModel: CargoViewModel,
    onBack: () -> Unit
) {
    val nextCargo = cargos.firstOrNull()
    
    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(company.companyName ?: "")
                        Text("${cargos.size} Teslimat", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        bottomBar = {
            if (nextCargo != null) {
                ActiveCallSection(
                    cargo = nextCargo,
                    viewModel = viewModel
                )
            } else {
                 Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                     Text("Tüm teslimatlar tamamlandı!")
                 }
            }
        },
        floatingActionButton = {
            com.serkantken.secuasist.ui.components.ScrollToTopButton(
                visible = showScrollToTop,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // List of Villas
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cargos, key = { it.cargo.cargoId }) { item ->
                    // Just Info Card, No actions
                    Card(
                        colors = if (item == cargos.firstOrNull()) 
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) // Highlight current
                        else CardDefaults.cardColors()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Villa ${item.villa.villaNo}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = item.cargo.date,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (item == cargos.firstOrNull()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text("Sırada", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallSection(
    cargo: com.serkantken.secuasist.models.CargoWithDetails,
    viewModel: CargoViewModel,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.getContactsForVilla(cargo.villa.villaId).collectAsState(initial = emptyList())
    var selectedContact by remember(cargo.cargo.cargoId) { mutableStateOf<Contact?>(null) }
    
    // Auto-select first contact
    LaunchedEffect(contacts) {
        if (selectedContact == null && contacts.isNotEmpty()) {
            selectedContact = contacts.first()
        }
    }
    
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        // Removed RoundedCornerShape to sit flush with bottom nav/edge
        shape = androidx.compose.ui.graphics.RectangleShape 
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Sıradaki: Villa ${cargo.villa.villaNo}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (contacts.isEmpty()) {
                Text("Bu villaya ait iletişim bilgisi yok.", color = MaterialTheme.colorScheme.error)
            } else {
                Text("Aranacak Kişi Seçin:", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    items(contacts) { contact ->
                        FilterChip(
                            selected = contact == selectedContact,
                            onClick = { selectedContact = contact },
                            label = { Text(contact.contactName ?: "İsimsiz") },
                            leadingIcon = if (contact == selectedContact) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                    }
                }
                
                Button(
                    onClick = {
                        selectedContact?.let { contact ->
                            // Trigger Call
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                data = android.net.Uri.parse("tel:${contact.contactPhone}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedContact != null
                ) {
                    Icon(Icons.Default.Call, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ara: ${selectedContact?.contactName ?: "..."}")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp)) // Add some spacing before actions

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                // Success Button
                TextButton(onClick = { 
                        selectedContact?.let { 
                            viewModel.updateCargoStatus(cargo.cargo, isCalled = true, isMissed = false, whoCalledId = it.contactId) 
                        } 
                }) { Text("✅ Teslim Edildi") }
                
                // Unreachable Button
                TextButton(onClick = {
                    selectedContact?.let {
                        viewModel.updateCargoStatus(cargo.cargo, isCalled = true, isMissed = true, whoCalledId = null) 
                    }
                }) { Text("❌ Ulaşılamadı") }
                
                // Skip Button
                TextButton(onClick = {
                        viewModel.skipCargo(cargo.cargo.cargoId)
                }) { Text("⏭️ Atla") }
            }
        }
    }
}

@Composable
fun ImportTextDialog(
    viewModel: CargoViewModel,
    initialText: String = "",
    onDismiss: () -> Unit,
    onConfirm: (List<Villa>) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    val context = LocalContext.current
    
    // Auto-paste from clipboard on open ONLY if initialText is empty (avoid overwriting OCR)
    LaunchedEffect(Unit) {
        if (initialText.isEmpty()) {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                 val pasted = clip.getItemAt(0).text.toString()
                 if (pasted.isNotEmpty()) text = pasted
            }
        }
    }

    var analysisResult by remember { mutableStateOf<CargoViewModel.TextImportResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metinden Villa Ekle") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (analysisResult == null) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Metni Buraya Yapıştırın") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 10
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Metin içindeki sayılar taranacak ve villa numaralarıyla eşleştirilecektir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    // Result View
                    val result = analysisResult!!
                    val verifiedVillas = remember(result) { result.foundVillas.filter { result.foundByNames.containsKey(it.villaId) } }
                    val unverifiedVillas = remember(result) { result.foundVillas.filter { !result.foundByNames.containsKey(it.villaId) } }
                    
                    // Selection State
                    // By default select verified. Unverified? Let's select all but warn.
                    // Or follow user request "Ask if they want to add".
                    // Let's use Checkboxes or just separate buttons. 
                    // Let's go with a custom list with checkboxes or toggles.
                    // For simplicity: Two sections. "Hepsini Ekle" vs "Sadece Doğrulananlar".
                    
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                         // 1. Verified Section
                         if (verifiedVillas.isNotEmpty()) {
                             Column {
                                 Text("✅ İsim ile Doğrulananlar (${verifiedVillas.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                 Spacer(modifier = Modifier.height(4.dp))
                                 verifiedVillas.forEach { villa ->
                                     val matchReason = result.foundByNames[villa.villaId]
                                     Text("• Villa ${villa.villaNo} ($matchReason)", style = MaterialTheme.typography.bodySmall)
                                 }
                             }
                         }
                         
                         // 2. Unverified Section
                         if (unverifiedVillas.isNotEmpty()) {
                             Column {
                                 Text("⚠️ İsim Doğrulanamayanlar (${unverifiedVillas.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                                 Text("Bu numaralar bulundu fakat kayıtlı isimlerle eşleşmedi:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                 Spacer(modifier = Modifier.height(4.dp))
                                 unverifiedVillas.forEach { villa ->
                                     val context = result.unverifiedContext[villa.villaId]
                                     if (context != null) {
                                         Text("• Villa ${villa.villaNo} (Bulunan: \"$context\"?)", style = MaterialTheme.typography.bodySmall)
                                     } else {
                                         Text("• Villa ${villa.villaNo}", style = MaterialTheme.typography.bodySmall)
                                     }
                                 }
                             }
                         }
                         
                         // 3. Other Info
                         if (result.alreadyAdded.isNotEmpty()) {
                             Text("ℹ️ Zaten Ekli (${result.alreadyAdded.size})", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                         }
                         if (result.notFoundNumbers.isNotEmpty()) {
                             Text("❓ Eşleşmeyen Sayılar: ${result.notFoundNumbers.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                         }
                    }
                }
            }
        },
        confirmButton = {
            if (analysisResult == null) {
                // Get scope for suspend call
                val scope = rememberCoroutineScope()
                Button(
                    onClick = { 
                        scope.launch {
                            analysisResult = viewModel.parseAndAddVillasFromText(text)
                        }
                    },
                    enabled = text.isNotBlank()
                ) {
                    Text("Analiz Et")
                }
            } else {
                val result = analysisResult!!
                val verifiedVillas = result.foundVillas.filter { result.foundByNames.containsKey(it.villaId) }
                val unverifiedVillas = result.foundVillas.filter { !result.foundByNames.containsKey(it.villaId) }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // Only show "Add Verified" if there are unverified items and verified items
                    if (unverifiedVillas.isNotEmpty() && verifiedVillas.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { 
                                onConfirm(verifiedVillas)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sadece Doğrulananları Ekle (${verifiedVillas.size})")
                        }
                    }
                    
                    Button(
                        onClick = { 
                            onConfirm(result.foundVillas)
                        },
                        colors = if (unverifiedVillas.isNotEmpty()) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (unverifiedVillas.isNotEmpty()) Text("Hepsini Ekle (Riskli Dahil)") else Text("Ekle (${result.foundVillas.size})")
                    }
                    
                    TextButton(
                        onClick = { analysisResult = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Geri")
                    }
                }
            }
        },
        dismissButton = {
            if (analysisResult == null) {
                TextButton(onClick = onDismiss) {
                    Text("İptal")
                }
            }
        }
    )
}

