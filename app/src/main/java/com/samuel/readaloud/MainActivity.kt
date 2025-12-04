package com.samuel.readaloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.samuel.readaloud.ui.MainScreen
import com.samuel.readaloud.ui.theme.ReadAloudTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Restore Python Initialization ---
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        enableEdgeToEdge()
        setContent {
            ReadAloudTheme {
                MainScreen()
            }
        }
    }
}