package com.example.a4kwa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.a4kwa.ui.StatusUploaderScreen
import com.example.a4kwa.ui.theme._4KWATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            _4KWATheme {
                StatusUploaderScreen()
            }
        }
    }
}
