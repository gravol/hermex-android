/**
 * Hermes Chat App — configuration.
 * Update these values to match your setup.
 */

// Bigred's addresses
export const BIGRED_LOCAL = '192.168.68.105';   // Home WiFi
export const TAILSCALE_HOST = '100.80.204.66';   // Tailscale IP

// Hermes API server (part of the gateway, enabled via API_SERVER_ENABLED=true)
export const API_PORT = 8650;
// Hermes API server key. Set YOUR real key in src/config.local.ts (gitignored).
// If config.local.ts doesn't exist, falls back to this placeholder.
import { API_KEY as _localApiKey } from './config.local';
export const API_KEY = _localApiKey;

// LAN/Tailscale-only ntfy server used by the UnifiedPush bridge.
export const NTFY_SERVER_URL = 'http://100.80.204.66:8080';

// Legacy WebSocket bridge auth token (kept for websocket.ts type-check compatibility)
export const HERMES_PSK = API_KEY;
