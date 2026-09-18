package com.duck.app.ui.quacks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duck.app.domain.DuckConstants
import com.duck.app.domain.model.DuckError
import com.duck.app.domain.model.QuackMode
import com.duck.app.domain.model.QuackPreset
import com.duck.app.domain.model.Result
import com.duck.app.domain.player.DuckSoundPlayer
import com.duck.app.domain.repository.DuckCatalogRepository
import com.duck.app.domain.repository.DuckSettingsRepository
import com.duck.app.widget.WidgetSyncer
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context

data class QuacksUiState(
    val presets: List<QuackPreset> = emptyList(),
    val quackMode: QuackMode = QuackMode.SINGLE,
    val activeId: String = DuckConstants.CLASSIC_QUACK_ID,
    val loading: Boolean = true,
    val catalogError: DuckError? = null
)

@HiltViewModel
class QuacksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: DuckCatalogRepository,
    private val settingsRepository: DuckSettingsRepository,
    private val soundPlayer: DuckSoundPlayer
) : ViewModel() {

    private val _state = MutableStateFlow(QuacksUiState())
    val state: StateFlow<QuacksUiState> = _state.asStateFlow()

    val errors: SharedFlow<DuckError> = soundPlayer.errors

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            when (val result = catalogRepository.getPresets()) {
                is Result.Success -> _state.value = _state.value.copy(presets = result.data)
                is Result.Failure -> _state.value = _state.value.copy(catalogError = result.error)
            }

            settingsRepository.observeSettings().stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.duck.app.domain.model.DuckSettings.now()
            ).collect { s ->
                _state.value = _state.value.copy(
                    quackMode = s.quackMode,
                    activeId = s.activeQuackId
                )
            }
        }
    }

    fun setMode(mode: QuackMode) {
        viewModelScope.launch {
            settingsRepository.setQuackMode(mode)
            syncWidget()
        }
    }

    fun selectPreset(id: String) {
        viewModelScope.launch {
            settingsRepository.setActiveQuack(id)
            syncWidget()
        }
    }

    fun preview(preset: QuackPreset) {
        viewModelScope.launch { soundPlayer.playPreview(preset) }
    }

    private fun syncWidget() {
        viewModelScope.launch { WidgetSyncer.refresh(context) }
    }
}