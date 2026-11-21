package com.postpole.wheelfile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.postpole.wheelfile.BuildConfig
import com.postpole.wheelfile.ui.entry.EntryFormRoute
import com.postpole.wheelfile.ui.garage.GarageActions
import com.postpole.wheelfile.ui.garage.GarageRoute
import com.postpole.wheelfile.ui.pro.ProScreen
import com.postpole.wheelfile.ui.settings.AdPreviewScreen
import com.postpole.wheelfile.ui.settings.SettingsScreen
import com.postpole.wheelfile.ui.vehicle.AddVehicleRoute
import com.postpole.wheelfile.ui.vehicle.VehicleDetailRoute
import com.postpole.wheelfile.ui.vehicle.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val GARAGE = "garage"
    const val ADD_VEHICLE = "add_vehicle"
    const val VEHICLE = "vehicle/{vehicleId}"
    const val ENTRY = "entry/{vehicleId}/{charge}"
    const val SETTINGS = "settings"
    const val PRO = "pro"
    const val AD_PREVIEW = "debug_ad_preview"

    fun vehicle(id: Long) = "vehicle/$id"
    fun entry(vehicleId: Long, charge: Boolean) = "entry/$vehicleId/$charge"
}

@Composable
fun AppNavHost(onScreen: (String) -> Unit = {}, startViewModel: StartViewModel = hiltViewModel()) {
    // The first frame waits for the stored state so a returning user never sees the Welcome screen flash by.
    val start by startViewModel.startRoute.collectAsStateWithLifecycle()
    val route = start ?: return
    val navController = rememberNavController()
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let(onScreen)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    NavHost(navController = navController, startDestination = route) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) })
        }
        composable(Routes.GARAGE) {
            GarageRoute(
                actions = GarageActions(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPro = { navController.navigate(Routes.PRO) },
                    onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) },
                    onOpenVehicle = { navController.navigate(Routes.vehicle(it)) },
                    onAddEntry = { id, charge -> navController.navigate(Routes.entry(id, charge)) },
                    onSelect = {},
                ),
            )
        }
        composable(Routes.ADD_VEHICLE) {
            AddVehicleRoute(
                onClose = { navController.popBackStack() },
                onSaved = {
                    // Start over at the Garage so Back never returns to the Welcome or the form.
                    navController.navigate(Routes.GARAGE) { popUpTo(navController.graph.id) { inclusive = true } }
                },
                onOpenPro = { navController.navigate(Routes.PRO) },
            )
        }
        composable(
            Routes.VEHICLE,
            arguments = listOf(navArgument("vehicleId") { type = NavType.LongType }),
        ) {
            VehicleDetailRoute(
                onBack = { navController.popBackStack() },
                onSwitch = { navController.navigate(Routes.vehicle(it)) { popUpTo(Routes.GARAGE) } },
                onAddEntry = { id, charge -> navController.navigate(Routes.entry(id, charge)) },
                onOpenPro = { navController.navigate(Routes.PRO) },
            )
        }
        composable(
            Routes.ENTRY,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("charge") { type = NavType.BoolType },
            ),
        ) {
            EntryFormRoute(onClose = { navController.popBackStack() })
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
