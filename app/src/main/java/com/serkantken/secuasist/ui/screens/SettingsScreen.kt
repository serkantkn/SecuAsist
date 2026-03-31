package com.serkantken.secuasist.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.data.AppTheme
import com.serkantken.secuasist.ui.viewmodels.SettingsViewModel
import com.serkantken.secuasist.SecuAsistApplication
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Logout
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onUserManagementClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as SecuAsistApplication
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Genel", "Bağlantı", "Görünüm", "İzinler")

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
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTabIndex) {
                    0 -> SettingsGeneralTab(viewModel, onUserManagementClick)
                    1 -> SettingsConnectionTab(viewModel, onBack, app)
                    2 -> SettingsAppearanceTab(viewModel)
                    3 -> SettingsPermissionsTab(viewModel)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun SettingsGeneralTab(
    viewModel: SettingsViewModel,
    onUserManagementClick: () -> Unit
) {
    val preferredGate by viewModel.preferredGate.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as com.serkantken.secuasist.SecuAsistApplication

    // Dialer Role State
    var isDefaultDialer by remember { mutableStateOf(viewModel.isDefaultDialer(context)) }
    val dialerRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isDefaultDialer = viewModel.isDefaultDialer(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gate Selection
        SettingCard(
            title = "Kapı Seçimi (Navigasyon)",
            description = "Yol tariflerinin başlangıç kapısını seçin."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                Spacer(modifier = Modifier.width(32.dp))
                RadioButton(
                    selected = preferredGate == "B",
                    onClick = { viewModel.updatePreferredGate("B") }
                )
                Text(
                    text = "B Kapısı",
                    modifier = Modifier.clickable { viewModel.updatePreferredGate("B") }
                )
            }
        }

        // Default Launcher
        SettingCard(
            title = "Kiosk Modu (Başlangıç Ekranı)",
            description = "Uygulamanın cihaz ilk açıldığında doğrudan başlaması için ana ekran olarak ayarlayın."
        ) {
            OutlinedButton(
                onClick = { viewModel.openDefaultAppsSettings(context) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Varsayılan Ana Ekran (Kiosk) Yap")
            }
        }

        // Default Dialer
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        val isRoleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true

        if (isRoleAvailable) {
            SettingCard(
                title = "Varsayılan Telefon (Arama)",
                description = "Çağrıları doğrudan uygulama içerisinden yönetmek için izin verin."
            ) {
                Button(
                    onClick = {
                        if (!isDefaultDialer && roleManager != null) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            dialerRoleLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDefaultDialer) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isDefaultDialer) "Varsayılan Telefon Uygulaması" else "Varsayılan Telefon Uygulaması Yap")
                }
            }
        }

    }
}

