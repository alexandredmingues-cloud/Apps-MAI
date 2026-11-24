package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MontyColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = IvoryWarm,
    primaryContainer = EmeraldMid,
    onPrimaryContainer = GoldChampagne,
    secondary = GoldAntique,
    onSecondary = ObsidianInk,
    secondaryContainer = ColorTokens.GoldSoftContainer,
    onSecondaryContainer = ObsidianInk,
    tertiary = EmeraldSurface,
    onTertiary = IvoryWarm,
    background = IvoryWarm,
    onBackground = ObsidianInk,
    surface = IvoryCard,
    onSurface = ObsidianInk,
    surfaceVariant = ColorTokens.WarmSurfaceAlt,
    onSurfaceVariant = SlateMuted,
    outline = BorderSubtle,
    error = ErrorRed,
    onError = IvoryCard,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorRed
)

private object ColorTokens {
    val GoldSoftContainer = androidx.compose.ui.graphics.Color(0xFFF3EBD8)
    val WarmSurfaceAlt = androidx.compose.ui.graphics.Color(0xFFEFEDE6)
}

val MontyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun MontyImmoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MontyColorScheme,
        typography = MontyTypography,
        shapes = MontyShapes,
        content = content
    )
}
