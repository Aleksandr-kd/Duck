package com.duck.app.ui.color

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duck.app.domain.model.DuckColors
import com.duck.app.domain.repository.DuckSettingsRepository
import com.duck.app.widget.WidgetSyncer
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

enum class ColorTarget {
    DUCK,
    WIDGET_BACKGROUND
}

/**
 * Пикер: hue/насыщенность задаются цветовым колесом, ползунок — «от яркого к пастельному».
 * w: яркий (насыщенный, средняя светлота) → 1: пастельный (бледный, светлый).
 */
data class PickerState(
    val hue: Float = 45f,
    val saturation: Float = 0.8f,
    val tone: Float = 0.5f
) {
    internal fun saturationOf(perc: Float): Float = (saturation * (1f - 0.65f * perc.coerceIn(0f, 1f)))
        .coerceIn(0f, 1f)
    internal fun lightnessOf(perc: Float): Float = (0.45f + 0.48f * perc.coerceIn(0f, 1f))
        .coerceIn(0f, 1f)

    val colorHex: String
        get() = DuckColors.hslToHex(
            hue = hue,
            saturation = saturationOf(tone),
            lightness = lightnessOf(tone)
        )

    /** Цвет на градиентном треке ползунка при данном [perc] (0 = яркий, 1 = пастельный). */
    fun trackColorHex(perc: Float): String =
        DuckColors.hslToHex(hue, saturationOf(perc), lightnessOf(perc))

    companion object {
        fun fromHsl(h: Float, s: Float, l: Float): PickerState {
            val tone = if (l <= 0.45f) 0f else ((l - 0.45f) / 0.48f).coerceIn(0f, 1f)
            val baseSat = if (tone >= 0.999f) s else (s / (1f - 0.65f * tone)).coerceIn(0f, 1f)
            return PickerState(hue = h, saturation = baseSat, tone = tone)
        }
    }
}

@HiltViewModel
class ColorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: DuckSettingsRepository
) : ViewModel() {

    private val _target = MutableStateFlow(ColorTarget.DUCK)
    val target: StateFlow<ColorTarget> = _target.asStateFlow()

    val currentColor: StateFlow<String> = combine(
        settingsRepository.observeSettings(),
        _target
    ) { s, t ->
        when (t) {
            ColorTarget.DUCK -> s.duckColor
            ColorTarget.WIDGET_BACKGROUND -> s.widgetBackgroundColor
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuckColors.DEFAULT_HEX)

    private val _picker = MutableStateFlow(PickerState())
    val picker: StateFlow<PickerState> = _picker.asStateFlow()

    private var pickerSyncedTo: ColorTarget? = null
    private var persistJob: Job? = null
    private var widgetSyncJob: Job? = null

    /** Настраивает цель (цвет утки или фон виджета) при входе на экран. */
    fun configure(colorTarget: ColorTarget) {
        if (_target.value == colorTarget && pickerSyncedTo == colorTarget) return
        _target.value = colorTarget
        pickerSyncedTo = null
    }

    /** Синхронизация пикера с сохранённым значением (при каждом входе на экран). */
    fun initPicker(colorHex: String) {
        val syncTarget = _target.value
        if (pickerSyncedTo == syncTarget) return
        pickerSyncedTo = syncTarget
        val (h, s, l) = DuckColors.hexToHsl(colorHex)
        _picker.value = PickerState.fromHsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /** Тап/перетаскивание по цветовому колесу: hue и насыщенность + автосохранение. */
    fun onWheelPicked(hue: Float, saturation: Float) {
        _picker.value = _picker.value.copy(
            hue = ((hue % 360f) + 360f) % 360f,
            saturation = saturation.coerceIn(0.05f, 1f)
        )
        persist()
    }

    /** Ползунок «от яркого к пастельному»: perc 0..1. */
    fun onToneChanged(perc: Float) {
        _picker.value = _picker.value.copy(tone = perc.coerceIn(0f, 1f))
        persist()
    }

    /** Выбор пресета — подгоняет колесо/ползунок под пресет + автосохранение. */
    fun selectPreset(hex: String) {
        val (h, s, l) = DuckColors.hexToHsl(hex)
        _picker.value = PickerState.fromHsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
        persist()
    }

    private fun persist() {
        val hex = _picker.value.colorHex
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(150)
            when (_target.value) {
                ColorTarget.DUCK -> settingsRepository.setDuckColor(hex)
                ColorTarget.WIDGET_BACKGROUND -> settingsRepository.setWidgetBackgroundColor(hex)
            }
            syncWidget()
        }
    }

    private fun syncWidget() {
        widgetSyncJob?.cancel()
        widgetSyncJob = viewModelScope.launch {
            delay(400)
            WidgetSyncer.refresh(context)
        }
    }
}