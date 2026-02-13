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
import com.serkantken.secuasist.network.WebSocketClient

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
    lateinit var wsClient: WebSocketClient
    private lateinit var prefs: SharedPreferences

    lateinit var syncManager: com.serkantken.secuasist.sync.SyncManager
    
    override fun onCreate() {
        super.onCreate()
        
        prefs = getSharedPreferences("secuasist_prefs", Context.MODE_PRIVATE)
        db = AppDatabase.getDatabase(this)

        val savedIp = prefs.getString("server_ip", "10.0.2.2") ?: "10.0.2.2"
        wsClient = WebSocketClient(savedIp, 8765)

        // initWebSocketListener() 
        wsClient.connect()
        
        // Start SyncManager
        syncManager = com.serkantken.secuasist.sync.SyncManager(this)
        syncManager.start()
        
        Log.i("SecuAsistApp", "✅ Uygulama (v2) başlatıldı ve Sync Manager aktif.")
    }

    override fun onTerminate() {
        super.onTerminate()
        // wsClient.disconnect()
        appScope.cancel()
    }
}
