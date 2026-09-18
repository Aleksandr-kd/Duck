package com.duck.app.ui.main

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duck.app.domain.model.AppTheme
import com.duck.app.domain.model.DuckSettings
import com.duck.app.domain.player.DuckSoundPlayer
import com.duck.app.domain.player.TapResult
import com.duck.app.domain.repository.DuckSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: DuckSettingsRepository,
    private val soundPlayer: DuckSoundPlayer
) : ViewModel() {

    val settings: StateFlow<DuckSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuckSettings.now())

    val isOnboardingCompleted: StateFlow<Boolean> = settings.map { it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val isDarkTheme: StateFlow<Boolean> = settings.map { s ->
        when (s.theme) {
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
            AppTheme.SYSTEM -> isSystemDark(context)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, true)

    val playbackState: StateFlow<Boolean> = soundPlayer.playbackState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val errors: SharedFlow<com.duck.app.domain.model.DuckError> = soundPlayer.errors

    private val _tapTick = MutableStateFlow(0L)
    val tapTick: StateFlow<Long> = _tapTick.asStateFlow()

    init {
        viewModelScope.launch { soundPlayer.preload() }
    }

    fun onDuckTap() {
        viewModelScope.launch {
            val result = soundPlayer.onDuckTap()
            if (result is TapResult.Played) {
                settingsRepository.touchDailyCounter()
                _tapTick.value++
            }
        }
    }

    private fun isSystemDark(context: Context): Boolean = (context.resources.configuration.uiMode
        and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}