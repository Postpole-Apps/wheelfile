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
import com.postpole.wheelfile.ui.document.DocumentDetailRoute
import com.postpole.wheelfile.ui.document.DocumentFormRoute
import com.postpole.wheelfile.ui.document.PresetsRoute
import com.postpole.wheelfile.ui.entry.EntryFormRoute
import com.postpole.wheelfile.ui.entry.RecordFormRoute
import com.postpole.wheelfile.ui.reminder.ReminderFormRoute
import com.postpole.wheelfile.ui.reminder.RemindersRoute
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
    const val RECORD = "record/{vehicleId}/{kind}"
    const val REMINDERS = "reminders?vehicleId={vehicleId}"
    const val REMINDER_FORM = "reminder/{vehicleId}"
    const val DOCUMENT = "document/{vehicleId}/{documentId}"
    const val DOCUMENT_FORM = "document_form/{vehicleId}/{documentId}"
    const val PRESETS = "presets/{vehicleId}/{onboarding}"
    const val SETTINGS = "settings"
    const val PRO = "pro"
    const val AD_PREVIEW = "debug_ad_preview"

    fun vehicle(id: Long) = "vehicle/$id"
    fun entry(vehicleId: Long, charge: Boolean) = "entry/$vehicleId/$charge"
    fun record(vehicleId: Long, expense: Boolean) = "record/$vehicleId/${if (expense) "expense" else "service"}"

    /** Reminders of one vehicle, or of all vehicles when [vehicleId] is null (Settings). */
    fun reminders(vehicleId: Long? = null) = "reminders?vehicleId=${vehicleId ?: -1L}"
    fun reminderForm(vehicleId: Long) = "reminder/$vehicleId"
    fun document(vehicleId: Long, documentId: Long) = "document/$vehicleId/$documentId"

    /** [documentId] 0 opens the form for a new document. */
    fun documentForm(vehicleId: Long, documentId: Long = 0L) = "document_form/$vehicleId/$documentId"

    /** The region presets offer: onboarding step 3 when [onboarding], otherwise opened from the Documents tab. */
    fun presets(vehicleId: Long, onboarding: Boolean = false) = "presets/$vehicleId/$onboarding"
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
                onSaved = { vehicleId, firstVehicle ->
                    // Start over so Back never returns to the Welcome or the form. The first vehicle continues with
                    // onboarding step 3 (region presets); later vehicles go straight to the Garage.
                    val next = if (firstVehicle) Routes.presets(vehicleId, onboarding = true) else Routes.GARAGE
                    navController.navigate(next) { popUpTo(navController.graph.id) { inclusive = true } }
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
                onAddRecord = { id, expense -> navController.navigate(Routes.record(id, expense)) },
                onOpenReminders = { navController.navigate(Routes.reminders(it)) },
                onOpenPro = { navController.navigate(Routes.PRO) },
                onOpenDocument = { vehicleId, documentId -> navController.navigate(Routes.document(vehicleId, documentId)) },
                onAddDocument = { navController.navigate(Routes.documentForm(it)) },
                onOpenPresets = { navController.navigate(Routes.presets(it)) },
            )
        }
        composable(
            Routes.DOCUMENT,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("documentId") { type = NavType.LongType },
            ),
        ) {
            DocumentDetailRoute(
                onBack = { navController.popBackStack() },
                onEdit = { vehicleId, documentId -> navController.navigate(Routes.documentForm(vehicleId, documentId)) },
            )
        }
        composable(
            Routes.DOCUMENT_FORM,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("documentId") { type = NavType.LongType },
            ),
        ) {
            DocumentFormRoute(onClose = { navController.popBackStack() }, onOpenPro = { navController.navigate(Routes.PRO) })
        }
        composable(
            Routes.PRESETS,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("onboarding") { type = NavType.BoolType },
            ),
        ) { entry ->
            val onboarding = entry.arguments?.getBoolean("onboarding") == true
            PresetsRoute(
                onboarding = onboarding,
                onDone = {
                    if (onboarding) {
                        // Onboarding ends at the Garage; Back must not return to the offer.
                        navController.navigate(Routes.GARAGE) { popUpTo(navController.graph.id) { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(
            Routes.RECORD,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("kind") { type = NavType.StringType },
            ),
        ) {
            RecordFormRoute(onClose = { navController.popBackStack() }, onOpenPro = { navController.navigate(Routes.PRO) })
        }
        composable(
            Routes.REMINDERS,
            arguments = listOf(navArgument("vehicleId") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            RemindersRoute(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Routes.reminderForm(it)) },
            )
        }
        composable(
            Routes.REMINDER_FORM,
            arguments = listOf(navArgument("vehicleId") { type = NavType.LongType }),
        ) {
            ReminderFormRoute(onClose = { navController.popBackStack() })
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
                onOpenReminders = { navController.navigate(Routes.reminders()) },
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
