package com.serkantken.secuasist.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.services.FloatingWidgetService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName

        val prefs = context.getSharedPreferences("secuasist_prefs", Context.MODE_PRIVATE)
        val isFloatingWidgetEnabled = prefs.getBoolean("floating_widget_enabled", true)

        // If we are the default dialer, the InCallService will handle the UI.
        // We only show the floating widget if we are NOT the default dialer.
        // Or if the floating widget is explicitly disabled in settings.
        if (isDefaultDialer || !isFloatingWidgetEnabled) {
            Log.d("CallReceiver", "App is default dialer or floating widget deactivated, skipping fallback.")
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d("CallReceiver", "Call State: $state, Number: $incomingNumber")

        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            if (phoneNumber != null) {
                fetchAndShowWidget(context, phoneNumber)
            }
            return
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (incomingNumber != null) {
                    fetchAndShowWidget(context, incomingNumber, isOffhook = (state == TelephonyManager.EXTRA_STATE_OFFHOOK))
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // The FloatingWidgetService handles its own lifecycle with a 2s delay
                // to show the disconnected state. We don't stop it immediately here.
                val serviceIntent = Intent(context, FloatingWidgetService::class.java)
                serviceIntent.putExtra("ACTION_STOP_WITH_DELAY", true)
                context.startService(serviceIntent)
            }
        }
    }

    private fun fetchAndShowWidget(context: Context, phoneNumber: String, isOffhook: Boolean = false) {
        val db = AppDatabase.getDatabase(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            // Clean phone number for better matching
            val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
            
            // Exact matches first
            var contact = db.contactDao().getContactByPhoneNumber(phoneNumber) 
                ?: db.contactDao().getContactByPhoneNumber(cleanNumber)
                ?: db.contactDao().getContactByPhoneNumber("+$cleanNumber")
                ?: db.contactDao().getContactByPhoneNumber("0$cleanNumber")

            // Loose match if exact fails
            if (contact == null && cleanNumber.length >= 10) {
                val searchNumber = if (cleanNumber.startsWith("90")) cleanNumber.substring(2) 
                    else if (cleanNumber.startsWith("0")) cleanNumber.substring(1) 
                    else cleanNumber
                
                contact = db.contactDao().findContactByPhoneLike(searchNumber)
            }

            if (contact != null) {
                val villas = db.villaContactDao().getVillasForContact(contact.contactId)
                if (villas.isNotEmpty()) {
                    val villa = villas.first()
                    
                    if (isOffhook) {
                        db.cargoDao().clearMissedCargosForVillaToday(villa.villaId)
                    }
                    
                    val serviceIntent = Intent(context, FloatingWidgetService::class.java).apply {
                        putExtra("VILLA_STREET", villa.villaStreet ?: "Bilinmeyen Sokak")
                        
                        // Select correct direction based on preference
                        val prefs = context.getSharedPreferences("secuasist_prefs", Context.MODE_PRIVATE)
                        val preferredGate = prefs.getString("preferred_gate", "A") ?: "A"
                        val directions = if (preferredGate == "A") villa.villaNavigationA else villa.villaNavigationB
                        
                        putExtra("VILLA_DIRECTIONS", directions ?: "Yol tarifi belirtilmemiş.")
                        putExtra("VILLA_NO", villa.villaNo.toString())
                        putExtra("CONTACT_NAME", contact.contactName)
                        
                        // Get the companies of missed cargos
                        val missedCompanies = db.cargoDao().getMissedCargoCompanyNamesToday(villa.villaId)
                        putExtra("SHOW_CARGO_WARNING", missedCompanies.joinToString(", "))
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
