package com.serkantken.secuasist

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.models.*
import com.serkantken.secuasist.network.WebSocketClient
import com.serkantken.secuasist.utils.VillaContactDeserializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Tüm uygulama genelinde WebSocket bağlantısını, gelen mesajların işlenmesini ve
 * veritabanı ile senkronizasyonu yöneten sınıf.
 */
class SecuAsistApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(VillaContact::class.java, VillaContactDeserializer())
        .create()

    lateinit var db: AppDatabase
    lateinit var wsClient: WebSocketClient

    private var debounceJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        Hawk.init(this).build()
        db = AppDatabase.getDatabase(this)

        val savedIp = Hawk.get("server_ip", "192.168.1.34")
        wsClient = WebSocketClient(savedIp, 8765)

        initWebSocketListener()
        wsClient.connect()

        Log.i("SecuAsistApp", "✅ Uygulama başlatıldı, WS bağlanıyor: $savedIp")
    }

    override fun onTerminate() {
        super.onTerminate()
        wsClient.disconnect()
        appScope.cancel()
    }

    // ============================================================
    // 🔊 GELEN MESAJLARIN DİNLENMESİ
    // ============================================================

    private fun initWebSocketListener() {
        wsClient.incomingMessages
            .onEach { messageText ->
                processIncomingMessage(messageText)
            }
            .catch { e ->
                Log.e("SecuAsistApp", "Mesaj akışı hatası", e)
            }
            .launchIn(appScope)
    }

    // ============================================================
    // 🧩 MESAJLARIN İŞLENMESİ
    // ============================================================

    private suspend fun processIncomingMessage(json: String) {
        if (!json.startsWith("{")) {
            Log.d("WSProcessing", "Durum mesajı (JSON değil): $json")
            return
        }

        try {
            val base = gson.fromJson(json, BaseWebSocketMessage::class.java)
            Log.d("WSProcessing", "📨 Alındı: ${base.type}")

            var dataChanged = false

            db.withTransaction {
                when (base.type) {

                    // ----------- EKLEME/GÜNCELLEME -----------

                    "VILLA_UPSERT" -> {
                        val villa = gson.fromJson(base.payload, Villa::class.java)
                        if (db.villaDao().getVillaById(villa.villaId) != null)
                            db.villaDao().update(villa)
                        else
                            db.villaDao().insert(villa)
                        dataChanged = true
                    }

                    "CONTACT_UPSERT" -> {
                        val contact = gson.fromJson(base.payload, Contact::class.java)
                        if (db.contactDao().getContactById(contact.contactId) != null)
                            db.contactDao().update(contact)
                        else
                            db.contactDao().insert(contact)
                        dataChanged = true
                    }

                    "VILLACONTACT_LINK" -> {
                        val vc = gson.fromJson(base.payload, VillaContact::class.java)
                        db.villaContactDao().insert(vc)
                        dataChanged = true
                    }

                    "CARGOCOMPANY_UPSERT" -> {
                        val c = gson.fromJson(base.payload, CargoCompany::class.java)
                        if (db.cargoCompanyDao().getCargoCompanyById(c.companyId) != null)
                            db.cargoCompanyDao().update(c)
                        else
                            db.cargoCompanyDao().insert(c)
                        dataChanged = true
                    }

                    "CARGO_UPSERT" -> {
                        val cargo = gson.fromJson(base.payload, Cargo::class.java)
                        if (db.cargoDao().getCargoById(cargo.cargoId) != null)
                            db.cargoDao().update(cargo)
                        else
                            db.cargoDao().insert(cargo)
                        dataChanged = true
                    }

                    // ----------- SİLME -----------

                    "VILLA_DELETE" -> {
                        val id = base.payload.asJsonObject["villaId"].asInt
                        db.villaDao().deleteById(id)
                        dataChanged = true
                    }

                    "CONTACT_DELETE" -> {
                        val id = base.payload.asJsonObject["contactId"].asInt
                        db.contactDao().deleteById(id)
                        dataChanged = true
                    }

                    "VILLACONTACT_UNLINK" -> {
                        val obj = base.payload.asJsonObject
                        val villaId = obj["villaId"].asInt
                        val contactId = obj["contactId"].asInt
                        db.villaContactDao().deleteByVillaIdAndContactId(villaId, contactId)
                        dataChanged = true
                    }

                    // ----------- SENKRONİZASYON -----------

                    "SYNC_RESPONSE" -> {
                        try {
                            val payloadObj = base.payload.asJsonObject ?: return@withTransaction

                            val villas = payloadObj["villas"]?.asJsonArray ?: JsonArray()
                            val contacts = payloadObj["contacts"]?.asJsonArray ?: JsonArray()
                            val villaContacts = payloadObj["villaContacts"]?.asJsonArray ?: JsonArray()
                            val cargoCompanies = payloadObj["cargoCompanies"]?.asJsonArray ?: JsonArray()

                            handleSyncData(villas, contacts, villaContacts, cargoCompanies)

                        } catch (ex: Exception) {
                            Log.e("WSProcessing", "❌ SYNC_RESPONSE parse hatası: $json", ex)
                        }
                        return@withTransaction
                    }

                    else -> Log.w("WSProcessing", "⚠️ Bilinmeyen mesaj tipi: ${base.type}")
                }
            }

            if (dataChanged) debounceUIBroadcast()

        } catch (e: Exception) {
            Log.e("WSProcessing", "Mesaj işlenirken hata: $json", e)
        }
    }

    private suspend fun handleSyncData(
        villas: JsonArray,
        contacts: JsonArray,
        villaContacts: JsonArray,
        cargoCompanies: JsonArray
    ) {
        db.withTransaction {
            // 1. VILLAS
            for (item in villas) {
                try {
                    val villa = gson.fromJson(item, Villa::class.java)
                    if (db.villaDao().getVillaById(villa.villaId) != null)
                        db.villaDao().update(villa)
                    else
                        db.villaDao().insert(villa)
                } catch (e: Exception) {
                    Log.e("SYNC", "❌ Villa parse hatası: $item", e)
                }
            }

            // 2. CONTACTS
            for (item in contacts) {
                try {
                    val contact = gson.fromJson(item, Contact::class.java)
                    if (db.contactDao().getContactById(contact.contactId) != null)
                        db.contactDao().update(contact)
                    else
                        db.contactDao().insert(contact)
                } catch (e: Exception) {
                    Log.e("SYNC", "❌ Contact parse hatası: $item", e)
                }
            }

            // 3. CARGO COMPANIES
            for (item in cargoCompanies) {
                try {
                    val company = gson.fromJson(item, CargoCompany::class.java)
                    if (db.cargoCompanyDao().getCargoCompanyById(company.companyId) != null)
                        db.cargoCompanyDao().update(company)
                    else
                        db.cargoCompanyDao().insert(company)
                } catch (e: Exception) {
                    Log.e("SYNC", "❌ CargoCompany parse hatası: $item", e)
                }
            }

            // 4. VILLA-CONTACTS (En Sonda)
            for (item in villaContacts) {
                try {
                    val vc = gson.fromJson(item, VillaContact::class.java)

                    val villaExists = db.villaDao().getVillaById(vc.villaId) != null
                    val contactExists = db.contactDao().getContactById(vc.contactId) != null

                    if (villaExists && contactExists) {
                        db.villaContactDao().insert(vc)
                    } else {
                        Log.w("SYNC", "⚠️ Atlandı (FK yok): $vc")
                    }
                } catch (e: Exception) {
                    Log.e("SYNC", "❌ VillaContact parse hatası: $item", e)
                }
            }
        }

        Log.d("SYNC", "✅ Senkronizasyon başarıyla tamamlandı.")
        debounceUIBroadcast()
    }

    // ============================================================
    // 🔁 UI GÜNCELLEMELERİ
    // ============================================================

    private fun debounceUIBroadcast() {
        debounceJob?.cancel()
        debounceJob = appScope.launch {
            delay(600)
            Log.i("WSProcessing", "🔄 Veri değişti, broadcast gönderiliyor.")
            sendBroadcast(Intent("com.serkantiken.secuasist.DATA_UPDATED"))
        }
    }

    // ============================================================
    // 📤 MESAJ GÖNDERME
    // ============================================================

    private fun sendMessage(type: String, payload: Any) {
        val json = gson.toJson(mapOf("type" to type, "payload" to payload))
        wsClient.sendMessage(json)
    }

    fun sendUpsert(data: Any) {
        val type = when (data) {
            is Villa -> "VILLA_UPSERT"
            is Contact -> "CONTACT_UPSERT"
            is VillaContact -> "VILLACONTACT_LINK"
            is CargoCompany -> "CARGOCOMPANY_UPSERT"
            is Cargo -> "CARGO_UPSERT"
            else -> return
        }
        sendMessage(type, data)
    }

    fun sendDelete(entity: String, id: Int) {
        val type = "${entity.uppercase()}_DELETE"
        val key = "${entity.lowercase()}Id"
        val payload = mapOf(key to id)
        sendMessage(type, payload)
    }

    fun sendUnlink(villaId: Int, contactId: Int) {
        val payload = mapOf("villaId" to villaId, "contactId" to contactId)
        sendMessage("VILLACONTACT_UNLINK", payload)
    }
}
