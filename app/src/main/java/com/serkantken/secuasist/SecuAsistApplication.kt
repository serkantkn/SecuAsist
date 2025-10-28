package com.serkantken.secuasist

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.text.get

// Gelen mesajın önce tipini anlamak için kullanılacak basit bir veri sınıfı
data class BaseWebSocketMessage(val type: String, val payload: JsonElement)

class SecuAsistApplication : Application() {

    // WebSocket istemcisini lazy initialization ile kur.
    // IP adresini Hawk'tan okur, bulamazsa varsayılan bir IP kullanır.
    val webSocketClient: WebSocketClient by lazy {
        WebSocketClient(Hawk.get("server_ip", "192.168.1.34"), 8765)
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
    private var uiUpdateDebounceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Hawk.init(this).build()
        initWebSocketListener()
        Log.d("SecuAsistApplication", "Uygulama başlatıldı, WebSocket bağlanıyor...")
        webSocketClient.connect() // WebSocket bağlantısını başlat
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("SecuAsistApplication", "Uygulama sonlandırılıyor, WebSocket bağlantısı kesiliyor...")
        uiUpdateDebounceJob?.cancel()
        webSocketClient.disconnect()
    }

    private fun initWebSocketListener() {
        Log.d("SecuAsistApplication", "WebSocket mesaj dinleyicisi başlatılıyor.")
        webSocketClient.incomingMessages
            .onEach { messageText ->
                // Bu blok, her yeni mesaj geldiğinde sıralı olarak çalışır
                processIncomingWebSocketMessage(messageText)
            }
            .catch { e ->
                Log.e("SecuAsistApplication", "Mesaj akışını dinlerken hata oluştu.", e)
            }
            .launchIn(applicationScope) // Bu Flow'u uygulama ömrü boyunca dinle
    }

    /**
     * Sunucudan gelen tüm WebSocket mesajlarını işleyen ana fonksiyon.
     */

    private suspend fun processIncomingWebSocketMessage(jsonMessage: String) {
        try {
            if (!jsonMessage.startsWith("{")) {
                Log.d("WebSocketProcessing", "Statü mesajı alındı, işlenmiyor: $jsonMessage")
                return
            }

            val baseMessage = gson.fromJson(jsonMessage, BaseWebSocketMessage::class.java)
            Log.d("WebSocketProcessing", "İşlem Başladı: ${baseMessage.type}")

            var isDataChanged = false
            database.withTransaction {
                when (baseMessage.type) {
                    "VILLA_UPSERT" -> {
                        val villa = gson.fromJson(baseMessage.payload, Villa::class.java)
                        val existing = database.villaDao().getVillaById(villa.villaId)
                        if (existing == null) database.villaDao().insert(villa)
                        else database.villaDao().update(villa)
                        isDataChanged = true
                    }
                    "CONTACT_UPSERT" -> {
                        val contact = gson.fromJson(baseMessage.payload, Contact::class.java)
                        val existing = database.contactDao().getContactById(contact.contactId)
                        if (existing == null) database.contactDao().insert(contact)
                        else database.contactDao().update(contact)
                        isDataChanged = true
                    }
                    // İlişki ve diğer tablolarda REPLACE kullanmak genellikle daha basittir
                    "VILLACONTACT_LINK" -> {
                        database.villaContactDao().insert(gson.fromJson(baseMessage.payload, VillaContact::class.java))
                        isDataChanged = true
                    }
                    "CARGOCOMPANY_UPSERT" -> {
                        database.cargoCompanyDao().insert(gson.fromJson(baseMessage.payload, CargoCompany::class.java))
                        isDataChanged = true
                    }
                    "CARGO_UPSERT" -> {
                        database.cargoDao().insert(gson.fromJson(baseMessage.payload, Cargo::class.java))
                        isDataChanged = true
                    }
                    "VILLA_DELETE" -> database.villaDao().deleteById(baseMessage.payload.asJsonObject.get("villaId").asInt)
                    "CONTACT_DELETE" -> database.contactDao().deleteById(baseMessage.payload.asJsonObject.get("contactId").asInt)
                    "VILLACONTACT_UNLINK" -> {
                        val payload = baseMessage.payload.asJsonObject
                        val villaId = payload.get("villaId").asInt
                        val contactId = payload.get("contactId").asInt
                        database.villaContactDao().deleteByVillaIdAndContactId(villaId, contactId)
                    }
                    else -> Log.w("WebSocketProcessing", "Bilinmeyen mesaj tipi alındı: ${baseMessage.type}")
                }
            }

            if (isDataChanged) {
                // Debounce mantığı arayüzün sadece en sonda güncellenmesini sağlar.
                uiUpdateDebounceJob?.cancel()
                uiUpdateDebounceJob = applicationScope.launch {
                    delay(750) // Gecikmeyi biraz artırmak fırtına sonrası için daha güvenli olabilir.
                    Log.i("WebSocketDebounce", "Mesaj fırtınası dindi. Arayüzü güncellemek için broadcast gönderiliyor.")
                    sendBroadcast(Intent("com.serkantken.secuasist.DATA_UPDATED"))
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