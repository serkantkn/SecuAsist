package com.serkantken.secuasist

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.text.get

class SecuAsistApplication : Application() {

    // WebSocket istemcisini lazy initialization ile kur.
    // IP adresini Hawk'tan okur, bulamazsa varsayılan bir IP kullanır.
    val webSocketClient: WebSocketClient by lazy {
        val serverIpToUse = Hawk.get("server_ip", "192.168.1.21")
        WebSocketClient(serverIpToUse, 8765)
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

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun processIncomingWebSocketMessage(jsonMessage: String) {
        try {
            val json = JSONObject(jsonMessage)
            val type = json.getString("type")
            database.withTransaction {
                when (type) {
                    "FULL_SYNC_DATA" -> {
                        val payload = json.getJSONObject("payload")
                        handleFullSync(payload)
                    }
                    "VILLA_UPSERT" -> {
                        val payload = json.getJSONObject("payload")
                        val villa = gson.fromJson(payload.toString(), Villa::class.java)
                        GlobalScope.launch(Dispatchers.IO) {
                            database.villaDao().insert(villa)
                        }
                    }
                    "CONTACT_UPSERT" -> {
                        val payload = json.getJSONObject("payload")
                        val contact = gson.fromJson(payload.toString(), Contact::class.java)
                        GlobalScope.launch(Dispatchers.IO) {
                            database.contactDao().insert(contact)
                        }
                    }
                    // İlişki ve diğer tablolarda REPLACE kullanmak genellikle daha basittir
                    "VILLACONTACT_LINK" -> {
                        val payload = json.getJSONObject("payload")
                        val vc = gson.fromJson(payload.toString(), VillaContact::class.java)
                        GlobalScope.launch(Dispatchers.IO) {
                            database.villaContactDao().insert(vc)
                        }
                    }
                    "CARGOCOMPANY_UPSERT" -> {
                        val payload = json.getJSONObject("payload")
                        val cc = gson.fromJson(payload.toString(), CargoCompany::class.java)
                        GlobalScope.launch(Dispatchers.IO) {
                            database.cargoCompanyDao().insert(cc)
                        }
                    }
                    "CARGO_UPSERT" -> {
                        val payload = json.getJSONObject("payload")
                        val cg = gson.fromJson(payload.toString(), Cargo::class.java)
                        GlobalScope.launch(Dispatchers.IO) {
                            database.cargoDao().insert(cg)
                        }
                    }
                    "VILLA_DELETE" -> {
                        val id = json.getJSONObject("payload").getInt("villaId")
                        GlobalScope.launch(Dispatchers.IO) {
                            database.villaDao().deleteById(id)
                        }
                    }
                    "CONTACT_DELETE" -> {
                        val id = json.getJSONObject("payload").getInt("contactId")
                        GlobalScope.launch(Dispatchers.IO) {
                            database.contactDao().deleteById(id)
                        }
                    }
                    "VILLACONTACT_UNLINK" -> {
                        val payload = json.getJSONObject("payload")
                        val villaId = payload.getInt("villaId")
                        val contactId = payload.getInt("contactId")
                        GlobalScope.launch(Dispatchers.IO) {
                            database.villaContactDao().deleteByVillaIdAndContactId(villaId, contactId)
                        }
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

    @OptIn(DelicateCoroutinesApi::class)
    private fun handleFullSync(payload: JSONObject) {
        GlobalScope.launch(Dispatchers.IO) {
            val villas = gson.fromJson(payload.getJSONArray("villas").toString(), Array<Villa>::class.java).toList()
            val contacts = gson.fromJson(payload.getJSONArray("contacts").toString(), Array<Contact>::class.java).toList()
            val links = gson.fromJson(payload.getJSONArray("villa_contacts").toString(), Array<VillaContact>::class.java).toList()
            val companies = gson.fromJson(payload.getJSONArray("cargo_companies").toString(), Array<CargoCompany>::class.java).toList()
            val cargos = gson.fromJson(payload.getJSONArray("cargos").toString(), Array<Cargo>::class.java).toList()


            database.runInTransaction {
                launch {
                    database.villaDao().deleteAll()
                    database.contactDao().deleteAll()
                    database.villaContactDao().deleteAll()
                    database.cargoCompanyDao().deleteAll()
                    database.cargoDao().deleteAll()

                    database.villaDao().insertAll(villas)
                    database.contactDao().insertAll(contacts)
                    database.villaContactDao().insertAll(links)
                    database.cargoCompanyDao().insertAll(companies)
                    database.cargoDao().insertAll(cargos)
                }
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