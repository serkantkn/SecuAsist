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
    private val port: Int,
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
