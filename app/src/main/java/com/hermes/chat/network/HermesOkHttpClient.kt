package com.hermes.chat.network

import com.hermes.chat.BuildConfig
import com.hermes.chat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Hermes HTTP client with Network Security Configuration support.
 *
 * == Build-type-aware defaults ==
 * [DEBUG builds]  → baseUrl = "http://localhost:8080/v1/chat/completions"
 *                  Certificate pinning DISABLED (localhost has no certs).
 *                  OS-level cleartext allowed via debug network_security_config.xml override.
 * [RELEASE builds] → baseUrl = BuildConfig.PRODUCTION_BASE_URL (placeholder)
 *                   Certificate pinning ENABLED for HTTPS hosts.
 *                   OS-level cleartext DENIED by network_security_config.xml.
 *
 * Override baseUrl or isPinningEnabled at runtime to customise.
 */
class HermesOkHttpClient : HermesClient {

    /**
     * Hermes API endpoint. Defaults to localhost in debug, production URL in release.
     * Override to point at a different server (e.g. from Settings).
     */
    var baseUrl: String = if (BuildConfig.DEBUG) {
        "http://localhost:8080/v1/chat/completions"
    } else {
        BuildConfig.PRODUCTION_BASE_URL
    }

    /** Model name sent in each request. Override to switch models. */
    var model: String = "hermes"

    /** Max tokens in the response. */
    var maxTokens: Int = 4096

    /**
     * Whether CertificatePinner is active.
     * Default: disabled in debug (localhost), enabled in release.
     * Set false explicitly to bypass pinning for a non-production host in a release build.
     */
    var isPinningEnabled: Boolean = !BuildConfig.DEBUG

    /**
     * Bearer auth token sent as Authorization header.
     * Empty string = no auth. Set via Settings.
     */
    var authToken: String = ""

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        if (isPinningEnabled) {
            val host = extractHost(baseUrl)
            if (host != null && !isLoopback(host)) {
                builder.certificatePinner(
                    CertificatePinner.Builder()
                        .add(host,
                            // TODO: Replace with real production cert SHA-256 hashes.
                            "sha256/CHANGE_ME_TO_REAL_HASH_1AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                            "sha256/CHANGE_ME_TO_REAL_BACKUP_HASH_2BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
                        )
                        .build()
                )
            }
        }

        builder.build()
    }

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val modelsUrl = modelsUrl() ?: return@withContext emptyList()
        val reqBuilder = Request.Builder()
            .url(modelsUrl)
            .get()
        if (authToken.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $authToken")
        }

        val response = try {
            client.newCall(reqBuilder.build()).execute()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        response.use {
            if (!it.isSuccessful) return@withContext emptyList()
            val bodyString = it.body?.string() ?: return@withContext emptyList()
            runCatching { HermesModelListResponse.fromJson(bodyString).ids }.getOrDefault(emptyList())
        }
    }

    /**
     * Test endpoint + API key without sending a chat completion request.
     * Uses `/v1/models`, which Hermes protects with the same Bearer API key.
     */
    suspend fun testApiKey(): String = withContext(Dispatchers.IO) {
        val modelsUrl = modelsUrl() ?: return@withContext "❌ Endpoint URL is invalid"
        val reqBuilder = Request.Builder()
            .url(modelsUrl)
            .get()
        if (authToken.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $authToken")
        }

        val response = try {
            client.newCall(reqBuilder.build()).execute()
        } catch (e: Exception) {
            return@withContext "❌ Connection failed: ${e.message ?: "unknown error"}"
        }

        response.use {
            when (it.code) {
                in 200..299 -> "✅ API key accepted"
                401 -> "⚠️ API key required or invalid"
                403 -> "⚠️ API key rejected"
                404 -> "⚠️ Endpoint reached, but /v1/models was not found"
                else -> "⚠️ HTTP ${it.code}: ${(it.body?.string() ?: "").take(120)}"
            }
        }
    }

    override suspend fun sendMessage(conversation: List<Message>): Message = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Message(
                role = "assistant",
                text = "⚠️ No endpoint configured. If you're away from home, set your Away Endpoint URL in Settings.",
            )
        }
        val requestBody = buildRequest(conversation)
        val reqBuilder = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
        if (authToken.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $authToken")
        }
        val httpRequest = reqBuilder.build()

        val httpResponse: okhttp3.Response = try {
            client.newCall(httpRequest).execute()
        } catch (e: Exception) {
            return@withContext Message(
                role = "assistant",
                text = "⚠️ Connection failed: ${e.message ?: "unknown error"}",
            )
        }

        val bodyString = httpResponse.body?.string() ?: ""
        if (!httpResponse.isSuccessful) {
            val message = when (httpResponse.code) {
                401 -> "⚠️ Auth failed (HTTP 401). Check your API token."
                403 -> "⚠️ Forbidden (HTTP 403). Token may lack permissions."
                404 -> "⚠️ Endpoint not found (HTTP 404). Check your URL."
                else -> "⚠️ HTTP ${httpResponse.code}: ${bodyString.take(200)}"
            }
            return@withContext Message(role = "assistant", text = message)
        }

        try {
            val response = HermesResponse.fromJson(bodyString)
            Message(role = "assistant", text = response.content.ifEmpty { "(empty response)" })
        } catch (e: Exception) {
            Message(
                role = "assistant",
                text = "⚠️ Parse error: ${e.message ?: "unknown"}\n\nRaw:\n${bodyString.take(300)}",
            )
        }
    }

    private fun buildRequest(conversation: List<Message>): okhttp3.RequestBody {
        val apiMessages = conversation.map { msg ->
            HermesRequest.RequestMessage(role = msg.role, content = msg.text)
        }
        val request = HermesRequest(
            model = model,
            messages = apiMessages,
            maxTokens = maxTokens,
        )
        return request.toJson().toRequestBody(jsonMediaType)
    }

    // ── Helpers ────────────────────────────────────────────────

    /** Derive OpenAI-compatible `/v1/models` URL from the configured chat completions URL. */
    private fun modelsUrl(): String? {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return null
        val segments = parsed.encodedPathSegments
        val v1Index = segments.indexOf("v1")
        return if (v1Index >= 0) {
            parsed.newBuilder().apply {
                repeat(parsed.pathSize) { removePathSegment(0) }
                segments.take(v1Index + 1).forEach { addPathSegment(it) }
                addPathSegment("models")
            }.build().toString()
        } else {
            parsed.newBuilder()
                .encodedPath("/v1/models")
                .build()
                .toString()
        }
    }

    /** Extract hostname (no port) from a URL. Returns null on failure. */
    private fun extractHost(url: String): String? {
        return try {
            val withoutProtocol = url.substringAfter("://")
            withoutProtocol.substringBefore(":").substringBefore("/")
        } catch (_: Exception) {
            null
        }
    }

    /** True if the host is localhost or a loopback IP. */
    private fun isLoopback(host: String): Boolean {
        return host.equals("localhost", ignoreCase = true) ||
               host == "127.0.0.1" ||
               host == "::1"
    }
}
