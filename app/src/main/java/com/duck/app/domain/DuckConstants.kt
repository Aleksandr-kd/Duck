package com.duck.app.domain

/**
 * Феноменология приложения (ТЗ §2.1 и др.).
 */
object DuckConstants {
    const val CUSTOM_QUACK_ID = "custom"
    const val CLASSIC_QUACK_ID = "short-low"
    const val WIDGET_BACKGROUND_DEFAULT_HEX = "#F1D9A5"
    const val DEBOUNCE_MS = 300L
    const val MAX_RECORD_MS = 5_000L
    const val MIN_RECORD_MS = 400L
    const val SILENT_VIBRATION_MS = 80L
    const val MAX_SILENT_ANIMATIONS = 2
    const val CATALOG_ASSET = "sound_catalog.json"
    const val CUSTOM_FILE_RELATIVE = "quack/custom_quack.wav"
    const val NOMEDIA_FILE_RELATIVE = "quack/.nomedia"
}