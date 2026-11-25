package com.postpole.wheelfile.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.backup.VehiclePdfExporter
import com.postpole.wheelfile.ui.report.PdfEvent
import com.postpole.wheelfile.ui.report.PdfReportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The history PDF is Pro only, and the view model enforces it itself, not just the button (issue #12, PR #14 review). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PdfReportViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(pro: Boolean) =
        PdfReportViewModel(context, VehiclePdfExporter(context, db), AdsPolicy({ 0L }, 0, false), { pro })

    @Test fun aFreeUserNeverBuildsTheReport() = runBlocking {
        val vm = vm(pro = false)

        vm.share(vehicleId = 1)

        assertEquals(PdfEvent.ProRequired, vm.events.first())
        assertFalse(vm.busy.value)
        assertFalse("nothing is written to the cache", File(context.cacheDir, "reports").exists())
    }

    @Test fun aProUserGetsAnEventForAVehicleThatNoLongerExists() = runBlocking {
        val vm = vm(pro = true)

        vm.share(vehicleId = 1) // no such vehicle: the exporter returns null, so the report fails cleanly

        assertEquals(PdfEvent.Failed, vm.events.first())
    }
}
