package com.serkantken.secuasist.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.serkantken.secuasist.models.AppInfo
import com.serkantken.secuasist.ui.viewmodels.AppsViewModel

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val appsList by viewModel.appsList.collectAsState()
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 80.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(appsList, key = { it.packageName }) { appInfo ->
            AppItem(appInfo = appInfo, context = context)
        }
    }
}

@Composable
fun AppItem(appInfo: AppInfo, context: Context) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable {
                // Launch the app
                val launchIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                } else {
                    android.widget.Toast.makeText(context, "Uygulama açılamadı", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(4.dp)
            .fillMaxWidth()
    ) {
        // Lazily fetch the icon in a background thread to prevent OOM
        var iconBitmap by androidx.compose.runtime.remember { mutableStateOf<ImageBitmap?>(null) }
        
        LaunchedEffect(appInfo.packageName) {
            withContext(Dispatchers.IO) {
                try {
                    val drawable = context.packageManager.getApplicationIcon(appInfo.packageName)
                    // Downscale the bitmap to 144x144 (approx 48dp on 3x density) to prevent OutOfMemoryError
                    iconBitmap = drawable.toBitmap(width = 144, height = 144).asImageBitmap()
                } catch (e: Exception) {
                    // Ignore, fallback will be shown
                }
            }
        }
        
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = appInfo.name,
                modifier = Modifier.size(56.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Transparent, shape = CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = appInfo.name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
