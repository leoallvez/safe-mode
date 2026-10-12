package com.poc.safemode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.poc.safemode.ui.theme.SafeModeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WifiInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeModeTheme {
                WifiInfoRoute(viewModel = viewModel)
            }
        }
    }
}
