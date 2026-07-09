// File: app/src/main/java/com/hermex/android/data/local/dao/ThinkingCardDao.kt
package com.hermex.android.data.local.dao

import androidx.room.*
import com.hermex.android.data.local.entities.ThinkingCard
import kotlinx.coroutines.flow.Flow

@Dao
interface ThinkingCardDao {
    @Query("SELECT * FROM thinking_cards WHERE messageId = :messageId ORDER BY timestamp ASC")
    fun getThinkingCards(messageId: String): Flow<List<ThinkingCard>>

    @Query("SELECT * FROM thinking_cards WHERE id = :cardId")
    fun getThinkingCard(cardId: String): Flow<ThinkingCard?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThinkingCard(card: ThinkingCard)

    @Update
    suspend fun updateThinkingCard(card: ThinkingCard)

    @Delete
    suspend fun deleteThinkingCard(card: ThinkingCard)

    @Query("DELETE FROM thinking_cards WHERE id = :cardId")
    suspend fun deleteThinkingCardById(cardId: String)

    @Query("DELETE FROM thinking_cards WHERE messageId = :messageId")
    suspend fun deleteThinkingCardsByMessage(messageId: String)
}