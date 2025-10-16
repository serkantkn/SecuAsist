package com.serkantken.secuasist.services

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.orhanobut.hawk.Hawk

class WhatsAppNotificationListener : NotificationListenerService() {

    private val TAG = "WhatsAppListener"
    private val WHATSAPP_PACKAGE_NAME = "com.whatsapp"

    companion object {
        const val HAWK_KEY_HAS_UNREAD_WHATSAPP = "has_unread_whatsapp"
        const val ACTION_NOTIFICATION_UPDATE = "com.serkantken.secuasist.WHATSAPP_NOTIFICATION_UPDATE"
    }

    override fun onCreate() {
        super.onCreate()
        if (!Hawk.isBuilt()) {
            Hawk.init(this).build()
        }
        Log.d(TAG, "onCreate: Servis oluşturuldu.")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Bu logu görüyorsan, kullanıcı izni vermiş ve servis sisteme bağlanmış demektir.
        Log.i(TAG, "onListenerConnected: Bildirim dinleyici sisteme başarıyla bağlandı.")
        checkForActiveWhatsAppNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Bu log, bir sorun olduğunu veya iznin geri alındığını gösterebilir.
        Log.w(TAG, "onListenerDisconnected: Bildirim dinleyici bağlantısı kesildi.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Sadece WhatsApp'tan gelen bildirimlerle ilgilen
        if (sbn.packageName == WHATSAPP_PACKAGE_NAME) {
            val notification = sbn.notification
            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

            // Gelen her WhatsApp bildiriminin detaylarını loglayalım
            Log.d(TAG, "onNotificationPosted: WhatsApp bildirimi geldi. " +
                    "ID: ${sbn.id}, " +
                    "Özet mi?: $isGroupSummary, " +
                    "Kategori: ${notification.category}, " +
                    "Başlık: ${notification.extras.getString(Notification.EXTRA_TITLE)}")

            // Eğer bu bir grup özeti değilse (yani tek bir sohbetten gelen mesaj gibi görünüyorsa),
            // durumu "okunmamış var" olarak güncelle.
            if (!isGroupSummary) {
                Log.i(TAG, "onNotificationPosted: Gerçek bir mesaj bildirimi tespit edildi. Durum güncelleniyor.")
                updateUnreadStatus(true)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        if (sbn.packageName == WHATSAPP_PACKAGE_NAME) {
            Log.d(TAG, "onNotificationRemoved: Bir WhatsApp bildirimi kaldırıldı. Aktif bildirimler tekrar kontrol ediliyor.")
            // Bir bildirim (okundu, kaydırıldı vb.) kaldırıldığında,
            // hala başka okunmamış WhatsApp bildirimi var mı diye tekrar kontrol et.
            checkForActiveWhatsAppNotifications()
        }
    }

    private fun checkForActiveWhatsAppNotifications() {
        try {
            val activeNotifications = activeNotifications ?: run {
                Log.d(TAG, "checkForActiveWhatsAppNotifications: Aktif bildirim listesi null. Durum 'false' olarak ayarlanıyor.")
                updateUnreadStatus(false)
                return
            }

            val hasUnread = activeNotifications.any {
                it.packageName == WHATSAPP_PACKAGE_NAME && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }

            Log.d(TAG, "checkForActiveWhatsAppNotifications: Aktif bildirim kontrolü tamamlandı. Sonuç: $hasUnread")
            updateUnreadStatus(hasUnread)

        } catch (e: Exception) {
            Log.e(TAG, "Aktif bildirimler kontrol edilirken hata oluştu", e)
            updateUnreadStatus(false)
        }
    }

    private fun updateUnreadStatus(hasUnread: Boolean) {
        val oldStatus = Hawk.get(HAWK_KEY_HAS_UNREAD_WHATSAPP, false)
        if (oldStatus != hasUnread) {
            Hawk.put(HAWK_KEY_HAS_UNREAD_WHATSAPP, hasUnread)
            // Bu logu görüyorsan, durum değişmiş ve MainActivity'e haber gönderiliyor demektir.
            Log.i(TAG, "updateUnreadStatus: WhatsApp okunmamış durumu değişti -> $hasUnread. Broadcast gönderiliyor.")
            sendBroadcast(Intent(ACTION_NOTIFICATION_UPDATE))
        } else {
            Log.d(TAG, "updateUnreadStatus: Durum değişmedi (hala $hasUnread), broadcast gönderilmiyor.")
        }
    }
}