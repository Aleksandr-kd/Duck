package com.duck.app.domain.model

import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Палитра цвета утки: 7 радужных пресетов (ТЗ §2.4) + произвольный hex.
 * Якорь перекраски — жёлтая доминанта тела `#FED950` (ТЗ §8 / §2.4, решение 18.09.2026).
 */
object DuckColors {

    /** Якорь (жёлтая доминанта тела утки), от которого считается hueDelta. */
    const val TINTING_HUE_ROTATION = "HUE_ROTATION"
    const val ANCHOR_HEX = "#FED950"
    const val DEFAULT_HEX = "#F8C840"

    const val HEX_PATTERN = "^#([0-9A-Fa-f]{6})$"
    const val MIN_LIGHTNESS = 0.3f

    data class Preset(val id: String, val hex: String, val nameResKey: String)

    val PRESETS: List<Preset> = listOf(
        Preset("rainbow_red", "#E5323B", "color_name_red"),
        Preset("rainbow_orange", "#F2781E", "color_name_orange"),
        Preset("rainbow_yellow", "#F6C120", "color_name_yellow"),
        Preset("rainbow_green", "#5DBB45", "color_name_green"),
        Preset("rainbow_cyan", "#34B4C9", "color_name_cyan"),
        Preset("rainbow_blue", "#3F6CBA", "color_name_blue"),
        Preset("rainbow_violet", "#8B57B8", "color_name_violet")
    )

    fun isPreset(hex: String): Boolean = PRESETS.any { it.hex.equals(hex, ignoreCase = true) }

    /** Валидация `#RRGGBB` (E_COLOR_002). */
    fun isValidHex(hex: String): Boolean = Regex(HEX_PATTERN).matches(hex.trim())

    fun normalizeHex(hex: String): String = hex.trim().uppercase(Locale.ROOT)

    /** Hex -> RGB компоненты [0..1f]. */
    fun hexToRgb(hex: String): Triple<Float, Float, Float> {
        val h = normalizeHex(hex).removePrefix("#")
        val r = h.substring(0, 2).toInt(16)
        val g = h.substring(2, 4).toInt(16)
        val b = h.substring(4, 6).toInt(16)
        return Triple(r / 255f, g / 255f, b / 255f)
    }

    /**
     * Hue (градусы 0..360) в цветовом пространстве HSV для hex.
     */
    fun hueOf(hex: String): Int {
        val (r, g, b) = hexToRgb(hex)
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta == 0f) return 0
        val hueDeg = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return ((hueDeg + 360f) % 360f).toInt()
    }

    /** Проверка Lightness/Value ограничения (E-L2): слишком тёмный цвет неприменим. */
    fun isTooDark(hex: String): Boolean {
        val (r, g, b) = hexToRgb(hex)
        val value = maxOf(r, g, b)
        return (value * 100f).toInt() < (MIN_LIGHTNESS * 100f).toInt()
    }

    /** RGB [0..1] -> HSL, hue 0..360, s/l 0..1. */
    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val d = max - min
        val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /** Hex -> HSL, hue 0..360, s/l 0..1. */
    fun hexToHsl(hex: String): FloatArray {
        val (r, g, b) = hexToRgb(hex)
        return rgbToHsl(r, g, b)
    }

    /** HSL -> Hex (#RRGGBB). */
    fun hslToHex(hue: Float, saturation: Float, lightness: Float): String {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = h / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(v: Float): String =
            ((v + m) * 255f).roundToInt().coerceIn(0, 255)
                .toString(16).padStart(2, '0').uppercase(Locale.ROOT)
        return "#${channel(r1)}${channel(g1)}${channel(b1)}"
    }
}