// File: app/src/main/java/com/hermex/core/deeplink/DeepLinkHandler.kt

package com.hermex.core.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.hermex.ui.MainActivity
import com.hermex.ui.ChatDetailActivity
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Handles deep link navigation for the 'hermes' scheme.
 * Supported formats:
 * - hermes://
 * - hermes://chat/{chatId}
 * - hermes://new
 * - hermes://settings
 */
object DeepLinkHandler {

    private const val SCHEME = "hermes"
    private const val PATH_CHAT = "chat"
    private const val PATH_NEW = "new"
    private const val PATH_SETTINGS = "settings"

    /**
     * Processes a given Intent to determine the next action.
     *
     * @param context The Context to launch activities
     * @param intent The incoming Intent containing the data
     * @return True if a deep link was handled and an activity was launched, false otherwise.
     */
    fun handleDeepLink(context: Context, intent: Intent): Boolean {
        val uri = intent.data
        if (uri == null || !uri.scheme.equals(SCHEME, ignoreCase = true)) {
            return false
        }

        val path = uri.path ?: return false
        val queryMap = parseQueryParameters(uri)

        return when {
            path == null || path == "/" -> {
                // Root link: Open main chat list
                navigateToMainActivity(context)
                true
            }
            path == "/$PATH_CHAT" -> {
                // Chat link: hermes://chat/{id}
                val chatId = path.substringAfter("/").takeIf { it.isNotEmpty() }
                if (chatId != null) {
                    navigateToChat(context, chatId)
                    true
                } else {
                    false
                }
            }
            path == "/$PATH_NEW" -> {
                // New Chat: hermes://new
                navigateToChat(context, "new_chat")
                true
            }
            path == "/$PATH_SETTINGS" -> {
                // Settings: hermes://settings
                navigateToSettings(context)
                true
            }
            else -> {
                // Unknown path
                Log.w("DeepLinkHandler", "Unknown path: $path")
                false
            }
        }
    }

    private fun navigateToMainActivity(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun navigateToChat(context: Context, chatId: String) {
        val intent = Intent(context, ChatDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_CHAT_ID", chatId)
        }
        context.startActivity(intent)
    }

    private fun navigateToSettings(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO_SETTINGS", true)
        }
        context.startActivity(intent)
    }

    /**
     * Parses query parameters from the Uri into a Map<String, String>.
     */
    private fun parseQueryParameters(uri: Uri): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val count = uri.getQueryParameterCount()
        for (i in 0 until count) {
            val name = uri.getQueryParameter(i)
            val value = uri.getQueryParameter(i)
            if (name != null && value != null) {
                params[name] = URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
            }
        }
        return params
    }
}