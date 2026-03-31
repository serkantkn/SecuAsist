package com.serkantken.secuasist.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.serkantken.secuasist.ui.screens.CargoScreen
import com.serkantken.secuasist.ui.screens.ContactsScreen
import com.serkantken.secuasist.ui.screens.HomeScreen
import com.serkantken.secuasist.ui.screens.FaultScreen

import com.serkantken.secuasist.SecuAsistApplication

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun SecuAsistApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("secuasist_prefs", android.content.Context.MODE_PRIVATE) }
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("is_first_launch", true)) }
    
    val app = context.applicationContext as SecuAsistApplication

    val navController = rememberNavController()
    var selectedItem by rememberSaveable { mutableStateOf(0) }
    
    // Check if app is default launcher
    var isDefaultLauncher by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                }
                val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                isDefaultLauncher = resolveInfo?.activityInfo?.packageName == context.packageName
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Track current route to hide bottom bar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val baseItems = listOf("Villalar", "Kişiler", "Kargo", "Arıza")
    val baseIcons = listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.LocalShipping, Icons.Default.Build)
    val baseRoutes = listOf("home", "contacts", "cargo", "faults")

    val items = if (isDefaultLauncher) baseItems + "Uygulamalar" else baseItems
    val icons = if (isDefaultLauncher) baseIcons + Icons.Default.Apps else baseIcons
    val routes = if (isDefaultLauncher) baseRoutes + "apps" else baseRoutes

    // Nav bar handling ... (unchanged logic for index)
    
    val cargoViewModel: com.serkantken.secuasist.ui.viewmodels.CargoViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val pendingCargos by cargoViewModel.pendingCargos.collectAsState()
    val hasPendingCargos = pendingCargos.isNotEmpty()

    Scaffold(
        bottomBar = {
            if (currentRoute != "onboarding" && currentRoute != "settings") {
                NavigationBar {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = {
                                if (index == 2 && hasPendingCargos) {
                                    androidx.compose.material3.BadgedBox(
                                        badge = { 
                                            androidx.compose.material3.Badge(
                                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onError
                                            ) {
                                                androidx.compose.material3.Text(pendingCargos.size.toString())
                                            }
                                        }
                                    ) {
                                        Icon(icons[index], contentDescription = item)
                                    }
                                } else {
                                    Icon(icons[index], contentDescription = item)
                                }
                            },
                            label = { 
                                Text(
                                    text = item,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false,
                                    fontSize = 11.sp
                                )
                            },
                            selected = currentRoute == routes[index],
                            onClick = {
                                selectedItem = index
                                navController.navigate(routes[index]) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isFirstLaunch) "onboarding" else "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("onboarding") {
                com.serkantken.secuasist.ui.screens.OnboardingScreen(
                    onContinue = { deviceName ->
                        prefs.edit()
                            .putBoolean("is_first_launch", false)
                            .putString("device_name", deviceName)
                            .apply()
                        isFirstLaunch = false
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("home") { 
                HomeScreen(
                    isDefaultLauncher = isDefaultLauncher,
                    onSettingsClick = { navController.navigate("settings") }
                ) 
            }
            composable("contacts") { ContactsScreen() }
            composable("cargo") { CargoScreen() }
            composable("faults") { FaultScreen() }
            composable("apps") {
                val context = androidx.compose.ui.platform.LocalContext.current
                val application = context.applicationContext as android.app.Application
                val appsViewModel: com.serkantken.secuasist.ui.viewmodels.AppsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )
                com.serkantken.secuasist.ui.screens.AppsScreen(viewModel = appsViewModel)
            }
            composable("settings") { 
                val context = androidx.compose.ui.platform.LocalContext.current
                val application = context.applicationContext as android.app.Application
                val settingsViewModel: com.serkantken.secuasist.ui.viewmodels.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )
                
                com.serkantken.secuasist.ui.screens.SettingsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = settingsViewModel,
                    onUserManagementClick = { /* Disabled */ }
                ) 
            }
        }
    }
}
