package com.yshalsager.mafza.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MafzaSpacingTokens(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp
)

@Immutable
data class MafzaRadiusTokens(
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val pill: Dp = 28.dp
)

@Immutable
data class MafzaElevationTokens(
    val flat: Dp = 0.dp,
    val raised: Dp = 1.dp,
    val overlay: Dp = 3.dp
)

@Immutable
data class MafzaTypeTokens(
    val screen_title: TextStyle,
    val section_title: TextStyle,
    val body: TextStyle,
    val body_compact: TextStyle,
    val label: TextStyle
)

internal val LocalMafzaSpacing = staticCompositionLocalOf { MafzaSpacingTokens() }
internal val LocalMafzaRadius = staticCompositionLocalOf { MafzaRadiusTokens() }
internal val LocalMafzaElevation = staticCompositionLocalOf { MafzaElevationTokens() }

object MafzaTokens {
    val spacing: MafzaSpacingTokens
        @Composable get() = LocalMafzaSpacing.current

    val radius: MafzaRadiusTokens
        @Composable get() = LocalMafzaRadius.current

    val elevation: MafzaElevationTokens
        @Composable get() = LocalMafzaElevation.current

    val type: MafzaTypeTokens
        @Composable get() = MafzaTypeTokens(
            screen_title = MaterialTheme.typography.headlineSmall,
            section_title = MaterialTheme.typography.titleMedium,
            body = MaterialTheme.typography.bodyMedium,
            body_compact = MaterialTheme.typography.bodySmall,
            label = MaterialTheme.typography.labelMedium
        )
}
