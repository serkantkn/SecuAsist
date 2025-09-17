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

class WebSocketClient(
    private var serverIp: String, // Changed to var
    private val serverPort: Int
) {

    private val TAG = "WebSocketClient"
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private var reconnectJob: Job? = null
    private val reconnectionAttemptDelayMs = 5000L

    private val _incomingMessages = Channel<String>(Channel.UNLIMITED)
    val incomingMessages: Flow<String> = _incomingMessages.receiveAsFlow()

    fun isConnected(): Boolean {
        return isConnected.get()
    }

    @Synchronized
    fun connect() {
        // If already connected with the current configuration, do nothing.
        // This check is simplified; a more robust check might compare target URLs.
        if (isConnected.get() && webSocket != null) {
            val currentWebSocketUrl = webSocket?.request()?.url?.toString()
            val targetUrl = "ws://$serverIp:$serverPort"
            if (currentWebSocketUrl == targetUrl) {
                Log.d(TAG, "WebSocket zaten aynı adrese bağlı: $targetUrl")
                return
            } else {
                Log.d(TAG, "WebSocket hedef URL değişti. Yeniden bağlanılıyor. Eski: $currentWebSocketUrl, Yeni: $targetUrl")
                // URL değiştiyse, önce mevcut bağlantıyı kapat (webSocket null değilse)
                webSocket?.close(1002, "Target URL changed") // 1002: Protocol error or endpoint going away
                webSocket = null
                isConnected.set(false)
            }
        }


        // Eğer zaten bir yeniden bağlanma denemesi varsa ve aktifse durdur
        if (reconnectJob?.isActive == true) {
            Log.d(TAG, "Mevcut yeniden bağlanma işi iptal ediliyor.")
            reconnectJob?.cancel()
        }
        reconnectJob = null // İşi null yap ki yenisi başlayabilsin

        Log.d(TAG, "WebSocket bağlantı denemesi: ws://$serverIp:$serverPort")

        if (client == null) {
            client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                // Ping interval can be useful for keeping connections alive or detecting dead ones
                // .pingInterval(30, TimeUnit.SECONDS)
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
                _incomingMessages.trySend("BINARY_MESSAGE_RECEIVED") // Placeholder for binary data
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket bağlantısı kapanıyor: Kod=$code, Sebep=$reason")
                isConnected.set(false)
                _incomingMessages.trySend("STATUS:DISCONNECTING")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket bağlantısı kapatıldı: Kod=$code, Sebep=$reason")
                isConnected.set(false)
                _incomingMessages.trySend("STATUS:DISCONNECTED")
                if (code != 1000) { // 1000 "Normal Closure" değilse yeniden bağlanmayı dene
                    startReconnectJob()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket bağlantı hatası: ${t.message}", t)
                isConnected.set(false)
                _incomingMessages.trySend("STATUS:ERROR: ${t.message}")
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
                startReconnectJob()
            }
            return sent
        } else {
            Log.e(TAG, "WebSocket bağlı değil veya null, mesaj gönderilemedi: $message. Yeniden bağlanmayı deniyor...")
            if (!isConnected.get()) { // Sadece bağlı değilse yeniden bağlanmayı başlat
                startReconnectJob()
            }
            return false
        }
    }

    @Synchronized
    fun disconnect(isPermanent: Boolean = false, reason: String = "Uygulama kapatılıyor") {
        Log.d(TAG, "WebSocket bağlantısı kesiliyor (permanent: $isPermanent)... Sebep: $reason")
        reconnectJob?.cancel()
        reconnectJob = null

        if (webSocket != null) {
            webSocket!!.close(1000, reason) // 1000 for Normal Closure
            webSocket = null
        }
        isConnected.set(false)

        if (isPermanent) {
            client?.dispatcher?.executorService?.shutdown()
            client = null
            if (!_incomingMessages.isClosedForSend) {
                _incomingMessages.close()
            }
            Log.d(TAG, "WebSocket client permanently destroyed.")
        } else {
            // client'ı null yapma ki tekrar kullanılabilsin, ya da connect içinde hep yeni client oluşturuluyorsa null yapılabilir.
            // Mevcut connect metodu client null ise yeni oluşturuyor.
            // IP değişimi senaryosu için client'ı ve executor'ı hayatta tutmak daha iyi olabilir.
            // Ancak, connect içinde yeni bir client build etmek daha temiz olabilir her seferinde.
            // Şimdilik, connect metodunun client'ı null ise oluşturmasını temel alalım.
            // Kalıcı olmayan disconnect'te client'ı null yapmak, connect'in yeni bir client oluşturmasını sağlar.
            client?.dispatcher?.executorService?.shutdown() // Executor'ı kapatmak iyi bir pratik
            client = null // Bir sonraki connect'in yeni bir client oluşturmasını sağlar.
        }
    }

    // Public method to update IP and reconnect
    @Synchronized
    fun updateIpAndReconnect(newIp: String) {
        val oldIp = this.serverIp
        Log.d(TAG, "Sunucu IP adresi güncelleniyor. Eski IP: $oldIp, Yeni IP: $newIp")

        if (oldIp == newIp && isConnected.get()) {
            Log.d(TAG, "Yeni IP adresi mevcut IP ile aynı ve zaten bağlı. İşlem yapılmadı.")
            return
        }

        // Cancel any ongoing reconnect job before changing IP and state
        reconnectJob?.cancel()
        reconnectJob = null

        // Disconnect from the old IP, but not permanently (don't close the channel)
        if (webSocket != null || isConnected.get()) {
            Log.d(TAG, "Mevcut bağlantı kapatılıyor (IP değişimi için)...")
            webSocket?.close(1001, "IP address changing") // 1001: Endpoint "going away"
            webSocket = null
            isConnected.set(false)
        }
        // Ensure client is null so connect() creates a new one, or reuses one if it was designed to.
        // Given the current connect() creates a new OkHttpClient if client is null,
        // and disconnect() already nulls it, this should be fine.
        // If client was not nulled in a "soft" disconnect, connect() would reuse it.
        // For simplicity and cleanliness, ensuring a fresh client for a new IP is often better.
        // The current disconnect() already handles nulling out the client.
        // So just calling disconnect(isPermanent = false) would prepare for a new client.

        this.serverIp = newIp
        Log.d(TAG, "Sunucu IP adresi $newIp olarak ayarlandı. Yeniden bağlanılıyor...")
        connect() // Yeni IP ile bağlan
    }


    private fun startReconnectJob() {
        if (!isConnected.get() && (reconnectJob == null || !reconnectJob!!.isActive)) {
            reconnectJob = CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Yeniden bağlanma işlemi başlatılıyor (hedef: ws://$serverIp:$serverPort)...")
                while (!isConnected.get() && isActive) { // isActive ile coroutine iptalini kontrol et
                    delay(reconnectionAttemptDelayMs)
                    if (!isActive) break // Coroutine iptal edilmişse döngüden çık
                    Log.d(TAG, "Yeniden bağlanma denemesi: ws://$serverIp:$serverPort")
                    connect() // Tekrar bağlanmayı dene
                    if (!isConnected.get() && isActive) {
                        _incomingMessages.trySend("STATUS:RECONNECTING...")
                    }
                }
                if (isActive) { // Sadece coroutine hala aktifse bu logu at
                    Log.d(TAG, "Yeniden bağlanma işi tamamlandı (Bağlandı: ${isConnected.get()}) veya iptal edildi.")
                } else {
                    Log.d(TAG, "Yeniden bağlanma işi iptal edildi.")
                }
            }
        } else if(isConnected.get()) {
            reconnectJob?.cancel() // Zaten bağlıysa, olası bir yeniden bağlanma işini iptal et
            reconnectJob = null
        }
    }

    // Call this method when the application is shutting down or WebSocket is no longer needed.
    fun destroy() {
        Log.d(TAG, "WebSocketClient destroy çağrıldı.")
        disconnect(isPermanent = true, reason = "Client destroyed")
    }
}