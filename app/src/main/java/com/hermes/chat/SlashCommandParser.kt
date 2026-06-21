package com.hermes.chat

import com.hermes.chat.model.ModelType

/**
 * Parses slash commands from user input.
 * Handles: /model flash|pro, /secure
 */
sealed class SlashCommand {
    data class SetModel(val model: ModelType) : SlashCommand()
    data object Secure : SlashCommand() {
        override val isPrivileged: Boolean get() = true
    }

    /** Whether this command requires local biometric / device-credential auth. */
    open val isPrivileged: Boolean get() = false
}

object SlashCommandParser {

    fun parse(text: String): SlashCommand? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) return null

        val parts = trimmed.substring(1).split("\\s+".toRegex(), limit = 3)
        if (parts.isEmpty()) return null

        return when (parts[0].lowercase()) {
            "model" -> parseModelCommand(parts)
            "secure" -> SlashCommand.Secure
            else -> null
        }
    }

    private fun parseModelCommand(parts: List<String>): SlashCommand.SetModel? {
        val arg = parts.getOrNull(1)?.lowercase() ?: return null
        val model = when (arg) {
            "flash" -> ModelType.FLASH
            "pro" -> ModelType.PRO
            else -> return null
        }
        return SlashCommand.SetModel(model)
    }
}
