package com.duck.app.analytics

import android.app.Application
import com.duck.app.BuildConfig
import com.duck.app.core.DuckLogger
import com.my.tracker.MyTracker
import com.my.tracker.MyTrackerConfig

/**
 * Инициализация MyTracker SDK (док: https://docs.tracker.my.com/ru/sdk/android/api#setup).
 *
 * SDK_KEY берётся из BuildConfig.MYTRACKER_SDK_KEY, который заполняется из
 * `local.properties` (или переменной окружения MYTRACKER_SDK_KEY) на этапе сборки.
 * Если ключ не задан — трекер не инициализируется и приложение работает как обычно.
 */
object MyTrackerInitializer {

    private const val TAG = "MyTracker"

    fun init(application: Application) {
        val sdkKey = BuildConfig.MYTRACKER_SDK_KEY
        if (sdkKey.isBlank()) {
            DuckLogger.w(TAG, "MYTRACKER_SDK_KEY is not set; MyTracker is disabled")
            return
        }

        runCatching {
            MyTracker.setDebugMode(BuildConfig.DEBUG)

            MyTracker.getTrackerConfig()
                .setTrackingLaunchEnabled(true)
                .setLaunchTimeout(30)
                .setBufferingPeriod(900)
                .setForcingPeriod(60)
                .setAutotrackingPurchaseEnabled(false)
                .setKidMode(false)
                .setLocationTrackingMode(MyTrackerConfig.LocationTrackingMode.NONE)

            MyTracker.initTracker(sdkKey, application)
            DuckLogger.i(TAG, "MyTracker initialized")
        }.onFailure { e ->
            DuckLogger.errorEvent("E_ANALYTICS_001", "MyTracker init failed", e)
        }
    }
}