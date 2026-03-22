package com.serkantken.secuasist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serkantken.secuasist.network.ConnectionState

@Composable
fun ScreenHeader(
    title: String,
    onNewClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    connectionState: ConnectionState? = null,
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Extra Actions Slot
            extraActions()
            
            // Connection Status (Optional)
            if (connectionState != null) {
                if (extraActions != {}) Spacer(modifier = Modifier.width(8.dp))
                val (iconColor, _) = when(connectionState) {
                    ConnectionState.CONNECTED -> Color.Green to "Bağlı"
                    ConnectionState.CONNECTING -> Color.Yellow to "Bağlanıyor"
                    ConnectionState.DISCONNECTED -> Color.Red to "Bağlı Değil"
                }
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            // Settings Button
            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // "Yeni" Button -> Plus Icon
            if (onNewClick != null) {
                IconButton(
                    onClick = onNewClick,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                     Icon(
                         imageVector = Icons.Default.Add,
                         contentDescription = "Yeni Ekle",
                         tint = MaterialTheme.colorScheme.onPrimaryContainer
                     )
                }
            }
        }
    }
}



@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.ArrowUpward, contentDescription = "Yukarı Çık")
        }
    }
}
