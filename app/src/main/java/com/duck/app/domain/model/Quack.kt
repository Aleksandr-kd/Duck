package com.duck.app.domain.model

/**
 * Кряк, готовый к воспроизведению (резолв по §2.1) или к предпрослушиванию.
 */
data class Quack(
    val id: String,
    val name: String,
    val durationMs: Int
)