package com.serkantken.secuasist.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.serkantken.secuasist.data.WhatsAppRepository

class WhatsAppNotificationViewModel : ViewModel() {
    val latestMessage = WhatsAppRepository.latestMessage

    fun dismissMessage() {
        WhatsAppRepository.clearMessage()
    }
}
