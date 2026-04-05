package com.serkantken.secuasist.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.models.CameraWithVillas
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.ui.viewmodels.FaultViewModel
import com.serkantken.secuasist.SecuAsistApplication
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaultScreen(
    viewModel: FaultViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as SecuAsistApplication
    val isAdmin = true
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Kameralar", "İnterkomlar")

    Scaffold(
        topBar = {
            val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
            Column {
                com.serkantken.secuasist.ui.components.ScreenHeader(
                    title = "Arıza Takibi",
                    offlineSyncCount = pendingSyncCount
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars)
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> CamerasTab(viewModel, isAdmin)
                1 -> IntercomsTab(viewModel, isAdmin)
            }
        }
    }
}

// --- CAMERAS TAB ---

@Composable
fun CamerasTab(viewModel: FaultViewModel, isAdmin: Boolean) {
    val cameras by viewModel.allCameras.collectAsState(initial = emptyList())
    val villas by viewModel.allVillas.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Filter State
    var showOnlyFaulty by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Filter Logic
    val filteredCameras = cameras.filter { cameraWithVillas ->
        val matchesFault = !showOnlyFaulty || (cameraWithVillas.camera.isWorking == 0)
        val matchesSearch = cameraWithVillas.camera.cameraIp.contains(searchQuery, ignoreCase = true)
        matchesFault && matchesSearch
    }
    
    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("IP ile Kamera Ara") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = "Ara") },
                singleLine = true
            )
    
            // Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showOnlyFaulty,
                    onClick = { showOnlyFaulty = !showOnlyFaulty },
                    label = { Text("Sadece Arızalılar") },
                    leadingIcon = if (showOnlyFaulty) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
    
            Box(modifier = Modifier.weight(1f)) {
                if (filteredCameras.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (showOnlyFaulty) "Arızalı kamera yok." else "Henüz kamera eklenmemiş.",
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredCameras) { cameraWithVillas ->
                            CameraCard(
                                cameraWithVillas = cameraWithVillas,
                                isAdmin = isAdmin,
                                onDelete = { viewModel.deleteCamera(cameraWithVillas.camera) },
                                onStatusChange = { viewModel.updateCameraStatus(cameraWithVillas.camera, it) }
                            )
                        }
                    }
                }
            }
        }
    
        // Scroll To Top Button (Above FAB)
        com.serkantken.secuasist.ui.components.ScrollToTopButton(
            visible = showScrollToTop,
            onClick = {
                scope.launch {
                    listState.animateScrollToItem(0)
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 88.dp, end = 16.dp)
        )

        if (isAdmin) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Kamera Ekle")
            }
        }
    }

    if (showAddDialog) {
        AddCameraDialog(
            villas = villas,
            onConfirm = { name, ip, selectedVillaIds ->
                viewModel.addCamera(name, ip, selectedVillaIds)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun CameraCard(
    cameraWithVillas: CameraWithVillas,
    isAdmin: Boolean,
    onDelete: () -> Unit,
    onStatusChange: (Boolean) -> Unit
) {
    val camera = cameraWithVillas.camera
    val linkedVillas = cameraWithVillas.visibleVillas
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (camera.isWorking == 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Content (Clickable to toggle expand)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        // SWAPPED: IP is now Title/Top, Name is Subtitle
                        Text(camera.cameraIp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(camera.cameraName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                // Switch always visible and separate from expand click
                Switch(checked = camera.isWorking == 1, onCheckedChange = onStatusChange)
            }
            
            // Expanded Content
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "Gördüğü Villalar:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (linkedVillas.isEmpty()) {
                    Text("Bağlı villa yok", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    Text(
                        linkedVillas.joinToString(", ") { "Villa ${it.villaNo}" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (expanded && isAdmin) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kamerayı Sil")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCameraDialog(
    villas: List<Villa>,
    onConfirm: (String, String, List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    val selectedVillas = remember { mutableStateListOf<Int>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Kamera Ekle") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Kamera Adı") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP Adresi") }, modifier = Modifier.fillMaxWidth())
                
                Text("Gördüğü Villalar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                
                villas.forEach { villa ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectedVillas.contains(villa.villaId)) {
                                    selectedVillas.remove(villa.villaId)
                                } else {
                                    selectedVillas.add(villa.villaId)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedVillas.contains(villa.villaId),
                            onCheckedChange = { 
                                if (it == true) selectedVillas.add(villa.villaId) else selectedVillas.remove(villa.villaId)
                            }
                        )
                        Text("Villa ${villa.villaNo}")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, ip, selectedVillas.toList()) }) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

// --- INTERCOMS TAB ---

@Composable
fun IntercomsTab(viewModel: FaultViewModel, isAdmin: Boolean) {
    val villas by viewModel.allVillas.collectAsState(initial = emptyList())
    var selectedVilla by remember { mutableStateOf<Villa?>(null) }
    
    // Scroll To Top Logic
    val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }

    Box(modifier = Modifier.fillMaxSize()) {
        if (villas.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz villa eklenmemiş.", color = Color.Gray)
            }
        } else {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalItemSpacing = 16.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                items(villas) { villa ->
                    IntercomVillaCard(
                        villa = villa,
                        viewModel = viewModel,
                        onClick = { selectedVilla = villa }
                    )
                }
            }
            
            com.serkantken.secuasist.ui.components.ScrollToTopButton(
                visible = showScrollToTop,
                onClick = {
                    scope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
    
    if (selectedVilla != null) {
            IntercomDetailSheet(
                villa = selectedVilla!!,
                viewModel = viewModel,
                isAdmin = isAdmin,
                onDismiss = { selectedVilla = null }
            )
    }
}

@Composable
fun IntercomVillaCard(
    villa: Villa,
    viewModel: FaultViewModel,
    onClick: () -> Unit
) {
    val intercoms by viewModel.getIntercomsForVilla(villa.villaId).collectAsState(initial = emptyList())
    val faultyIntercoms = intercoms.count { it.isWorking == 0 }
    val totalIntercoms = intercoms.size
    
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
             containerColor = if (faultyIntercoms > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
         Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Villa ${villa.villaNo}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (totalIntercoms > 0) {
                     if (faultyIntercoms == 0) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint =  Color(0xFF2E7D32))
                    } else {
                        Icon(Icons.Default.Error, contentDescription = "Fault", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (totalIntercoms == 0) {
                 Text("İnterkom Yok", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                 Text(
                    "$totalIntercoms İnterkom (${if (faultyIntercoms > 0) "$faultyIntercoms Arızalı" else "OK"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (faultyIntercoms > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
         }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntercomDetailSheet(
    villa: Villa,
    viewModel: FaultViewModel,
    isAdmin: Boolean,
    onDismiss: () -> Unit
) {
    val intercoms by viewModel.getIntercomsForVilla(villa.villaId).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
             Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("İnterkomlar - Villa ${villa.villaNo}", style = MaterialTheme.typography.titleLarge)
                if (isAdmin) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            if (intercoms.isEmpty()) {
                Text("Kayıtlı interkom yok.", color = Color.Gray)
            } else {
                 intercoms.forEach { intercom ->
                    DeviceRow(
                        name = intercom.intercomName,
                        detail = "",
                        isWorking = intercom.isWorking == 1,
                        isAdmin = isAdmin,
                        onDelete = { viewModel.deleteIntercom(intercom) },
                        onStatusChange = { viewModel.updateIntercomStatus(intercom, it) },
                        icon = Icons.Default.Call
                    )
                }
            }
        }
    }
    
    if (showAddDialog) {
         AddDeviceDialog(
            title = "İnterkom Ekle",
            fields = listOf("Konum İsmi (Örn: Giriş)" to ""),
            onConfirm = { values ->
                 viewModel.addIntercom(villa.villaId, values[0])
                 showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

// Reusing DeviceRow and AddDeviceDialog from previous version (assumed to be in same file or common)
// I will include them here to be safe as I am overwriting the file.

@Composable
fun DeviceRow(
    name: String,
    detail: String,
    isWorking: Boolean,
    isAdmin: Boolean,
    onStatusChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    icon: ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isWorking) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = if (isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (detail.isNotEmpty()) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text(
                        if (isWorking) "Çalışıyor" else "ARIZALI",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isWorking) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = isWorking,
                    onCheckedChange = onStatusChange
                )
                if (isAdmin) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AddDeviceDialog(
    title: String,
    fields: List<Pair<String, String>>, // Label, Initial
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var inputValues by remember { mutableStateOf(fields.map { it.second }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { index, (label, _) ->
                    OutlinedTextField(
                        value = inputValues[index],
                        onValueChange = { newValue ->
                            val newList = inputValues.toMutableList()
                            newList[index] = newValue
                            inputValues = newList
                        },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(inputValues) }) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
