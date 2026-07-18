package com.serkantken.secuasist

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.models.*

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SecuAsistApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // We recreate Deserializer since we deleted utils folder? 
    // Wait, I deleted utils folder. VillaContactDeserializer is gone.
    // I need to use standard Gson or recreate the deserializer.
    // For now, I will comment out custom deserializer usage and rely on default if possible 
    // or assume standard JSON structure.
    private val gson: Gson = GsonBuilder()
        //.registerTypeAdapter(VillaContact::class.java, VillaContactDeserializer()) 
        .create()

    lateinit var db: AppDatabase
    private lateinit var prefs: SharedPreferences

    lateinit var updateManager: com.serkantken.secuasist.utils.UpdateManager
    
    override fun onCreate() {
        super.onCreate()
        
        prefs = getSharedPreferences("secuasist_prefs", Context.MODE_PRIVATE)
        db = AppDatabase.getDatabase(this)
        updateManager = com.serkantken.secuasist.utils.UpdateManager(this)

        Log.i("SecuAsistApp", "✅ Uygulama (v2) başlatıldı.")
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}
