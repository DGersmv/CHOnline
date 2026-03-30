package com.example.chonline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.navigation.AppNavHost
import com.example.chonline.ui.theme.CHOnlineTheme
import com.example.chonline.ui.theme.CorpChatColors

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)
        enableEdgeToEdge()
        setContent {
            CHOnlineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CorpChatColors.bgDeep,
                ) {
                    AppNavHost(container = appContainer)
                }
            }
        }
    }
}
