package com.duck.app.domain.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.duck.app.core.DuckLogger
import com.duck.app.domain.DuckConstants
import com.duck.app.domain.model.DuckError
import com.duck.app.domain.model.DuckErrorCode
import com.duck.app.domain.repository.CustomQuackRepository
import com.duck.app.domain.repository.DuckCatalogRepository
import com.duck.app.domain.repository.DuckSettingsRepository
import com.duck.app.domain.usecase.ResolveQuackUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер воспроизведения кряков (ТЗ §1, §2.1). Singleton, maxStreams=1, debounce 300 мс.
 *
 * Пресеты — SoundPool (WAV PCM <=1c), запись пользователя — MediaPlayer (§1).
 * Sound и UI развязаны: ViewModel вызывает только [onDuckTap]/[playPreview].
 */
@Singleton
class DuckSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: DuckCatalogRepository,
    private val settingsRepository: DuckSettingsRepository,
    private val customQuackRepository: CustomQuackRepository,
    private val resolveQuackUseCase: ResolveQuackUseCase
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mutex = Mutex()

    private var soundPool: SoundPool? = null
    private val soundIds = HashMap<String, Int>()
    private val presetBySound = HashMap<Int, String>()

    private var mediaPlayer: MediaPlayer? = null

    private var lastTapAt = 0L
    private var silentAnimationsInRow = 0

    private val _errors = MutableSharedFlow<DuckError>(extraBufferCapacity = 4)
    val errors: SharedFlow<DuckError> = _errors.asSharedFlow()

    private val _playbackState = MutableStateFlow(false)
    val playbackState: StateFlow<Boolean> = _playbackState.asStateFlow()

    private var preloaded = false

    /**
     * Плановый предзагрузка пресетов (вызывать один раз при старте приложения).
     * E_AUDIO_002: если ни один пресет не загрузился, состояние считается ошибочным.
     */
    suspend fun preload() {
        mutex.withLock {
            if (preloaded) return
            try {
                ensureSoundPool()
                val result = catalogRepository.getPresets()
                if (result is com.duck.app.domain.model.Result.Success) {
                    val presets = result.data
                    presets.forEach { preset ->
                        val loaded = loadIntoPool(preset.id, preset.file)
                        if (loaded == null) {
                            DuckLogger.w(TAG, "preset [${preset.id}] not loaded (E_AUDIO_001)")
                        }
                    }
                } else {
                    DuckLogger.errorEvent(
                        DuckErrorCode.E_AUDIO_002.name, "catalog unavailable"
                    )
                }
                preloaded = true
            } finally {
                if (soundPool == null) {
                    DuckLogger.errorEvent(DuckErrorCode.E_AUDIO_002.name, "SoundPool init failed")
                    preloaded = false
                }
            }
        }
    }

    /**
     * Тап по утке (UC-01). Debounce 300 мс, silent/ДНД -> анимация без звука (+вибро).
     */
    suspend fun onDuckTap(): TapResult {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastTapAt < DuckConstants.DEBOUNCE_MS) return TapResult.Ignored
        lastTapAt = now

        val settings = settingsRepository.getSettingsOnce()
        val customExists = customQuackRepository.hasCustom()

        val resolvedResult = catalogRepository.getPresets()
        if (resolvedResult is com.duck.app.domain.model.Result.Failure) {
            emitError(
                DuckError(
                    resolvedResult.error.code,
                    message = resolvedResult.error.message
                )
            )
            return fallbackToClassicSilent()
        }
        val presets = (resolvedResult as com.duck.app.domain.model.Result.Success).data

        val (resolved, fellBack) = resolveQuackUseCase.resolve(
            settings = settings,
            presets = presets,
            customExists = customExists,
            customMeta = settings.customQuackMeta
        )
        if (fellBack) {
            DuckLogger.w(TAG, "resolve fallback (E-07/E_CATALOG_001)")
            if (settings.activeQuackId == DuckConstants.CUSTOM_QUACK_ID && !customExists) {
                // E-07: активен custom, файл удалён -> сброс active на classic.
                settingsRepository.setActiveQuack(DuckConstants.CLASSIC_QUACK_ID)
            }
        }

        return playResolved(resolved, settings.vibrateOnQuack)
    }

    private suspend fun playResolved(
        resolved: ResolveQuackUseCase.ResolvedQuack,
        vibrateOnQuack: Boolean
    ): TapResult {
        val silent = isRingerSilent()

        return when (resolved) {
            is ResolveQuackUseCase.ResolvedQuack.Custom -> {
                val file = if (resolved.fileExists) customQuackRepository.getCustomFile() else null
                if (file == null || !file.isFile) {
                    DuckLogger.errorEvent(
                        DuckErrorCode.E_AUDIO_004.name, "custom file missing"
                    )
                    settingsRepository.setActiveQuack(DuckConstants.CLASSIC_QUACK_ID)
                    val fallback = catalogRepository.getPresetById(DuckConstants.CLASSIC_QUACK_ID)
                    if (fallback != null) {
                        return playPreset(fallback, vibrateOnQuack, silent)
                    }
                    return TapResult.Played(
                        com.duck.app.domain.model.Quack(fallback?.id ?: "none", "—", 800),
                        silentMode = silent,
                        playFailed = true
                    )
                }
                playCustom(file, silent, vibrateOnQuack)
            }

            is ResolveQuackUseCase.ResolvedQuack.Preset -> {
                val preset = resolved.preset
                playPreset(preset, vibrateOnQuack, silent)
            }
        }
    }

    private suspend fun playPreset(preset: com.duck.app.domain.model.QuackPreset,
                                   vibrateOnQuack: Boolean,
                                   silent: Boolean): TapResult {
        var soundId = ensureSoundPool()?.let { soundIds[preset.id] } ?: 0
        if (soundId == 0) {
            // E-10: retry через load() один раз.
            soundId = loadIntoPool(preset.id, preset.file) ?: 0
            if (soundId == 0) {
                val alt = findWorkingPreset(preset.id)
                if (alt != null) {
                    DuckLogger.errorEvent(
                        DuckErrorCode.E_AUDIO_001.name,
                        "preset [${preset.id}] broken, fallback to [${alt.id}]"
                    )
                    return playPreset(alt, vibrateOnQuack, silent)
                }
                // E-06: ничего не играет -> счётчик "молчаливых анимаций".
                onSilentAnimation()
                return TapResult.Played(
                    com.duck.app.domain.model.Quack(preset.id, preset.name, preset.durationMs),
                    silentMode = silent,
                    playFailed = true
                )
            }
        }

        val pool = ensureSoundPool() ?: return TapResult.Played(
            com.duck.app.domain.model.Quack(preset.id, preset.name, preset.durationMs),
            silentMode = silent,
            playFailed = true
        )

        if (!silent) {
            val ret = pool.play(soundId, VOLUME, VOLUME, 0, 0, 1f)
            if (ret == 0) {
                val alt = findWorkingPreset(preset.id)
                if (alt != null && alt.id != preset.id) {
                    DuckLogger.errorEvent(
                        DuckErrorCode.E_AUDIO_001.name,
                        "playStream 0 for [${preset.id}], fallback to [${alt.id}]"
                    )
                    return playPreset(alt, vibrateOnQuack, silent)
                }
                onSilentAnimation()
                return TapResult.Played(
                    com.duck.app.domain.model.Quack(preset.id, preset.name, preset.durationMs),
                    silentMode = false,
                    playFailed = true
                )
            }
        } else {
            // E-03/E-04: без звука, по настройке - вибрация 80 мс (5a).
            if (vibrateOnQuack) vibrate(DuckConstants.SILENT_VIBRATION_MS)
        }

        onSuccessfulPlay()
        return TapResult.Played(
            com.duck.app.domain.model.Quack(preset.id, preset.name, preset.durationMs),
            silentMode = silent
        )
    }

    private suspend fun playCustom(file: File, silent: Boolean, vibrateOnQuack: Boolean): TapResult {
        if (silent) {
            if (vibrateOnQuack) vibrate(DuckConstants.SILENT_VIBRATION_MS)
            onSuccessfulPlay()
            return TapResult.Played(
                com.duck.app.domain.model.Quack(DuckConstants.CUSTOM_QUACK_ID, "Свой кряк", 0),
                silentMode = true
            )
        }
        return try {
            stopMediaPlayer()
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setOnCompletionListener { stopMediaPlayer() }
            player.setOnErrorListener { _, _, _ -> stopMediaPlayer(); true }
            player.prepare()
            player.start()
            mediaPlayer = player
            _playbackState.value = true
            onSuccessfulPlay()
            TapResult.Played(
                com.duck.app.domain.model.Quack(DuckConstants.CUSTOM_QUACK_ID, "Свой кряк", player.duration.coerceAtLeast(0)),
                silentMode = false
            )
        } catch (e: Exception) {
            DuckLogger.errorEvent(
                DuckErrorCode.E_RECORD_PLAYBACK_001.name, "custom playback error", e
            )
            onSilentAnimation()
            TapResult.Played(
                com.duck.app.domain.model.Quack(DuckConstants.CUSTOM_QUACK_ID, "Свой кряк", 0),
                silentMode = false,
                playFailed = true
            )
        }
    }

    /**
     * Предпрослушивание пресета (UC-02). Не влияет на активный кряк.
     */
    suspend fun playPreview(preset: com.duck.app.domain.model.QuackPreset): Boolean {
        val pool = ensureSoundPool() ?: return false
        var soundId = soundIds[preset.id] ?: 0
        if (soundId == 0) soundId = loadIntoPool(preset.id, preset.file) ?: 0
        if (soundId == 0) {
            DuckLogger.errorEvent(DuckErrorCode.E_AUDIO_001.name, "preview failed [${preset.id}]")
            return false
        }
        val ret = pool.play(soundId, VOLUME, VOLUME, 0, 0, 1f)
        return ret != 0
    }

    /**
     * Предпрослушивание записи пользователя.
     */
    suspend fun playPreviewCustom(): Boolean {
        val file = customQuackRepository.getCustomFile() ?: return false
        return try {
            stopMediaPlayer()
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setOnCompletionListener { stopMediaPlayer() }
            player.setOnErrorListener { _, _, _ -> stopMediaPlayer(); true }
            player.prepare()
            player.start()
            mediaPlayer = player
            _playbackState.value = true
            true
        } catch (e: Exception) {
            DuckLogger.errorEvent(DuckErrorCode.E_RECORD_PLAYBACK_001.name, "custom preview error", e)
            false
        }
    }

    fun stopAll() {
        stopMediaPlayer()
        soundPool?.autoPause()
        soundPool?.release()
        soundPool = null
        soundIds.clear()
        presetBySound.clear()
        preloaded = false
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.apply {
            runCatching { stop() }
            runCatching { release() }
        }
        mediaPlayer = null
        _playbackState.value = false
    }

    private suspend fun findWorkingPreset(excludedId: String): com.duck.app.domain.model.QuackPreset? {
        val result = catalogRepository.getPresets()
        return (result as? com.duck.app.domain.model.Result.Success)?.data
            ?.firstOrNull { it.id != excludedId && (soundIds[it.id] ?: 0) != 0 }
    }

    private suspend fun ensureSoundPool(): SoundPool? {
        soundPool?.let { return it }
        val pool = buildSoundPool()
        soundPool = pool
        return pool
    }

    private fun buildSoundPool(): SoundPool? = try {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(1)              // E-12
            .setAudioAttributes(attrs)
            .build()
    } catch (e: Throwable) {
        DuckLogger.errorEvent(DuckErrorCode.E_AUDIO_003.name, "SoundPool build failed", e)
        null
    }

    private fun loadIntoPool(id: String, filePath: String): Int? {
        val pool = soundPool ?: buildSoundPool() ?: return null
        soundPool = pool
        return try {
            val afd = context.assets.openFd(filePath)
            val soundId = pool.load(afd, 1)
            afd.close()
            if (soundId != 0) {
                soundIds[id] = soundId
                presetBySound[soundId] = id
            }
            soundId
        } catch (e: IOException) {
            DuckLogger.errorEvent(DuckErrorCode.E_AUDIO_001.name, "load failed [$id]", e)
            null
        }
    }

    private fun onSuccessfulPlay() {
        silentAnimationsInRow = 0
        _playbackState.value = true
    }

    private fun onSilentAnimation() {
        silentAnimationsInRow++
        if (silentAnimationsInRow >= DuckConstants.MAX_SILENT_ANIMATIONS) {
            silentAnimationsInRow = 0
            emitError(DuckError(DuckErrorCode.E_AUDIO_001, "Проверь настройки звука"))
        }
    }

    private fun emitError(error: DuckError) {
        _errors.tryEmit(error)
    }

    /** Silent-режим: ringer silent или ДНД (E-03/E-04). */
    fun isRingerSilent(): Boolean {
        val ringerSilent = audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
        val dnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
                nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE ||
                    nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
            } catch (e: Throwable) {
                false
            }
        } else false
        return ringerSilent || dnd
    }

    private fun vibrate(ms: Long) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(ms)
                }
            }
        }
    }

    private fun fallbackToClassicSilent(): TapResult =
        TapResult.Played(
            com.duck.app.domain.model.Quack(DuckConstants.CLASSIC_QUACK_ID, "Кряк-классика", 800),
            silentMode = isRingerSilent(),
            playFailed = true
        )

    private companion object {
        const val TAG = "DuckSoundPlayer"
        const val VOLUME = 1f
    }
}