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

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

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

    override suspend fun sendMessage(conversation: List<Message>): Message = withContext(Dispatchers.IO) {
        val requestBody = buildRequest(conversation)
        val httpRequest = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .build()

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
            return@withContext Message(
                role = "assistant",
                text = "⚠️ HTTP ${httpResponse.code}: ${bodyString.take(200)}",
            )
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
