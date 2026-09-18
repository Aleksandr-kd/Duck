package com.duck.app.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duck.app.R
import com.duck.app.ui.components.DuckAssetImage
import com.duck.app.ui.components.ErrorCollector

@Composable
fun DuckScreen(
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tick by viewModel.tapTick.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val scale = remember { Animatable(1f) }
    LaunchedEffect(tick) {
        if (tick == 0L) return@LaunchedEffect
        scale.animateTo(1.14f, animationSpec = tween(80, easing = LinearEasing))
        scale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
    }

    ErrorCollector(errors = viewModel.errors, snackbarHostState = snackbarHostState)

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    onClickLabel = stringResource(R.string.duck_click_action),
                    onClick = { viewModel.onDuckTap() }
                )
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.nav_settings)
                )
            }

            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        },
                    contentAlignment = Alignment.Center
                ) {
                    DuckAssetImage(
                        assetPath = "design/duck_cutout.png",
                        colorHex = settings.duckColor,
                        contentDescription = stringResource(R.string.duck_content_description),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.main_counter_today, settings.dailyCounter),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}