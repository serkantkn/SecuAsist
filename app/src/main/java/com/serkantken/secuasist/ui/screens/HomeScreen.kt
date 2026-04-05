package com.serkantken.secuasist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.lazy.grid.items
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.network.ConnectionState
import com.serkantken.secuasist.ui.viewmodels.HomeViewModel
import com.serkantken.secuasist.SecuAsistApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDefaultLauncher: Boolean = false,
    viewModel: HomeViewModel = viewModel(),
    onSettingsClick: () -> Unit
) {
    val searchResults by viewModel.filteredResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsState()
    val latestInfo by viewModel.latestVersionInfo.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as SecuAsistApplication
    val isAdmin = true
    
    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    
    // 1. Intercept Back Press to scroll to top if not at top
    androidx.activity.compose.BackHandler(enabled = showScrollToTop) {
        scope.launch {
            listState.animateScrollToItem(0)
        }
    }

    // 2. Intercept Back Press to DO NOTHING if at top AND is default launcher (Kiosk mode)
    androidx.activity.compose.BackHandler(enabled = !showScrollToTop && isDefaultLauncher) {
        // Do absolutely nothing to prevent exiting the app and redrawing
    }

    // State for detailing
    var selectedVilla by remember { mutableStateOf<Villa?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var selectedContact by remember { mutableStateOf<com.serkantken.secuasist.models.Contact?>(null) }
    var showContactBottomSheet by remember { mutableStateOf(false) }
    val contactSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showDialerSheet by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // CSV Launcher removed

    Scaffold(
        topBar = {
            Column {
                if (isUpdateAvailable && latestInfo != null) {
                    UpdateBanner(
                        version = latestInfo!!.first,
                        onClick = { app.updateManager.checkForUpdates() }
                    )
                }

                // 1. IOS-Style Header
                val context = androidx.compose.ui.platform.LocalContext.current
                com.serkantken.secuasist.ui.components.ScreenHeader(
                    title = "Villalar",
                    onNewClick = if (isAdmin) {
                        {
                            val newVilla = Villa(
                                villaNo = 0,
                                villaStreet = null,
                                villaNotes = null,
                                villaNavigationA = null,
                                villaNavigationB = null
                            )
                            selectedVilla = newVilla
                            showBottomSheet = true
                        }
                    } else null,
                    onSettingsClick = onSettingsClick,
                    connectionState = connectionState,
                    offlineSyncCount = pendingSyncCount,
                    extraActions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Yenile",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
                
                WhatsAppNotificationCard()
                
                // 2. Search Bar (Customized to look integrated)
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Villa Ara...") },
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
            
            if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.any { it.isLetter() }) "Kişi bulunamadı." else "Kayıtlı villa bulunamadı.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    state = listState,
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = searchResults,
                        key = { item ->
                            when (item) {
                                is HomeViewModel.HomeSearchResult.VillaResult -> "v_${item.villa.villaId}"
                                is HomeViewModel.HomeSearchResult.ContactResult -> "c_${item.contact.contactId}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is HomeViewModel.HomeSearchResult.VillaResult -> {
                                VillaItem(
                                    villa = item.villa,
                                    onClick = {
                                        selectedVilla = item.villa
                                        showBottomSheet = true
                                    }
                                )
                            }
                            is HomeViewModel.HomeSearchResult.ContactResult -> {
                                ContactSearchResultItem(
                                    contact = item.contact,
                                    onClick = {
                                        selectedContact = item.contact
                                        showContactBottomSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val allContacts by viewModel.allContacts.collectAsState()
    // Load linked contacts for selected villa
    val linkedContacts by remember(selectedVilla) {
        if (selectedVilla != null && selectedVilla!!.villaId != 0) {
            viewModel.getContactsForVilla(selectedVilla!!.villaId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<com.serkantken.secuasist.models.Contact>())
        }
    }.collectAsState(initial = emptyList<com.serkantken.secuasist.models.Contact>())

    if (showBottomSheet && selectedVilla != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                selectedVilla = null
            },
            sheetState = sheetState
        ) {
            VillaDetailSheet(
                villa = selectedVilla!!,
                linkedContacts = linkedContacts,
                allContacts = allContacts,
                isAdmin = isAdmin,
                onDismiss = {
                    showBottomSheet = false
                    selectedVilla = null
                },
                onSave = { updatedVilla ->
                    if (updatedVilla.villaId == 0) {
                        viewModel.saveNewVilla(updatedVilla)
                    } else {
                        viewModel.updateVilla(updatedVilla)
                    }
                    showBottomSheet = false
                    selectedVilla = null
                },
                onDelete = { villaToDelete ->
                    viewModel.deleteVilla(villaToDelete)
                    showBottomSheet = false
                    selectedVilla = null
                },
                onLinkContact = { contact, isOwner, type ->
                    if (selectedVilla!!.villaId != 0) {
                        viewModel.addContactToVilla(selectedVilla!!.villaId, contact, isOwner, type)
                    }
                },
                onUnlinkContact = { contact ->
                     if (selectedVilla!!.villaId != 0) {
                        viewModel.removeContactFromVilla(selectedVilla!!.villaId, contact.contactId)
                     }
                }
            )
        }
    }

    var linkedVillasForContact by remember { mutableStateOf<List<Villa>>(emptyList()) }
    LaunchedEffect(selectedContact) {
        if (selectedContact != null) {
            linkedVillasForContact = viewModel.getVillasForContact(selectedContact!!.contactId)
        } else {
            linkedVillasForContact = emptyList()
        }
    }

    if (showContactBottomSheet && selectedContact != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showContactBottomSheet = false
                selectedContact = null
            },
            sheetState = contactSheetState
        ) {
            ContactDetailSheet(
                contact = selectedContact!!,
                linkedVillas = linkedVillasForContact,
                isAdmin = isAdmin,
                onDismiss = {
                    showContactBottomSheet = false
                    selectedContact = null
                },
                onSave = { updatedContact ->
                    viewModel.updateContact(updatedContact)
                    selectedContact = updatedContact
                }
            )
        }
    }

    if (showDialerSheet) {
        com.serkantken.secuasist.ui.components.DialerSheet(
            onDismissRequest = { showDialerSheet = false }
        )
    }
}

@Composable
fun VillaItem(villa: Villa, onClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Large Number Left
            Text(
                text = "${villa.villaNo}",
                style = MaterialTheme.typography.headlineLarge,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(end = 12.dp)
            )

            // Info Column Right
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Street - optional
                Text(
                    text = villa.villaStreet?.ifBlank { "Sokak Belirtilmedi" } ?: "Sokak Belirtilmedi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Icons Badges
                val statusIcons = mutableListOf<Triple<androidx.compose.ui.graphics.vector.ImageVector, Color, String>>()
                if (villa.isVillaUnderConstruction == 1) statusIcons.add(Triple(Icons.Default.Build, Color(0xFFFFA500), "Tadilat"))
                if (villa.isVillaEmpty == 1) statusIcons.add(Triple(Icons.Default.Home, Color.Red, "Boş"))
                if (villa.isVillaRental == 1) statusIcons.add(Triple(Icons.Default.VpnKey, Color.Blue, "Kiracı"))
                if (villa.isVillaSpecial == 1) statusIcons.add(Triple(Icons.Default.Star, Color(0xFFFFD700), "VIP"))
                if (villa.isVillaCallFromHome == 1) statusIcons.add(Triple(Icons.Default.Phone, Color(0xFF4CAF50), "Evden Ara"))
                if (villa.isVillaCallForCargo == 0) statusIcons.add(Triple(Icons.Default.Inventory2, Color.Red, "Kargo Red"))
                if (villa.isCallOnlyMobile == 1) statusIcons.add(Triple(Icons.Default.Smartphone, Color(0xFFC2185B), "Sadece Cep"))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(18.dp)
                ) {
                    if (statusIcons.isNotEmpty()) {
                        statusIcons.take(4).forEach { (icon, color, _) ->
                             Icon(
                                 imageVector = icon,
                                 contentDescription = null,
                                 tint = color,
                                 modifier = Modifier.size(14.dp)
                             )
                        }
                        if (statusIcons.size > 4) {
                             Text("..", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}


@Composable
fun ContactSearchResultItem(contact: com.serkantken.secuasist.models.Contact, onClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.contactName ?: "İsimsiz",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = contact.contactPhone ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}
@Composable
fun UpdateBanner(version: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding() // Ensure it stays below status bar if needed, or Column handles it
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Yeni Sürüm Mevcut: v$version. Güncellemek için dokunun.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
