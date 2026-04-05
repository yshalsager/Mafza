package com.yshalsager.mafza.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = MafzaBlue,
    error = MafzaRed
)

private val DarkColors = darkColorScheme(
    primary = MafzaBlue,
    error = MafzaRed
)

@Composable
fun MafzaTheme(
    dark_theme: Boolean = isSystemInDarkTheme(),
    dynamic_color: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamic_color && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark_theme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark_theme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
