package com.duck.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import com.duck.app.DuckApplication
import com.duck.app.data.datastore.duckDataStore
import com.duck.app.domain.model.DuckColors
import com.duck.app.domain.player.TapResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Виджет «Утка Debug» (US-05, ТЗ §6): 2×2 / 4×2 / 4×3.
 * Тап по утке — QUACK (debounce 300 мс внутри DuckSoundPlayer),
 * перекраска кадра — HSL-remap. Утка заполняет виджет и растёт вместе с ним.
 */
class DuckGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = readWidgetSettings(context)
        val bitmap = withContext(Dispatchers.IO) {
            DuckBitmapTinter.tint(
                assetPath = "design/widget_base.png",
                targetHex = settings.colorHex,
                context = context,
                maxSize = 512
            )
        }
        val image = bitmap?.let { ImageProvider(it) }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(widgetBackgroundColor(settings.backgroundColorHex)))
                    .clickable(onClick = actionRunCallback<QuackCallback>()),
                contentAlignment = Alignment.Center
            ) {
                if (image != null) {
                    Image(
                        provider = image,
                        contentDescription = settings.activeName,
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

class DuckWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuckGlanceWidget()
}

class QuackCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext as? DuckApplication ?: return
        runCatching {
            val result = app.soundPlayer.onDuckTap()
            if (result != TapResult.Ignored) {
                app.settingsRepository.touchDailyCounter()
            }
        }
    }
}

private suspend fun readWidgetSettings(context: Context): WidgetSettings {
    return runCatching {
        val prefs = context.applicationContext.duckDataStore.data.first()
        val color = prefs[PrefKey.duckColor]?.let {
            if (DuckColors.isValidHex(it)) DuckColors.normalizeHex(it) else null
        } ?: DuckColors.DEFAULT_HEX
        val background = prefs[PrefKey.widgetBackgroundColor]?.let {
            if (DuckColors.isValidHex(it)) DuckColors.normalizeHex(it) else null
        } ?: com.duck.app.domain.DuckConstants.WIDGET_BACKGROUND_DEFAULT_HEX
        WidgetSettings(
            colorHex = color,
            backgroundColorHex = background,
            activeName = prefs[PrefKey.activeQuackId] ?: "Кряк-классика"
        )
    }.getOrElse {
        WidgetSettings(
            DuckColors.DEFAULT_HEX,
            com.duck.app.domain.DuckConstants.WIDGET_BACKGROUND_DEFAULT_HEX,
            "Кряк-классика"
        )
    }
}

/** Преобразует #RRGGBB в цвет для Glance. */
private fun widgetBackgroundColor(hex: String): Color {
    return Color((0xFF000000L or hex.removePrefix("#").toLong(16)).toInt())
}

private object PrefKey {
    val duckColor = stringPreferencesKey("duck_color")
    val widgetBackgroundColor = stringPreferencesKey("widget_background_color")
    val activeQuackId = stringPreferencesKey("active_quack_id")
}

private data class WidgetSettings(val colorHex: String, val backgroundColorHex: String, val activeName: String)

/** Синхронизация настроек в виджет (после изменения цвета/пресета/темы). */
object WidgetSyncer {
    suspend fun refresh(context: Context) {
        runCatching { DuckGlanceWidget().updateAll(context) }
    }
}