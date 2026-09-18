package com.duck.app.data.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Структура `assets/sound_catalog.json` (ТЗ §3.3). Валидация при парсинге.
 */
@Serializable
data class SoundCatalogDto(
    val version: Int = 1,
    val presets: List<PresetDto> = emptyList()
) {
    @Serializable
    data class PresetDto(
        val id: String = "",
        val name: String = "",
        val file: String = "",
        val durationMs: Int = 0,
        val isDefault: Boolean = false,
        val license: String? = null,
        val credit: String? = null,
        val sourceUrl: String? = null
    )

    companion object {
        const val MIN_PRESETS = 5
        val ID_REGEX = Regex("^[a-z0-9-]+$", RegexOption.IGNORE_CASE)
        const val MAX_ID_LENGTH = 32
        const val MAX_NAME_LENGTH = 40
        const val MIN_DURATION_MS = 100
        const val MAX_DURATION_MS = 30_000

        fun parse(raw: String, json: Json): SoundCatalogDto? = runCatching {
            val dto = json.decodeFromString<SoundCatalogDto>(raw)
            dto
        }.getOrNull()

        /** Валидация документа по ограничениям §3.3. */
        fun isValid(dto: SoundCatalogDto): Boolean {
            if (dto.presets.size < MIN_PRESETS) return false
            val ids = mutableSetOf<String>()
            for (p in dto.presets) {
                if (p.id.length > MAX_ID_LENGTH) return false
                if (!ID_REGEX.matches(p.id)) return false
                if (!ids.add(p.id)) return false
                if (p.name.isBlank() || p.name.length > MAX_NAME_LENGTH) return false
                if (p.file.isBlank()) return false
                if (p.durationMs !in MIN_DURATION_MS..MAX_DURATION_MS) return false
            }
            return true
        }

        /** Статический резервный список (E_CATALOG_003): пресеты пользователя из ресурсов APK. */
        fun fallbackPresets(): List<PresetDto> = listOf(
            PresetDto("short-low", "Низкий кряк", "quacks/short-low-quacking-sound.wav", 313, true,
                "локальный файл", "Пользователь", null),
            PresetDto("pond-1", "Утка, пруд 1", "quacks/pond_1.wav", 859, false,
                "CC BY-SA 3.0", "Утки, Mudchute City Farm (запись Secretlondon)",
                "https://commons.wikimedia.org/wiki/File:Mudchute_duck_1.ogg"),
            PresetDto("pond-2", "Утка, пруд 2", "quacks/pond_2.wav", 900, false,
                "CC BY-SA 3.0", "Утки, Mudchute City Farm (запись Secretlondon)",
                "https://commons.wikimedia.org/wiki/File:Mudchute_duck_2.ogg"),
            PresetDto("squeak", "Резиновая уточка", "quacks/squeak.wav", 900, false,
                "CC BY-SA 4.0", "Писк резиновой утки (запись Dragonhawk12)",
                "https://commons.wikimedia.org/wiki/File:Rubber_Duck_Squeaker.ogg"),
            PresetDto("cartoon", "Мультяшный кряк", "quacks/cartoon-quacking.wav", 213, false,
                "локальный файл", "Пользователь", null)
        )
    }
}

fun JsonObject.getString(key: String): String? = this[key]?.jsonPrimitive?.content