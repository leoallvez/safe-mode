package com.poc.safemode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.poc.safemode.ui.SafeModeApp
import com.poc.safemode.ui.theme.SafeModeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeModeTheme {
                SafeModeApp()
            }
        }
    }
}
