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
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
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
import com.serkantken.secuasist.services.CallManager
import android.telecom.Call
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    
    // UI State
    private val isUIVisible = MutableStateFlow(false)
    private var localCallState = mutableIntStateOf(TelephonyManager.CALL_STATE_IDLE)
    private var localCallDuration = mutableLongStateOf(0L)
    private var localTimerJob: Job? = null
    private val scopeRunning = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (intent?.getBooleanExtra("ACTION_STOP_WITH_DELAY", false) == true) {
            updateLocalCallState(TelephonyManager.CALL_STATE_IDLE)
            return START_STICKY
        }

        val street = intent?.getStringExtra("VILLA_STREET") ?: "Bilinmeyen Sokak"
        val directions = intent?.getStringExtra("VILLA_DIRECTIONS") ?: "Yol tarifi yok"
        val villaNo = intent?.getStringExtra("VILLA_NO") ?: "?"
        val contactName = intent?.getStringExtra("CONTACT_NAME") ?: "Bilinmeyen Kişi"
        val showCargoWarning = intent?.getStringExtra("SHOW_CARGO_WARNING") ?: ""
        
        if (telephonyManager.callState == TelephonyManager.CALL_STATE_IDLE && !hasCallStarted) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (floatingView == null) {
            showFloatingWidget(street, directions, villaNo, contactName, showCargoWarning)
            isUIVisible.value = true
            listenToCallState()
        }
        
        return START_NOT_STICKY
    }

    private fun closeWithAnimation() {
        scopeRunning.launch {
            isUIVisible.value = false
            delay(500) // Wait for exit animation
            stopSelf()
        }
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
        val targetHeight = (screenHeight * 0.6).toInt()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 
            targetHeight,
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
            y = 0
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
                        onClose = { closeWithAnimation() },
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
        val isVisible by isUIVisible.collectAsState()
        val currentCallInfo by CallManager.currentCall.collectAsState()
        val globalDuration by CallManager.callDuration.collectAsState()
        
        val callState = remember(currentCallInfo, localCallState.value) {
            when {
                currentCallInfo != null -> currentCallInfo!!.state
                localCallState.value == TelephonyManager.CALL_STATE_RINGING -> Call.STATE_RINGING
                localCallState.value == TelephonyManager.CALL_STATE_OFFHOOK -> {
                    // Only treat as ACTIVE if we were already RINGING (incoming answer)
                    // or if the timer is already running. Otherwise it's DIALING.
                    if (hasCallStarted && localTimerJob?.isActive == true) Call.STATE_ACTIVE else Call.STATE_DIALING
                }
                hasCallStarted && localCallState.value == TelephonyManager.CALL_STATE_IDLE -> Call.STATE_DISCONNECTED
                else -> Call.STATE_NEW
            }
        }
        
        val displayDuration = if (globalDuration > 0) globalDuration else localCallDuration.value

        val backgroundColor by animateColorAsState(
            targetValue = when (callState) {
                Call.STATE_ACTIVE -> Color(0xFF065F46) // Emerald Green for ACTIVE
                Call.STATE_DISCONNECTED -> Color(0xFF991B1B) // Red for ENDED
                else -> Color(0xFF0C1222) // Default Dark Blue for Ringing/Dialing
            },
            animationSpec = tween(durationMillis = 600)
        )

        val accentBlue = Color(0xFF3B82F6)
        val accentEmerald = Color(0xFF10B981)
        val accentAmber = Color(0xFFF59E0B)
        val textMuted = Color(0xFF475569)
        val borderColor = Color(0xFF1E293B)

        val contentColor by animateColorAsState(
            targetValue = if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DISCONNECTED) Color.White else accentBlue,
            animationSpec = tween(durationMillis = 600)
        )

        val titleColor by animateColorAsState(
            targetValue = if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DISCONNECTED) Color.White else Color(0xFFF1F5F9),
            animationSpec = tween(durationMillis = 600)
        )

        val subtitleColor by animateColorAsState(
            targetValue = if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DISCONNECTED) Color.White.copy(alpha = 0.9f) else Color(0xFF94A3B8),
            animationSpec = tween(durationMillis = 600)
        )

        val labelColor by animateColorAsState(
            targetValue = if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DISCONNECTED) Color.White.copy(alpha = 0.7f) else Color(0xFF475569),
            animationSpec = tween(durationMillis = 600)
        )

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // --- Close Button ---
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White.copy(alpha = 0.5f))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // --- Drag Handle ---
                        Surface(
                            color = borderColor,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        ) {
                            Box(modifier = Modifier.width(40.dp).height(4.dp))
                        }

                        // --- Status Label ---
                        Surface(
                            color = if (callState == Call.STATE_ACTIVE) accentEmerald.copy(alpha = 0.2f) else accentBlue.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val stateIcon = when(callState) {
                                    Call.STATE_ACTIVE -> "⏱️"
                                    Call.STATE_DISCONNECTED -> "🔚"
                                    else -> "📞"
                                }
                                val stateText = when(callState) {
                                    Call.STATE_ACTIVE -> formatDuration(displayDuration)
                                    Call.STATE_RINGING -> "ÇALIYOR..."
                                    Call.STATE_DIALING -> "ARANIYOR..."
                                    Call.STATE_DISCONNECTED -> "KAPANDI"
                                    else -> "BAĞLANIYOR..."
                                }
                                
                                Text(stateIcon, fontSize = 12.sp)
                                Text(
                                    text = stateText,
                                    color = if (callState == Call.STATE_ACTIVE) Color.White else if (callState == Call.STATE_DISCONNECTED) Color.White else accentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(0.15f))

                        // --- Villa No ---
                        Text(
                            text = "Villa $villaNo",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = titleColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // --- İsim ---
                        Text(
                            text = contactName,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = subtitleColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Spacer(modifier = Modifier.weight(0.15f))

                        // --- Bilgi Kartı ---
                        Surface(
                            color = accentBlue.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                InfoRow(label = "Sokak", value = street, valueColor = contentColor, labelColor = labelColor)
                                HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                                InfoRow(label = "Navigasyon", value = directions.ifBlank { "Belirtilmedi" }, valueColor = contentColor, labelColor = labelColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // --- Kargo Uyarısı ---
                        if (showCargoWarning.isNotBlank()) {
                            Surface(
                                color = accentAmber.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📦 Bekleyen Kargo",
                                        color = textMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = showCargoWarning,
                                        color = accentAmber,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    @Composable
    private fun InfoRow(label: String, value: String, valueColor: Color, labelColor: Color) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = labelColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    private fun listenToCallState() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            updateLocalCallState(state)
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        updateLocalCallState(state)
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun updateLocalCallState(state: Int) {
        val prevState = localCallState.value
        localCallState.value = state
        
        if (state != TelephonyManager.CALL_STATE_IDLE) {
            hasCallStarted = true
        }
        
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            // Start timer ONLY if we were RINGING (incoming answer)
            // or if we have an active CallManager call
            if (prevState == TelephonyManager.CALL_STATE_RINGING || CallManager.currentCall.value?.state == Call.STATE_ACTIVE) {
                startLocalTimer()
            }
        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
            stopLocalTimer()
            scopeRunning.launch {
                delay(2000) // Keep visible for 2 seconds after hangup
                isUIVisible.value = false
                delay(500)
                stopSelf()
            }
        }
    }
    
    // For outgoing calls, we might need a way to detect answer if InCallService fails.
    // But for now, this strictly prevents answering-pretense during ringing.

    private fun startLocalTimer() {
        if (localTimerJob != null) return
        localCallDuration.value = 0L
        localTimerJob = scopeRunning.launch {
            while (isActive) {
                delay(1000)
                localCallDuration.value++
            }
        }
    }

    private fun stopLocalTimer() {
        localTimerJob?.cancel()
        localTimerJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocalTimer()
        store.clear()
        floatingView?.let {
            windowManager.removeView(it)
        }
    }
}
