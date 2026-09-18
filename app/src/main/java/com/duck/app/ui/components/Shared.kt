package com.duck.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duck.app.R
import com.duck.app.domain.model.DuckError
import com.duck.app.widget.DuckBitmapTinter
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * [DuckAssetImage] рендерит перекрашенный кадр утки (hue-rotation, ТЗ §2.4).
 * Путь — assets; кэш битмапов в DuckBitmapTinter.
 */
@Composable
fun DuckAssetImage(
    assetPath: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    var image by remember(assetPath, colorHex) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(assetPath, colorHex) { mutableStateOf(false) }

    LaunchedEffect(assetPath, colorHex) {
        failed = false
        image = withContext(Dispatchers.Default) {
            DuckBitmapTinter.tint(assetPath, colorHex, context)?.asImageBitmap()
        }
        if (image == null) failed = true
    }

    if (image != null) {
        Image(
            bitmap = image!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else if (failed) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "🦆", style = MaterialTheme.typography.displayMedium)
        }
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}

/** Каркас экрана с заголовком и кнопкой «назад». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuckHeaderScreen(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState ?: remember { SnackbarHostState() }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            content()
        }
    }
}

/**
 * Слушает глобальный поток ошибок (DuckSoundPlayer.errors и др.) и показывает Snackbar.
 */
@Composable
fun ErrorCollector(
    errors: SharedFlow<DuckError>,
    snackbarHostState: SnackbarHostState
) {
    LaunchedEffect(errors) {
        errors.collectLatest { error ->
            val text = when {
                error.code.name == "E_REC_002" -> "Микрофон занят. Попробуйте позже"
                else -> error.message ?: "Что-то пошло не так (${error.code.name})"
            }
            snackbarHostState.showSnackbar(text)
        }
    }
}