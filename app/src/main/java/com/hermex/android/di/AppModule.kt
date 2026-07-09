// File: app/src/main/java/com/hermex/android/di/AppModule.kt
package com.hermex.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.hermex.android.data.local.Database
import com.hermex.android.data.local.DataStoreManager
import com.hermex.android.data.local.cache.CacheManager
import com.hermex.android.data.local.dao.AuthDao
import com.hermex.android.data.local.dao.MessageDao
import com.hermex.android.data.local.dao.SessionDao
import com.hermex.android.data.local.dao.ThinkingCardDao
import com.hermex.android.data.local.dao.ToolCallDao
import com.hermex.android.data.network.ApiService
import com.hermex.android.data.network.RetrofitClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hermex_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): Database {
        return Database.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAuthDao(database: Database): AuthDao {
        return database.authDao()
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: Database): SessionDao {
        return database.sessionDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: Database): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideToolCallDao(database: Database): ToolCallDao {
        return database.toolCallDao()
    }

    @Provides
    @Singleton
    fun provideThinkingCardDao(database: Database): ThinkingCardDao {
        return database.thinkingCardDao()
    }

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return RetrofitClient.instance.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCacheManager(): CacheManager {
        return CacheManager
    }

    @Provides
    @Singleton
    fun provideDataStoreManager(@ApplicationContext context: Context): DataStoreManager {
        return DataStoreManager(context.dataStore)
    }
}
