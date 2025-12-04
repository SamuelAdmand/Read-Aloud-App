package com.samuel.readaloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.samuel.readaloud.ui.theme.ReadAloudTheme
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- Start of Python Test Code ---
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("tts_engine")
                // calling the sync wrapper we wrote in python
                val voicesJson = module.callAttr("get_voices_json").toString()

                Log.d("CHAQUOPY_TEST", "Success! Voices fetched: ${voicesJson.take(100)}...")
            } catch (e: Exception) {
                Log.e("CHAQUOPY_TEST", "Python Error", e)
            }
        }
        // --- End of Python Test Code ---
        enableEdgeToEdge()
        setContent {
            ReadAloudTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ReadAloudTheme {
        Greeting("Android")
    }
}