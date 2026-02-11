package com.serkantken.secuasist.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.serkantken.secuasist.ui.screens.CargoScreen
import com.serkantken.secuasist.ui.screens.ContactsScreen
import com.serkantken.secuasist.ui.screens.HomeScreen
import com.serkantken.secuasist.ui.screens.FaultScreen

@Composable
fun SecuAsistApp() {
    val navController = rememberNavController()
    var selectedItem by rememberSaveable { mutableStateOf(0) }
    
    val items = listOf("Villalar", "Kişiler", "Kargo", "Arıza")
    val icons = listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.LocalShipping, Icons.Default.Build)
    val routes = listOf("home", "contacts", "cargo", "faults")

    val cargoViewModel: com.serkantken.secuasist.ui.viewmodels.CargoViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val pendingCargos by cargoViewModel.pendingCargos.collectAsState()
    val hasPendingCargos = pendingCargos.isNotEmpty()

    Scaffold(
        bottomBar = {
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
                        label = { Text(item) },
                        selected = selectedItem == index,
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen() }
            composable("contacts") { ContactsScreen() }
            composable("cargo") { CargoScreen() }
            composable("faults") { FaultScreen() }
        }
    }
}
