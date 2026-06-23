package com.hermes.chat.network

import com.hermes.chat.BuildConfig
import com.hermes.chat.model.AttachmentType
import com.hermes.chat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    suspend fun transcribeAudio(file: File): Result<String> = withContext(Dispatchers.IO) {
        val transcribeUrl = transcribeUrl()
            ?: return@withContext Result.failure(IllegalStateException("Endpoint URL is invalid"))
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Voice note file is empty"))
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                file.name,
                file.asRequestBody("audio/mp4".toMediaType()),
            )
            .build()

        val reqBuilder = Request.Builder()
            .url(transcribeUrl)
            .post(body)
        if (authToken.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $authToken")
        }

        val response = try {
            client.newCall(reqBuilder.build()).execute()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        response.use {
            val bodyString = it.body?.string() ?: ""
            if (!it.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${it.code}: ${bodyString.take(200)}"))
            }
            runCatching {
                val root = JSONObject(bodyString)
                val transcript = root.optString("transcript", "").trim()
                if (transcript.isBlank()) error(root.optString("error", "Empty transcript"))
                transcript
            }
        }
    }

    suspend fun syncMessageToObsidian(message: Message): Boolean = withContext(Dispatchers.IO) {
        if (message.isSystem || message.text.isBlank() || message.text == "...") return@withContext false
        val url = obsidianChatUrl() ?: return@withContext false
        val body = JSONObject().apply {
            put("id", message.id)
            put("role", message.role)
            put("text", message.text)
            put("timestamp", message.timestamp)
            put("source", "Hermes Chat Android")
        }.toString().toRequestBody(jsonMediaType)

        val reqBuilder = Request.Builder()
            .url(url)
            .post(body)
        if (authToken.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $authToken")
        }

        val response = try {
            client.newCall(reqBuilder.build()).execute()
        } catch (_: Exception) {
            return@withContext false
        }
        response.use { it.isSuccessful }
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

    suspend fun streamMessage(
        conversation: List<Message>,
        onDelta: suspend (String) -> Unit,
    ): Message = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            return@withContext Message(
                role = "assistant",
                text = "⚠️ No endpoint configured. If you're away from home, set your Away Endpoint URL in Settings.",
            )
        }
        val reqBuilder = Request.Builder()
            .url(baseUrl)
            .post(buildRequest(conversation, stream = true))
            .header("Accept", "text/event-stream")
        if (authToken.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $authToken")
        }

        val httpResponse = try {
            client.newCall(reqBuilder.build()).execute()
        } catch (e: Exception) {
            return@withContext Message(
                role = "assistant",
                text = "⚠️ Connection failed: ${e.message ?: "unknown error"}",
            )
        }

        httpResponse.use { response ->
            if (!response.isSuccessful) {
                val bodyString = response.body?.string() ?: ""
                val message = when (response.code) {
                    401 -> "⚠️ Auth failed (HTTP 401). Check your API token."
                    403 -> "⚠️ Forbidden (HTTP 403). Token may lack permissions."
                    404 -> "⚠️ Endpoint not found (HTTP 404). Check your URL."
                    else -> "⚠️ HTTP ${response.code}: ${bodyString.take(200)}"
                }
                return@withContext Message(role = "assistant", text = message)
            }

            val fullText = StringBuilder()
            val reader = response.body?.byteStream()?.bufferedReader()
                ?: return@withContext Message(role = "assistant", text = "⚠️ Empty stream response")
            reader.useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") return@forEach
                    val delta = runCatching {
                        val root = JSONObject(data)
                        val choices = root.optJSONArray("choices")
                        val first = choices?.optJSONObject(0)
                        val deltaObj = first?.optJSONObject("delta")
                        deltaObj?.optString("content", "").orEmpty()
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) {
                        fullText.append(delta)
                        onDelta(delta)
                    }
                }
            }
            Message(role = "assistant", text = fullText.toString().ifEmpty { "(empty response)" })
        }
    }

    private fun buildRequest(conversation: List<Message>, stream: Boolean = false): okhttp3.RequestBody {
        val apiMessages = conversation.map { msg ->
            HermesRequest.RequestMessage(role = msg.role, content = contentForMessage(msg))
        }
        val request = HermesRequest(
            model = model,
            messages = apiMessages,
            maxTokens = maxTokens,
            stream = stream,
        )
        return request.toJson().toRequestBody(jsonMediaType)
    }

    private fun contentForMessage(message: Message): Any {
        if (message.attachments.isEmpty()) return message.text

        val parts = JSONArray()
        val text = buildString {
            append(message.text)
            val unsupported = message.attachments.filter { it.type != AttachmentType.IMAGE || it.dataUrl.isNullOrBlank() }
            if (unsupported.isNotEmpty()) {
                if (isNotBlank()) append("\n\n")
                append("Attachments not sent to Hermes yet:\n")
                unsupported.forEach { att ->
                    val label = when (att.type) {
                        AttachmentType.IMAGE -> "image"
                        AttachmentType.VOICE -> "voice note"
                        AttachmentType.FILE -> "file"
                    }
                    append("- ${att.displayName} ($label)\n")
                }
            }
        }.trim()

        if (text.isNotBlank()) {
            parts.put(JSONObject().apply {
                put("type", "text")
                put("text", text)
            })
        }

        message.attachments
            .filter { it.type == AttachmentType.IMAGE && !it.dataUrl.isNullOrBlank() }
            .forEach { att ->
                parts.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", att.dataUrl)
                        put("detail", "auto")
                    })
                })
            }

        return if (parts.length() == 1 && message.attachments.none { it.type == AttachmentType.IMAGE && !it.dataUrl.isNullOrBlank() }) {
            text
        } else {
            parts
        }
    }

    // ── Helpers ────────────────────────────────────────────────

    /** Derive Hermes `/api/transcribe` URL from the configured chat completions URL. */
    private fun transcribeUrl(): String? {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return null
        return parsed.newBuilder()
            .encodedPath("/api/transcribe")
            .query(null)
            .build()
            .toString()
    }

    /** Derive Hermes `/api/obsidian/chat` URL from the configured chat completions URL. */
    private fun obsidianChatUrl(): String? {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return null
        return parsed.newBuilder()
            .encodedPath("/api/obsidian/chat")
            .query(null)
            .build()
            .toString()
    }

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
