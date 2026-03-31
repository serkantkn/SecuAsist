package com.serkantken.secuasist.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image // Import explicit property if needed
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.role.RoleManager
import android.telecom.TelecomManager
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun OnboardingScreen(
    onContinue: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var deviceName by remember { mutableStateOf("") }
    var deviceNameError by remember { mutableStateOf(false) }

    val telecomManager = context.getSystemService(android.content.Context.TELECOM_SERVICE) as TelecomManager
    var isDefaultDialer by remember { mutableStateOf(telecomManager.defaultDialerPackage == context.packageName) }
    
    val dialerRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName
        }
    }

    // Definition of permissions we need
    val permissions = listOf(
        PermissionItem(
            permission = Manifest.permission.CALL_PHONE,
            title = "Telefon Araması",
            description = "Villa sakinlerini uygulama üzerinden doğrudan arayabilmeniz için gereklidir.",
            icon = Icons.Default.Call,
            isEssential = true
        ),
        PermissionItem(
            permission = Manifest.permission.READ_CONTACTS,
            title = "Kişiler",
            description = "Telefon rehberinizdeki kişileri kolayca içe aktarabilmeniz için gereklidir.",
            icon = Icons.Default.Contacts,
            isEssential = false
        ),
        PermissionItem(
            permission = Manifest.permission.READ_PHONE_STATE,
            title = "Telefon Durumu",
            description = "Arama bittiğinde yüzen kutucuğun otomatik kapanabilmesi için gereklidir.",
            icon = Icons.Default.Call,
            isEssential = false
        ),
        PermissionItem(
            permission = Manifest.permission.READ_CALL_LOG,
            title = "Arama Kayıtları",
            description = "Kargo teslimatlarında 'son aranan' bilgisini görebilmeniz için gereklidir.",
            icon = Icons.Default.History,
            isEssential = false
        ),
        PermissionItem(
            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
                Manifest.permission.READ_MEDIA_IMAGES 
            else 
                Manifest.permission.READ_EXTERNAL_STORAGE,
            title = "Galeri Erişimi",
            description = "Kargo etiketlerini fotoğraftan tarayıp (OCR) otomatik veri girişi yapabilmeniz için gereklidir.",
            icon = Icons.Default.Image, // Correct usage
            isEssential = false
        )
    )

    // Add notification permission for Android 13+
    val allPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions + PermissionItem(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            title = "Bildirimler",
            description = "Önemli uyarılar ve arka plan işlemleri hakkında bilgi alabilmeniz için önerilir.",
            icon = Icons.Default.Notifications,
            isEssential = false
        )
    } else {
        permissions
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBatteryOptimizations by remember { 
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) 
    }
    
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    Scaffold(
        bottomBar = {
            Button(
                onClick = {
                    if (deviceName.isBlank()) {
                        deviceNameError = true
                    } else {
                        onContinue(deviceName)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp)
            ) {
                Text("Devam Et")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SecuAsist'e Hoş Geldiniz",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = deviceName,
                onValueChange = { 
                    deviceName = it
                    if (it.isNotBlank()) deviceNameError = false 
                },
                label = { Text("Cihaz İsmi (Zorunlu)") },
                isError = deviceNameError,
                supportingText = if (deviceNameError) { { Text("Lütfen cihaza bir isim verin (Örn: A Kapısı Tableti)") } } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Uygulamanın tam performansla çalışabilmesi için aşağıdaki izinleri vermenizi öneririz.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            allPermissions.forEach { item ->
                PermissionRow(item)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Default Dialer Request Button
            val roleManager = context.getSystemService(android.content.Context.ROLE_SERVICE) as? RoleManager
            val isRoleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
            
            if (isRoleAvailable) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!isDefaultDialer) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            dialerRoleLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDefaultDialer) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (isDefaultDialer) "Varsayılan Arama Uygulaması Ayarlandı \u2713" 
                        else "Varsayılan Arama Uygulaması Yap",
                        fontSize = 16.sp
                    )
                }
            }

            // Battery Optimization Section
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                     else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Arka Plan Çalışması (Pil Tasarrufu)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Uygulamanın aramaları her zaman yakalayabilmesi için pil kısıtlamalarından muaf olması önerilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (!isIgnoringBatteryOptimizations) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                batteryOptimizationLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isIgnoringBatteryOptimizations
                    ) {
                        Text(if (isIgnoringBatteryOptimizations) "Kısıtlamalar Kaldırıldı \u2713" else "Arka Planda Çalışmaya İzin Ver")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // Space for bottom bar
        }
    }
}

data class PermissionItem(
    val permission: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isEssential: Boolean
)

@Composable
fun PermissionRow(item: PermissionItem) {
    val context = LocalContext.current
    var isGranted by remember { 
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                item.permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "İzin Verildi",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = { launcher.launch(item.permission) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("İzin Ver", fontSize = 12.sp)
                }
            }
        }
    }
}
