package com.duck.app.domain.usecase

import com.duck.app.domain.DuckConstants
import com.duck.app.domain.model.DuckError
import com.duck.app.domain.model.DuckErrorCode
import com.duck.app.domain.model.DuckSettings
import com.duck.app.domain.model.QuackCustom
import com.duck.app.domain.model.QuackPreset
import com.duck.app.domain.model.QuackMode
import com.duck.app.domain.model.Result
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбор звука для воспроизведения (resolveQuack, ТЗ §2.1). Чистый вычисляемый UseCase без состояния.
 */
@Singleton
class ResolveQuackUseCase @Inject constructor() {

    sealed interface ResolvedQuack {
        data class Preset(val preset: QuackPreset) : ResolvedQuack
        data class Custom(val durationMs: Int, val fileExists: Boolean) : ResolvedQuack
    }

    /**
     * @param settings          настройки
     * @param presets           пресеты каталога (уже валидные)
     * @param customExists      существует ли файл custom-записи
     * @param customMeta        мета записанного кряка (для длительности)
     * @param randomOverride    RNG для RANDOM-режима (тесты/эмуляция)
     * @return пара: кряк + был ли применён fallback (для логирования)
     */
    fun resolve(
        settings: DuckSettings,
        presets: List<QuackPreset>,
        customExists: Boolean,
        customMeta: QuackCustom?,
        randomIndex: (Int) -> Int = { kotlin.random.Random.nextInt(it).coerceAtLeast(0) }
    ): Pair<ResolvedQuack, Boolean> {
        val classic = presets.firstOrNull { it.id == DuckConstants.CLASSIC_QUACK_ID }
            ?: presets.firstOrNull()
            ?: return Pair(ResolvedQuack.Custom(0, fileExists = false), true)
        val availablePresets = presets
        if (availablePresets.isEmpty()) {
            return Pair(ResolvedQuack.Preset(classic), false)
        }

        val customAvailable = customExists && customMeta != null

        // Режим RANDOM: пул = пресеты + custom (если есть и валиден) (E-C2/E-C3).
        if (settings.quackMode == QuackMode.RANDOM) {
            val rnd = randomIndex(availablePresets.size + if (customAvailable) 1 else 0)
            if (customAvailable && rnd == availablePresets.size) {
                return Pair(
                    ResolvedQuack.Custom(customMeta!!.durationMs, fileExists = true),
                    false
                )
            }
            return Pair(ResolvedQuack.Preset(availablePresets[rnd.coerceIn(availablePresets.indices)]), false)
        }

        // SINGLE
        return when {
            settings.activeQuackId == DuckConstants.CUSTOM_QUACK_ID -> {
                when {
                    customExists && customMeta != null ->
                        Pair(ResolvedQuack.Custom(customMeta.durationMs, fileExists = true), false)

                    customExists && customMeta == null ->
                        // Инвариант нарушен (E-07): файл есть, мета нет -> fallback.
                        Pair(ResolvedQuack.Preset(classic), true)

                    else ->
                        // Запись удалена программно/извне -> dangling (E-AUDIO-004).
                        Pair(ResolvedQuack.Preset(classic), true)
                }
            }

            presets.any { it.id == settings.activeQuackId } ->
                Pair(ResolvedQuack.Preset(presets.first { it.id == settings.activeQuackId }), false)

            else ->
                // E_CATALOG_001: неизвестный id -> сохранить прежний (fallback).
                Pair(ResolvedQuack.Preset(classic), true)
        }
    }
}