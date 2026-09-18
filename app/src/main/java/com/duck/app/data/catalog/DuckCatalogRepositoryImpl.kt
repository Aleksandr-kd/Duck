package com.duck.app.data.catalog

import android.content.Context
import com.duck.app.core.DuckLogger
import com.duck.app.domain.DuckConstants
import com.duck.app.domain.model.DuckError
import com.duck.app.domain.model.DuckErrorCode
import com.duck.app.domain.model.Quack
import com.duck.app.domain.model.QuackPreset
import com.duck.app.domain.model.Result
import com.duck.app.domain.repository.CustomQuackRepository
import com.duck.app.domain.repository.DuckCatalogRepository
import com.duck.app.domain.repository.DuckSettingsRepository
import com.duck.app.domain.usecase.ResolveQuackUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Каталог пресетов из `assets/sound_catalog.json`.
 * E_CATALOG_003 — невалидный JSON -> резервный список; E_CATALOG_002 — пуст.
 */
@Singleton
class DuckCatalogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: DuckSettingsRepository,
    private val customQuackRepository: CustomQuackRepository,
    private val resolveQuackUseCase: ResolveQuackUseCase
) : DuckCatalogRepository {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<QuackPreset>? = null

    override suspend fun getPresets(): Result<List<QuackPreset>> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext Result.Success(it) }

        val raw = runCatching {
            context.assets.open(DuckConstants.CATALOG_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()

        val dto = raw?.let { SoundCatalogDto.parse(it, json) }
        val valid = dto != null && SoundCatalogDto.isValid(dto)

        if (!valid) {
            DuckLogger.errorEvent(
                DuckErrorCode.E_CATALOG_003.name,
                "sound_catalog.json невалиден/нечитаем -> резервный список"
            )
        }

        val rawPresets = if (valid) dto!!.presets else SoundCatalogDto.fallbackPresets()
        val mapped = rawPresets
            .filter { it.id.isNotBlank() && it.file.isNotBlank() }
            .map { it.toDomain() }

        if (mapped.isEmpty()) {
            return@withContext Result.Failure(
                DuckError(DuckErrorCode.E_CATALOG_002, "Кряков нет")
            )
        }
        cache = mapped
        Result.Success(mapped)
    }

    override suspend fun getActiveQuack(): Result<Quack> {
        val settings = settingsRepository.getSettingsOnce()
        val presetsResult = getPresets()
        if (presetsResult is Result.Failure) return presetsResult

        val presets = (presetsResult as Result.Success).data
        val customExists = customQuackRepository.hasCustom()
        val (resolved, fellBack) = resolveQuackUseCase.resolve(
            settings = settings,
            presets = presets,
            customExists = customExists,
            customMeta = settings.customQuackMeta
        )
        if (fellBack) {
            DuckLogger.w(TAG, "resolveQuack fallback (E-07/E_CATALOG_001)")
        }
        return when (resolved) {
            is ResolveQuackUseCase.ResolvedQuack.Preset ->
                Result.Success(
                    Quack(resolved.preset.id, resolved.preset.name, resolved.preset.durationMs)
                )
            is ResolveQuackUseCase.ResolvedQuack.Custom ->
                Result.Success(
                    Quack(
                        DuckConstants.CUSTOM_QUACK_ID,
                        "Свой кряк",
                        resolved.durationMs
                    )
                )
        }
    }

    override suspend fun getPresetById(id: String): QuackPreset? {
        val presetsResult = getPresets()
        return (presetsResult as? Result.Success)?.data?.find { it.id == id }
    }

    private companion object {
        const val TAG = "Catalog"
    }
}

private fun SoundCatalogDto.PresetDto.toDomain(): QuackPreset = QuackPreset(
    id = id,
    name = name,
    file = file,
    durationMs = durationMs,
    isDefault = isDefault,
    license = license,
    credit = credit,
    sourceUrl = sourceUrl
)