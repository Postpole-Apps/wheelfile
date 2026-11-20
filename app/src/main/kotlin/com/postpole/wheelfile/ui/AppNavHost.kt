package com.postpole.wheelfile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.postpole.wheelfile.BuildConfig
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.ui.energy.EnergyFormScreen
import com.postpole.wheelfile.ui.garage.GarageScreen
import com.postpole.wheelfile.ui.onboarding.WelcomeScreen
import com.postpole.wheelfile.ui.pro.ProScreen
import com.postpole.wheelfile.ui.settings.AdPreviewScreen
import com.postpole.wheelfile.ui.settings.SettingsScreen
import com.postpole.wheelfile.ui.vehicle.AddVehicleScreen
import com.postpole.wheelfile.ui.vehicle.VehicleDetailScreen

object Routes {
    const val WELCOME = "welcome"
    const val GARAGE = "garage"
    const val SETTINGS = "settings"
    const val PRO = "pro"
    const val AD_PREVIEW = "debug_ad_preview"

    const val ADD_VEHICLE = "vehicle/add?onboarding={onboarding}"
    const val VEHICLE = "vehicle/{vehicleId}?tab={tab}"
    const val ENERGY = "energy/{vehicleId}/{stream}"

    fun addVehicle(onboarding: Boolean) = "vehicle/add?onboarding=$onboarding"
    fun vehicle(id: Long, tab: Int = 0) = "vehicle/$id?tab=$tab"
    fun energy(vehicleId: Long, stream: EnergyStream) = "energy/$vehicleId/${stream.name}"
}

@Composable
fun AppNavHost(onScreen: (String) -> Unit = {}, startViewModel: StartViewModel = hiltViewModel()) {
    val start by startViewModel.startRoute.collectAsStateWithLifecycle()
    // Wait for the first read so the right first screen shows with no flash of the other one.
    val route = start ?: return
    val startRoute = remember { route }
    val navController = rememberNavController()
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let(onScreen)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onAddVehicle = { navController.navigate(Routes.addVehicle(onboarding = true)) })
        }
        composable(Routes.GARAGE) {
            GarageScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPro = { navController.navigate(Routes.PRO) },
                onAddVehicle = { navController.navigate(Routes.addVehicle(onboarding = false)) },
                onOpenVehicle = { id, tab -> navController.navigate(Routes.vehicle(id, tab)) },
                onAddEnergy = { id, stream -> navController.navigate(Routes.energy(id, stream)) },
            )
        }
        composable(
            Routes.ADD_VEHICLE,
            arguments = listOf(navArgument("onboarding") { type = NavType.BoolType; defaultValue = false }),
        ) {
            AddVehicleScreen(
                onClose = { navController.popBackStack() },
                onSaved = { _, onboarding ->
                    if (onboarding) {
                        navController.navigate(Routes.GARAGE) { popUpTo(Routes.WELCOME) { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
                onLimitReached = { navController.popBackStack() },
            )
        }
        composable(
            Routes.VEHICLE,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            VehicleDetailScreen(
                initialTab = entry.arguments?.getInt("tab") ?: 0,
                onBack = { navController.popBackStack() },
                onAddEnergy = { id, stream -> navController.navigate(Routes.energy(id, stream)) },
                onSwitchVehicle = { id ->
                    navController.navigate(Routes.vehicle(id)) { popUpTo(Routes.GARAGE) }
                },
            )
        }
        composable(
            Routes.ENERGY,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("stream") { type = NavType.StringType },
            ),
        ) {
            EnergyFormScreen(
                onClose = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPro = { navController.navigate(Routes.PRO) },
                onOpenAdPreview = { navController.navigate(Routes.AD_PREVIEW) },
            )
        }
        if (BuildConfig.DEBUG) {
            composable(Routes.AD_PREVIEW) { AdPreviewScreen(onBack = { navController.popBackStack() }) }
        }
        composable(Routes.PRO) {
            ProScreen(onBack = { navController.popBackStack() })
        }
    }
}
