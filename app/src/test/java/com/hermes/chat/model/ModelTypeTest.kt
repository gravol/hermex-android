package com.hermes.chat.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTypeTest {

    @Test
    fun `FLASH has correct properties`() {
        val flash = ModelType.FLASH
        assertEquals("Flash", flash.displayName)
        assertEquals("deepseek-v4-flash", flash.apiName)
    }

    @Test
    fun `PRO has correct properties`() {
        val pro = ModelType.PRO
        assertEquals("Pro", pro.displayName)
        assertEquals("deepseek-v4-pro", pro.apiName)
    }

    @Test
    fun `all entries are present`() {
        assertEquals(2, ModelType.entries.size)
        assertTrue(ModelType.entries.containsAll(listOf(ModelType.FLASH, ModelType.PRO)))
    }
}
