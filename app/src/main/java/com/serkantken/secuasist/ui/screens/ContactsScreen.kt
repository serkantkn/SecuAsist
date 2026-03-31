package com.serkantken.secuasist.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.ui.viewmodels.ContactsViewModel
import com.serkantken.secuasist.SecuAsistApplication
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.launch

import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: ContactsViewModel = viewModel()) {
    val contacts by viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val context = LocalContext.current
    val app = context.applicationContext as SecuAsistApplication
    val isAdmin = true
    var contactToEdit by remember { mutableStateOf<Contact?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDialerSheet by remember { mutableStateOf(false) }

    val isImporting by viewModel.isImporting.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val importSummary by viewModel.importSummary.collectAsState()

    // Launcher for Dialer Role Permission
    val dialerRoleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Permission granted, user can try calling again
        }
    }
    
    // Launcher for Call Log Permission
    val callLogPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Trigger UI refresh if needed
    }

    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            Column {
                com.serkantken.secuasist.ui.components.ScreenHeader(
                    title = "Rehber",
                    onNewClick = if (isAdmin) { { showAddDialog = true } } else null
                )
                
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Kişi Ara (İsim veya Numara)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { 
                                    viewModel.updateSearchQuery("") 
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Temizle")
                                }
                            }

                             // Import Menu
                            var showMenu by remember { mutableStateOf(false) }
                            val context = androidx.compose.ui.platform.LocalContext.current
                            
                            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                            ) { isGranted ->
                                if (isGranted) {
                                    viewModel.importContactsFromDevice(context)
                                }
                            }

                            if (isAdmin) {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Daha Fazla")
                                }
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rehberden Al") },
                                    onClick = {
                                        showMenu = false
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.READ_CONTACTS
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        ) {
                                            viewModel.importContactsFromDevice(context)
                                        } else {
                                            permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                                        }
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .focusRequester(focusRequester)
                ) {}
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { showDialerSheet = true },
                    modifier = Modifier.padding(bottom = 16.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = "Numara Çevir")
                }

                com.serkantken.secuasist.ui.components.ScrollToTopButton(
                    visible = showScrollToTop,
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                )
            }
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars)
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Kayıtlı kişi bulunamadı.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val context = androidx.compose.ui.platform.LocalContext.current
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts, key = { it.contactId }) { contact ->
                        ContactItem(
                            contact = contact, 
                            onClick = { /* Expand logic is internal now */ },
                            onEdit = { if (isAdmin) contactToEdit = contact },
                            onCall = {
                                val hasCallPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CALL_PHONE
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                scope.launch {
                                    try {
                                        val action = if (hasCallPermission) android.content.Intent.ACTION_CALL else android.content.Intent.ACTION_DIAL
                                        val callIntent = android.content.Intent(action).apply {
                                            data = android.net.Uri.parse("tel:${contact.contactPhone}")
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(callIntent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        android.widget.Toast.makeText(context, "Arama başlatılamadı.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            // Pass permission checkers/launchers to expand logic
                            hasCallLogPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.READ_CALL_LOG
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                            onRequestCallLogPermission = {
                                callLogPermissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
                            },
                            isAdmin = isAdmin
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, saveToDevice, saveToGoogle ->
                viewModel.addContact(name, phone, context, saveToDevice, saveToGoogle)
                showAddDialog = false
            }
        )
    }

    if (contactToEdit != null) {
        EditContactDialog(
            contact = contactToEdit!!,
            onDismiss = { contactToEdit = null },
            onUpdate = { updatedContact ->
                viewModel.updateContact(updatedContact)
                contactToEdit = null
            },
            onDelete = { contact ->
                viewModel.deleteContact(contact)
                contactToEdit = null
            }
        )
    }

    if (isImporting) {
        ImportProgressDialog(progress = importProgress)
    }

    importSummary?.let { summary ->
        ImportSummaryDialog(
            summary = summary,
            onDismiss = { viewModel.dismissImportSummary() }
        )
    }

    if (showDialerSheet) {
        com.serkantken.secuasist.ui.components.DialerSheet(
            onDismissRequest = { showDialerSheet = false }
        )
    }
}

@Composable
fun ImportProgressDialog(progress: Float) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Rehber İçe Aktarılıyor...") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Lütfen bekleyin, veriler işleniyor ve mükerrer kayıtlar temizleniyor...",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
fun ImportSummaryDialog(
    summary: com.serkantken.secuasist.ui.viewmodels.ImportSummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("İşlem Özeti")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryRow("Toplam İşlenen:", "${summary.totalProcessed}")
                SummaryRow("Yeni Eklenen:", "${summary.newAdded}", Color(0xFF4CAF50))
                SummaryRow("Zaten Kayıtlı:", "${summary.alreadyExists}")
                SummaryRow("Birleştirilen Mükerrer:", "${summary.duplicatesMerged}", MaterialTheme.colorScheme.error)
                SummaryRow("Villaya Bağlanan:", "${summary.villasLinked}", MaterialTheme.colorScheme.primary)
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Rehberiniz başarıyla güncellendi ve düzenlendi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tamam")
            }
        }
    )
}

@Composable
fun SummaryRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
fun EditContactDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onUpdate: (Contact) -> Unit,
    onDelete: (Contact) -> Unit
) {
    var name by remember { mutableStateOf(contact.contactName ?: "") }
    var phone by remember { mutableStateOf(contact.contactPhone ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Kişiyi Sil") },
            text = { Text("${contact.contactName} adlı kişiyi silmek istediğinize emin misiniz?") },
            confirmButton = {
                TextButton(onClick = { onDelete(contact) }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Kişiyi Düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Ad Soyad") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.all { char -> char.isDigit() }) phone = it },
                        label = { Text("Telefon Numarası") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) }
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (name.isNotEmpty() && phone.isNotEmpty()) {
                                onUpdate(contact.copy(contactName = name, contactPhone = phone))
                            }
                        }
                    ) {
                        Text("Güncelle")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("İptal")
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    hasCallLogPermission: Boolean,
    onRequestCallLogPermission: () -> Unit,
    isAdmin: Boolean = false,
    viewModel: ContactsViewModel = viewModel()
) {
    // ... Avatar Color Logic (Same) ...
    val avatarColor = remember(contact.contactName) {
        val hash = contact.contactName?.hashCode() ?: 0
        Color(
            red = (hash and 0xFF0000 shr 16) / 255f,
            green = (hash and 0x00FF00 shr 8) / 255f,
            blue = (hash and 0x0000FF) / 255f,
            alpha = 1f
        ).let { if (it.luminance() > 0.5) it.copy(alpha = 0.6f) else it.copy(alpha = 0.8f) }
    }

    // Lifecycle Aware Refresh Trigger
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Connect swipe state
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onCall()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (isAdmin) onEdit()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                SwipeToDismissBoxValue.EndToStart -> Color(0xFF2196F3)
                else -> Color.Transparent
            }
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Call
                SwipeToDismissBoxValue.EndToStart -> if (isAdmin) Icons.Default.MoreVert else Icons.Default.Person
                else -> Icons.Default.Person
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        },
        content = {
            var isExpanded by remember { mutableStateOf(false) }
            
            // State for Real Last Call Date
            var realLastCallTimestamp by remember { mutableStateOf<Long?>(null) }
            val context = androidx.compose.ui.platform.LocalContext.current

            // Fetch Logic
            // Add refreshTrigger to keys to re-run on resume
            LaunchedEffect(isExpanded, hasCallLogPermission, refreshTrigger) {
                if (isExpanded && hasCallLogPermission && !contact.contactPhone.isNullOrEmpty()) {
                    realLastCallTimestamp = com.serkantken.secuasist.utils.ContactUtils.getLastOutgoingCallDate(context, contact.contactPhone)
                }
            }

            // Animate Background Color
            val backgroundColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                label = "cardBackground"
            )

            Card(
                onClick = { isExpanded = !isExpanded },
                elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 8.dp else 2.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                modifier = Modifier.animateContentSize()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Row (Avatar + Name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                             Box(contentAlignment = Alignment.Center) {
                                 Text(
                                     text = contact.contactName?.take(1)?.uppercase() ?: "?",
                                     style = MaterialTheme.typography.headlineSmall,
                                     fontWeight = FontWeight.Bold,
                                     color = MaterialTheme.colorScheme.onPrimaryContainer
                                 )
                             }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.contactName ?: "İsimsiz",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!contact.contactPhone.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Call, 
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = contact.contactPhone ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    // Expanded Content
                    if (isExpanded) {
                        Divider(modifier = Modifier.padding(horizontal = 12.dp))
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Call Action
                            Button(
                                onClick = onCall,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Hemen Ara")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Linked Villas
                            var linkedVillas by remember { mutableStateOf<List<com.serkantken.secuasist.models.Villa>>(emptyList()) }
                            LaunchedEffect(contact.contactId) {
                                linkedVillas = viewModel.getLinkedVillas(contact.contactId)
                            }

                            if (linkedVillas.isNotEmpty()) {
                                Text(
                                    "Bağlı Villalar:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    linkedVillas.forEach { villa ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("No: ${villa.villaNo}") }
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    "Henüz bir villaya atanmamış.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            
                            if (isAdmin) {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Link Villa Button
                                var showLinkDialog by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = { showLinkDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Villaya Bağla")
                                }
                                
                                if (showLinkDialog) {
                                    LinkVillaDialog(
                                        viewModel = viewModel,
                                        onDismiss = { showLinkDialog = false },
                                        onConfirm = { villa, isOwner, type ->
                                            viewModel.linkContactToVilla(contact.contactId, villa.villaId, isOwner, type)
                                            showLinkDialog = false
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Last Call Info
                            if (!hasCallLogPermission) {
                                TextButton(onClick = onRequestCallLogPermission) {
                                    Text("Son arama geçmişini görmek için izin ver")
                                }
                            } else {
                                val timestamp = realLastCallTimestamp ?: contact.lastCallTimestamp
                                if (timestamp != null && timestamp > 0) {
                                    val date = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(timestamp))
                                    Text(
                                        "Son Arama: $date",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        "Arama geçmişi bulunamadı.",
                                        style = MaterialTheme.typography.bodySmall, 
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onConfirm: (String, String, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    
    var saveToDevice by remember { mutableStateOf(false) }
    var saveToGoogle by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            saveToDevice = false
            saveToGoogle = false
        }
    }
    
    fun checkPermissionAndToggle(isGoogle: Boolean, currentValue: Boolean) {
        if (!currentValue) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(android.Manifest.permission.WRITE_CONTACTS)
                if (isGoogle) saveToGoogle = true else saveToDevice = true
            } else {
                if (isGoogle) saveToGoogle = true else saveToDevice = true
            }
        } else {
            if (isGoogle) saveToGoogle = false else saveToDevice = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Kişi Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad Soyad") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.all { char -> char.isDigit() }) phone = it },
                    label = { Text("Telefon Numarası") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { checkPermissionAndToggle(false, saveToDevice) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveToDevice,
                        onCheckedChange = { checkPermissionAndToggle(false, saveToDevice) }
                    )
                    Text("Cihaz hafızasına kaydet", style = MaterialTheme.typography.bodyMedium)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { checkPermissionAndToggle(true, saveToGoogle) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveToGoogle,
                        onCheckedChange = { checkPermissionAndToggle(true, saveToGoogle) }
                    )
                    Text("Google hesabına kaydet", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        onConfirm(name, phone, saveToDevice, saveToGoogle)
                    }
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun LinkVillaDialog(
    viewModel: ContactsViewModel,
    onDismiss: () -> Unit,
    onConfirm: (com.serkantken.secuasist.models.Villa, Boolean, String) -> Unit
) {
    var villas by remember { mutableStateOf<List<com.serkantken.secuasist.models.Villa>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedVilla by remember { mutableStateOf<com.serkantken.secuasist.models.Villa?>(null) }
    var isOwner by remember { mutableStateOf(false) }
    
    // Default type
    val contactType = "Resident" 

    LaunchedEffect(Unit) {
        villas = viewModel.getAllVillas()
    }

    val filteredVillas = if (searchQuery.isEmpty()) {
        villas.sortedBy { it.villaNo }
    } else {
        villas.filter { it.villaNo.toString().contains(searchQuery) }.sortedBy { it.villaNo }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Villaya Bağla") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Villa Ara (No)") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredVillas) { villa ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVilla = villa }
                                .background(
                                    if (selectedVilla?.villaId == villa.villaId) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Villa ${villa.villaNo}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (filteredVillas.isEmpty()) {
                        item {
                            Text("Villa bulunamadı", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                
                if (selectedVilla != null) {
                    Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isOwner,
                            onCheckedChange = { isOwner = it }
                        )
                        Text("Ev Sahibi")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedVilla != null) {
                        onConfirm(selectedVilla!!, isOwner, contactType)
                    }
                },
                enabled = selectedVilla != null
            ) {
                Text("Bağla")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
