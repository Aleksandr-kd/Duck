package com.duck.app.domain.repository

import com.duck.app.domain.model.AppTheme
import com.duck.app.domain.model.DuckSettings
import com.duck.app.domain.model.QuackMode
import kotlinx.coroutines.flow.Flow

interface DuckSettingsRepository {
    fun observeSettings(): Flow<DuckSettings>
    suspend fun getSettingsOnce(): DuckSettings
    suspend fun setActiveQuack(id: String, setDefault: Boolean = true)
    suspend fun setQuackMode(mode: QuackMode)
    suspend fun setDuckColor(colorHex: String)
    suspend fun setWidgetBackgroundColor(colorHex: String)
    suspend fun setTheme(theme: AppTheme)
    suspend fun completeOnboarding()
    suspend fun setVibrateOnQuack(v: Boolean)
    suspend fun touchDailyCounter()
    suspend fun setCustomQuackMeta(meta: QuackCustomMetaInput?)
}

data class QuackCustomMetaInput(
    val date: Long,
    val durationMs: Int
)