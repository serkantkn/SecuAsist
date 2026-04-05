package com.serkantken.secuasist.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantken.secuasist.models.Villa
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VillaDetailSheet(
    villa: Villa,
    linkedContacts: List<com.serkantken.secuasist.models.Contact>,
    allContacts: List<com.serkantken.secuasist.models.Contact>,
    onDismiss: () -> Unit,
    onSave: (Villa) -> Unit,
    onDelete: (Villa) -> Unit,
    isAdmin: Boolean = false,
    onLinkContact: (com.serkantken.secuasist.models.Contact, Boolean, String) -> Unit,
    onUnlinkContact: (com.serkantken.secuasist.models.Contact) -> Unit
) {
    val context = LocalContext.current
    
    // Mode State: Read-Only (default) vs Editing
    var isEditing by remember { mutableStateOf(villa.villaId == 0) }

    // Local state for editing fields
    var villaNo by remember { mutableStateOf(if (villa.villaId == 0) "" else villa.villaNo.toString()) }
    var street by remember { mutableStateOf(villa.villaStreet ?: "") }
    var notes by remember { mutableStateOf(villa.villaNotes ?: "") }
    var navA by remember { mutableStateOf(villa.villaNavigationA ?: "") }
    var navB by remember { mutableStateOf(villa.villaNavigationB ?: "") }

    // Toggles
    var isRental by remember { mutableStateOf(villa.isVillaRental == 1) }
    var isEmpty by remember { mutableStateOf(villa.isVillaEmpty == 1) }
    var isUnderConstruction by remember { mutableStateOf(villa.isVillaUnderConstruction == 1) }
    var isSpecial by remember { mutableStateOf(villa.isVillaSpecial == 1) }
    var callFromHome by remember { mutableStateOf(villa.isVillaCallFromHome == 1) }
    var dontCallForCargo by remember { mutableStateOf(villa.isVillaCallForCargo == 0) }
    var callOnlyMobile by remember { mutableStateOf(villa.isCallOnlyMobile == 1) }

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (villa.villaId == 0) "Yeni Villa" else "Villa Detay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                },
                actions = {
                    if (!isEditing && isAdmin) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                        }
                    } else if (isEditing) {
                        if (villa.villaId != 0) {
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = {
                            val villaNumber = villaNo.toIntOrNull()
                            if (villaNumber != null) {
                                val updatedVilla = villa.copy(
                                    villaNo = villaNumber,
                                    villaStreet = street,
                                    villaNotes = notes,
                                    villaNavigationA = navA,
                                    villaNavigationB = navB,
                                    isVillaRental = if (isRental) 1 else 0,
                                    isVillaEmpty = if (isEmpty) 1 else 0,
                                    isVillaUnderConstruction = if (isUnderConstruction) 1 else 0,
                                    isVillaSpecial = if (isSpecial) 1 else 0,
                                    isVillaCallFromHome = if (callFromHome) 1 else 0,
                                    isVillaCallForCargo = if (dontCallForCargo) 0 else 1,
                                    isCallOnlyMobile = if (callOnlyMobile) 1 else 0,
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(updatedVilla)
                                isEditing = false
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Kaydet", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hasActiveStatus = isRental || isEmpty || isUnderConstruction || isSpecial || callFromHome || dontCallForCargo || callOnlyMobile

            // --- Hero Section ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasActiveStatus && !isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(100.dp),
                                tonalElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isEditing && villa.villaId == 0) "?" else villaNo,
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (street.isNotBlank()) street else "Sokak Belirtilmedi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                            if (villa.villaId != 0) {
                                Text(
                                    text = "Villa Kaydı",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(20.dp))
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (isUnderConstruction) HeroStatusBadge(Icons.Default.Build, "Tadilat", Color(0xFFFFA500))
                            if (isEmpty) HeroStatusBadge(Icons.Default.Home, "Boş", Color.Red)
                            if (isRental) HeroStatusBadge(Icons.Default.VpnKey, "Kiracı", Color.Blue)
                            if (isSpecial) HeroStatusBadge(Icons.Default.Star, "VIP", Color(0xFFFFD700))
                            if (callFromHome) HeroStatusBadge(Icons.Default.Phone, "Evden Ara", Color(0xFF4CAF50))
                            if (dontCallForCargo) HeroStatusBadge(Icons.Default.Inventory2, "Kargo Red", Color.Red)
                            if (callOnlyMobile) HeroStatusBadge(Icons.Default.Smartphone, "Sadece Cep", Color(0xFFC2185B))
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(100.dp),
                            tonalElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isEditing && villa.villaId == 0) "?" else villaNo,
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (street.isNotBlank()) street else "Sokak Belirtilmedi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        if (villa.villaId != 0) {
                            Text(
                                text = "Villa Kaydı",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Contacts Section ---
                SectionHeader(title = "Bağlı Kişiler", icon = Icons.Outlined.Person)
                
                if (linkedContacts.isEmpty()) {
                    EmptyStateCard(message = "Bu villaya bağlı kişi bulunmuyor.")
                } else {
                    linkedContacts.forEach { contact ->
                        ContactCard(
                            contact = contact,
                            isEditing = isEditing,
                            onCall = { phone ->
                                val hasCallPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.CALL_PHONE
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                try {
                                    val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                                    val callIntent = Intent(action).apply {
                                        data = Uri.parse("tel:$phone")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(callIntent)
                                } catch (e: Exception) { e.printStackTrace() }
                            },
                            onUnlink = { onUnlinkContact(contact) }
                        )
                    }
                }

                if (isEditing) {
                    var showContactSelection by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { showContactSelection = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kişi Ekle")
                    }

                    if (showContactSelection) {
                        ContactSelectionDialog(
                            allContacts = allContacts,
                            linkedContacts = linkedContacts,
                            onDismiss = { showContactSelection = false },
                            onSelect = { contact ->
                                onLinkContact(contact, false, "Tenant")
                                showContactSelection = false
                            }
                        )
                    }
                }

                // --- Details Section ---
                SectionHeader(title = "Villa Detayları", icon = Icons.Outlined.Info)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (isEditing) {
                            if (villa.villaId == 0) {
                                OutlinedTextField(
                                    value = villaNo,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) villaNo = it },
                                    label = { Text("Villa No") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            OutlinedTextField(
                                value = street,
                                onValueChange = { street = it },
                                label = { Text("Sokak / Konum") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Villa Notları") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            DetailItem(icon = Icons.Outlined.Place, label = "Sokak", value = street.ifBlank { "-" })
                            
                            val prefs = context.getSharedPreferences("secuasist_prefs", android.content.Context.MODE_PRIVATE)
                            val preferredGate = prefs.getString("preferred_gate", "A") ?: "A"
                            val currentNav = if (preferredGate == "A") navA else navB
                            
                            DetailItem(
                                icon = Icons.Outlined.Navigation, 
                                label = "Yol Tarifi (${preferredGate} Kapısı)", 
                                value = currentNav.ifBlank { "Tarif belirtilmemiş" }
                            )
                            
                            DetailItem(icon = Icons.Outlined.Description, label = "Notlar", value = notes.ifBlank { "-" })
                        }
                    }
                }

                if (isEditing) {
                    SectionHeader(title = "Navigasyon Ayarları", icon = Icons.Outlined.Map)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = navA,
                                onValueChange = { navA = it },
                                label = { Text("Yol Tarifi (A Kapısı)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = navB,
                                onValueChange = { navB = it },
                                label = { Text("Yol Tarifi (B Kapısı)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // --- Status Section ---
                if (isEditing) {
                    @Suppress("DEPRECATION")
                    SectionHeader(title = "Durum ve Tercihler", icon = Icons.Outlined.FactCheck)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryStatusChip("Mülkiyet", "Kiracı", isRental, isEditing) { isRental = it }
                            CategoryStatusChip("Mülkiyet", "Boş Villa", isEmpty, isEditing) { isEmpty = it }
                            CategoryStatusChip("Hizmet", "İnşaat", isUnderConstruction, isEditing) { isUnderConstruction = it }
                            CategoryStatusChip("Hizmet", "VIP", isSpecial, isEditing) { isSpecial = it }
                            CategoryStatusChip("Arama", "Evden Ara", callFromHome, isEditing) { callFromHome = it }
                            CategoryStatusChip("Arama", "Kargo Engel", dontCallForCargo, isEditing, isDestructive = true) { dontCallForCargo = it }
                            CategoryStatusChip("Arama", "Sadece Cep", callOnlyMobile, isEditing) { callOnlyMobile = it }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Villayı Sil") },
            text = { Text("Villa ${villa.villaNo} ve tüm bağlı kayıtları kalıcı olarak silinecek. Emin misiniz?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(villa); showDeleteConfirmation = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Evet, Sil") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("İptal") }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ContactCard(
    contact: com.serkantken.secuasist.models.Contact,
    isEditing: Boolean,
    onCall: (String) -> Unit,
    onUnlink: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = contact.contactName ?: "İsimsiz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = contact.contactPhone ?: "-", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                if (!contact.contactPhone.isNullOrEmpty()) {
                    IconButton(onClick = { onCall(contact.contactPhone) }) {
                        Icon(Icons.Default.Call, contentDescription = "Ara", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (isEditing) {
                    IconButton(onClick = onUnlink) {
                        Icon(Icons.Default.LinkOff, contentDescription = "Kaldır", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItem(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CategoryStatusChip(
    category: String,
    label: String,
    selected: Boolean,
    isEditing: Boolean,
    isDestructive: Boolean = false,
    onSelectedChange: (Boolean) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { if (isEditing) onSelectedChange(!selected) },
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        colors = if (isDestructive && selected) {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            FilterChipDefaults.filterChipColors()
        },
        enabled = true,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun ContactSelectionDialog(
    allContacts: List<com.serkantken.secuasist.models.Contact>,
    linkedContacts: List<com.serkantken.secuasist.models.Contact>,
    onDismiss: () -> Unit,
    onSelect: (com.serkantken.secuasist.models.Contact) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = allContacts.filter { 
        searchQuery.isEmpty() || (it.contactName?.contains(searchQuery, ignoreCase = true) == true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kişi Bağla") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Ara...") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    filtered.filter { contact -> linkedContacts.none { it.contactId == contact.contactId } }.forEach { contact ->
                        ListItem(
                            headlineContent = { Text(contact.contactName ?: "İsimsiz") },
                            supportingContent = { Text(contact.contactPhone ?: "-") },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.clickable { onSelect(contact) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
fun HeroStatusBadge(icon: ImageVector, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
