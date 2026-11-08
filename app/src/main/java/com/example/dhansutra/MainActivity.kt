package com.example.dhansutra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.dhansutra.ui.navigation.AppNavHost
import com.example.dhansutra.ui.theme.DhanSutraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DhanSutraTheme {
                AppNavHost()
            }
        }
    }
}
