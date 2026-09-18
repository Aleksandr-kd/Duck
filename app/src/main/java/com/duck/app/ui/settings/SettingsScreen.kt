package com.duck.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.duck.app.R
import com.duck.app.domain.DuckConstants
import com.duck.app.domain.model.AppTheme
import com.duck.app.domain.model.DuckSettings
import com.duck.app.domain.repository.DuckSettingsRepository
import com.duck.app.ui.components.DuckHeaderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: DuckSettingsRepository
) : ViewModel() {
    val settings: StateFlow<DuckSettings> = settingsRepository
        .observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuckSettings.now())

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onQuacks: () -> Unit,
    onColor: () -> Unit,
    onWidgetColor: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    DuckHeaderScreen(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val activeName = if (settings.activeQuackId == DuckConstants.CUSTOM_QUACK_ID) {
                stringResource(R.string.quacks_custom_title)
            } else settings.activeQuackId

            SectionHeader(text = stringResource(R.string.settings_section_appearance))
            ThemeCard(theme = settings.theme, onSelectTheme = viewModel::setTheme)
            SettingsCard(
                title = stringResource(R.string.settings_duck_color),
                subtitle = stringResource(R.string.settings_duck_color_current, settings.duckColor),
                leading = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color = hexToColor(settings.duckColor), shape = CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                },
                onClick = onColor
            )
            SettingsCard(
                title = stringResource(R.string.settings_widget_background),
                subtitle = stringResource(
                    R.string.settings_widget_background_current,
                    settings.widgetBackgroundColor
                ),
                leading = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color = hexToColor(settings.widgetBackgroundColor), shape = CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                },
                onClick = onWidgetColor
            )

            SectionHeader(text = stringResource(R.string.settings_section_sound))
            SettingsCard(
                title = stringResource(R.string.settings_quacks),
                subtitle = stringResource(R.string.settings_quacks_active, activeName),
                leading = { GlyphDot(glyph = "🦆") },
                onClick = onQuacks
            )

            SectionHeader(text = stringResource(R.string.settings_section_other))
            SettingsCard(
                title = stringResource(R.string.settings_about),
                leading = { GlyphDot(glyph = "ℹ") },
                onClick = onAbout
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemeCard(theme: AppTheme, onSelectTheme: (AppTheme) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options: List<Pair<AppTheme, Int>> = listOf(
                    AppTheme.SYSTEM to R.string.settings_theme_system,
                    AppTheme.LIGHT to R.string.settings_theme_light,
                    AppTheme.DARK to R.string.settings_theme_dark
                )
                options.forEachIndexed { index, (value, labelRes) ->
                    SegmentedButton(
                        selected = theme == value,
                        onClick = { onSelectTheme(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) { Text(stringResource(labelRes)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    leading: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GlyphDot(glyph: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = glyph, style = MaterialTheme.typography.titleMedium)
    }
}

private fun hexToColor(hex: String): Color {
    val v = hex.removePrefix("#").toLong(16) or 0xFF000000L
    return Color(v)
}