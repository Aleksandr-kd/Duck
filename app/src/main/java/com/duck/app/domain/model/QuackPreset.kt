package com.duck.app.domain.model

/**
 * Пресет кряка из каталога `assets/sound_catalog.json` (ТЗ §3.3).
 */
data class QuackPreset(
    val id: String,
    val name: String,
    val file: String,
    val durationMs: Int,
    val isDefault: Boolean,
    val license: String?,
    val credit: String?,
    val sourceUrl: String?
) {
    val label: String
        get() = "%.1f с".format(durationMs / 1000.0)
}