/**
 * Network detection — auto-detect home WiFi vs Tailscale for bigred.
 */
import { BIGRED_LOCAL, TAILSCALE_HOST, API_PORT } from '../config';

export interface NetworkInfo {
  host: string;
  type: 'local' | 'tailscale' | 'unknown';
}

export async function detectNetwork(): Promise<NetworkInfo> {
  // Try local IP first (home WiFi)
  try {
    const resp = await fetch(`http://${BIGRED_LOCAL}:${API_PORT}/health`, {
      method: 'GET',
      signal: AbortSignal.timeout(4000),
    });
    if (resp.ok) {
      return { host: BIGRED_LOCAL, type: 'local' };
    }
  } catch {}

  // Fall back to Tailscale
  return { host: TAILSCALE_HOST, type: 'tailscale' };
}
