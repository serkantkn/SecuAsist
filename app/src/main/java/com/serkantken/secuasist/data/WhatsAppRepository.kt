package com.serkantken.secuasist.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WhatsAppMessage(
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val intent: android.app.PendingIntent? = null
)

object WhatsAppRepository {
    private val _latestMessage = MutableStateFlow<WhatsAppMessage?>(null)
    val latestMessage: StateFlow<WhatsAppMessage?> = _latestMessage.asStateFlow()

    fun postMessage(sender: String, message: String, intent: android.app.PendingIntent?) {
        _latestMessage.value = WhatsAppMessage(sender, message, System.currentTimeMillis(), intent)
    }

    fun clearMessage() {
        _latestMessage.value = null
    }
}
