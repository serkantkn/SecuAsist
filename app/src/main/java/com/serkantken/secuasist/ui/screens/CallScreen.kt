package com.serkantken.secuasist.ui.screens

import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColor
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import com.serkantken.secuasist.services.CallManager
import com.serkantken.secuasist.ui.viewmodels.CallViewModel
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween

@Composable
fun CallScreen(
    onClose: () -> Unit,
    viewModel: CallViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val call = uiState.call
    val callState = uiState.callState
    
    // Automatically close activity if call is null/ended
    LaunchedEffect(callState) {
        if (call == null || callState == Call.STATE_DISCONNECTED) {
            delay(1500) // Show for a moment before closing
            onClose()
        }
    }
    
    val isMuted by CallManager.isMuted.collectAsState()
    val isSpeaker by CallManager.isSpeaker.collectAsState()
    var showKeypad by remember { mutableStateOf(false) }
    
    val displayName = uiState.contact?.contactName ?: uiState.phoneNumber
    var durationSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            while(true) {
                delay(1000)
                durationSeconds++
            }
        }
    }

    val stateText = when (callState) {
        Call.STATE_RINGING -> "Aranıyor..."
        Call.STATE_DIALING -> "Çalıyor..."
        Call.STATE_ACTIVE -> formatDuration(durationSeconds)
        Call.STATE_DISCONNECTED -> "Çağrı Sonlandı"
        else -> "Bağlanıyor..."
    }

    val accentBlue = Color(0xFF93C5FD)
    val borderColor = Color(0xFF1E293B)

    val backgroundColor by animateColorAsState(
        targetValue = when (callState) {
            Call.STATE_ACTIVE -> Color(0xFF065F46) // Emerald 800 (Dark Green)
            Call.STATE_DISCONNECTED -> Color(0xFF991B1B) // Red 800 (Dark Red)
            else -> Color(0xFF0C1222) // Default Blue Slate
        },
        animationSpec = tween(durationMillis = 600)
    )

    // Dynamic Status & Navigation Bar Coloring
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as android.app.Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            
            // Ensure icons stay readable (white) since our bgs are dark
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    val contentColor by animateColorAsState(
        targetValue = if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DISCONNECTED) Color.White else accentBlue,
        animationSpec = tween(durationMillis = 600)
    )

    val titleColor by animateColorAsState(
        targetValue = if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DISCONNECTED) Color.White else Color(0xFFF1F5F9),
        animationSpec = tween(durationMillis = 600)
    )

    val labelColor by animateColorAsState(
        targetValue = if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DISCONNECTED) Color.White.copy(alpha = 0.7f) else Color(0xFF475569),
        animationSpec = tween(durationMillis = 600)
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Dark Mockup Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = 0.8f),
                            backgroundColor
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // --- "GELEN ÇAĞRI" Label ---
            Surface(
                color = Color.White.copy(alpha = 0.15f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📞", fontSize = 12.sp)
                    Text(
                        text = stateText.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // --- Villa No ---
            if (uiState.villa != null) {
                Text(
                    text = "Villa ${uiState.villa!!.villaNo}",
                    color = titleColor,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- İsim ---
            Text(
                text = displayName,
                color = titleColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(0.15f))
            
            // --- Bilgi Kartı ---
            if (uiState.villa != null && !showKeypad) {
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoRow(
                            label = "Sokak", 
                            value = uiState.villa!!.villaStreet ?: "Bilinmeyen Sokak", 
                            valueColor = contentColor,
                            labelColor = labelColor
                        )
                        HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                        
                        // Preferred Gate Logic for Navigation
                        val prefs = context.getSharedPreferences("secuasist_prefs", android.content.Context.MODE_PRIVATE)
                        val preferredGate = prefs.getString("preferred_gate", "A") ?: "A"
                        val currentNav = if (preferredGate == "A") uiState.villa!!.villaNavigationA else uiState.villa!!.villaNavigationB
                        
                        InfoRow(
                            label = "Navigasyon (${preferredGate})", 
                            value = currentNav.takeIf { !it.isNullOrBlank() } ?: "Belirtilmedi", 
                            valueColor = contentColor,
                            labelColor = labelColor
                        )
                    }
                }
            } else if (uiState.isSearching) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }

            // --- Kargo Uyarısı ---
            if (uiState.missedCargoCompanies.isNotEmpty()) {
                val companiesStr = uiState.missedCargoCompanies.joinToString(", ")
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📦 Bekleyen Kargo", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(companiesStr, color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }


            Spacer(modifier = Modifier.weight(1f))
            
            if (showKeypad && callState == Call.STATE_ACTIVE) {
                DtmfKeypad(modifier = Modifier.padding(bottom = 24.dp))
            }

            // Feature Buttons Row (Now enabled during ringing as well)
            if (callState == Call.STATE_ACTIVE || callState == Call.STATE_RINGING || callState == Call.STATE_DIALING) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FeatureButton(
                        icon = if (isSpeaker) Icons.Filled.VolumeUp else Icons.Outlined.VolumeUp,
                        text = "Hoparlör",
                        isActive = isSpeaker,
                        onClick = { CallManager.toggleSpeaker() }
                    )
                    FeatureButton(
                        icon = Icons.Outlined.Dialpad,
                        text = "Tuş Takımı",
                        isActive = showKeypad && callState == Call.STATE_ACTIVE, // Keypad only usable while active
                        enabled = callState == Call.STATE_ACTIVE,
                        onClick = { showKeypad = !showKeypad }
                    )
                    FeatureButton(
                        icon = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                        text = "Sessiz",
                        isActive = isMuted,
                        onClick = { CallManager.toggleMute() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            // Answer / Disconnect Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (callState == Call.STATE_RINGING) {
                    // Incoming Call: Answer Button
                    SwipeableActionButton(
                        icon = Icons.Default.Call,
                        containerColor = Color(0xFF4CAF50), // Green Theme
                        contentDescription = "Cevapla",
                        onSwipe = { CallManager.answerCall() }
                    )
                    
                    // Incoming Call: Reject Button
                    SwipeableActionButton(
                        icon = Icons.Default.CallEnd,
                        containerColor = Color(0xFFE53935), // Red Theme
                        contentDescription = "Reddet",
                        onSwipe = { CallManager.rejectCall() }
                    )
                } else if (callState != Call.STATE_DISCONNECTED) {
                    // Outgoing/Active Call: End Button (Tap is fine here, or we can make it swipe too, but tap is standard for ending)
                    FloatingActionButton(
                        onClick = { CallManager.disconnectCall() },
                        containerColor = Color(0xFFE53935), // Red Theme
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isActive: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        !enabled -> Color.DarkGray.copy(alpha = 0.1f)
        isActive -> Color.White
        else -> Color.DarkGray.copy(alpha = 0.3f)
    }
    val iconColor = when {
        !enabled -> Color.Gray
        isActive -> Color.Black
        else -> Color.White
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(backgroundColor)
        ) {
            Icon(icon, contentDescription = text, tint = iconColor, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = text, color = if (enabled) Color.LightGray else Color.Gray, fontSize = 12.sp)
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun SwipeableActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentDescription: String,
    onSwipe: () -> Unit
) {
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val maxDrag = 220f // pixels to trigger swipe
    
    val infinitePulse = rememberInfiniteTransition()
    val pulseScale by infinitePulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val pulseAlpha by infinitePulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(120.dp)
    ) {
        // Pulsing background ring
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .background(containerColor, CircleShape)
        )
        
        // Draggable Button
        Box(
            modifier = Modifier
                .size(72.dp)
                .offset { androidx.compose.ui.unit.IntOffset(offset.value.x.toInt(), offset.value.y.toInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val dragDistance = sqrt(offset.value.x * offset.value.x + offset.value.y * offset.value.y)
                                if (dragDistance >= maxDrag * 0.6f) {
                                    onSwipe()
                                }
                                offset.animateTo(Offset.Zero)
                            }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(Offset.Zero) }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newOffset = offset.value + dragAmount
                            val dragDistance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                            if (dragDistance <= maxDrag) {
                                offset.snapTo(newOffset)
                            } else {
                                val ratio = maxDrag / dragDistance
                                offset.snapTo(Offset(newOffset.x * ratio, newOffset.y * ratio))
                            }
                        }
                    }
                }
        ) {
            FloatingActionButton(
                onClick = { /* Disabled tap for strict swipe requirement */ },
                containerColor = containerColor,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape
            ) {
                Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
fun DtmfKeypad(modifier: Modifier = Modifier) {
    val keys = listOf(
        "1" to "", "2" to "ABC", "3" to "DEF",
        "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to ""
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in keys.chunked(3)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((number, letters) in row) {
                    DtmfKey(
                        number = number, 
                        letters = letters, 
                        modifier = Modifier.size(60.dp),
                        onPressed = { CallManager.playDtmfTone(number.first()) },
                        onReleased = { CallManager.stopDtmfTone() }
                    )
                }
            }
        }
    }
}

@Composable
fun DtmfKey(
    number: String,
    letters: String,
    modifier: Modifier = Modifier,
    onPressed: () -> Unit,
    onReleased: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.DarkGray.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onPressed() },
                    onDragEnd = { onReleased() },
                    onDragCancel = { onReleased() },
                    onDrag = { _, _ -> /* Drag ignored */ }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = { /* Handled by pointer input to support press+hold tones */ },
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = number, fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Normal)
                if (letters.isNotEmpty()) {
                    Text(text = letters, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String, 
    value: String, 
    valueColor: Color,
    labelColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label, 
            color = labelColor, 
            fontSize = 15.sp, 
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value, 
            color = valueColor, 
            fontSize = 15.sp, 
            fontWeight = FontWeight.Bold
        )
    }
}
