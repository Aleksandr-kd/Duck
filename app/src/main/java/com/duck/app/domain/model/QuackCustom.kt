package com.duck.app.domain.model

/**
 * Запись пользователя (QCustom) — метаданные из DataStore (ТЗ §3.1, `custom_quack_meta`).
 */
data class QuackCustom(
    val date: Long,
    val durationMs: Int
)