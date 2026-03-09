package com.serkantken.secuasist.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.data.AppTheme
import com.serkantken.secuasist.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val currentTheme by viewModel.currentTheme.collectAsState()
    
    val ipAddress by viewModel.ipAddress.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val preferredGate by viewModel.preferredGate.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    var showOnboarding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
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
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            // --- Legacy Settings ---
            Text(
                text = "Kapı Seçimi (Navigasyon)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = preferredGate == "A",
                    onClick = { viewModel.updatePreferredGate("A") }
                )
                Text(
                    text = "A Kapısı",
                    modifier = Modifier.clickable { viewModel.updatePreferredGate("A") }
                )
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = preferredGate == "B",
                    onClick = { viewModel.updatePreferredGate("B") }
                )
                Text(
                    text = "B Kapısı",
                    modifier = Modifier.clickable { viewModel.updatePreferredGate("B") }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "Sunucu Bağlantı Bilgileri",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { viewModel.updateIpAddress(it) },
                label = { Text("IP Adresi") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = serverPort,
                onValueChange = { viewModel.updateServerPort(it) },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (ipAddress.isNotBlank() && serverPort.toIntOrNull() != null) {
                        viewModel.saveSettings()
                        android.widget.Toast.makeText(context, "Ayarlar kaydedildi ve yeniden bağlanılıyor...", android.widget.Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        android.widget.Toast.makeText(context, "Lütfen geçerli değerler giriniz.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kaydet ve Bağlan")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
             OutlinedButton(
                onClick = { showOnboarding = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("İzinleri ve Başlangıç Ekranını Gör")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = { viewModel.openDefaultAppsSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Varsayılan Ana Ekran (Kiosk) Olarak Ayarla")
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Theme Settings ---
            Text(
                text = "Görünüm",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            ThemeOption(
                title = "Sistem Varsayılanı",
                selected = currentTheme == AppTheme.SYSTEM,
                onClick = { viewModel.updateTheme(AppTheme.SYSTEM) }
            )
            ThemeOption(
                title = "Açık Tema",
                selected = currentTheme == AppTheme.LIGHT,
                onClick = { viewModel.updateTheme(AppTheme.LIGHT) }
            )
            ThemeOption(
                title = "Koyu Tema",
                selected = currentTheme == AppTheme.DARK,
                onClick = { viewModel.updateTheme(AppTheme.DARK) }
            )
        }
    }
    
    if (showOnboarding) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOnboarding = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                com.serkantken.secuasist.ui.screens.OnboardingScreen(
                    onContinue = { showOnboarding = false }
                )
            }
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
