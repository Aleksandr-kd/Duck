package com.duck.app.ui.color

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duck.app.R
import com.duck.app.ui.components.DuckAssetImage
import com.duck.app.ui.components.DuckHeaderScreen
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorScreen(
    target: ColorTarget,
    onBack: () -> Unit,
    viewModel: ColorViewModel = hiltViewModel()
) {
    val currentColor by viewModel.currentColor.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.configure(target) }
    LaunchedEffect(currentColor) { viewModel.initPicker(currentColor) }

    val previewHex = picker.colorHex
    val previewColor = hexToColor(previewHex)

    DuckHeaderScreen(
        title = stringResource(
            if (target == ColorTarget.DUCK) R.string.color_title else R.string.color_title_background
        ),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (target == ColorTarget.DUCK) {
                DuckAssetImage(
                    assetPath = "design/duck_cutout.png",
                    colorHex = previewHex,
                    contentDescription = stringResource(R.string.duck_content_description),
                    modifier = Modifier.size(200.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(color = previewColor, shape = CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ColorWheel(
                currentHue = picker.hue,
                currentSaturation = picker.saturation,
                onPicked = { hue, sat -> viewModel.onWheelPicked(hue, sat) },
                modifier = Modifier.size(240.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.color_wheel_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color = previewColor, shape = CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = previewHex,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.color_picker_lightness),
                style = MaterialTheme.typography.labelLarge
            )
            ToneSlider(
                hue = picker.hue,
                saturation = picker.saturation,
                tone = picker.tone,
                onToneChanged = { viewModel.onToneChanged(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DuckColorsPresets(viewModel = viewModel, currentHex = previewHex)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun pickHueSat(position: Offset, size: IntSize): Pair<Float, Float> {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = position.x - cx
    val dy = position.y - cy
    val radius = min(cx, cy)
    val dist = sqrt(dx * dx + dy * dy)
    val hue = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
    val sat = (dist / radius).coerceIn(0f, 1f)
    return hue to sat
}

/** Цветовое колесо: hue по углу, насыщенность по радиусу. Тап и перетаскивание. */
@Composable
private fun ColorWheel(
    currentHue: Float,
    currentSaturation: Float,
    onPicked: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier
            .pointerInput(currentHue) {
                detectTapGestures(onTap = { pos ->
                    val (h, s) = pickHueSat(pos, size)
                    onPicked(h, s)
                })
            }
            .pointerInput(currentHue) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val (h, s) = pickHueSat(pos, size)
                        onPicked(h, s)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val (h, s) = pickHueSat(change.position, size)
                        onPicked(h, s)
                    }
                )
            }
    ) {
        val radius = min(size.width, size.height) / 2f
        val rainbow = listOf(
            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
        )
        drawCircle(brush = Brush.sweepGradient(rainbow), radius = radius, center = center)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = outlineColor,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Маркер текущей позиции.
        val markerRadius = (radius * currentSaturation).coerceAtMost(radius - 4f)
        val angle = currentHue * PI.toFloat() / 180f
        val markerPos = Offset(
            x = center.x + cos(angle) * markerRadius,
            y = center.y + sin(angle) * markerRadius
        )
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx() / 2f,
            center = markerPos,
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(color = Color.Black, radius = 10.dp.toPx() / 2f, center = markerPos)
        drawCircle(color = Color.White, radius = 6.dp.toPx() / 2f, center = markerPos)
    }
}

@Composable
private fun DuckColorsPresets(viewModel: ColorViewModel, currentHex: String) {
    com.duck.app.domain.model.DuckColors.PRESETS.forEach { preset ->
        val c = hexToColor(preset.hex)
        val isActive = currentHex.equals(preset.hex, ignoreCase = true)
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color = c, shape = CircleShape)
                .border(
                    width = if (isActive) 3.dp else 1.dp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .clickable { viewModel.selectPreset(preset.hex) }
        )
    }
}

/** Ползунок «яркий → пастельный»: градиентный трек от насыщенного к почти белому. */
@Composable
private fun ToneSlider(
    hue: Float,
    saturation: Float,
    tone: Float,
    onToneChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val start = hexToColor(com.duck.app.domain.model.DuckColors.hslToHex(hue, saturation, 0.5f))
    val mid = hexToColor(com.duck.app.domain.model.DuckColors.hslToHex(hue, saturation, 0.78f))
    val end = hexToColor(com.duck.app.domain.model.DuckColors.hslToHex(hue, saturation, 0.97f))
    val thumbColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .height(40.dp)
            .pointerInput(hue, saturation) {
                detectTapGestures(onTap = { pos ->
                    onToneChanged(pos.x / size.width.toFloat())
                })
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        onToneChanged(change.position.x / size.width.toFloat())
                    }
                )
            }
    ) {
        val trackHeight = 12.dp.toPx()
        val radius = trackHeight / 2f
        val top = (size.height - trackHeight) / 2f

        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(start, mid, end)),
            cornerRadius = CornerRadius(radius, radius),
            size = Size(size.width, trackHeight),
            topLeft = Offset(0f, top)
        )

        val thumbRadius = 10.dp.toPx()
        val thumbX = tone.coerceIn(0f, 1f) * size.width
        val thumbY = size.height / 2f
        drawCircle(color = Color.White, radius = thumbRadius + 2.dp.toPx(), center = Offset(thumbX, thumbY))
        drawCircle(color = thumbColor, radius = thumbRadius, center = Offset(thumbX, thumbY))
    }
}

private fun hexToColor(hex: String): Color {
    val v = hex.removePrefix("#").toLong(16) or 0xFF000000L
    return Color(v)
}