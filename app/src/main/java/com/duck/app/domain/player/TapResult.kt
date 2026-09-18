package com.duck.app.domain.player

/**
 * Результат тапа по утке (UC-01). UI запускает анимацию по [quack].
 */
sealed interface TapResult {
    /** Тап проигнорирован (debounce < 300 мс, E-01). */
    data object Ignored : TapResult

    data class Played(
        val quack: com.duck.app.domain.model.Quack,
        val silentMode: Boolean,
        val playFailed: Boolean = false
    ) : TapResult
}