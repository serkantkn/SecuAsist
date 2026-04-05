package com.serkantken.secuasist.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.ui.viewmodels.CallLogEntry
import com.serkantken.secuasist.ui.viewmodels.CallLogViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CallLogTab(viewModel: CallLogViewModel = viewModel()) {
    val context = LocalContext.current
    val callLogs by viewModel.callLogs.collectAsState()
    
    var hasPermission by remember { 
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALL_LOG
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) 
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadCallLogs()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadCallLogs()
        } else {
            launcher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasPermission) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && hasPermission) {
                viewModel.loadCallLogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Çağrı kaydını görebilmek için izin gerekiyor.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.READ_CALL_LOG) }) {
                    Text("İzin Ver")
                }
            }
        }
    } else if (callLogs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Çağrı kaydı bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(callLogs, key = { it.id }) { entry ->
                CallLogItemRow(entry = entry, onCall = {
                    val hasCallPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CALL_PHONE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    try {
                        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        val callIntent = Intent(action).apply {
                            data = Uri.parse("tel:${entry.formattedNumber}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(callIntent)
                    } catch (e: Exception) { e.printStackTrace() }
                })
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
fun CallLogItemRow(entry: CallLogEntry, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        val (icon, color) = when (entry.type) {
            CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade to Color(0xFF4CAF50) // Green
            CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived to Color(0xFF2196F3) // Blue
            CallLog.Calls.MISSED_TYPE -> Icons.Default.CallMissed to Color.Red // Red
            CallLog.Calls.REJECTED_TYPE -> Icons.Default.CallMissed to Color(0xFFF44336) // Red
            else -> Icons.Default.Call to Color.Gray
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.contactName?.takeIf { it.isNotBlank() } ?: entry.formattedNumber,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (entry.type == CallLog.Calls.MISSED_TYPE || entry.type == CallLog.Calls.REJECTED_TYPE) Color.Red else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val displayDesc = buildString {
                if (!entry.contactName.isNullOrBlank()) {
                    append(entry.formattedNumber)
                    append(" • ")
                }
                append(formatCallTimestamp(entry.timestamp))
            }

            Text(
                text = displayDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Show duration only for placed/answered calls
        if (entry.durationSeconds > 0 && entry.type != CallLog.Calls.MISSED_TYPE && entry.type != CallLog.Calls.REJECTED_TYPE) {
            val mins = entry.durationSeconds / 60
            val secs = entry.durationSeconds % 60
            val durationText = buildString {
                if (mins > 0) append("${mins}d ")
                append("${secs}s")
            }
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatCallTimestamp(timestamp: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    
    val now = Calendar.getInstance()
    val isToday = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                  now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
                  
    now.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                      now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("HH:mm", Locale("tr"))
    return when {
        isToday -> "Bugün ${timeFormat.format(cal.time)}"
        isYesterday -> "Dün ${timeFormat.format(cal.time)}"
        else -> SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(cal.time)
    }
}
