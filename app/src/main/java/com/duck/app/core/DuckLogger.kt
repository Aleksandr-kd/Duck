package com.duck.app.core

import android.util.Log
import timber.log.Timber

/**
 * Единая обёртка логирования (ТЗ §5.5). Tag "DuckApp", компоненты — через под-теги.
 */
object DuckLogger {
    private const val BASE_TAG = "DuckApp"

    fun tag(component: String): String = "$BASE_TAG:$component"

    fun d(tag: String?, message: String, vararg args: Any?) {
        if (BuildFlags.DEBUG) Timber.tag(tag ?: BASE_TAG).d(message, *args)
    }

    fun i(tag: String?, message: String, vararg args: Any?) =
        Timber.tag(tag ?: BASE_TAG).i(message, *args)

    fun w(tag: String?, message: String, vararg args: Any?) =
        Timber.tag(tag ?: BASE_TAG).w(message, *args)

    fun e(tag: String?, throwable: Throwable?, message: String, vararg args: Any?) =
        Timber.tag(tag ?: BASE_TAG).e(throwable, message, *args)

    /** Катастрофическая ошибка с кодовым маркером из §2.8 (E_AUDIO_001 и т.п.). */
    fun errorEvent(code: String, message: String, throwable: Throwable? = null) {
        Timber.tag(BASE_TAG).e(throwable, "[$code] $message")
    }
}

object BuildFlags { var DEBUG: Boolean = true }