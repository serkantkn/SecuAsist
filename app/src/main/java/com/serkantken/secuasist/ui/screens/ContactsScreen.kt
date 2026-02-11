package com.serkantken.secuasist.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.foundation.background
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.ui.viewmodels.ContactsViewModel
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.items

import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: ContactsViewModel = viewModel()) {
    val contacts by viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var contactToEdit by remember { mutableStateOf<Contact?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Launcher for Call Permission
    val callPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, user can try calling again
        }
    }
    
    // Launcher for Call Log Permission
    val callLogPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Trigger UI refresh if needed
    }

    Scaffold(
        topBar = {
            Column {
                com.serkantken.secuasist.ui.components.ScreenHeader(
                    title = "Rehber",
                    onNewClick = { showAddDialog = true }
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
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
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

                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Daha Fazla")
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
                ) {}
            }
        }
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts, key = { it.contactId }) { contact ->
                        ContactItem(
                            contact = contact, 
                            onClick = { /* Expand logic is internal now */ },
                            onEdit = { contactToEdit = contact },
                            onCall = {
                                // Check Permission
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CALL_PHONE
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    // Direct Call
                                    val intent = android.content.Intent(android.content.Intent.ACTION_CALL).apply {
                                        data = android.net.Uri.parse("tel:${contact.contactPhone}")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                                }
                            },
                            // Pass permission checkers/launchers to expand logic
                            hasCallLogPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.READ_CALL_LOG
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                            onRequestCallLogPermission = {
                                callLogPermissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone ->
                viewModel.addContact(name, phone)
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
                    onEdit()
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
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.MoreVert
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

            Card(
                onClick = { isExpanded = !isExpanded },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
fun AddContactDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        onConfirm(name, phone)
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
