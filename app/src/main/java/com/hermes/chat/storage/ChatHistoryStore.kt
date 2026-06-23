package com.hermes.chat.storage

import android.content.Context
import com.hermes.chat.model.AttachmentType
import com.hermes.chat.model.Message
import com.hermes.chat.model.MessageAttachment
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * App-private JSON persistence for the current chat transcript.
 *
 * Stored in internal app files so it survives process death and normal app
 * updates, but is removed if the user uninstalls or clears app storage.
 */
class ChatHistoryStore(context: Context) {
    private val historyFile = context.filesDir.resolve(FILE_NAME)

    fun loadMessages(): List<Message> = runCatching {
        if (!historyFile.exists()) return emptyList()
        val root = JSONObject(historyFile.readText())
        val version = root.optInt("version", 1)
        if (version > CURRENT_VERSION) return emptyList()
        val messages = root.optJSONArray("messages") ?: JSONArray()
        (0 until messages.length()).mapNotNull { index ->
            messages.optJSONObject(index)?.toMessageOrNull()
        }
    }.getOrDefault(emptyList())

    fun saveMessages(messages: List<Message>) {
        val durableMessages = messages
            .filter { !it.isSystem && !(it.isAssistant && it.text == "...") }
            .takeLast(MAX_MESSAGES)

        val root = JSONObject().apply {
            put("version", CURRENT_VERSION)
            put("messages", JSONArray().apply {
                durableMessages.forEach { put(it.toJson()) }
            })
        }
        runCatching {
            historyFile.parentFile?.mkdirs()
            historyFile.writeText(root.toString())
        }
    }

    fun clear() {
        runCatching { historyFile.delete() }
    }

    private fun Message.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("text", text)
        put("timestamp", timestamp)
        put("attachments", JSONArray().apply {
            attachments.forEach { put(it.toJson()) }
        })
    }

    private fun MessageAttachment.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("uri", uri)
        put("displayName", displayName)
        put("mimeType", mimeType ?: JSONObject.NULL)
        put("dataUrl", dataUrl ?: JSONObject.NULL)
    }

    private fun JSONObject.toMessageOrNull(): Message? = runCatching {
        val role = optString("role").takeIf { it == "user" || it == "assistant" } ?: return null
        val text = optString("text", "")
        Message(
            id = optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            role = role,
            text = text,
            timestamp = optLong("timestamp", System.currentTimeMillis()),
            attachments = optJSONArray("attachments")?.let { arr ->
                (0 until arr.length()).mapNotNull { index ->
                    arr.optJSONObject(index)?.toAttachmentOrNull()
                }
            } ?: emptyList(),
        )
    }.getOrNull()

    private fun JSONObject.toAttachmentOrNull(): MessageAttachment? = runCatching {
        MessageAttachment(
            id = optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            type = AttachmentType.valueOf(optString("type")),
            uri = optString("uri"),
            displayName = optString("displayName"),
            mimeType = optNullableString("mimeType"),
            dataUrl = optNullableString("dataUrl"),
        )
    }.getOrNull()

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val FILE_NAME = "chat_history.json"
        private const val CURRENT_VERSION = 1
        private const val MAX_MESSAGES = 500
    }
}
