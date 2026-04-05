package com.serkantken.secuasist.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.ui.viewmodels.WhatsAppNotificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppNotificationCard(viewModel: WhatsAppNotificationViewModel = viewModel()) {
    val context = LocalContext.current
    val message by viewModel.latestMessage.collectAsState()
    
    // Lifecycle check for Permission mapping
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
                hasPermission = enabledListeners.contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!hasPermission) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("WhatsApp Entegrasyonu Aktif Değil", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("Bildirim erişimi izni vermeniz gerekiyor.", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { 
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) {
                    Text("İzin Ver")
                }
            }
        }
        return
    }

    if (message != null) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = {
                if (it != SwipeToDismissBoxValue.Settled) {
                    viewModel.dismissMessage()
                    true
                } else false
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color = if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) Color(0xFFF44336) else Color.Transparent
                val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(color, MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sil", tint = Color.White)
                }
            },
            content = {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            try {
                                if (message!!.intent != null) {
                                    val options = android.app.ActivityOptions.makeBasic()
                                    try {
                                        val method = options.javaClass.getMethod("setPendingIntentBackgroundActivityLaunchAllowed", Boolean::class.javaPrimitiveType)
                                        method.invoke(options, true)
                                    } catch (ignored: Exception) {}
                                    try {
                                        val method2 = options.javaClass.getMethod("setPendingIntentBackgroundActivityStartMode", Int::class.javaPrimitiveType)
                                        method2.invoke(options, 1) // MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                    } catch (ignored: Exception) {}
                                    
                                    message!!.intent!!.send(context, 0, null, null, null, null, options.toBundle())
                                } else {
                                    val pm = context.packageManager
                                    val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
                                        ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        context.startActivity(launchIntent)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                try {
                                    val intent = Intent(Intent.ACTION_MAIN)
                                    intent.setPackage("com.whatsapp")
                                    intent.addCategory(Intent.CATEGORY_LAUNCHER)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    context.startActivity(intent)
                                } catch(ex: Exception) {
                                    ex.printStackTrace()
                                }
                            } finally {
                                viewModel.dismissMessage()
                            }
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)) // Light Whatsapp Green Background
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color(0xFF4CAF50), // WhatsApp Green Icon
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message!!.senderName, 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = message!!.messageText, 
                                style = LocalTextStyle.current.copy(fontSize = 15.sp),
                                color = Color(0xFF2E7D32),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        )
    }
}
