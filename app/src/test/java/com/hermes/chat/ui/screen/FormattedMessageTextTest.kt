package com.hermes.chat.ui.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormattedMessageTextTest {

    @Test
    fun `splits paragraphs and fenced code blocks`() {
        val blocks = parseTelegramTextBlocks("Before\n```kotlin\nval x = 1\n```\nAfter")

        assertEquals(3, blocks.size)
        assertEquals(TelegramTextBlock.Paragraph("Before"), blocks[0])
        assertTrue(blocks[1] is TelegramTextBlock.Code)
        assertEquals("val x = 1", (blocks[1] as TelegramTextBlock.Code).text)
        assertEquals(TelegramTextBlock.Paragraph("After"), blocks[2])
    }

    @Test
    fun `keeps plain text when no markdown is present`() {
        val formatted = formatInline("plain message")

        assertEquals("plain message", formatted.text)
        assertEquals(0, formatted.spanStyles.size)
    }

    @Test
    fun `applies inline styles for bold italic and code`() {
        val formatted = formatInline("Use **bold**, *italic*, and `code`")

        assertEquals("Use bold, italic, and code", formatted.text)
        assertEquals(3, formatted.spanStyles.size)
    }
}
