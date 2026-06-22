package com.hermes.chat.network

import com.hermes.chat.model.NetworkMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HermesEndpointResolverTest {

    private val localUrl = "http://192.168.1.50:8080/v1/chat/completions"
    private val tailscaleUrl = "http://100.64.1.2:8080/v1/chat/completions"

    @Test
    fun `AUTO prefers Tailscale when configured`() {
        assertEquals(
            tailscaleUrl,
            HermesEndpointResolver.resolve(NetworkMode.AUTO, localUrl, tailscaleUrl),
        )
    }

    @Test
    fun `AUTO falls back to Local when Tailscale is blank`() {
        assertEquals(
            localUrl,
            HermesEndpointResolver.resolve(NetworkMode.AUTO, localUrl, ""),
        )
    }

    @Test
    fun `LOCAL returns Local URL`() {
        assertEquals(
            localUrl,
            HermesEndpointResolver.resolve(NetworkMode.LOCAL, localUrl, tailscaleUrl),
        )
    }

    @Test
    fun `TAILSCALE returns Tailscale URL`() {
        assertEquals(
            tailscaleUrl,
            HermesEndpointResolver.resolve(NetworkMode.TAILSCALE, localUrl, tailscaleUrl),
        )
    }

    @Test
    fun `blank configured URLs return blank`() {
        assertEquals("", HermesEndpointResolver.resolve(NetworkMode.AUTO, "", ""))
        assertEquals("", HermesEndpointResolver.resolve(NetworkMode.LOCAL, "", tailscaleUrl))
        assertEquals("", HermesEndpointResolver.resolve(NetworkMode.TAILSCALE, localUrl, ""))
    }
}
