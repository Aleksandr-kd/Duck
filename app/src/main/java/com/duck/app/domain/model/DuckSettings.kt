package com.duck.app.domain.model

import com.duck.app.domain.DuckConstants
import java.time.LocalDate

/**
 * Настройки приложения. Единый объект, маппится на DataStore Preferences (ТЗ §3.1, §3.4).
 */
data class DuckSettings(
    val activeQuackId: String = DuckConstants.CLASSIC_QUACK_ID,
    val quackMode: QuackMode = QuackMode.SINGLE,
    val duckColor: String = DuckColors.DEFAULT_HEX,
    val widgetBackgroundColor: String = DuckConstants.WIDGET_BACKGROUND_DEFAULT_HEX,
    val theme: AppTheme = AppTheme.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val customQuackMeta: QuackCustom? = null,
    val isCustomQuackActive: Boolean = false,
    val vibrateOnQuack: Boolean = false,
    val dailyCounter: Int = 0,
    val dailyCounterDate: String = LocalDate.now().toString()
) {
    companion object {
        fun now(): DuckSettings = DuckSettings(dailyCounterDate = LocalDate.now().toString())
    }
}