package com.serkantken.secuasist.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.* // Coroutine ile ilgili import'ları ekle

class WebSocketClient(private val serverIp: String, private val serverPort: Int) {

    private val TAG = "WebSocketClient"
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private var reconnectJob: Job? = null // Yeniden bağlanma Coroutine işi
    private val reconnectionAttemptDelayMs = 5000L // 5 saniye bekleme süresi

    private val _incomingMessages = Channel<String>(Channel.UNLIMITED)
    val incomingMessages: Flow<String> = _incomingMessages.receiveAsFlow()

    fun isConnected(): Boolean {
        return isConnected.get()
    }

    @Synchronized
    fun connect() {
        if (isConnected.get()) {
            Log.d(TAG, "WebSocket zaten bağlı.")
            return
        }

        // Eğer zaten bir yeniden bağlanma denemesi varsa durdur
        reconnectJob?.cancel()
        reconnectJob = null

        Log.d(TAG, "WebSocket bağlantı denemesi: ws://$serverIp:$serverPort")

        if (client == null) {
            client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        val request = Request.Builder()
            .url("ws://$serverIp:$serverPort")
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket bağlantısı açıldı: ${response.message}")
                isConnected.set(true)
                _incomingMessages.trySend("STATUS:CONNECTED")
                reconnectJob?.cancel() // Başarıyla bağlandı, yeniden bağlanma işini iptal et
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Gelen mesaj (text): $text")
                _incomingMessages.trySend(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Gelen mesaj (bytes): ${bytes.hex()}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket bağlantısı kapanıyor: Kod=$code, Sebep=$reason")
                // webSocket.close(1000, null) // Listener'da manuel kapatmaya gerek yok, OkHttp halleder
                isConnected.set(false)
                _incomingMessages.trySend("STATUS:DISCONNECTING")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket bağlantısı kapatıldı: Kod=$code, Sebep=$reason")
                isConnected.set(false)
                _incomingMessages.trySend("STATUS:DISCONNECTED")
                // Bağlantı kapandığında otomatik yeniden bağlanma denemesi başlat
                startReconnectJob()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket bağlantı hatası: ${t.message}", t)
                isConnected.set(false)
                _incomingMessages.trySend("STATUS:ERROR: ${t.message}")
                // Hata durumunda yeniden bağlanma denemesi başlat
                startReconnectJob()
            }
        })
    }

    fun sendMessage(message: String): Boolean {
        if (isConnected.get() && webSocket != null) {
            val sent = webSocket!!.send(message)
            if (sent) {
                Log.d(TAG, "Mesaj gönderildi: $message")
            } else {
                Log.e(TAG, "Mesaj gönderilemedi: $message. Bağlantı tekrar kuruluyor...")
                startReconnectJob() // Mesaj gönderilemezse yeniden bağlanmayı dene
            }
            return sent
        } else {
            Log.e(TAG, "WebSocket bağlı değil veya null, mesaj gönderilemedi: $message. Yeniden bağlanmayı deniyor...")
            startReconnectJob() // Bağlı değilse yeniden bağlanmayı dene
            return false
        }
    }

    fun disconnect() {
        Log.d(TAG, "WebSocket bağlantısı kesiliyor...")
        reconnectJob?.cancel() // Yeniden bağlanma işini iptal et
        if (webSocket != null) {
            webSocket!!.close(1000, "Uygulama kapatılıyor")
            webSocket = null
        }
        client?.dispatcher?.executorService?.shutdown()
        client = null
        isConnected.set(false)
        _incomingMessages.close() // Channel'ı kapat
    }

    // Yeniden bağlanma Coroutine'ini başlatır
    private fun startReconnectJob() {
        // Eğer zaten aktif bir yeniden bağlanma denemesi yoksa başlat
        if (reconnectJob == null || reconnectJob?.isCompleted == true || reconnectJob?.isCancelled == true) {
            reconnectJob = CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Yeniden bağlanma işlemi başlatılıyor...")
                while (!isConnected.get() && isActive) {
                    delay(reconnectionAttemptDelayMs)
                    Log.d(TAG, "Yeniden bağlanma denemesi...")
                    connect() // Tekrar bağlanmayı dene
                    if (!isConnected.get()) { // Eğer hala bağlı değilse bir süre daha bekle
                        _incomingMessages.trySend("STATUS:RECONNECTING...")
                    }
                }
                Log.d(TAG, "Yeniden bağlanma işi tamamlandı veya iptal edildi.")
            }
        }
    }
}