package com.serkantken.secuasist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.items
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.network.ConnectionState
import com.serkantken.secuasist.ui.viewmodels.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onSettingsClick: () -> Unit
) {
    val villas by viewModel.filteredVillas.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // State for detailing
    var selectedVilla by remember { mutableStateOf<Villa?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importVillasFromCsv(it) }
    }

    Scaffold(
        topBar = {
            Column {
                // 1. IOS-Style Header
                val context = androidx.compose.ui.platform.LocalContext.current
                com.serkantken.secuasist.ui.components.ScreenHeader(
                    title = "Villalar",
                    onNewClick = {
                        val newVilla = Villa(
                            villaNo = 0,
                            villaStreet = null,
                            villaNotes = null,
                            villaNavigationA = null,
                            villaNavigationB = null
                        )
                        selectedVilla = newVilla
                        showBottomSheet = true
                    },
                    onSettingsClick = onSettingsClick,
                    connectionState = connectionState
                )
                
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
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Temizle")
                                }
                            }
                            // CSV Import Menu
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Daha")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Dışarıdan Al (CSV)") },
                                    onClick = {
                                        showMenu = false
                                        csvLauncher.launch(arrayOf("*/*")) 
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
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            
            if (villas.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Kayıtlı villa bulunamadı.", style = MaterialTheme.typography.bodyLarge)
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
                        items = villas,
                        key = { it.villaId }
                    ) { villa ->
                        VillaItem(
                            villa = villa,
                            onClick = {
                                selectedVilla = villa
                                showBottomSheet = true
                            }
                        )
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
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Left
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info Column Right
            Column(
                modifier = Modifier.weight(1f), // Take remaining space
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Villa ${villa.villaNo}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Street - optional
                if (!villa.villaStreet.isNullOrEmpty()) {
                    Text(
                        text = villa.villaStreet ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        fontSize = 11.sp
                    )
                }

                // Badges
                val badges = mutableListOf<Pair<String, Color>>()
                if (villa.isVillaUnderConstruction == 1) badges.add("Tadilatta" to Color(0xFFFFA500))
                if (villa.isVillaEmpty == 1) badges.add("Boş" to Color.Red)
                if (villa.isVillaRental == 1) badges.add("Kiracı" to Color.Blue)

                if (badges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        badges.take(2).forEach { (text, color) ->
                            Surface(
                                color = color.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = text,
                                    color = color,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                                    softWrap = false
                                )
                            }
                        }
                        if (badges.size > 2) {
                             Text("..", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


