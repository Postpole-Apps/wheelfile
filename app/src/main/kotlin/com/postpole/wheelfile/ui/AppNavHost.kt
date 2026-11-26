package com.postpole.wheelfile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.postpole.wheelfile.ui.adaptive.GarageListDetail
import com.postpole.wheelfile.ui.adaptive.isListDetailWidth
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
import com.postpole.wheelfile.ui.entry.EntryFormRoute
import com.postpole.wheelfile.ui.entry.RecordFormRoute
import com.postpole.wheelfile.ui.reminder.ReminderFormRoute
import com.postpole.wheelfile.ui.reminder.RemindersRoute
import com.postpole.wheelfile.ui.garage.GarageActions
import com.postpole.wheelfile.ui.garage.GarageRoute
import com.postpole.wheelfile.ui.importer.ImportRoute
import com.postpole.wheelfile.ui.pro.PaywallTrigger
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
    const val DOCUMENT = "document/{documentId}"
    const val DOCUMENT_FORM = "document_form/{vehicleId}?documentId={documentId}"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val PRO = "pro?trigger={trigger}"
    const val AD_PREVIEW = "debug_ad_preview"

    fun pro(trigger: PaywallTrigger) = "pro?trigger=${trigger.analyticsValue}"
    fun vehicle(id: Long) = "vehicle/$id"
    fun entry(vehicleId: Long, charge: Boolean) = "entry/$vehicleId/$charge"
    fun record(vehicleId: Long, expense: Boolean) = "record/$vehicleId/${if (expense) "expense" else "service"}"

    /** Reminders of one vehicle, or of all vehicles when [vehicleId] is null (Settings). */
    fun reminders(vehicleId: Long? = null) = "reminders?vehicleId=${vehicleId ?: -1L}"
    fun reminderForm(vehicleId: Long) = "reminder/$vehicleId"
    fun document(id: Long) = "document/$id"

    /** The document form: a new document for [vehicleId], or [documentId] to edit. */
    fun documentForm(vehicleId: Long, documentId: Long? = null) = "document_form/$vehicleId?documentId=${documentId ?: -1L}"
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
            WelcomeScreen(
                onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) },
                onImport = { navController.navigate(Routes.IMPORT) },
            )
        }
        composable(Routes.GARAGE) {
            val actions = GarageActions(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPro = { navController.navigate(Routes.pro(PaywallTrigger.GARAGE)) },
                onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) },
                onOpenVehicle = { navController.navigate(Routes.vehicle(it)) },
                onAddEntry = { id, charge -> navController.navigate(Routes.entry(id, charge)) },
                onSelect = {},
                onOpenPaywall = { navController.navigate(Routes.pro(it)) },
            )
            if (!isListDetailWidth()) {
                GarageRoute(actions = actions)
            } else {
                // Medium and expanded widths and unfolded foldables: Garage | Vehicle side by side (spec §3.2).
                var openVehicle by rememberSaveable { mutableStateOf<Long?>(null) }
                GarageListDetail(
                    selectedVehicleId = openVehicle,
                    listPane = {
                        GarageRoute(
                            actions = GarageActions(
                                actions.onOpenSettings, actions.onOpenPro, actions.onAddVehicle,
                                onOpenVehicle = { openVehicle = it },
                                onAddEntry = actions.onAddEntry,
                                onSelect = actions.onSelect,
                                onOpenPaywall = actions.onOpenPaywall,
                            ),
                            // The open vehicle's tab carries its own banner; never two banners on one screen.
                            showBanner = openVehicle == null,
                        )
                    },
                    detailPane = { id ->
                        VehiclePane(id, navController, onClose = { openVehicle = null }, onSwitch = { openVehicle = it })
                    },
                )
            }
        }
        composable(Routes.ADD_VEHICLE) {
            AddVehicleRoute(
                onClose = { navController.popBackStack() },
                onSaved = {
                    // Start over at the Garage so Back never returns to the Welcome or the form.
                    navController.navigate(Routes.GARAGE) { popUpTo(navController.graph.id) { inclusive = true } }
                },
                onOpenPro = { navController.navigate(Routes.pro(PaywallTrigger.VEHICLE_LIMIT)) },
            )
        }
        composable(
            Routes.VEHICLE,
            arguments = listOf(navArgument("vehicleId") { type = NavType.LongType }),
        ) {
            VehicleDetail(
                navController,
                onBack = { navController.popBackStack() },
                onSwitch = { navController.navigate(Routes.vehicle(it)) { popUpTo(Routes.GARAGE) } },
            )
        }
        composable(
            Routes.DOCUMENT,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
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
                navArgument("documentId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            DocumentFormRoute(
                onClose = { navController.popBackStack() },
                onOpenPro = { navController.navigate(Routes.pro(PaywallTrigger.ATTACHMENT_LIMIT)) },
            )
        }
        composable(
            Routes.RECORD,
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("kind") { type = NavType.StringType },
            ),
        ) {
            RecordFormRoute(
                onClose = { navController.popBackStack() },
                onOpenPro = { navController.navigate(Routes.pro(PaywallTrigger.ATTACHMENT_LIMIT)) },
            )
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
                onOpenPro = { navController.navigate(Routes.pro(PaywallTrigger.SETTINGS)) },
                onOpenReminders = { navController.navigate(Routes.reminders()) },
                onOpenAdPreview = { navController.navigate(Routes.AD_PREVIEW) },
                onOpenImport = { navController.navigate(Routes.IMPORT) },
                // Everything is gone: back to the first-run Welcome screen with an empty back stack.
                onDataDeleted = {
                    navController.navigate(Routes.WELCOME) { popUpTo(navController.graph.id) { inclusive = true } }
                },
            )
        }
        composable(Routes.IMPORT) {
            ImportRoute(
                onBack = { navController.popBackStack() },
                onOpenPro = { navController.navigate(Routes.pro(PaywallTrigger.VEHICLE_LIMIT)) },
            )
        }
        if (BuildConfig.DEBUG) {
            composable(Routes.AD_PREVIEW) { AdPreviewScreen(onBack = { navController.popBackStack() }) }
        }
        composable(
            Routes.PRO,
            arguments = listOf(navArgument("trigger") { type = NavType.StringType; defaultValue = "settings" }),
        ) { entry ->
            ProScreen(
                trigger = PaywallTrigger.fromRoute(entry.arguments?.getString("trigger")),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Vehicle detail with every outgoing navigation going through [nav] (the app's main controller). */
@Composable
private fun VehicleDetail(nav: NavHostController, onBack: () -> Unit, onSwitch: (Long) -> Unit) {
    VehicleDetailRoute(
        onBack = onBack,
        onSwitch = onSwitch,
        onAddEntry = { id, charge -> nav.navigate(Routes.entry(id, charge)) },
        onAddRecord = { id, expense -> nav.navigate(Routes.record(id, expense)) },
        onOpenReminders = { nav.navigate(Routes.reminders(it)) },
        onOpenPro = { nav.navigate(Routes.pro(it)) },
        onOpenDocument = { nav.navigate(Routes.document(it)) },
        onAddDocument = { nav.navigate(Routes.documentForm(it)) },
    )
}

/**
 * The detail pane of the two-pane Garage. It hosts Vehicle detail in a small NavHost of its own so the screen and its
 * ViewModel are exactly the ones the compact layout navigates to. Forms and other screens still open full screen.
 */
@Composable
private fun VehiclePane(vehicleId: Long, nav: NavHostController, onClose: () -> Unit, onSwitch: (Long) -> Unit) {
    key(vehicleId) {
        val inner = rememberNavController()
        NavHost(inner, startDestination = Routes.VEHICLE) {
            composable(
                Routes.VEHICLE,
                arguments = listOf(navArgument("vehicleId") { type = NavType.LongType; defaultValue = vehicleId }),
            ) {
                VehicleDetail(nav, onBack = onClose, onSwitch = onSwitch)
            }
        }
    }
}
