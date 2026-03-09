package com.serkantken.secuasist.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.serkantken.secuasist.models.Villa
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VillaDetailSheet(
    villa: Villa,
    linkedContacts: List<com.serkantken.secuasist.models.Contact>,
    allContacts: List<com.serkantken.secuasist.models.Contact>,
    onDismiss: () -> Unit,
    onSave: (Villa) -> Unit,
    onDelete: (Villa) -> Unit,
    onLinkContact: (com.serkantken.secuasist.models.Contact, Boolean, String) -> Unit,
    onUnlinkContact: (com.serkantken.secuasist.models.Contact) -> Unit
) {
    val context = LocalContext.current
    
    // Call Permission Launcher
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.CALL_PHONE] == true) {
             // Permission granted, user can try calling again
        }
    }

    // Mode State: Read-Only (default) vs Editing
    // If villaId is 0 (new villa), start in editing mode
    var isEditing by remember { mutableStateOf(villa.villaId == 0) }

    // Local state for editing fields
    var villaNo by remember { mutableStateOf(if (villa.villaId == 0) "" else villa.villaNo.toString()) }
    var street by remember { mutableStateOf(villa.villaStreet ?: "") }
    var notes by remember { mutableStateOf(villa.villaNotes ?: "") }
    var navA by remember { mutableStateOf(villa.villaNavigationA ?: "") }
    var navB by remember { mutableStateOf(villa.villaNavigationB ?: "") }

    // Toggles (0/1 to Boolean)
    var isRental by remember { mutableStateOf(villa.isVillaRental == 1) }
    var isEmpty by remember { mutableStateOf(villa.isVillaEmpty == 1) }
    var isUnderConstruction by remember { mutableStateOf(villa.isVillaUnderConstruction == 1) }
    var isSpecial by remember { mutableStateOf(villa.isVillaSpecial == 1) }
    var callFromHome by remember { mutableStateOf(villa.isVillaCallFromHome == 1) }
    var dontCallForCargo by remember { mutableStateOf(villa.isVillaCallForCargo == 0) }

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (villa.villaId == 0) {
                        Text("Yeni Villa Ekle") 
                    } else {
                        Text(if (isEditing) "Düzenle: Villa ${villa.villaNo}" else "Detay: Villa ${villa.villaNo}")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                },
                actions = {
                    if (!isEditing) {
                        // Read-Only Actions
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                        }
                    } else {
                        // Editing Actions
                        if (villa.villaId != 0) { // Only show delete for existing villas
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = MaterialTheme.colorScheme.error
                                )
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
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(updatedVilla)
                                isEditing = false
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Kaydet")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- LINKED CONTACTS SECTION (Moved to Top) ---
            Text("Bağlı Kişiler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            if (linkedContacts.isEmpty()) {
                Text("Bu villaya bağlı kişi yok.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                linkedContacts.forEach { contact ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.contactName ?: "İsimsiz",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (!contact.contactPhone.isNullOrEmpty()) {
                                    Text(
                                        text = contact.contactPhone,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            
                            Row {
                                // Call Button
                                if (!contact.contactPhone.isNullOrEmpty()) {
                                    IconButton(onClick = {
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.CALL_PHONE
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                                            androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.READ_PHONE_STATE
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        ) {
                                            if (android.provider.Settings.canDrawOverlays(context)) {
                                                try {
                                                    // 1. Start Floating Widget Service FIRST (while app is in foreground)
                                                    val serviceIntent = Intent(context, com.serkantken.secuasist.services.FloatingWidgetService::class.java).apply {
                                                        putExtra("VILLA_STREET", villa.villaStreet)
                                                        putExtra("VILLA_DIRECTIONS", villa.villaNavigationA)
                                                    }
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                        context.startForegroundService(serviceIntent)
                                                    } else {
                                                        context.startService(serviceIntent)
                                                    }

                                                    // 2. Start Phone Call Activity
                                                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                                                        data = Uri.parse("tel:${contact.contactPhone}")
                                                    }
                                                    context.startActivity(callIntent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            } else {
                                                val intent = Intent(
                                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            }
                                        } else {
                                            callPermissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.CALL_PHONE,
                                                    android.Manifest.permission.READ_PHONE_STATE
                                                )
                                            )
                                        }
                                    }) {
                                        Icon(Icons.Default.Call, contentDescription = "Ara", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                
                                // Unlink Button (Only in Edit Mode)
                                if (isEditing) {
                                    IconButton(onClick = { onUnlinkContact(contact) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Kaldır", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isEditing) {
                var showContactSelection by remember { mutableStateOf(false) }
                Button(
                    onClick = { showContactSelection = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kişi Ekle")
                }

                if (showContactSelection) {
                    AlertDialog(
                        onDismissRequest = { showContactSelection = false },
                        title = { Text("Kişi Seçin") },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                allContacts.forEach { contact ->
                                    if (linkedContacts.none { it.contactId == contact.contactId }) {
                                        TextButton(
                                            onClick = {
                                                onLinkContact(contact, false, "Tenant")
                                                showContactSelection = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${contact.contactName} (${contact.contactPhone})",
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showContactSelection = false }) { Text("İptal") }
                        }
                    )
                }
            }
            
            HorizontalDivider()

            // --- VILLA DETAILS SECTION ---
            // --- VILLA DETAILS SECTION ---
            if (isEditing) {
                // Determine if we are creating a new villa (id=0)
                val isCreatingNew = villa.villaId == 0
                
                if (isCreatingNew) {
                    OutlinedTextField(
                        value = villaNo,
                        onValueChange = { if (it.all { char -> char.isDigit() }) villaNo = it },
                        label = { Text("Villa No") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                val context = LocalContext.current
                OutlinedTextField(
                    value = street,
                    onValueChange = { street = it },
                    label = { Text("Sokak / Konum") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notlar") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                OutlinedTextField(
                    value = navA,
                    onValueChange = { navA = it },
                    label = { Text("Yol Tarifi (Navigasyon A)") },
                    placeholder = { Text("Örn: Siteden girince 2. sağdan dönün...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = navB,
                    onValueChange = { navB = it },
                    label = { Text("Yol Tarifi (Navigasyon B)") },
                    placeholder = { Text("Örn: Arka kapıdan giriş şifresi 1234...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            } else {
                InfoRow(label = "Sokak / Konum", value = street.ifEmpty { "-" })

                // Gate Preference Logic
                val prefs = context.getSharedPreferences("secuasist_prefs", android.content.Context.MODE_PRIVATE)
                val preferredGate = prefs.getString("preferred_gate", "A") ?: "A"
                
                if (preferredGate == "A") {
                    if (navA.isNotBlank()) InfoRow(label = "Yol Tarifi (A Kapısı)", value = navA)
                } else {
                    if (navB.isNotBlank()) InfoRow(label = "Yol Tarifi (B Kapısı)", value = navB)
                }

                InfoRow(label = "Notlar", value = notes.ifEmpty { "-" })
            }

            HorizontalDivider()

            Text("Durumlar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isEditing || isRental) {
                    StatusChip(
                        label = "Kiracı Var",
                        selected = isRental,
                        enabled = isEditing,
                        onSelectedChange = { isRental = it }
                    )
                }
                if (isEditing || isEmpty) {
                    StatusChip(
                        label = "Boş Villa",
                        selected = isEmpty,
                        enabled = isEditing,
                        onSelectedChange = { isEmpty = it }
                    )
                }
                if (isEditing || isUnderConstruction) {
                    StatusChip(
                        label = "İnşaat Halinde",
                        selected = isUnderConstruction,
                        enabled = isEditing,
                        onSelectedChange = { isUnderConstruction = it }
                    )
                }
                if (isEditing || isSpecial) {
                    StatusChip(
                        label = "Özel İlgi",
                        selected = isSpecial,
                        enabled = isEditing,
                        onSelectedChange = { isSpecial = it }
                    )
                }
                if (isEditing || callFromHome) {
                    StatusChip(
                        label = "Evden Aransın",
                        selected = callFromHome,
                        enabled = isEditing,
                        onSelectedChange = { callFromHome = it }
                    )
                }
                if (isEditing || dontCallForCargo) {
                    StatusChip(
                        label = "Kargo İçin Aranmasın",
                        selected = dontCallForCargo,
                        enabled = isEditing,
                        isDestructive = true,
                        onSelectedChange = { dontCallForCargo = it }
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Villayı Sil") },
            text = { Text("Villa ${villa.villaNo} kalıcı olarak silinecek. Emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(villa)
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun StatusChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    isDestructive: Boolean = false,
    onSelectedChange: (Boolean) -> Unit
) {
    FilterChip(
        selected = selected,
        // If editing is enabled, toggle. If not, do nothing (Read-Only).
        // giving enabled=true allows the color to show.
        onClick = { if (enabled) onSelectedChange(!selected) },
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null) }
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
        enabled = true // Always visually enabled to show colors
    )
}