@Composable
fun SettingsConnectionTab(
    viewModel: SettingsViewModel, 
    onBack: () -> Unit, 
    app: com.serkantken.secuasist.SecuAsistApplication
) {
    val ipAddress by viewModel.ipAddress.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val context = LocalContext.current
    val connectionState by app.wsClient.connectionState.collectAsState(initial = com.serkantken.secuasist.network.ConnectionState.DISCONNECTED)
    val coroutineScope = rememberCoroutineScope()
    var showWipeWarning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingCard(
            title = "Cihaz Kimliği / İsmi",
            description = "Bu cihazın sistemdeki görünen adı. (Örn: A Kapısı Tableti)"
        ) {
            OutlinedTextField(
                value = deviceName,
                onValueChange = { viewModel.updateDeviceName(it) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = true,
                singleLine = true,
                trailingIcon = {
                    Button(
                        onClick = { 
                            viewModel.saveSettings()
                            android.widget.Toast.makeText(context, "Cihaz ismi kaydedildi", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(end = 4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Kaydet", fontSize = 12.sp)
                    }
                }
            )
        }

        SettingCard(
            title = "Sunucu Bağlantısı",
            description = "SecuAsist yönetim sunucusuna bağlanmak için gerekli olan yerel IP adresi ve port bilgilerini girin. Değişiklikler uygulandıktan sonra sistem otomatik olarak yeniden bağlanacaktır."
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { viewModel.updateIpAddress(it) },
                    label = { Text("IP Adresi") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { viewModel.updateServerPort(it) },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (showWipeWarning) {
                    AlertDialog(
                        onDismissRequest = { showWipeWarning = false },
                        title = { Text("Veri Sıfırlama Uyarısı") },
                        text = { Text("Bu işlem cihazınızdaki tüm yerel kayıtları ve verileri tamamen temizleyecek ve yalnızca sunucudaki güncel verilerle eşitlenmenizi sağlayacaktır. İşlemi onaylıyor musunuz?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showWipeWarning = false
                                    viewModel.saveSettings()
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        app.db.clearAllTables()
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "Veriler temizlendi ve sunucuya bağlanılıyor...", android.widget.Toast.LENGTH_LONG).show()
                                            onBack()
                                        }
                                    }
                                }
                            ) { Text("Evet, Sil ve Bağlan", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWipeWarning = false }) { Text("Vazgeç") }
                        }
                    )
                }

                Button(
                    onClick = {
                        if (ipAddress.isNotBlank() && serverPort.toIntOrNull() != null) {
                            val currentIp = context.getSharedPreferences("secuasist_prefs", Context.MODE_PRIVATE).getString("server_ip", "")
                            if (currentIp == ipAddress) {
                                // Sadece aynı IP'ye bağlanılıyorsa uyarı vermeden bağlan
                                viewModel.saveSettings()
                                android.widget.Toast.makeText(context, "Mevcut ayarlarla bağlanılıyor...", android.widget.Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                // Yeni bir IP adresi girildiyse tüm verilerin silineceği uyarısını çıkar
                                showWipeWarning = true
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Lütfen geçerli değerler giriniz.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kaydet ve Bağlan")
                }
            }
        }
    }
}

@Composable
fun SettingsAppearanceTab(viewModel: SettingsViewModel) {
    val currentTheme by viewModel.currentTheme.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingCard(
            title = "Tema Ayarları",
            description = "Uygulamanın genel görünümünü tercihinize göre açık veya koyu mod olarak ayarlayın."
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                ThemeOptionWithDescription(
                    title = "Sistem Varsayılanı",
                    description = "Cihazınızın mevcut temasına otomatik olarak uyum sağlar.",
                    selected = currentTheme == AppTheme.SYSTEM,
                    onClick = { viewModel.updateTheme(AppTheme.SYSTEM) }
                )
                ThemeOptionWithDescription(
                    title = "Açık Tema",
                    description = "Aydınlık ve ferah bir görünüm sunar.",
                    selected = currentTheme == AppTheme.LIGHT,
                    onClick = { viewModel.updateTheme(AppTheme.LIGHT) }
                )
                ThemeOptionWithDescription(
                    title = "Koyu Tema",
                    description = "Göz yormayan karanlık bir arayüz sağlar.",
                    selected = currentTheme == AppTheme.DARK,
                    onClick = { viewModel.updateTheme(AppTheme.DARK) }
                )
            }
        }
    }
}

@Composable
fun SettingsPermissionsTab(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBatteryOptimizations by remember { 
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) 
    }
    
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

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
            icon = Icons.Default.Image,
            isEssential = false
        )
    )

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Battery Optimization is handled separately since it's an Intent, not a standard Permission
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

        // Standard Permissions
        allPermissions.forEach { item ->
            SettingsPermissionRow(item)
        }
    }
}

// -------------------------------------------------------------------------------------------
// Helper Composables
// -------------------------------------------------------------------------------------------

@Composable
fun SettingCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun ThemeOptionWithDescription(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsPermissionRow(item: PermissionItem) {
    val context = LocalContext.current
    var isGranted by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
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
