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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hermex_settings")

/**
 * Manual dependency injection — no Hilt/Dagger.
 * Provide singletons via lazy-initialized factory methods.
 */
object AppModule {

    @Volatile
    private var database: Database? = null
    @Volatile
    private var dataStoreManager: DataStoreManager? = null

    fun provideDatabase(context: Context): Database {
        return database ?: synchronized(this) {
            database ?: Database.getInstance(context).also { database = it }
        }
    }

    fun provideAuthDao(context: Context): AuthDao =
        provideDatabase(context).authDao()

    fun provideSessionDao(context: Context): SessionDao =
        provideDatabase(context).sessionDao()

    fun provideMessageDao(context: Context): MessageDao =
        provideDatabase(context).messageDao()

    fun provideToolCallDao(context: Context): ToolCallDao =
        provideDatabase(context).toolCallDao()

    fun provideThinkingCardDao(context: Context): ThinkingCardDao =
        provideDatabase(context).thinkingCardDao()

    fun provideCacheManager(): CacheManager = CacheManager

    fun provideDataStoreManager(context: Context): DataStoreManager {
        return dataStoreManager ?: synchronized(this) {
            dataStoreManager ?: DataStoreManager(context.dataStore).also { dataStoreManager = it }
        }
    }
}
