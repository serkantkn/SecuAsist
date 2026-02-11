package com.serkantken.secuasist.ui.activities

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.ui.theme.SecuAsistTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecuAsistTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SecuAsistApplication
    val prefs = context.getSharedPreferences("secuasist_prefs", Context.MODE_PRIVATE)

    var ipAddress by remember { mutableStateOf(prefs.getString("server_ip", "10.0.2.2") ?: "10.0.2.2") }
    var port by remember { mutableStateOf(prefs.getInt("server_port", 8765).toString()) }
    var preferredGate by remember { mutableStateOf(prefs.getString("preferred_gate", "A") ?: "A") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sunucu Ayarları") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    onClick = { preferredGate = "A" }
                )
                Text(
                    text = "A Kapısı",
                    modifier = Modifier.clickable { preferredGate = "A" }
                )
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = preferredGate == "B",
                    onClick = { preferredGate = "B" }
                )
                Text(
                    text = "B Kapısı",
                    modifier = Modifier.clickable { preferredGate = "B" }
                )
            }

            HorizontalDivider()

            Text(
                text = "Sunucu Bağlantı Bilgileri",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("IP Adresi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = port,
                onValueChange = { if (it.all { char -> char.isDigit() }) port = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val portInt = port.toIntOrNull()
                    if (ipAddress.isNotBlank() && portInt != null) {
                        prefs.edit()
                            .putString("server_ip", ipAddress)
                            .putInt("server_port", portInt)
                            .putString("preferred_gate", preferredGate)
                            .apply()
                        
                        app.wsClient.reconnectWithNewIp(ipAddress, portInt)
                        
                        Toast.makeText(context, "Ayarlar kaydedildi ve yeniden bağlanılıyor...", Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        Toast.makeText(context, "Lütfen geçerli değerler giriniz.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kaydet ve Bağlan")
            }
        }
    }
}
