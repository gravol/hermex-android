/**
 * Hermes Chat App — configuration.
 * Update these values to match your setup.
 */

// Bigred's addresses
export const BIGRED_LOCAL = '192.168.68.105';   // Home WiFi
export const TAILSCALE_HOST = '100.80.204.66';   // Tailscale IP

// Hermes API server (part of the gateway, enabled via API_SERVER_ENABLED=true)
export const API_PORT = 8650;
// Hermes API server key. Get yours from: grep API_SERVER_KEY ~/.hermes/.env
// The repo is private — this is fine in code.
export const API_KEY='hmrs-apiserver-a1b2c3d4e5f6';

// LAN/Tailscale-only ntfy server used by the UnifiedPush bridge.
export const NTFY_SERVER_URL = 'http://100.80.204.66:8080';

// Legacy WebSocket bridge auth token (kept for websocket.ts type-check compatibility)
export const HERMES_PSK = API_KEY;
