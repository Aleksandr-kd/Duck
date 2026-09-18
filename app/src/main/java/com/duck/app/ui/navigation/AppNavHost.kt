package com.duck.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.duck.app.ui.about.AboutScreen
import com.duck.app.ui.color.ColorScreen
import com.duck.app.ui.color.ColorTarget
import com.duck.app.ui.main.DuckScreen
import com.duck.app.ui.onboarding.OnboardingScreen
import com.duck.app.ui.quacks.QuacksScreen
import com.duck.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Main : Screen("main")
    data object Settings : Screen("settings")
    data object Quacks : Screen("settings/quacks")
    data object Color : Screen("settings/color")
    data object WidgetColor : Screen("settings/color/widget")
    data object About : Screen("about")
}

@Composable
fun AppNavHost(
    isOnboardingCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val startDest = if (isOnboardingCompleted) Screen.Main.route else Screen.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = startDest,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(animationSpec = tween(120)) + fadeIn(animationSpec = tween(120))
        },
        exitTransition = { fadeOut(animationSpec = tween(120)) },
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(120), initialOffsetX = { -it }) + fadeIn(animationSpec = tween(120))
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(120), targetOffsetX = { it }) + fadeOut(animationSpec = tween(120))
        }
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Main.route) {
            DuckScreen(
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onQuacks = { navController.navigate(Screen.Quacks.route) },
                onColor = { navController.navigate(Screen.Color.route) },
                onWidgetColor = { navController.navigate(Screen.WidgetColor.route) },
                onAbout = { navController.navigate(Screen.About.route) }
            )
        }
        composable(Screen.Quacks.route) {
            QuacksScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Color.route) {
            ColorScreen(target = ColorTarget.DUCK, onBack = { navController.popBackStack() })
        }
        composable(Screen.WidgetColor.route) {
            ColorScreen(target = ColorTarget.WIDGET_BACKGROUND, onBack = { navController.popBackStack() })
        }
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}