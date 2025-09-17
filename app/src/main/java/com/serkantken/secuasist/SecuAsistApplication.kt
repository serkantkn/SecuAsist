package com.serkantken.secuasist

import android.app.Application
import android.util.Log
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SecuAsistApplication : Application() {

    val webSocketClient: WebSocketClient by lazy {
        val savedIp = Hawk.get<String?>("server_ip", null)
        val defaultIp = "192.168.1.34" // Hawk'ta IP yoksa veya geçersizse kullanılacak varsayılan IP
        val serverIpToUse = if (savedIp.isNullOrEmpty() || !android.net.InetAddresses.isNumericAddress(savedIp)) {
            Log.w("SecuAsistApplication", "Hawk'ta geçerli IP bulunamadı veya formatı yanlış, varsayılan IP kullanılıyor: $defaultIp")
            defaultIp
        } else {
            Log.i("SecuAsistApplication", "Hawk'tan okunan IP kullanılıyor: $savedIp")
            savedIp
        }
        WebSocketClient(serverIpToUse, 8765)
    }

    // Uygulama yaşam döngüsü için CoroutineScope
    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Hawk.init(this).build()
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