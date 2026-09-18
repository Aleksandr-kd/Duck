package com.duck.app.ui.quacks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duck.app.R
import com.duck.app.domain.model.QuackMode
import com.duck.app.domain.model.QuackPreset
import com.duck.app.ui.components.DuckHeaderScreen
import com.duck.app.ui.components.ErrorCollector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuacksScreen(
    onBack: () -> Unit,
    viewModel: QuacksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ErrorCollector(errors = viewModel.errors, snackbarHostState = snackbarHostState)

    DuckHeaderScreen(title = stringResource(R.string.quacks_title), onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {

            item {
                Text(
                    text = stringResource(R.string.quacks_mode_header),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.quackMode == QuackMode.SINGLE,
                        onClick = { viewModel.setMode(QuackMode.SINGLE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.quacks_mode_single)) }
                    SegmentedButton(
                        selected = state.quackMode == QuackMode.RANDOM,
                        onClick = { viewModel.setMode(QuackMode.RANDOM) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.quacks_mode_random)) }
                }

                Text(
                    text = stringResource(R.string.quacks_active_header),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            if (state.presets.isEmpty() && !state.loading) {
                item {
                    Text(
                        text = stringResource(R.string.quacks_empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            items(state.presets, key = { it.id }) { preset ->
                PresetRow(
                    preset = preset,
                    selected = state.activeId == preset.id,
                    onSelect = { viewModel.selectPreset(preset.id) },
                    onPreview = { viewModel.preview(preset) }
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: QuackPreset,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = preset.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.quacks_label_duration, preset.durationMs / 1000f, preset.name),
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onPreview) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.quacks_preview_play, preset.name))
        }
    }
    HorizontalDivider()
}