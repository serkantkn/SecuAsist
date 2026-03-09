package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.models.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val packageManager = application.packageManager

    private val _appsList = MutableStateFlow<List<AppInfo>>(emptyList())
    val appsList: StateFlow<List<AppInfo>> = _appsList

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            // Get all apps that have a launcher activity
            val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
            
            val appInfos = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                
                // Exclude our own app so it doesn't show up in its own app drawer
                if (packageName == getApplication<Application>().packageName) {
                    return@mapNotNull null
                }
                
                try {
                    val name = resolveInfo.loadLabel(packageManager).toString()
                    val icon = resolveInfo.loadIcon(packageManager)
                    AppInfo(name, packageName, icon)
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.name.lowercase() } // Sort alphabetically
            
            _appsList.value = appInfos
        }
    }
}
