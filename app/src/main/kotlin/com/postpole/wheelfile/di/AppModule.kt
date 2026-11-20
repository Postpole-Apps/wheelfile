package com.postpole.wheelfile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.postpole.wheelfile.ads.AdsSdk
import com.postpole.wheelfile.ads.ConsentManager
import com.postpole.wheelfile.ads.ConsentSdk
import com.postpole.wheelfile.ads.FirebaseRemoteAdsConfig
import com.postpole.wheelfile.ads.GmsAdsSdk
import com.postpole.wheelfile.ads.RemoteAdsConfig
import com.postpole.wheelfile.ads.UmpConsentSdk
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
    fun provideAnalytics(@ApplicationContext context: Context): Analytics = createAnalytics(context)

    @Provides
    @Singleton
    fun provideRemoteAdsConfig(@ApplicationContext context: Context, analytics: Analytics): RemoteAdsConfig =
        FirebaseRemoteAdsConfig.create(context, analytics)

    @Provides
    @Singleton
    fun provideConsentSdk(@ApplicationContext context: Context): ConsentSdk = UmpConsentSdk(context)

    @Provides
    @Singleton
    fun provideAdsSdk(@ApplicationContext context: Context): AdsSdk = GmsAdsSdk(context)

    @Provides
    @Singleton
    fun provideConsentManager(
        sdk: ConsentSdk,
        ads: AdsSdk,
        analytics: Analytics,
        remoteConfig: RemoteAdsConfig,
    ): ConsentManager = ConsentManager(sdk, ads, analytics) { remoteConfig.config.value.maxContentRating }

    @Provides
    fun provideReviewPrompter(policy: ReviewPolicy): ReviewPrompter = policy

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
