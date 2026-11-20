package com.postpole.wheelfile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.postpole.wheelfile.ui.garage.GarageScreen
import com.postpole.wheelfile.ui.pro.ProScreen
import com.postpole.wheelfile.ui.settings.SettingsScreen

object Routes {
    const val GARAGE = "garage"
    const val SETTINGS = "settings"
    const val PRO = "pro"
}

@Composable
fun AppNavHost(onScreen: (String) -> Unit = {}) {
    val navController = rememberNavController()
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let(onScreen)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    NavHost(navController = navController, startDestination = Routes.GARAGE) {
        composable(Routes.GARAGE) {
            GarageScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPro = { navController.navigate(Routes.PRO) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPro = { navController.navigate(Routes.PRO) },
            )
        }
        composable(Routes.PRO) {
            ProScreen(onBack = { navController.popBackStack() })
        }
    }
}
