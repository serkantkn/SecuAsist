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
import com.serkantken.secuasist.ui.theme.SecuAsistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
