package com.duck.app.data.custom

import android.content.Context
import com.duck.app.core.DuckLogger
import com.duck.app.domain.DuckConstants
import com.duck.app.domain.model.DuckError
import com.duck.app.domain.model.DuckErrorCode
import com.duck.app.domain.model.QuackCustom
import com.duck.app.domain.model.Result
import com.duck.app.domain.repository.CustomQuackRepository
import com.duck.app.domain.repository.DuckSettingsRepository
import com.duck.app.domain.repository.QuackCustomMetaInput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Управление записью пользователя (ТЗ §3.2, §4.2).
 * Доступ к filesDir — только через этот репозиторий.
 */
@Singleton
class CustomQuackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: DuckSettingsRepository
) : CustomQuackRepository {

    private val quackDir: File
        get() = File(context.filesDir, "quack")

    override suspend fun hasCustom(): Boolean = withContext(Dispatchers.IO) {
        getCustomFile()?.let { it.isFile && it.length() > 44 } ?: false
    }

    override suspend fun saveRecording(tmpPath: File, durationMs: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            ensureNomedia()
            val dir = quackDir
            val target = File(dir, CUSTOM_FILE_NAME)

            if (tmpPath.length() < MIN_WAV_BYTES) {
                tmpPath.delete()
                return@withContext Result.Failure(
                    DuckError(DuckErrorCode.E_REC_003, "Слишком короткая запись. Запиши не меньше 0.4 сек")
                )
            }

            // E-R9: атомарность — старый файл удаляем ТОЛЬКО после успешной финализации нового.
            val oldRemoved = !target.exists() || runCatching { target.delete() }.getOrDefault(false)
            val ok = oldRemoved && tmpPath.renameTo(target)
            if (!ok || !target.isFile) {
                tmpPath.delete()
                DuckLogger.errorEvent(DuckErrorCode.E_STORAGE_001.name, "rename failed")
                return@withContext Result.Failure(
                    DuckError(DuckErrorCode.E_STORAGE_001, "Недостаточно свободного места")
                )
            }

            settingsRepository.setCustomQuackMeta(
                QuackCustomMetaInput(date = System.currentTimeMillis(), durationMs = durationMs.toInt())
            )
            DuckLogger.i(TAG, "custom quack saved: $durationMs ms")
            Result.Success(Unit)
        }

    override suspend fun deleteCustom(): Result<Unit> = withContext(Dispatchers.IO) {
        ensureNomedia()
        val file = File(quackDir, CUSTOM_FILE_NAME)
        runCatching { file.delete() }
        // Идемпотентен (E_AUDIO_004): файл отсутствует — это не ошибка.
        settingsRepository.setCustomQuackMeta(null)
        settingsRepository.setActiveQuack(DuckConstants.CLASSIC_QUACK_ID, setDefault = true)
        DuckLogger.i(TAG, "custom quack deleted")
        Result.Success(Unit)
    }

    override fun observeCustomMeta(): Flow<QuackCustom?> =
        settingsRepository.observeSettings().map { it.customQuackMeta }

    override suspend fun getCustomMeta(): QuackCustom? =
        settingsRepository.getSettingsOnce().customQuackMeta

    override fun getCustomFile(): File? {
        val f = File(quackDir, CUSTOM_FILE_NAME)
        return f.takeIf { it.isFile }
    }

    override fun provideTmpFile(): File = File(quackDir, "tmp_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt()}.wav")

    private fun ensureNomedia() {
        val dir = quackDir
        if (!dir.exists()) dir.mkdirs()
        val marker = File(dir, NOMEDIA_NAME)
        if (!marker.exists()) runCatching { marker.createNewFile() }
    }

    private companion object {
        const val TAG = "CustomQuack"
        const val CUSTOM_FILE_NAME = "custom_quack.wav"
        const val NOMEDIA_NAME = ".nomedia"
        const val MIN_WAV_BYTES = 44 + (DuckConstants.MIN_RECORD_MS * 44100L * 2) / 1000L
    }
}