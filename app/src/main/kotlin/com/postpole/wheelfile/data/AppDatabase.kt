package com.postpole.wheelfile.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.Converters
import com.postpole.wheelfile.data.db.CustomCategory
import com.postpole.wheelfile.data.db.CustomCategoryDao
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyLogDao
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseDao
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.ServiceRecordDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleDocumentDao

@Database(
    entities = [
        Vehicle::class,
        EnergyLog::class,
        ServiceRecord::class,
        Expense::class,
        Reminder::class,
        VehicleDocument::class,
        Attachment::class,
        CustomCategory::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun energyLogDao(): EnergyLogDao
    abstract fun serviceRecordDao(): ServiceRecordDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun vehicleDocumentDao(): VehicleDocumentDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun customCategoryDao(): CustomCategoryDao
}
