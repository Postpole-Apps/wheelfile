package com.postpole.wheelfile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.ads.ConsentManager
import com.postpole.wheelfile.ads.buildConsentRequestParameters
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.data.ThemeMode
import com.postpole.wheelfile.ui.AppNavHost
import com.postpole.wheelfile.ui.MainViewModel
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import com.postpole.wheelfile.update.InAppUpdate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var analytics: Analytics
    @Inject lateinit var consent: ConsentManager

    private lateinit var update: InAppUpdate

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Consent first: ads (and MobileAds.initialize) wait for it; the app never does.
        // gatherConsent is a no-op after the first successful run in this process.
        consent.gatherConsent(
            this,
            buildConsentRequestParameters(this, BuildConfig.UMP_DEBUG_GEOGRAPHY, BuildConfig.UMP_TEST_DEVICE_HASHED_ID),
        )
        update = InAppUpdate(this, onError = analytics::recordNonFatal)
        if (savedInstanceState == null) update.check()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val updateReady by update.readyToInstall.collectAsStateWithLifecycle()
            var dismissed by rememberSaveable { mutableStateOf(false) }
            val dark = when (theme.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WheelfileTheme(darkTheme = dark) {
                AppNavHost(onScreen = viewModel::onScreen)
                if (updateReady && !dismissed) {
                    AlertDialog(
                        onDismissRequest = { dismissed = true },
                        title = { Text(stringResource(R.string.update_ready_title)) },
                        text = { Text(stringResource(R.string.update_ready_message)) },
                        confirmButton = {
                            TextButton(onClick = update::completeUpdate) { Text(stringResource(R.string.update_restart)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.update_later)) }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        update.release()
        super.onDestroy()
    }
}
