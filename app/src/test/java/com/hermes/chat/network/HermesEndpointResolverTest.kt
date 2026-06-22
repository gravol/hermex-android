package com.hermes.chat.network

import com.hermes.chat.model.NetworkMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HermesEndpointResolverTest {

    private val homeUrl = "http://localhost:8080/v1/chat/completions"

    @Test
    fun `HOME returns localhost URL regardless of awayUrl`() {
        assertEquals(homeUrl, HermesEndpointResolver.resolve(NetworkMode.HOME, ""))
        assertEquals(homeUrl, HermesEndpointResolver.resolve(NetworkMode.HOME, "https://remote:8080/path"))
    }

    @Test
    fun `AWAY with valid URL returns that URL`() {
        val away = "https://tailscale-host:8080/v1/chat/completions"
        assertEquals(away, HermesEndpointResolver.resolve(NetworkMode.AWAY, away))
    }

    @Test
    fun `AWAY with blank URL returns empty string`() {
        assertEquals("", HermesEndpointResolver.resolve(NetworkMode.AWAY, ""))
        assertEquals("", HermesEndpointResolver.resolve(NetworkMode.AWAY, "  "))
    }
}
