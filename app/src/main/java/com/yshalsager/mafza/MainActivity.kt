package com.yshalsager.mafza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yshalsager.mafza.ui.theme.MafzaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MafzaTheme {
                MafzaApp()
            }
        }
    }
}

@Composable
private fun MafzaApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { inner_padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner_padding),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Mafza", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
