package com.postpole.wheelfile

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The merged manifest (app plus every library) must not ask for camera, storage, location or exact alarms. */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {
    private val requested: Set<String> by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions.orEmpty().toSet()
    }

    @Test
    fun noCameraPermission() {
        assertFalse(requested.any { it.endsWith(".CAMERA") })
    }

    @Test
    fun noStoragePermissions() {
        val storage = requested.filter {
            it.startsWith("android.permission.READ_MEDIA_") ||
                it.endsWith(".READ_EXTERNAL_STORAGE") ||
                it.endsWith(".WRITE_EXTERNAL_STORAGE") ||
                it.endsWith(".MANAGE_EXTERNAL_STORAGE")
        }
        assertTrue("Unexpected storage permissions: $storage", storage.isEmpty())
    }

    @Test
    fun noLocationPermissions() {
        val location = requested.filter { it.contains("LOCATION") }
        assertTrue("Unexpected location permissions: $location", location.isEmpty())
    }

    @Test
    fun noExactAlarmPermissions() {
        assertFalse(requested.any { it.endsWith(".SCHEDULE_EXACT_ALARM") || it.endsWith(".USE_EXACT_ALARM") })
    }

    @Test
    fun notificationsAndAdIdStayOn() {
        assertTrue("android.permission.POST_NOTIFICATIONS" in requested)
        assertTrue("com.google.android.gms.permission.AD_ID" in requested)
    }
}
