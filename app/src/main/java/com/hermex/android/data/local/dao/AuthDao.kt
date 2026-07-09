// File: app/src/main/java/com/hermex/android/data/local/dao/AuthDao.kt
package com.hermex.android.data.local.dao

import androidx.room.*
import com.hermex.android.data.local.entities.Auth
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthDao {
    @Query("SELECT * FROM auth LIMIT 1")
    fun getAuth(): Flow<Auth?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuth(auth: Auth)

    @Delete
    suspend fun deleteAuth(auth: Auth)

    @Query("DELETE FROM auth")
    suspend fun clearAuth()
}