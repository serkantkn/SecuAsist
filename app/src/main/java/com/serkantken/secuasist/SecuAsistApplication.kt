package com.serkantken.secuasist

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaContact
import com.serkantken.secuasist.network.WebSocketClient
import com.serkantken.secuasist.utils.VillaContactDeserializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Gelen mesajın önce tipini anlamak için kullanılacak basit bir veri sınıfı
data class BaseWebSocketMessage(val type: String, val payload: JsonElement)

class SecuAsistApplication : Application() {

    // WebSocket istemcisini lazy initialization ile kur.
    // IP adresini Hawk'tan okur, bulamazsa varsayılan bir IP kullanır.
    val webSocketClient: WebSocketClient by lazy {
        val savedIp = Hawk.get<String?>("server_ip", null)
        val defaultIp = "192.168.1.34" // Varsayılan IP
        val serverIpToUse = if (savedIp.isNullOrEmpty() || !android.net.InetAddresses.isNumericAddress(savedIp)) {
            Log.w("SecuAsistApplication", "Geçerli IP bulunamadı, varsayılan kullanılıyor: $defaultIp")
            defaultIp
        } else {
            Log.i("SecuAsistApplication", "Hawk'tan okunan IP kullanılıyor: $savedIp")
            savedIp
        }
        WebSocketClient(serverIpToUse, 8765, // Port 8765
            onMessageReceived = { messageText ->
                processIncomingWebSocketMessage(messageText)
            })
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    // Gson nesnesini tekrar tekrar oluşturmamak için
    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(VillaContact::class.java, VillaContactDeserializer())
            .create()
    }

