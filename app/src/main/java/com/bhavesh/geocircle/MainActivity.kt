package com.bhavesh.geocircle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bhavesh.geocircle.ui.navigation.GeoCircleNavGraph
import com.bhavesh.geocircle.ui.theme.GeoCircleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeoCircleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    GeoCircleNavGraph()
                }
            }
        }
    }
}
