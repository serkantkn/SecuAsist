package com.serkantken.secuasist.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.ui.viewmodels.CargoViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargoReportScreen(
    viewModel: CargoViewModel,
    onBack: () -> Unit
) {
    val reports by viewModel.cargoReports.collectAsState()
    val companies by viewModel.companies.collectAsState()
    
    // Filter States
    var startDate by remember { mutableStateOf<String?>(null) } // YYYY-MM-DD
    var endDate by remember { mutableStateOf<String?>(null) }
    var selectedCompany by remember { mutableStateOf<CargoCompany?>(null) }
    
    // Initial fetch trigger
    LaunchedEffect(startDate, endDate, selectedCompany) {
        // Need to add " 00:00:00" and " 23:59:59" for accurate range if comparing strings
        val start = startDate?.let { "$it 00:00:00" }
        val end = endDate?.let { "$it 23:59:59" }
        viewModel.setReportFilters(start, end, selectedCompany?.companyId, null)
    }

    // Scroll To Top Logic
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kargo Dağıtım Raporu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- FILTERS ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Filtrele", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Date Filter Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start Date
                        DatePickerButton(
                            label = if (startDate == null) "Başlangıç" else startDate!!,
                            modifier = Modifier.weight(1f),
                            onDateSelected = { startDate = it }
                        )
                        // End Date
                        DatePickerButton(
                            label = if (endDate == null) "Bitiş" else endDate!!,
                            modifier = Modifier.weight(1f),
                            onDateSelected = { endDate = it }
                        )
                        // Clear Dates
                        if (startDate != null || endDate != null) {
                            IconButton(onClick = { 
                                startDate = null
                                endDate = null 
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Tarihi Temizle")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Company Filter
                    CompanyDropdown(
                        companies = companies,
                        selectedCompany = selectedCompany,
                        onCompanySelected = { selectedCompany = it }
                    )
                }
            }
            
            // --- LIST ---
            if (reports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Kayıt bulunamadı.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Toplam: ${reports.size} Kayıt", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                    items(reports) { report ->
                        CargoReportItem(report)
                    }
                }
            }
        }
    }
}

@Composable
fun CargoReportItem(report: com.serkantken.secuasist.models.CargoReport) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row (Always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Villa ${report.villaNo}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    report.callStatus,
                    color = if (report.callStatus == "Başarılı") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            // Expanded Details
            // Using a simple if for now, could animate content size if requested specifically
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Firma: ${report.companyName ?: "-"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Teslim Alan/Arayan: ${report.whoCalledName ?: "-"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Tarih: ${report.callDate ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                     if (report.callAttemptCount != null && report.callAttemptCount > 0) {
                         Text(
                             "Deneme: ${report.callAttemptCount}", 
                             style = MaterialTheme.typography.bodySmall, 
                             color = Color.Gray
                         )
                    }
                }
            }
        }
    }
}

@Composable
fun DatePickerButton(
    label: String,
    modifier: Modifier = Modifier,
    onDateSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            onDateSelected(formattedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Button(
        onClick = { datePickerDialog.show() },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDropdown(
    companies: List<CargoCompany>,
    selectedCompany: CargoCompany?,
    onCompanySelected: (CargoCompany?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedCompany?.companyName ?: "Tüm Firmalar",
            onValueChange = {},
            readOnly = true,
            label = { Text("Firma Filtresi") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Tüm Firmalar") },
                onClick = {
                    onCompanySelected(null)
                    expanded = false
                }
            )
            companies.forEach { company ->
                DropdownMenuItem(
                    text = { Text(company.companyName ?: "") },
                    onClick = {
                        onCompanySelected(company)
                        expanded = false
                    }
                )
            }
        }
    }
}
