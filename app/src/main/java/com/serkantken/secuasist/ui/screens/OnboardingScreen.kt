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
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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

    Scaffold(
        bottomBar = {
            Button(
                onClick = onContinue,
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
