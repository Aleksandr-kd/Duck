package com.duck.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun DuckTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (isDark) darkColorScheme(
        primary = DarkColors.primary,
        onPrimary = DarkColors.onPrimary,
        primaryContainer = DarkColors.primaryContainer,
        secondary = DarkColors.secondary,
        background = DarkColors.background,
        surface = DarkColors.surface,
        onSurface = DarkColors.onSurface,
        surfaceVariant = DarkColors.surfaceVariant,
        error = DarkColors.error,
        outline = DarkColors.outline
    ) else lightColorScheme(
        primary = LightColors.primary,
        onPrimary = LightColors.onPrimary,
        primaryContainer = LightColors.primaryContainer,
        secondary = LightColors.secondary,
        background = LightColors.background,
        surface = LightColors.surface,
        onSurface = LightColors.onSurface,
        surfaceVariant = LightColors.surfaceVariant,
        error = LightColors.error,
        outline = LightColors.outline
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}