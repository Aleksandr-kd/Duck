package com.duck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duck.app.ui.main.MainViewModel
import com.duck.app.ui.navigation.AppNavHost
import com.duck.app.ui.theme.DuckTheme
import com.duck.app.update.SilentUpdateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var silentUpdateManager: SilentUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DuckRoot(viewModel = viewModel)
        }
        silentUpdateManager.checkForUpdateSilently()
    }
}

@Composable
private fun DuckRoot(viewModel: MainViewModel) {
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = false)

    DuckTheme(isDark = isDark) {
        AppNavHost(isOnboardingCompleted = isOnboardingCompleted)
    }
}