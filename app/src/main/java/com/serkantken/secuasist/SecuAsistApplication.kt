package com.serkantken.secuasist

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.util.Log // Log için
import com.serkantken.secuasist.network.WebSocketClient

class SecuAsistApplication : Application() {

    val webSocketClient: WebSocketClient by lazy {
        WebSocketClient("192.168.1.34", 8765) // Kendi sunucu IP'nizi ve portunuzu girin
    }

    // Uygulama yaşam döngüsü için CoroutineScope
    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("SecuAsistApplication", "Uygulama başlatıldı, WebSocket bağlanıyor...")
        webSocketClient.connect()

        // Application sınıfı içinde gelen mesajları dinlemeye gerek yok,
        // Toast'lar Activity'den gösterileceği için.
        // Ancak burada global bir dinleyiciye ihtiyacınız varsa kalabilir,
        // sadece Toast direkt gösterilemez.
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("SecuAsistApplication", "Uygulama sonlandırılıyor, WebSocket bağlantısı kesiliyor...")
        webSocketClient.disconnect()
    }
}