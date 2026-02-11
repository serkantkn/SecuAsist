package com.serkantken.secuasist.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

class WebSocketClient(
    private val serverIp: String,
    // Emulator: 10.0.2.2, Physical: Actual IP
    private val port: Int = 8765,
    private val reconnectDelay: Long = 30000L
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectJob: Job? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 🔹 Mesaj akışı
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val incomingMessages = _incomingMessages.asSharedFlow()

    // 🔹 Bağlantı durumu akışı
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    fun connect() {
        if (isConnected) return
        coroutineScope.launch {
            try {
                _connectionState.emit(ConnectionState.CONNECTING)
                Log.d("WebSocketClient", "🟡 Bağlanıyor...")

                val request = Request.Builder()
                    .url("ws://$serverIp:$port")
                    .build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        isConnected = true
                        Log.i("WebSocketClient", "✅ Bağlantı kuruldu → $serverIp:$port")

                        // Send AUTH
                        // Ideally use unique Android ID or GUID. For now, random or specific.
                        val deviceId = java.util.UUID.randomUUID().toString()
                        sendMessage("AUTH $deviceId")

                        coroutineScope.launch { _connectionState.emit(ConnectionState.CONNECTED) }
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        coroutineScope.launch {
                            _incomingMessages.emit(text)
                        }
                    }

                    override fun onMessage(ws: WebSocket, bytes: ByteString) {
                        coroutineScope.launch {
                            _incomingMessages.emit(bytes.utf8())
                        }
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        isConnected = false
                        Log.w("WebSocketClient", "🔌 Bağlantı kapandı: $reason")

                        coroutineScope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }

                        scheduleReconnect()
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        isConnected = false
                        Log.e("WebSocketClient", "❌ WebSocket hatası: ${t.message}")

                        coroutineScope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }

                        scheduleReconnect()
                    }
                })
            } catch (e: Exception) {
                coroutineScope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = coroutineScope.launch {
            delay(reconnectDelay)
            Log.i("WebSocketClient", "🔁 Yeniden bağlanma deneniyor...")
            connect()
        }
    }

    fun reconnectWithNewIp(newIp: String, newPort: Int = port) {
        disconnect()
        coroutineScope.launch {
            delay(1000)
            Log.i("WebSocketClient", "🌐 Yeni IP ile yeniden bağlanılıyor: $newIp:$newPort")
            val request = Request.Builder()
                .url("ws://$newIp:$newPort")
                .build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    isConnected = true
                    Log.i("WebSocketClient", "✅ Yeni IP ile bağlantı kuruldu → $newIp:$newPort")
                    coroutineScope.launch {
                        _incomingMessages.emit("STATUS:CONNECTED:NEW_IP")
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    isConnected = false
                    Log.e("WebSocketClient", "❌ Yeni IP bağlantı hatası: ${t.message}")
                    coroutineScope.launch {
                        _incomingMessages.emit("STATUS:ERROR:NEW_IP")
                    }
                }
            })
        }
    }


    fun sendData(type: String, payload: Any) {
        if (!isConnected) return
        try {
            val jsonMap = mapOf("type" to type, "payload" to payload)
            // Need Gson here or manual JSON creation.
            // Since we don't have global Gson ref here easily, let's use a simple approach or pass it in.
            // For now, assume payload is a JSON string or we construct it.
            // actually, let's use the Gson instance from Application if possible, or just add Gson dependency here.
            // To keep it simple without DI:
            val json = com.google.gson.Gson().toJson(jsonMap)
            sendMessage(json)
        } catch (e: Exception) {
            Log.e("WebSocketClient", "JSON Send Error: ${e.message}")
        }
    }

    fun sendMessage(message: String) {
        if (isConnected) {
            webSocket?.send(message)
            Log.d("WebSocketClient", "📤 Gönderildi: $message")
        } else {
            Log.w("WebSocketClient", "⚠️ Gönderim başarısız. WebSocket bağlı değil.")
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Manuel kapatma")
        webSocket = null
        isConnected = false
        Log.i("WebSocketClient", "🔕 Bağlantı manuel olarak kapatıldı.")

        coroutineScope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }
    }
}
