package com.serkantken.secuasist.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.Villa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncManager(private val context: Context) {
    private val app = context.applicationContext as SecuAsistApplication
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Server Health Status
    private val _serverStatus = MutableStateFlow<JsonObject?>(null)
    val serverStatus = _serverStatus.asStateFlow()

    fun start() {
        // Listen for connection state changes
        app.wsClient.connectionState
            .onEach { state ->
                Log.i("SyncManager", "🔌 Connection state changed to: $state")
                if (state == com.serkantken.secuasist.network.ConnectionState.CONNECTED) {
                    Log.i("SyncManager", "🚀 Connected! Syncing all data...")
                    flushSyncQueue()
                    refreshAllData()
                    requestServerStatus()
                }
            }
            .launchIn(scope)

        // Listen for incoming messages
        app.wsClient.incomingMessages
            .onEach { message ->
                handleMessage(message)
            }
            .launchIn(scope)
    }

    fun requestServerStatus() {
        scope.launch {
            if (app.wsClient.isConnected()) {
                Log.d("SyncManager", "🖥️ Requesting server health status...")
                app.wsClient.sendData("GET_SERVER_STATUS", emptyMap<String, Any>())
            }
        }
    }

    fun refreshAllData() {
        scope.launch {
            if (app.wsClient.isConnected()) {
                Log.i("SyncManager", "🔄 Manual refresh requested → GET_ALL_DATA")
                app.wsClient.sendData("GET_ALL_DATA", emptyMap<String, Any>())
            } else {
                Log.w("SyncManager", "⚠️ Cannot refresh: WebSocket not connected.")
            }
        }
    }

    fun sendData(type: String, payload: Any) {
        scope.launch {
            if (app.wsClient.isConnected()) {
                app.wsClient.sendData(type, payload)
            } else {
                // Offline: Queue it
                val jsonPayload = gson.toJson(payload)
                val log = com.serkantken.secuasist.models.SyncLog(
                    actionType = type,
                    payload = jsonPayload
                )
                app.db.syncLogDao().insert(log)
                Log.i("SyncManager", "fYz Offline: Queued $type")
            }
        }
    }

    private suspend fun flushSyncQueue() {
        val pendingLogs = app.db.syncLogDao().getAllPendingLogs()
        if (pendingLogs.isNotEmpty()) {
            Log.i("SyncManager", "📤 Flushing ${pendingLogs.size} pending actions...")
            pendingLogs.forEach { log ->
                try {
                    // Send directly via WebSocketClient since we are connected
                    // We need to reconstruct the message format or just send payload map if sendData supports it?
                    // WebSocketClient.sendData takes Any.
                    // But payload in SyncLog is a JSON String.
                    // We should deserialize it to an Object or Map, OR updated WebSocketClient to support raw JSON payload?
                    // Actually WebSocketClient.sendData uses Gson to serialize `payload`.
                    // So if we pass a String, it will be double encoded JSON string.
                    // We must deserialize `log.payload` string back to an Object/Map.
                    val payloadObj = gson.fromJson(log.payload, Object::class.java) // or JsonElement
                    app.wsClient.sendData(log.actionType, payloadObj)
                    
                    // Delete index
                    app.db.syncLogDao().deleteById(log.id)
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to flush log ${log.id}: ${e.message}")
                }
            }
            Log.i("SyncManager", "✅ Queue flushed.")
        }
    }

    private suspend fun handleMessage(message: String) {
        try {
            if (!message.trim().startsWith("{")) return // Ignore non-JSON

            val jsonObject = gson.fromJson(message, JsonObject::class.java)
            if (!jsonObject.has("type")) return // Payload might be optional for some messages

            val type = jsonObject.get("type").asString
            val payload = if (jsonObject.has("payload")) jsonObject.get("payload") else JsonObject()

            Log.i("SyncManager", "📩 Received: $type")

            when (type) {
                "FULL_SYNC_REQUIRED" -> {
                    Log.i("SyncManager", "📢 Server requested FULL_SYNC_REQUIRED")
                    refreshAllData()
                }
                "FULL_SYNC" -> {
                    Log.i("SyncManager", "🔄 Starting FULL_SYNC...")
                    try {
                        Log.i("SyncManager", "Payload size: ${payload.toString().length} chars")
                        val data = gson.fromJson(payload, JsonObject::class.java)
                        
                        app.db.withTransaction {
                            Log.d("SyncManager", "🧹 Clearing old data...")
                            app.db.companyDelivererDao().deleteAll()
                            app.db.villaContactDao().deleteAll()
                            app.db.cameraDao().deleteAllCrossRefs()
                            app.db.cargoDao().deleteAll()
                            app.db.intercomDao().deleteAll()
                            app.db.cameraDao().deleteAll()
                            app.db.cargoCompanyDao().deleteAll()
                            app.db.contactDao().deleteAll()
                            app.db.villaDao().deleteAll()

                            Log.d("SyncManager", "📥 Inserting new data...")
                            if (data.has("villas")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<Villa>>() {}.type
                                val items: List<Villa> = gson.fromJson(data.get("villas"), listType)
                                app.db.villaDao().insertAll(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} Villas")
                            }
                            if (data.has("contacts")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<com.serkantken.secuasist.models.Contact>>() {}.type
                                val items: List<com.serkantken.secuasist.models.Contact> = gson.fromJson(data.get("contacts"), listType)
                                app.db.contactDao().insertAll(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} Contacts")
                            }
                            if (data.has("villaContacts")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<com.serkantken.secuasist.models.VillaContact>>() {}.type
                                val items: List<com.serkantken.secuasist.models.VillaContact> = gson.fromJson(data.get("villaContacts"), listType)
                                app.db.villaContactDao().insertAll(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} VillaContacts")
                            }
                            if (data.has("companies")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<CargoCompany>>() {}.type
                                val items: List<CargoCompany> = gson.fromJson(data.get("companies"), listType)
                                app.db.cargoCompanyDao().insertAll(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} Companies")
                            }
                            if (data.has("companyDeliverers")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<com.serkantken.secuasist.models.CompanyDelivererCrossRef>>() {}.type
                                val items: List<com.serkantken.secuasist.models.CompanyDelivererCrossRef> = gson.fromJson(data.get("companyDeliverers"), listType)
                                app.db.companyDelivererDao().insertAll(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} CompanyDeliverers")
                            }
                            if (data.has("cargos")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<Cargo>>() {}.type
                                val items: List<Cargo> = gson.fromJson(data.get("cargos"), listType)
                                app.db.cargoDao().insertAll(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} Cargos")
                            }
                            if (data.has("cameras")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<com.serkantken.secuasist.models.Camera>>() {}.type
                                val items: List<com.serkantken.secuasist.models.Camera> = gson.fromJson(data.get("cameras"), listType)
                                app.db.cameraDao().insertAll(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} Cameras")
                            }
                            if (data.has("intercoms")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<com.serkantken.secuasist.models.Intercom>>() {}.type
                                val items: List<com.serkantken.secuasist.models.Intercom> = gson.fromJson(data.get("intercoms"), listType)
                                app.db.intercomDao().insertAll(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} Intercoms")
                            }
                            if (data.has("cameraVisibleVillas")) {
                                val listType = object : com.google.gson.reflect.TypeToken<List<com.serkantken.secuasist.models.CameraVisibleVillaCrossRef>>() {}.type
                                val items: List<com.serkantken.secuasist.models.CameraVisibleVillaCrossRef> = gson.fromJson(data.get("cameraVisibleVillas"), listType)
                                app.db.cameraDao().insertAllCrossRefs(items)
                                Log.i("SyncManager", "✅ Synced ${items.size} CameraVisibleVillas")
                            }
                        }
                        Log.i("SyncManager", "🏁 FULL_SYNC Completed Successfully!")
                    } catch (e: Exception) {
                        Log.e("SyncManager", "❌ FULL_SYNC Failed: ${e.message}", e)
                    }
                }
                "ADD_CARGO" -> {
                    val cargo = gson.fromJson(payload, Cargo::class.java)
                    app.db.cargoDao().insert(cargo)
                    Log.i("SyncManager", "📥 Synced New Cargo: ${cargo.cargoId}")
                }
                "UPDATE_CARGO_STATUS" -> {
                    // Update specific fields or replace entire object
                    // Payload might be partial or full. In server.py we used full payload structure for insert, 
                    // and partial for update. Let's assume we receive specific fields or we can parse as Cargo 
                    // but some fields might be irrelevant.
                    // Actually, the server broadcasts what it received.
                    // For UPDATE_CARGO_STATUS, the payload has: isCalled, isMissed, callDate, ...
                    // We can deserialize into a helper object or just use fields manually.
                    val updateData = gson.fromJson(payload, JsonObject::class.java)
                    val cargoId = updateData.get("cargoId").asInt
                    val isCalled = updateData.get("isCalled").asInt
                    val isMissed = updateData.get("isMissed").asInt
                    val callDate = if (updateData.has("callDate") && !updateData.get("callDate").isJsonNull) updateData.get("callDate").asString else null
                    val attempt = updateData.get("callAttemptCount").asInt
                    val whoCalled = if (updateData.has("whoCalled") && !updateData.get("whoCalled").isJsonNull) updateData.get("whoCalled").asString else null
                    
                    app.db.cargoDao().updateCargoCallStatus(
                        cargoId, isCalled, isMissed, callDate, attempt, whoCalled
                    )
                    Log.i("SyncManager", "📥 Synced Cargo Status: $cargoId")
                }
                "ADD_COMPANY" -> {
                    val company = gson.fromJson(payload, CargoCompany::class.java)
                    app.db.cargoCompanyDao().insert(company)
                    Log.i("SyncManager", "📥 Synced New Company: ${company.companyName}")
                }
                "ADD_VILLA" -> {
                    val villa = gson.fromJson(payload, Villa::class.java)
                    app.db.villaDao().insert(villa)
                    Log.i("SyncManager", "📥 Synced New Villa: ${villa.villaNo}")
                }
                "UPDATE_VILLA" -> {
                    val villa = gson.fromJson(payload, Villa::class.java)
                    app.db.villaDao().update(villa)
                    Log.i("SyncManager", "📥 Synced Update Villa: ${villa.villaNo}")
                }
                "DELETE_VILLA" -> {
                    // Payload might be Integer ID directly or an Object. 
                    // Gson maps numbers to Double by default for Object/Any types if not specified.
                    // Let's assume we send object with ID or just check.
                    // Safest to send object { "villaId": 123 }
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("villaId")) {
                        val id = data.get("villaId").asInt
                        app.db.villaDao().deleteById(id)
                        Log.i("SyncManager", "🗑️ Synced Delete Villa: $id")
                    }
                }
                "ADD_CONTACT" -> {
                    val contact = gson.fromJson(payload, com.serkantken.secuasist.models.Contact::class.java)
                    app.db.contactDao().insert(contact)
                    Log.i("SyncManager", "📥 Synced New Contact: ${contact.contactName}")
                }
                "DELETE_CONTACT" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("contactId")) {
                        val id = data.get("contactId").asString
                        app.db.contactDao().deleteById(id)
                        Log.i("SyncManager", "🗑️ Synced Delete Contact: $id")
                    }
                }
                "UPDATE_CONTACT" -> {
                    val contact = gson.fromJson(payload, com.serkantken.secuasist.models.Contact::class.java)
                    // We need to use update method.
                    app.db.contactDao().update(contact)
                    Log.i("SyncManager", "📥 Synced Update Contact: ${contact.contactName}")
                }
                "ADD_VILLA_CONTACT" -> {
                    val link = gson.fromJson(payload, com.serkantken.secuasist.models.VillaContact::class.java)
                    app.db.villaContactDao().insert(link)
                    Log.i("SyncManager", "📥 Synced Villa Link: V${link.villaId}-C${link.contactId}")
                }
                "DELETE_VILLA_CONTACT" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("villaId") && data.has("contactId")) {
                        val vId = data.get("villaId").asInt
                        val cId = data.get("contactId").asString
                        app.db.villaContactDao().deleteByVillaIdAndContactId(vId, cId)
                        Log.i("SyncManager", "🗑️ Synced Delete Villa Link: V$vId-C$cId")
                    }
                }
                
                "UPDATE_COMPANY" -> {
                    val company = gson.fromJson(payload, CargoCompany::class.java)
                    app.db.cargoCompanyDao().update(company)
                    Log.i("SyncManager", "📥 Synced Update Company: ${company.companyName}")
                }
                "DELETE_COMPANY" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("companyId")) {
                        val id = data.get("companyId").asInt
                        app.db.cargoCompanyDao().deleteById(id)
                        Log.i("SyncManager", "🗑️ Synced Delete Company: $id")
                    }
                }
                "ADD_COMPANY_CONTACT" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    val companyId = data.get("companyId").asInt
                    val contactId = data.get("contactId").asString
                    // Optional role/primary
                    val role = if (data.has("role") && !data.get("role").isJsonNull) data.get("role").asString else "Driver"
                    val isPrimary = if (data.has("isPrimaryContact")) data.get("isPrimaryContact").asInt else 0

                    val crossRef = com.serkantken.secuasist.models.CompanyDelivererCrossRef(
                        companyId = companyId,
                        contactId = contactId,
                        role = role,
                        isPrimaryContact = isPrimary
                    )
                    app.db.companyDelivererDao().addDelivererToCompany(crossRef)
                    Log.i("SyncManager", "📥 Synced Company Link: Co$companyId-Ct$contactId")
                }
                "DELETE_COMPANY_CONTACT" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("companyId") && data.has("contactId")) {
                        val companyId = data.get("companyId").asInt
                        val contactId = data.get("contactId").asString
                        app.db.companyDelivererDao().removeDelivererFromCompany(companyId, contactId)
                        Log.i("SyncManager", "🗑️ Synced Delete Company Link: Co$companyId-Ct$contactId")
                    }
                }

                // --- FAULT TRACKING ---
                "ADD_CAMERA" -> {
                    val camera = gson.fromJson(payload, com.serkantken.secuasist.models.Camera::class.java)
                    app.db.cameraDao().insert(camera)
                    Log.i("SyncManager", "📥 Synced New Camera: ${camera.cameraName}")
                }
                "UPDATE_CAMERA_STATUS" -> {
                     val camera = gson.fromJson(payload, com.serkantken.secuasist.models.Camera::class.java)
                     app.db.cameraDao().update(camera)
                     Log.i("SyncManager", "📥 Synced Update Camera: ${camera.cameraId}")
                }
                "DELETE_CAMERA" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("cameraId")) {
                        val id = data.get("cameraId").asString
                        app.db.cameraDao().deleteById(id)
                        Log.i("SyncManager", "🗑️ Synced Delete Camera: $id")
                    }
                }
                "ADD_CAMERA_VISIBLE_VILLA" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("cameraId") && data.has("villaId")) {
                        val cId = data.get("cameraId").asString
                        val vId = data.get("villaId").asInt
                        val crossRef = com.serkantken.secuasist.models.CameraVisibleVillaCrossRef(cId, vId)
                        app.db.cameraDao().insertCrossRef(crossRef)
                        Log.i("SyncManager", "📥 Synced Camera Link: C$cId-V$vId")
                    }
                }
                "DELETE_CAMERA_VISIBLE_VILLA" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("cameraId") && data.has("villaId")) {
                        val cId = data.get("cameraId").asString
                        val vId = data.get("villaId").asInt
                        app.db.cameraDao().deleteCrossRef(cId, vId)
                        Log.i("SyncManager", "🗑️ Synced Delete Camera Link: C$cId-V$vId")
                    }
                }
                "ADD_INTERCOM" -> {
                    val intercom = gson.fromJson(payload, com.serkantken.secuasist.models.Intercom::class.java)
                    app.db.intercomDao().insert(intercom)
                    Log.i("SyncManager", "📥 Synced New Intercom: ${intercom.intercomName}")
                }
                "UPDATE_INTERCOM_STATUS" -> {
                    val intercom = gson.fromJson(payload, com.serkantken.secuasist.models.Intercom::class.java)
                    app.db.intercomDao().update(intercom)
                    Log.i("SyncManager", "📥 Synced Update Intercom: ${intercom.intercomId}")
                }
                "DELETE_INTERCOM" -> {
                    val data = gson.fromJson(payload, JsonObject::class.java)
                    if (data.has("intercomId")) {
                        val id = data.get("intercomId").asString
                        app.db.intercomDao().deleteById(id)
                        Log.i("SyncManager", "🗑️ Synced Delete Intercom: $id")
                    }
                }
                "SERVER_STATUS" -> {
                    _serverStatus.value = payload.asJsonObject
                    Log.d("SyncManager", "🖥️ Server metrics updated")
                }
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync Error: ${e.message}")
        }
    }
}
