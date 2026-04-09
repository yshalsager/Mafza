package com.yshalsager.mafza.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = MafzaBlue,
    error = MafzaRed
)

private val DarkColors = darkColorScheme(
    primary = MafzaBlue,
    error = MafzaRed
)

private val MafzaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val MafzaTypography = Typography().copy(
    headlineSmall = Typography().headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.15.sp
    ),
    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.Medium
    ),
    bodyMedium = Typography().bodyMedium.copy(
        lineHeight = 22.sp
    ),
    bodySmall = Typography().bodySmall.copy(
        lineHeight = 18.sp
    )
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

    CompositionLocalProvider(
        LocalMafzaSpacing provides MafzaSpacingTokens(),
        LocalMafzaRadius provides MafzaRadiusTokens(),
        LocalMafzaElevation provides MafzaElevationTokens()
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = MafzaTypography,
            shapes = MafzaShapes,
            content = content
        )
    }
}
