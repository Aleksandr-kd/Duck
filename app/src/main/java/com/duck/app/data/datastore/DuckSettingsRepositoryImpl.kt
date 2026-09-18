package com.duck.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duck.app.core.DuckLogger
import com.duck.app.domain.DuckConstants
import com.duck.app.domain.model.AppTheme
import com.duck.app.domain.model.DuckColors
import com.duck.app.domain.model.DuckErrorCode
import com.duck.app.domain.model.DuckSettings
import com.duck.app.domain.model.QuackCustom
import com.duck.app.domain.model.QuackMode
import com.duck.app.domain.repository.DuckSettingsRepository
import com.duck.app.domain.repository.QuackCustomMetaInput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация настроек поверх DataStore Preferences (ТЗ §3.1).
 * Все операции последовательны (гарантия DataStore), ошибки — E_DATASTORE_001 с дефолтами.
 */
@Singleton
class DuckSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : DuckSettingsRepository {

    private val dataStore = context.duckDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val ACTIVE_QUACK_ID = stringPreferencesKey("active_quack_id")
        val QUACK_MODE = stringPreferencesKey("quack_mode")
        val DUCK_COLOR = stringPreferencesKey("duck_color")
        val WIDGET_BACKGROUND_COLOR = stringPreferencesKey("widget_background_color")
        val THEME = stringPreferencesKey("theme")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val CUSTOM_QUACK_META = stringPreferencesKey("custom_quack_meta")
        val IS_CUSTOM_QUACK_ACTIVE = booleanPreferencesKey("is_custom_quack_active")
        val VIBRATE_ON_QUACK = booleanPreferencesKey("vibrate_on_quack")
        val DAILY_COUNTER = intPreferencesKey("daily_counter")
        val DAILY_COUNTER_DATE = stringPreferencesKey("daily_counter_date")
    }

    override fun observeSettings(): Flow<DuckSettings> = dataStore.data
        .catch { e ->
            DuckLogger.errorEvent(
                DuckErrorCode.E_DATASTORE_001.name, "DataStore read error", e
            )
            emit(emptyPreferences())
        }
        .map { it.toSettings() }

    override suspend fun getSettingsOnce(): DuckSettings {
        val prefs = runCatching { dataStore.data.first() }.getOrElse { e ->
            DuckLogger.errorEvent(DuckErrorCode.E_DATASTORE_001.name, "DataStore read error", e)
            emptyPreferences()
        }
        return prefs.toSettings()
    }

    override suspend fun setActiveQuack(id: String, setDefault: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_QUACK_ID] = id
            prefs[Keys.IS_CUSTOM_QUACK_ACTIVE] = id == DuckConstants.CUSTOM_QUACK_ID
        }
    }

    override suspend fun setQuackMode(mode: QuackMode) {
        dataStore.edit { prefs -> prefs[Keys.QUACK_MODE] = mode.storageValue }
    }

    override suspend fun setDuckColor(colorHex: String) {
        dataStore.edit { prefs -> prefs[Keys.DUCK_COLOR] = DuckColors.normalizeHex(colorHex) }
    }

    override suspend fun setWidgetBackgroundColor(colorHex: String) {
        dataStore.edit { prefs ->
            prefs[Keys.WIDGET_BACKGROUND_COLOR] = DuckColors.normalizeHex(colorHex)
        }
    }

    override suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { prefs -> prefs[Keys.THEME] = theme.storageValue }
    }

    override suspend fun completeOnboarding() {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = true }
    }

    override suspend fun setVibrateOnQuack(v: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.VIBRATE_ON_QUACK] = v }
    }

    override suspend fun touchDailyCounter() {
        dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val counter = if (prefs[Keys.DAILY_COUNTER_DATE] == today) {
                prefs[Keys.DAILY_COUNTER] ?: 0
            } else 0
            prefs[Keys.DAILY_COUNTER] = (counter + 1).coerceAtMost(Int.MAX_VALUE)
            prefs[Keys.DAILY_COUNTER_DATE] = today
        }
    }

    override suspend fun setCustomQuackMeta(meta: QuackCustomMetaInput?) {
        dataStore.edit { prefs ->
            if (meta == null) {
                prefs.remove(Keys.CUSTOM_QUACK_META)
                prefs[Keys.IS_CUSTOM_QUACK_ACTIVE] = false
                if (prefs[Keys.ACTIVE_QUACK_ID] == DuckConstants.CUSTOM_QUACK_ID) {
                    prefs[Keys.ACTIVE_QUACK_ID] = DuckConstants.CLASSIC_QUACK_ID
                }
            } else {
                val obj = buildJsonObject {
                    put("date", meta.date)
                    put("durationMs", meta.durationMs)
                }
                prefs[Keys.CUSTOM_QUACK_META] = obj.toString()
            }
        }
    }

    private fun Preferences.toSettings(): DuckSettings {
        val today = LocalDate.now().toString()
        val storedDate = this[Keys.DAILY_COUNTER_DATE] ?: today
        val counter = if (storedDate == today) this[Keys.DAILY_COUNTER] ?: 0 else 0

        val activeId = this[Keys.ACTIVE_QUACK_ID] ?: DuckConstants.CLASSIC_QUACK_ID
        val customMeta = parseCustomMeta(this[Keys.CUSTOM_QUACK_META])
        var isCustomActive = this[Keys.IS_CUSTOM_QUACK_ACTIVE] ?: false

        // Инвариант §3.1 + коррекция по E-07: активен custom без метаданных => сброс на CLASSIC.
        if (activeId == DuckConstants.CUSTOM_QUACK_ID && customMeta == null) {
            isCustomActive = false
            DuckLogger.w(TAG, "custom quack dangling, fallback to classic (E-07)")
        }

        return DuckSettings(
            activeQuackId = activeId,
            quackMode = QuackMode.fromStorage(this[Keys.QUACK_MODE]),
            duckColor = this[Keys.DUCK_COLOR]?.let {
                if (DuckColors.isValidHex(it)) DuckColors.normalizeHex(it) else null
            } ?: DuckColors.DEFAULT_HEX,
            widgetBackgroundColor = this[Keys.WIDGET_BACKGROUND_COLOR]?.let {
                if (DuckColors.isValidHex(it)) DuckColors.normalizeHex(it) else null
            } ?: DuckConstants.WIDGET_BACKGROUND_DEFAULT_HEX,
            theme = AppTheme.fromStorage(this[Keys.THEME]),
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
            customQuackMeta = customMeta,
            isCustomQuackActive = isCustomActive,
            vibrateOnQuack = this[Keys.VIBRATE_ON_QUACK] ?: false,
            dailyCounter = counter,
            dailyCounterDate = storedDate
        )
    }

    private fun parseCustomMeta(raw: String?): QuackCustom? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            QuackCustom(
                date = obj["date"]?.jsonPrimitive?.long ?: return null,
                durationMs = obj["durationMs"]?.jsonPrimitive?.int ?: return null
            )
        }.getOrElse {
            DuckLogger.w(TAG, "custom_quack_meta parse error: ${it.message}")
            null
        }
    }

    private companion object {
        const val TAG = "DataStore"
    }
}