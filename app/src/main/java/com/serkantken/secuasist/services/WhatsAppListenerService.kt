package com.serkantken.secuasist.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.serkantken.secuasist.data.WhatsAppRepository

class WhatsAppListenerService : NotificationListenerService() {
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        if (sbn.packageName == "com.whatsapp" || sbn.packageName == "com.whatsapp.w4b") {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return
            
            val sender = extras.getString(Notification.EXTRA_TITLE)
            val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            if (!sender.isNullOrBlank() && !message.isNullOrBlank()) {
                // Typical generic system messages like "WhatsApp Web aktif", don't show.
                if (message.contains("WhatsApp Web") || message.contains("mesaj okunmadı") || message.contains("messages from")) return
                
                Log.d("WhatsAppListener", "Received WA from $sender: $message")
                WhatsAppRepository.postMessage(sender, message, notification.contentIntent)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Leaving the message visible in the app even if dismissed from the system tray.
        // It must be manually swiped away within SecuAsist UI.
    }
}