    // Uygulama genelinde arka plan işlemleri için CoroutineScope
    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Hawk.init(this).build()
        Log.d("SecuAsistApplication", "Uygulama başlatıldı, WebSocket bağlanıyor...")
        webSocketClient.connect() // WebSocket bağlantısını başlat
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("SecuAsistApplication", "Uygulama sonlandırılıyor, WebSocket bağlantısı kesiliyor...")
        webSocketClient.disconnect()
    }

    /**
     * Sunucudan gelen tüm WebSocket mesajlarını işleyen ana fonksiyon.
     */
    private fun processIncomingWebSocketMessage(jsonMessage: String) {try {
        val baseMessage = gson.fromJson(jsonMessage, BaseWebSocketMessage::class.java)
        Log.d("WebSocketProcessing", "Gelen Mesaj Tipi: ${baseMessage.type}")

        applicationScope.launch {
            when (baseMessage.type) {
                "VILLA_UPSERT" -> {
                    val villa = gson.fromJson(baseMessage.payload, Villa::class.java)
                    database.villaDao().insert(villa)
                    Log.i("WebSocketProcessing", "Villa güncellendi/eklendi: ID ${villa.villaId}")
                }
                "CONTACT_UPSERT" -> {
                    val contact = gson.fromJson(baseMessage.payload, Contact::class.java)
                    database.contactDao().insert(contact)
                    Log.i("WebSocketProcessing", "Kişi güncellendi/eklendi: ID ${contact.contactId}")
                }
                "VILLACONTACT_LINK" -> {
                    val villaContact = gson.fromJson(baseMessage.payload, VillaContact::class.java)
                    database.villaContactDao().insert(villaContact)
                    Log.i("WebSocketProcessing", "Villa-Kişi ilişkisi güncellendi/eklendi: VillaID ${villaContact.villaId}, ContactID ${villaContact.contactId}")
                }
                "CARGOCOMPANY_UPSERT" -> {
                    val cargoCompany = gson.fromJson(baseMessage.payload, CargoCompany::class.java)
                    database.cargoCompanyDao().insert(cargoCompany)
                    Log.i("WebSocketProcessing", "Kargo Şirketi güncellendi/eklendi: ID ${cargoCompany.companyId}")
                }
                "CARGO_UPSERT" -> {
                    val cargo = gson.fromJson(baseMessage.payload, Cargo::class.java)
                    database.cargoDao().insert(cargo)
                    Log.i("WebSocketProcessing", "Kargo güncellendi/eklendi: ID ${cargo.cargoId}")
                }
                "VILLA_DELETE" -> {
                    val villaId = baseMessage.payload.asJsonObject.get("villaId").asInt
                    database.villaDao().deleteById(villaId)
                    Log.i("WebSocketProcessing", "Villa silindi: ID $villaId")
                }
                "CONTACT_DELETE" -> {
                    val contactId = baseMessage.payload.asJsonObject.get("contactId").asInt
                    database.contactDao().deleteById(contactId) // Dao'da bu metod olmalı
                    Log.i("WebSocketProcessing", "Kişi silindi: ID $contactId")
                }
                "VILLACONTACT_UNLINK" -> {
                    val payload = baseMessage.payload.asJsonObject
                    val villaId = payload.get("villaId").asInt
                    val contactId = payload.get("contactId").asInt
                    database.villaContactDao().deleteByVillaIdAndContactId(villaId, contactId) // Dao'da bu metod olmalı
                    Log.i("WebSocketProcessing", "Villa-Kişi ilişkisi silindi: VillaID $villaId, ContactID $contactId")
                }
                else -> {
                    Log.w("WebSocketProcessing", "Bilinmeyen mesaj tipi alındı: ${baseMessage.type}")
                }
            }
        }
    } catch (e: Exception) {
        Log.e("WebSocketProcessing", "Gelen WebSocket mesajı işlenirken hata oluştu: $jsonMessage", e)
    }
    }

    /**
     * Sunucuya bir veri göndermek için kullanılan genel yardımcı fonksiyon.
     * @param type Mesajın tipi (örn: "VILLA_UPSERT", "CONTACT_DELETE").
     * @param payload Gönderilecek veri nesnesi (örn: bir Villa, Contact veya sadece bir ID).
     */
    private fun sendWebSocketMessage(type: String, payload: Any) {
        applicationScope.launch {
            try {
                // Sunucunun beklediği format: {"type": "...", "payload": ...}
                val message = mapOf("type" to type, "payload" to payload)
                val jsonMessage = gson.toJson(message)
                webSocketClient.sendMessage(jsonMessage)
            } catch (e: Exception) {
                Log.e("WebSocketSending", "WebSocket mesajı gönderilirken hata: Type=$type", e)
            }
        }
    }

    // --- DIŞARIDAN ÇAĞRILACAK KOLAYLAŞTIRICI FONKSİYONLAR ---

    fun sendUpsert(data: Any) {
        val type = when (data) {
            is Villa -> "VILLA_UPSERT"
            is Contact -> "CONTACT_UPSERT"
            is VillaContact -> "VILLACONTACT_LINK"
            is CargoCompany -> "CARGOCOMPANY_UPSERT"
            is Cargo -> "CARGO_UPSERT"
            else -> {
                Log.w("SecuAsistApplication", "Desteklenmeyen veri tipi: ${data.javaClass.simpleName}")
                return
            }
        }
        sendWebSocketMessage(type, data)
    }

    fun sendDelete(entityName: String, id: Any) {
        val type = when(entityName.uppercase()) {
            "VILLA" -> "VILLA_DELETE"
            "CONTACT" -> "CONTACT_DELETE"
            // Diğerleri...
            else -> {
                Log.w("SecuAsistApplication", "Desteklenmeyen silme tipi: $entityName")
                return
            }
        }
        // Silme işlemi için genellikle sadece ID'yi içeren bir payload göndeririz.
        val payload = mapOf("${entityName.lowercase()}Id" to id)
        sendWebSocketMessage(type, payload)
    }

    fun sendUnlinkVillaContact(villaId: Int, contactId: Int) {
        val payload = mapOf("villaId" to villaId, "contactId" to contactId)
        sendWebSocketMessage("VILLACONTACT_UNLINK", payload)
    }
}