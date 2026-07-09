// File: app/src/test/java/com/hermex/network/ApiServiceTest.kt
package com.hermex.network

import com.hermex.data.model.ChatRequest
import com.hermex.data.remote.ApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import okio.source
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class ApiServiceTest {

    private val mockWebServer = MockWebServer()
    private lateinit var apiService: ApiService

    @BeforeAll
    fun setUp() {
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/")
        
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    @AfterAll
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `testSendChatMessage`() = runTest {
        // Arrange
        val chatRequest = ChatRequest("testUser", "Hello Network", "system")
        val mockResponse = MockResponse().setResponseCode(200).setBody("{\"status\": \"sent\"}")
        mockWebServer.enqueue(mockResponse)

        // Act
        val response = apiService.sendChatMessage(chatRequest)

        // Assert
        Assertions.assertEquals(200, response.code())
        val request = mockWebServer.takeRequest()
        Assertions.assertEquals("/api/chat/send", request.path)
        Assertions.assertEquals("POST", request.method)
    }

    @Test
    fun `testGetChatHistory`() = runTest {
        // Arrange
        val mockResponse = MockResponse().setResponseCode(200)
            .setBody("[{\"id\": \"1\", \"text\": \"History\"}]")
        mockWebServer.enqueue(mockResponse)

        // Act
        val response = apiService.getChatHistory("userId")

        // Assert
        Assertions.assertEquals(200, response.code())
        val request = mockWebServer.takeRequest()
        Assertions.assertEquals("/api/chat/history?userId=userId", request.path)
    }
}