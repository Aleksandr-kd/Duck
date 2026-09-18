package com.duck.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.duck.app.core.DuckLogger
import com.duck.app.domain.model.DuckColors
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Перекраска растровой утки (ТЗ §2.4/E-L1, §8). Механизм «HSL-remap»:
 * hue/pixel пикселя заменяются на целевые, а S/L масштабируются/сдвигаются
 * относительно референсных значений тела утки (DuckColors.DEFAULT_HEX).
 * Кэш по hex (один кадр на цвет).
 */
object DuckBitmapTinter {

    private val cache = ConcurrentHashMap<String, Bitmap>()

    /** Референсные S/L тела утки (DEFAULT_HEX). Вычисляются один раз. */
    private val bodyRef: Triple<Float, Float, Float> = run {
        val (h, s, l) = DuckColors.hexToHsl(DuckColors.DEFAULT_HEX).toList()
        Triple(h, s, l)
    }

    fun tint(assetPath: String, targetHex: String, context: Context, maxSize: Int = 0): Bitmap? {
        val key = "$assetPath|${DuckColors.normalizeHex(targetHex)}"
        cache[key]?.let { return it }

        val source = try {
            BitmapFactory.decodeStream(context.assets.open(assetPath))
        } catch (e: Exception) {
            DuckLogger.errorEvent("E_ANIM_001", "bitmap load failed: $assetPath", e)
            return null
        } ?: return null

        val target = DuckColors.hexToHsl(targetHex)
        val targetHue = target[0]
        val targetSat = target[1]
        val targetLight = target[2]
        val (_, bodySat, bodyLight) = bodyRef

        val remapped = if (source.isRecycled) source
        else remapToHsl(source, targetHue, targetSat, targetLight, bodySat, bodyLight)
        if (source !== remapped) source.recycle()

        val result = if (maxSize > 0 && (remapped.width > maxSize || remapped.height > maxSize)) {
            val scale = maxSize.toFloat() / maxOf(remapped.width, remapped.height)
            Bitmap.createScaledBitmap(
                remapped,
                (remapped.width * scale).toInt().coerceAtLeast(1),
                (remapped.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else remapped

        cache[key] = result
        return result
    }

    /**
     * По-пиксельный HSL-remap:
     *  — hue заменяется на [targetHue];
     *  — S масштабируется: s' = s * (targetSat / bodySat);
     *  — L сдвигается:    l' = clamp(l + (targetLight − bodyLight)).
     * Ахроматичные пиксели (глаза, блики, тени) не трогаются.
     */
    private fun remapToHsl(
        source: Bitmap,
        targetHue: Float,
        targetSat: Float,
        targetLight: Float,
        bodySat: Float,
        bodyLight: Float
    ): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val lDelta = targetLight - bodyLight
        val sRatio = if (bodySat > 0.01f) targetSat / bodySat else 1f

        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            if (a == 0) continue
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val l = (maxC + minC) / 2f
            val d = maxC - minC
            val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))

            if (s < 0.04f) continue

            val newS = (s * sRatio).coerceIn(0f, 1f)
            val newL = (l + lDelta).coerceIn(0f, 1f)

            val h2 = targetHue / 60f
            val c = (1f - abs(2f * newL - 1f)) * newS
            val x = c * (1f - abs(h2 % 2f - 1f))
            val m = newL - c / 2f
            val (r1, g1, b1) = when {
                h2 < 1f -> Triple(c, x, 0f)
                h2 < 2f -> Triple(x, c, 0f)
                h2 < 3f -> Triple(0f, c, x)
                h2 < 4f -> Triple(0f, x, c)
                h2 < 5f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            val nr = (((r1 + m) * 255f) + 0.5f).toInt().coerceIn(0, 255)
            val ng = (((g1 + m) * 255f) + 0.5f).toInt().coerceIn(0, 255)
            val nb = (((b1 + m) * 255f) + 0.5f).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }

        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}