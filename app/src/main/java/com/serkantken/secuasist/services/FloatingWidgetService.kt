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
import androidx.compose.foundation.shape.CircleShape
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
        val targetHeight = (screenHeight * 0.6).toInt() // Ekranın %60'ı (3/5)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 
            targetHeight, // 60% Height
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = 0 // En tepeye daya
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
                        onDrag = { _, dy ->
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
                .fillMaxWidth()
                .fillMaxHeight() // Fill 60% Screen Height
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center // İçeriği dikeyde ORTALA
            ) {
                // Header (Close Button at Top)
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onClose, 
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(40.dp)
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(0.2f))
                
                // --- VİLLA NO ---
                Text(
                    text = "VİLLA: $villaNo", 
                    style = MaterialTheme.typography.displaySmall, 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- İSİM ---
                Text(
                    text = contactName.uppercase(), 
                    style = MaterialTheme.typography.displaySmall, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- SOKAK ---
                Text(
                    text = street, 
                    style = MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showCargoWarning.isNotBlank()) {
                    Surface(
                        color = Color.Red.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Text(
                            text = "⚠ $showCargoWarning KARGO UYARISI",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Red,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(0.3f))
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                
                // Yol Tarifi
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "YOL TARİFİ / VARDIYA", style = MaterialTheme.typography.labelMedium, color = Color.Gray, letterSpacing = 3.sp)
                    Text(
                        text = directions, 
                        style = MaterialTheme.typography.headlineSmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
                
                Spacer(modifier = Modifier.weight(0.1f))
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
