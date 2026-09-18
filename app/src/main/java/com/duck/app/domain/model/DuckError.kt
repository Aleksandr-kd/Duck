package com.duck.app.domain.model

/**
 * Единая ошибка приложения (ТЗ §3.4: DuckError(code, message, cause)).
 */
data class DuckError(
    val code: DuckErrorCode,
    val message: String = "",
    val cause: Throwable? = null
)