// File: app/src/test/java/com/hermex/data/local/CacheTest.kt
package com.hermex.data.local

import android.app.Application
import androidx.room.Room
import com.hermex.data.local.dao.MessageDao
import com.hermex.data.local.database.HermexDatabase
import com.hermex.data.model.Message
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import java.util.UUID

class CacheTest {

    private lateinit var database: HermexDatabase
    private lateinit var messageDao: MessageDao

    @BeforeAll
    fun createDatabase() {
        // Use in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            Application(),
            HermexDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        
        messageDao = database.messageDao()
    }

    @AfterAll
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `testInsertMessage`() = runTest {
        // Arrange
        val message = Message(
            id = UUID.randomUUID().toString(),
            text = "Test Message",
            sender = "user",
            timestamp = System.currentTimeMillis()
        )

        // Act
        messageDao.insertMessage(message)

        // Assert
        val inserted = messageDao.getMessageById(message.id)
        Assertions.assertNotNull(inserted)
        Assertions.assertEquals("Test Message", inserted?.text)
    }

    @Test
    fun `testGetAllMessages`() = runTest {
        // Arrange
        val message1 = Message(UUID.randomUUID().toString(), "Msg 1", "user", 1L)
        val message2 = Message(UUID.randomUUID().toString(), "Msg 2", "user", 2L)
        messageDao.insertMessage(message1)
        messageDao.insertMessage(message2)

        // Act
        val allMessages = messageDao.getAllMessages().first()

        // Assert
        Assertions.assertEquals(2, allMessages.size)
    }

    @Test
    fun `testDeleteMessage`() = runTest {
        // Arrange
        val message = Message(UUID.randomUUID().toString(), "Delete Me", "user", 3L)
        messageDao.insertMessage(message)

        // Act
        messageDao.deleteMessage(message)

        // Assert
        val deleted = messageDao.getMessageById(message.id)
        Assertions.assertNull(deleted)
    }
}