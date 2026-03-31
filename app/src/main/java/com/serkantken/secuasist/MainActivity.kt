package com.serkantken.secuasist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.serkantken.secuasist.ui.SecuAsistApp
import com.serkantken.secuasist.ui.SecuAsistApp
import com.serkantken.secuasist.ui.theme.SecuAsistTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.*

object NavigationEventBus {
    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeEvents = _homeEvents.asSharedFlow()

    fun triggerHomeEvent() {
        _homeEvents.tryEmit(Unit)
    }
}

class MainActivity : ComponentActivity() {
    private var lastInteractionTime = System.currentTimeMillis()
    private val inactivityTimeout = 2 * 60 * 1000L // 2 minutes
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == android.content.Intent.ACTION_MAIN && 
            intent.categories?.contains(android.content.Intent.CATEGORY_HOME) == true) {
            // Physical Home button was pressed while app is default launcher
            NavigationEventBus.triggerHomeEvent()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto-update check
        val updateManager = com.serkantken.secuasist.utils.UpdateManager(this)
        updateManager.checkForUpdates()

        startInactivityTimer()

        enableEdgeToEdge()
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val themePreferences = androidx.compose.runtime.remember { com.serkantken.secuasist.data.ThemePreferences(context) }
            val appTheme by themePreferences.theme.collectAsState(initial = com.serkantken.secuasist.data.AppTheme.SYSTEM)
            
            val darkTheme = when(appTheme) {
                com.serkantken.secuasist.data.AppTheme.LIGHT -> false
                com.serkantken.secuasist.data.AppTheme.DARK -> true
                com.serkantken.secuasist.data.AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            SecuAsistTheme(darkTheme = darkTheme) {
                // Surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecuAsistApp()
                }
            }
        }
    }

    private fun startInactivityTimer() {
        scope.launch {
            while (isActive) {
                delay(1000)
                if (System.currentTimeMillis() - lastInteractionTime > inactivityTimeout) {
                    // Reset to prevent repeated triggers until interaction
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
