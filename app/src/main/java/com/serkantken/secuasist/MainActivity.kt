package com.serkantken.secuasist

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.serkantken.secuasist.databinding.ActivityMainBinding
import com.serkantken.secuasist.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webSocketClient: WebSocketClient

    // Activity yaşam döngüsü için bir CoroutineScope
    private var activityScopeJob: Job? = null
    private val activityScope: CoroutineScope get() = CoroutineScope(Dispatchers.Main + (activityScopeJob ?: Job()))


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Application sınıfı üzerinden WebSocketClient instance'ına erişim
        webSocketClient = (application as SecuAsistApplication).webSocketClient

        // Gönder butonuna tıklama dinleyicisi
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            if (message.isNotBlank()) {
                // Mesaj göndermeden önce bağlantının aktif olduğunu kontrol et
                if (webSocketClient.isConnected()) {
                    val success = webSocketClient.sendMessage(message)
                    if (success) {
                        showToast("Mesaj gönderildi: $message")
                        binding.messageInput.text.clear()
                    } else {
                        showToast("Mesaj gönderilemedi. WebSocket hatası.")
                    }
                } else {
                    showToast("WebSocket bağlı değil. Yeniden bağlanmayı deniyor...")
                    webSocketClient.connect() // Bağlı değilse yeniden bağlanmayı dene
                }
            } else {
                showToast("Boş mesaj gönderilemez.")
            }
        }

        // İlk başta bağlantı durumunu kontrol et
        //if (!webSocketClient.isConnected()) {
        //    showToast("Uygulama başlatıldı, WebSocket bağlanıyor...")
        //    webSocketClient.connect()
        //}
    }

    override fun onStart() {
        super.onStart()
        // Activity ön plana geldiğinde bağlantıyı kontrol et ve gerekirse yeniden bağla
        //if (!webSocketClient.isConnected()) {
        //    showToast("Uygulama ön plana geldi, WebSocket bağlanıyor...")
        //    webSocketClient.connect()
        //}

        // Gelen mesajları dinlemeye başla
        activityScopeJob = activityScope.launch {
            webSocketClient.incomingMessages.collect { message ->
                // "STATUS:" ile başlayan mesajlar bağlantı durumu bildirimidir
                if (message.startsWith("STATUS:")) {
                    when (message) {
                        "STATUS:CONNECTED" -> showToast("WebSocket Bağlandı.")
                        "STATUS:DISCONNECTED" -> showToast("WebSocket Bağlantısı Kesildi.")
                        "STATUS:DISCONNECTING" -> showToast("WebSocket Bağlantısı Kapanıyor...")
                        else -> showToast("WebSocket Durum: ${message.substringAfter("STATUS:")}")
                    }
                } else {
                    // Normal mesajları göster
                    binding.receivedMessagesTextView.append("Sunucudan: $message\n")
                    binding.receivedMessagesTextView.post {
                        val scrollAmount = binding.receivedMessagesTextView.layout.getLineTop(binding.receivedMessagesTextView.lineCount) - binding.receivedMessagesTextView.height
                        if (scrollAmount > 0) {
                            binding.receivedMessagesTextView.scrollTo(0, scrollAmount)
                        }
                    }
                    showToast("Gelen Mesaj: $message")
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Activity arka plana gittiğinde CoroutineScope'u iptal et
        // Bağlantıyı koparmıyoruz, çünkü Application sınıfı yönetiyor.
        activityScopeJob?.cancel()
        activityScopeJob = null // Job'u null'a set et
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}