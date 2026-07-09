// File: app/src/main/java/com/hermex/android/data/local/Database.kt
package com.hermex.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hermex.android.data.local.dao.*
import com.hermex.android.data.local.entities.*

@Database(
    entities = [
        Auth::class,
        Session::class,
        Message::class,
        ToolCall::class,
        ThinkingCard::class,
        CachedSession::class,
        CachedMessage::class
    ],
    version = 1,
    exportSchema = true
)
abstract class Database : RoomDatabase() {
    abstract fun authDao(): AuthDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun toolCallDao(): ToolCallDao
    abstract fun thinkingCardDao(): ThinkingCardDao

    companion object {
        @Volatile
        private var INSTANCE: com.hermex.android.data.local.Database? = null

        fun getInstance(context: Context): com.hermex.android.data.local.Database {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    com.hermex.android.data.local.Database::class.java,
                    "hermex_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}