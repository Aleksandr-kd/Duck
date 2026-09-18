package com.duck.app

import android.app.Application
import com.duck.app.analytics.MyTrackerInitializer
import com.duck.app.domain.player.DuckSoundPlayer
import com.duck.app.domain.repository.DuckSettingsRepository
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint

@HiltAndroidApp
class DuckApplication : Application() {

    lateinit var soundPlayer: DuckSoundPlayer
        private set
    lateinit var settingsRepository: DuckSettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        MyTrackerInitializer.init(this)
        val graph = EntryPointAccessors.fromApplication(this, DuckGraph::class.java)
        soundPlayer = graph.soundPlayer()
        settingsRepository = graph.settingsRepository()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DuckGraph {
        fun soundPlayer(): DuckSoundPlayer
        fun settingsRepository(): DuckSettingsRepository
    }
}