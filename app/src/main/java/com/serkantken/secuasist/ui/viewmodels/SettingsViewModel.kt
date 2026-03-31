package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.data.AppTheme
import com.serkantken.secuasist.data.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val themePreferences = ThemePreferences(application)
    private val prefs = application.getSharedPreferences("secuasist_prefs", android.content.Context.MODE_PRIVATE)

    fun openDefaultAppsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback if the specific settings page is not available
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun isDefaultDialer(context: Context): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecomManager.defaultDialerPackage == context.packageName
    }

    // Legacy Settings State
    private val _ipAddress = MutableStateFlow(prefs.getString("server_ip", "10.0.2.2") ?: "10.0.2.2")
    val ipAddress: StateFlow<String> = _ipAddress

    private val _serverPort = MutableStateFlow(
        try {
            prefs.getString("server_port", "8765") ?: "8765"
        } catch (e: Exception) {
            prefs.getInt("server_port", 8765).toString()
        }
    )
    val serverPort: StateFlow<String> = _serverPort
    
    private val _deviceName = MutableStateFlow(prefs.getString("device_name", "") ?: "")
    val deviceName: StateFlow<String> = _deviceName
    
    private val _preferredGate = MutableStateFlow(prefs.getString("preferred_gate", "A") ?: "A")
    val preferredGate: StateFlow<String> = _preferredGate

    val currentTheme: StateFlow<AppTheme> = themePreferences.theme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.SYSTEM
        )

    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch {
            themePreferences.saveTheme(theme)
        }
    }
    
    fun updateIpAddress(ip: String) {
        _ipAddress.value = ip
    }
    
    fun updateServerPort(port: String) {
        if (port.all { it.isDigit() }) {
            _serverPort.value = port
        }
    }
    
    fun updatePreferredGate(gate: String) {
        _preferredGate.value = gate
        prefs.edit().putString("preferred_gate", gate).apply()
    }
    
    fun updateDeviceName(name: String) {
        _deviceName.value = name
    }
    
    fun saveSettings() {
        val ip = _ipAddress.value
        val port = _serverPort.value.toIntOrNull()
        val gate = _preferredGate.value
        val devName = _deviceName.value
        
        if (ip.isNotBlank() && port != null && devName.isNotBlank()) {
            prefs.edit()
                .putString("server_ip", ip)
                .putString("server_port", port.toString())
                .putString("preferred_gate", gate)
                .putString("device_name", devName)
                .apply()
                
            // Reconnect logic
            val app = getApplication<com.serkantken.secuasist.SecuAsistApplication>()
            try {
                app.wsClient.reconnectWithNewIp(ip, port)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
