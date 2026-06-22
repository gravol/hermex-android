package com.hermes.chat.model

/**
 * User-selected Hermes endpoint routing mode.
 *
 * - [AUTO] — prefer Tailscale when reachable, then fall back to Local/LAN.
 * - [LOCAL] — force the configured Local/LAN endpoint.
 * - [TAILSCALE] — force the configured Tailscale endpoint.
 */
enum class NetworkMode(val displayName: String) {
    AUTO("Auto"),
    LOCAL("Local"),
    TAILSCALE("Tailscale"),
}
