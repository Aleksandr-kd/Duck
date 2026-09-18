package com.duck.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Из ТЗ §1.1 — M3 Tokens Light. */
object LightColors {
    val primary = Color(0xFF6B5B95)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFE6DDFF)
    val secondary = Color(0xFF5E8C61)
    val background = Color(0xFFFDF8F2)
    val surface = Color(0xFFFFFFFF)
    val onSurface = Color(0xFF1A1C1E)
    val surfaceVariant = Color(0xFFF4EFE6)
    val error = Color(0xFFBA1A1A)
    val outline = Color(0xFF79747E)
    val success = Color(0xFF2E7D32)
}

/** Из ТЗ §1.1 — M3 Tokens Dark. */
object DarkColors {
    val primary = Color(0xFFD0BCFF)
    val onPrimary = Color(0xFF381E72)
    val primaryContainer = Color(0xFF4F378B)
    val secondary = Color(0xFF7FD080)
    val background = Color(0xFF141218)
    val surface = Color(0xFF1E1A22)
    val onSurface = Color(0xFFE6E1E5)
    val surfaceVariant = Color(0xFF49454F)
    val error = Color(0xFFFFB4AB)
    val outline = Color(0xFF938F99)
    val success = Color(0xFF81C995)
}

val DuckBaseColor = Color(0xFFF8C840) // §3.1 default duckColor
val DuckShadeColor = Color(0xFFE8A828)

/** 7 радужных пресетов §2.4. */
val RainbowPresetColors = listOf(
    Color(0xFFE5323B),
    Color(0xFFF2781E),
    Color(0xFFF6C120),
    Color(0xFF5DBB45),
    Color(0xFF34B4C9),
    Color(0xFF3F6CBA),
    Color(0xFF8B57B8)
)