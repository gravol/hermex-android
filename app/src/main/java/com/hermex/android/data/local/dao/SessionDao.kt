// File: app/src/main/java/com/hermex/android/data/local/dao/SessionDao.kt
package com.hermex.android.data.local.dao

import androidx.room.*
import com.hermex.android.data.local.entities.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSession(sessionId: String): Flow<Session?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Update
    suspend fun updateSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    fun getActiveSession(): Flow<Session?>
}