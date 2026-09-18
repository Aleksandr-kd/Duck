package com.duck.app.update

import android.content.Context
import com.duck.app.core.DuckLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.RuStoreAppUpdateManager
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.appupdate.model.InstallState
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Тихое обновление приложения через RuStore In-app updates SDK (US-11).
 *
 * Сценарий SILENT не показывает UI RuStore: фоновая проверка обновления при
 * запуске, загрузка и установка без диалогов (интерфейс на нашей стороне).
 * Условия работы: актуальный RuStore, авторизация, доступность UPDATE_AVAILABLE.
 */
@Singleton
class SilentUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val updateManager: RuStoreAppUpdateManager by lazy {
        RuStoreAppUpdateManagerFactory.create(context)
    }

    private val listener = object : InstallStateUpdateListener {
        override fun onStateUpdated(state: InstallState) {
            when (state.installStatus) {
                InstallStatus.DOWNLOADED -> {
                    DuckLogger.i(TAG, "update downloaded, completing silent install")
                    launchSilentInstall()
                }

                InstallStatus.DOWNLOADING -> {
                    DuckLogger.d(
                        TAG,
                        "update downloading: %d/%d bytes",
                        state.bytesDownloaded,
                        state.totalBytesToDownload
                    )
                }

                InstallStatus.FAILED -> {
                    DuckLogger.e(TAG, null, "update download failed (code %d)", state.installErrorCode)
                }

                InstallStatus.DOWNLOAD_INTERRUPTED -> {
                    DuckLogger.w(TAG, "update download interrupted; will not auto-restart")
                }
            }
        }
    }

    /** Проверяет наличие обновления и при доступности запускает тихую загрузку/установку. */
    fun checkForUpdateSilently() {
        updateManager
            .getAppUpdateInfo()
            .addOnSuccessListener { info ->
                when (info.updateAvailability) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        DuckLogger.i(TAG, "update available (%s), starting silent flow", info.availableVersionName)
                        updateManager.registerListener(listener)
                        updateManager
                            .startUpdateFlow(
                                info,
                                AppUpdateOptions.Builder()
                                    .appUpdateType(AppUpdateType.SILENT)
                                    .build()
                            )
                            .addOnFailureListener { error ->
                                DuckLogger.e(TAG, error, "silent update start failed")
                            }
                    }

                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        if (info.installStatus == InstallStatus.DOWNLOADED) {
                            DuckLogger.i(TAG, "update already downloaded, completing install")
                            launchSilentInstall()
                        }
                    }

                    else -> DuckLogger.d(TAG, "no update available")
                }
            }
            .addOnFailureListener { error ->
                DuckLogger.e(TAG, error, "getAppUpdateInfo failed")
            }
    }

    private fun launchSilentInstall() {
        updateManager
            .completeUpdate(
                AppUpdateOptions.Builder()
                    .appUpdateType(AppUpdateType.SILENT)
                    .build()
            )
            .addOnFailureListener { error ->
                DuckLogger.e(TAG, error, "silent install failed")
            }
    }

    private companion object {
        const val TAG = "SilentUpdate"
    }
}