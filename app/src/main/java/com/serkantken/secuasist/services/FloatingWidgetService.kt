package com.serkantken.secuasist.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.serkantken.secuasist.ui.theme.SecuAsistTheme
import kotlin.math.roundToInt

class FloatingWidgetService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    
    // Telephony
    private lateinit var telephonyManager: TelephonyManager
    private var hasCallStarted = false

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val street = intent?.getStringExtra("VILLA_STREET") ?: "Bilinmeyen Sokak"
        val directions = intent?.getStringExtra("VILLA_DIRECTIONS") ?: "Yol tarifi yok"
        val villaNo = intent?.getStringExtra("VILLA_NO") ?: "?"
        val contactName = intent?.getStringExtra("CONTACT_NAME") ?: "Bilinmeyen Kişi"
        val showCargoWarning = intent?.getStringExtra("SHOW_CARGO_WARNING") ?: ""
        
        if (telephonyManager.callState == TelephonyManager.CALL_STATE_IDLE) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (floatingView == null) {
            showFloatingWidget(street, directions, villaNo, contactName, showCargoWarning)
            listenToCallState()
        }
        
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "floating_widget_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Arama Yüzen Pencere",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("SecuAsist Çağrı Asistanı")
            .setContentText("Villa bilgileri ekranda gösteriliyor.")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
            
        if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWidget(street: String, directions: String, villaNo: String, contactName: String, showCargoWarning: String) {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = screenHeight / 6 // Üst yarının ortasına denk gelecek şekilde
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWidgetService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            setViewTreeViewModelStoreOwner(this@FloatingWidgetService)
            
            setContent {
                SecuAsistTheme {
                    FloatingWidgetUI(
                        street = street,
                        directions = directions,
                        villaNo = villaNo,
                        contactName = contactName,
                        showCargoWarning = showCargoWarning,
                        onClose = { stopSelf() },
                        onDrag = { dx, dy ->
                            layoutParams.x += dx.roundToInt()
                            layoutParams.y += dy.roundToInt()
                            windowManager.updateViewLayout(this, layoutParams)
                        }
                    )
                }
            }
        }

        windowManager.addView(floatingView, layoutParams)
    }

    @Composable
    private fun FloatingWidgetUI(
        street: String, 
        directions: String, 
        villaNo: String, 
        contactName: String,
        showCargoWarning: String,
        onClose: () -> Unit, 
        onDrag: (Float, Float) -> Unit
    ) {
        Card(
            modifier = Modifier
                .width(320.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Villa No: $villaNo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = street, 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showCargoWarning.isNotBlank()) {
                    Text(
                        text = "$showCargoWarning Kargo için arandı, ulaşılamadı",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                
                Text(text = "Yol Tarifi", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(
                    text = directions, 
                    style = MaterialTheme.typography.bodyLarge, 
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    private fun listenToCallState() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            if (state != TelephonyManager.CALL_STATE_IDLE) {
                                hasCallStarted = true
                            }
                            if (state == TelephonyManager.CALL_STATE_IDLE && hasCallStarted) {
                                stopSelf()
                            }
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        if (state != TelephonyManager.CALL_STATE_IDLE) {
                            hasCallStarted = true
                        }
                        if (state == TelephonyManager.CALL_STATE_IDLE && hasCallStarted) {
                            stopSelf()
                        }
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            // If we don't have READ_PHONE_STATE permission, we simply cannot auto-close the widget.
            // The user must close it manually using the close button.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        store.clear()
        floatingView?.let {
            windowManager.removeView(it)
        }
    }
}
