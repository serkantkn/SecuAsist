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

@Composable
fun CallScreen(
    onClose: () -> Unit,
    viewModel: CallViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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

    val infiniteTransition = rememberInfiniteTransition()
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Rotating Gradient Background
        Box(
            modifier = Modifier
                .requiredSize(2000.dp)
                .align(Alignment.Center)
                .graphicsLayer { rotationZ = rotationAngle }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE53935), // Red top
                            Color(0xFF8E24AA), // Pinkish Purple center
                            Color(0xFFFF9800)  // Orange bottom
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
            Spacer(modifier = Modifier.height(60.dp))
            
            // --- UNIFIED CALL INFO (Matches Floating Widget Design) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.villa != null) {
                    // VİLLA NO
                    Text(
                        text = "VİLLA: ${uiState.villa!!.villaNo}",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // İSİM / NUMARA
                Text(
                    text = displayName.uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (uiState.villa != null) {
                    // SOKAK
                    Text(
                        text = uiState.villa!!.villaStreet ?: "Bilinmeyen Sokak",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // Eyalet / Zamanlayıcı
                Text(
                    text = stateText,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (uiState.missedCargoCompanies.isNotEmpty()) {
                val companiesStr = uiState.missedCargoCompanies.joinToString(", ")
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "⚠ $companiesStr KARGO UYARISI",
                        color = Color.Red,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // --- Navigation / Bottom Details ---
            if (uiState.villa != null && !showKeypad) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(horizontal = 16.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "VARDIYA / YOL TARİFİ", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = uiState.villa!!.villaNavigationA.takeIf { !it.isNullOrBlank() } ?: "Yol tarifi belirtilmemiş.", 
                            style = MaterialTheme.typography.bodyLarge, 
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            } else if (uiState.isSearching) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.weight(1f))
            
            if (showKeypad && callState == Call.STATE_ACTIVE) {
                DtmfKeypad(modifier = Modifier.padding(bottom = 24.dp))
            }

            // Feature Buttons Row
            if (callState == Call.STATE_ACTIVE) {
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
                        isActive = showKeypad,
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
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) Color.White else Color.DarkGray.copy(alpha = 0.3f)
    val iconColor = if (isActive) Color.Black else Color.White

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(backgroundColor)
        ) {
            Icon(icon, contentDescription = text, tint = iconColor, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = text, color = Color.LightGray, fontSize = 12.sp)
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
