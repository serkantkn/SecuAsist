package com.serkantken.secuasist.services

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class WhatsAppNotificationListener : NotificationListenerService() {

    private val TAG = "WhatsAppListener"
    private val WHATSAPP_PACKAGE_NAME = "com.whatsapp"

    companion object {
        const val PREF_HAS_UNREAD = "has_unread_whatsapp"
        const val ACTION_NOTIFICATION_UPDATE = "com.serkantken.secuasist.WHATSAPP_NOTIFICATION_UPDATE"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "onListenerConnected: Bildirim dinleyici sisteme başarıyla bağlandı.")
        checkForActiveWhatsAppNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "onListenerDisconnected: Bildirim dinleyici bağlantısı kesildi.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        if (sbn.packageName == WHATSAPP_PACKAGE_NAME) {
            val notification = sbn.notification
            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

            if (!isGroupSummary) {
                updateUnreadStatus(true)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        if (sbn.packageName == WHATSAPP_PACKAGE_NAME) {
            checkForActiveWhatsAppNotifications()
        }
    }

    private fun checkForActiveWhatsAppNotifications() {
        try {
            val activeNotifications = activeNotifications ?: run {
                updateUnreadStatus(false)
                return
            }

            val hasUnread = activeNotifications.any {
                it.packageName == WHATSAPP_PACKAGE_NAME && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }

            updateUnreadStatus(hasUnread)

        } catch (e: Exception) {
            Log.e(TAG, "Aktif bildirimler kontrol edilirken hata oluştu", e)
            updateUnreadStatus(false)
        }
    }

    private fun updateUnreadStatus(hasUnread: Boolean) {
        val prefs = getSharedPreferences("secuasist_prefs", Context.MODE_PRIVATE)
        val oldStatus = prefs.getBoolean(PREF_HAS_UNREAD, false)
        
        if (oldStatus != hasUnread) {
            prefs.edit().putBoolean(PREF_HAS_UNREAD, hasUnread).apply()
            Log.i(TAG, "WhatsApp durumu değişti: $hasUnread")
            sendBroadcast(Intent(ACTION_NOTIFICATION_UPDATE))
        }
    }
}