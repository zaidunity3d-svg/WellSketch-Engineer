package com.wellsketch.engineer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wellsketch.engineer.core.ui.theme.WellSketchTheme
import com.wellsketch.engineer.navigation.WellSketchNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WellSketchApplication).container
        setContent {
            WellSketchTheme {
                WellSketchNavHost(container)
            }
        }
    }
}
