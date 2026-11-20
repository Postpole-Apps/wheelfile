package com.postpole.wheelfile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.google.android.ump.UserMessagingPlatform
import com.postpole.wheelfile.ads.AdsConfigSource
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.ConsentManager
import com.postpole.wheelfile.ads.FirebaseAdsConfigSource
import com.postpole.wheelfile.ads.GoogleAdsSdk
import com.postpole.wheelfile.ads.UmpConsentForms
import com.postpole.wheelfile.ads.createAdsPolicy
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.createAnalytics
import com.postpole.wheelfile.review.ReviewPolicy
import com.postpole.wheelfile.review.ReviewPrompter
import com.postpole.wheelfile.billing.BillingGateway
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.billing.PlayBillingGateway
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.CustomCategoryDao
import com.postpole.wheelfile.data.db.EnergyLogDao
import com.postpole.wheelfile.data.db.ExpenseDao
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.ServiceRecordDao
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.data.db.VehicleDocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "app.db").build()

    @Provides fun provideVehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()
    @Provides fun provideEnergyLogDao(db: AppDatabase): EnergyLogDao = db.energyLogDao()
    @Provides fun provideServiceRecordDao(db: AppDatabase): ServiceRecordDao = db.serviceRecordDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideVehicleDocumentDao(db: AppDatabase): VehicleDocumentDao = db.vehicleDocumentDao()
    @Provides fun provideAttachmentDao(db: AppDatabase): AttachmentDao = db.attachmentDao()
    @Provides fun provideCustomCategoryDao(db: AppDatabase): CustomCategoryDao = db.customCategoryDao()

    @Provides
    fun provideProStatus(billing: BillingRepository): com.postpole.wheelfile.billing.ProStatus =
        object : com.postpole.wheelfile.billing.ProStatus {
            override val isPro = billing.isPro
        }

    @Provides
    fun provideLocaleProvider(): com.postpole.wheelfile.data.LocaleProvider =
        com.postpole.wheelfile.data.LocaleProvider { java.util.Locale.getDefault() }

    @Provides
    fun provideClock():com.postpole.wheelfile.data.Clock = com.postpole.wheelfile.data.Clock.System

    @Provides
    @Singleton
    fun provideBillingGateway(@ApplicationContext context: Context): BillingGateway =
        PlayBillingGateway(context)

    @Provides
    @Singleton
    fun provideBillingRepository(
        gateway: BillingGateway,
        dataStore: DataStore<Preferences>,
        analytics: Analytics,
    ): BillingRepository = BillingRepository(
        gateway = gateway,
        dataStore = dataStore,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        analytics = analytics,
    )

    @Provides
    @Singleton
    fun provideAdsConfigSource(@ApplicationContext context: Context, analytics: Analytics): AdsConfigSource =
        FirebaseAdsConfigSource(context, analytics)

    @Provides
    @Singleton
    fun provideAdsPolicy(@ApplicationContext context: Context): AdsPolicy = createAdsPolicy(context)

    @Provides
    @Singleton
    fun provideConsentManager(
        @ApplicationContext context: Context,
        analytics: Analytics,
        configSource: AdsConfigSource,
    ): ConsentManager = ConsentManager(
        info = UserMessagingPlatform.getConsentInformation(context),
        forms = UmpConsentForms(),
        ads = GoogleAdsSdk(context),
        analytics = analytics,
        maxContentRating = { configSource.config.value.maxContentRating },
    )

    @Provides
    @Singleton
    fun provideAnalytics(@ApplicationContext context: Context): Analytics = createAnalytics(context)

    @Provides
    fun provideReviewPrompter(policy: ReviewPolicy): ReviewPrompter = policy

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
