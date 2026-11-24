package com.nesperoudelmonico.projectvideo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nesperoudelmonico.projectvideo.diagnostics.DiagnosticsScreen
import com.nesperoudelmonico.projectvideo.ui.theme.ProjectVideoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProjectVideoTheme {
                DiagnosticsScreen()
            }
        }
    }
}
