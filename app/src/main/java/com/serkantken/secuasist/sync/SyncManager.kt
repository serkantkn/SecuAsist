package com.serkantken.secuasist.sync

import android.content.Context
import android.util.Log
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

class SyncManager(private val context: Context) {
    private val app = context.applicationContext as SecuAsistApplication
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        app.wsClient.incomingMessages
            .onEach { message ->
                handleMessage(message)
            }
            .launchIn(scope)
    }

    private suspend fun handleMessage(message: String) {
        try {
            if (!message.trim().startsWith("{")) return // Ignore non-JSON

            val jsonObject = gson.fromJson(message, JsonObject::class.java)
            if (!jsonObject.has("type") || !jsonObject.has("payload")) return

            val type = jsonObject.get("type").asString
            val payload = jsonObject.get("payload")

            when (type) {
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
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync Error: ${e.message}")
        }
    }
}
