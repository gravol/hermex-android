package com.hermes.chat

import com.hermes.chat.model.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlashCommandParserTest {

    @Test
    fun `parse 'slash model flash' returns SetModel FLASH`() {
        val result = SlashCommandParser.parse("/model flash")
        assertTrue(result is SlashCommand.SetModel)
        assertEquals(ModelType.FLASH, (result as SlashCommand.SetModel).model)
    }

    @Test
    fun `parse 'slash model pro' returns SetModel PRO`() {
        val result = SlashCommandParser.parse("/model pro")
        assertTrue(result is SlashCommand.SetModel)
        assertEquals(ModelType.PRO, (result as SlashCommand.SetModel).model)
    }

    @Test
    fun `parse 'slash secure' returns Secure`() {
        val result = SlashCommandParser.parse("/secure")
        assertTrue(result is SlashCommand.Secure)
        assertTrue(result!!.isPrivileged)
    }

    @Test
    fun `parse 'slash MODEL Flash' is case-insensitive`() {
        val result = SlashCommandParser.parse("/MODEL Flash")
        assertTrue(result is SlashCommand.SetModel)
        assertEquals(ModelType.FLASH, (result as SlashCommand.SetModel).model)
    }

    @Test
    fun `parse plain text returns null`() {
        assertNull(SlashCommandParser.parse("hello world"))
    }

    @Test
    fun `parse empty string returns null`() {
        assertNull(SlashCommandParser.parse(""))
    }

    @Test
    fun `parse unknown slash command returns null`() {
        assertNull(SlashCommandParser.parse("/unknown"))
    }

    @Test
    fun `parse 'slash model' without arg returns null`() {
        assertNull(SlashCommandParser.parse("/model"))
    }

    @Test
    fun `parse 'slash model invalid' returns null`() {
        assertNull(SlashCommandParser.parse("/model turbo"))
    }

    @Test
    fun `parse leading whitespace is trimmed`() {
        val result = SlashCommandParser.parse("  /model flash")
        assertTrue(result is SlashCommand.SetModel)
        assertEquals(ModelType.FLASH, (result as SlashCommand.SetModel).model)
    }
}
