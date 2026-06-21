package com.hermes.chat.model

/**
 * Network connectivity mode for Hermes endpoint routing.
 *
 * - [HOME] — device is on the same local network as the Hermes server.
 *   Uses http://localhost:8080 (or LAN IP).
 * - [AWAY] — device is on a different network (e.g. mobile data / coffee shop).
 *   Uses a user-configured Tailscale (or public) base URL.
 */
enum class NetworkMode(val displayName: String) {
    HOME("Home"),
    AWAY("Away"),
}
