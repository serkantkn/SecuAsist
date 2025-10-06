package com.serkantken.secuasist.services

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat.FLAG_GROUP_SUMMARY
import com.orhanobut.hawk.Hawk

class WhatsAppNotificationListener : NotificationListenerService() {

    private val TAG = "WhatsAppListener"
    private val WHATSAPP_PACKAGE_NAME = "com.whatsapp"

    // Hawk'ta durumu saklamak için kullanacağımız anahtar
    companion object {
        const val HAWK_KEY_HAS_UNREAD_WHATSAPP = "has_unread_whatsapp"
    }

    override fun onCreate() {
        super.onCreate()
        if (!Hawk.isBuilt()) {
            Hawk.init(this).build()
        }
        Log.d(TAG, "WhatsAppNotificationListener servisi oluşturuldu.")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Bildirim dinleyici bağlandı.")
        // Servis bağlandığında, mevcut aktif bildirimleri kontrol et
        checkForActiveWhatsAppNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Bildirim WhatsApp'tan mı geldi?
        if (sbn.packageName == WHATSAPP_PACKAGE_NAME) {
            // ÖNEMLİ: WhatsApp'ın grup mesajları veya "mesajlar kontrol ediliyor" gibi
            // bildirimlerini filtrelememiz gerekir. Gerçek mesaj bildirimleri genellikle
            // Notification.FLAG_GROUP_SUMMARY bayrağını içermez.
            if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) {
                Log.d(TAG, "Yeni WhatsApp mesaj bildirimi algılandı.")
                updateUnreadStatus(true)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        if (sbn.packageName == WHATSAPP_PACKAGE_NAME) {
            Log.d(TAG, "Bir WhatsApp bildirimi kaldırıldı.")
            // Bir bildirim kaldırıldığında, hala başka okunmamış WhatsApp bildirimi var mı diye tekrar kontrol et.
            checkForActiveWhatsAppNotifications()
        }
    }

    private fun checkForActiveWhatsAppNotifications() {
        try {
            // Aktif bildirimler listesini al
            val activeNotifications = activeNotifications
            if (activeNotifications == null) {
                updateUnreadStatus(false)
                return
            }

            // WhatsApp'tan gelen ve özet olmayan (gerçek mesaj) bir bildirim var mı diye kontrol et
            val hasUnread = activeNotifications.any {
                it.packageName == WHATSAPP_PACKAGE_NAME && (it.notification.flags and FLAG_GROUP_SUMMARY == 0)
            }

            Log.d(TAG, "Aktif WhatsApp bildirimi kontrolü. Sonuç: $hasUnread")
            updateUnreadStatus(hasUnread)

        } catch (e: Exception) {
            Log.e(TAG, "Aktif bildirimler kontrol edilirken hata oluştu", e)
            // Hata durumunda durumu false yapabiliriz.
            updateUnreadStatus(false)
        }
    }

    private fun updateUnreadStatus(hasUnread: Boolean) {
        // Durumu Hawk'a kaydet. Sadece durum değiştiyse işlem yap.
        val oldStatus = Hawk.get(HAWK_KEY_HAS_UNREAD_WHATSAPP, false)
        if (oldStatus != hasUnread) {
            Hawk.put(HAWK_KEY_HAS_UNREAD_WHATSAPP, hasUnread)
            Log.i(TAG, "WhatsApp okunmamış durumu değişti: $hasUnread. MainActivity'e haber verilecek.")

            val intent = Intent("com.serkantken.secuasist.WHATSAPP_NOTIFICATION_UPDATE")
            sendBroadcast(intent)
        }
    }
}