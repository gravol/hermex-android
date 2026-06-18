/**
 * WebSocket client for Hermes Chat Bridge.
 * Handles connection, auth, message sending, and reconnection.
 */
import { HERMES_PSK } from '../config';

export type MessageHandler = (data: any) => void;
export type StatusHandler = (status: ConnectionStatus) => void;

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

export interface ConnectionInfo {
  host: string;
  port: number;
  type: 'local' | 'tailscale';
}

export class HermesWebSocket {
  private ws: WebSocket | null = null;
  private psk: string;
  private messageHandlers: MessageHandler[] = [];
  private statusHandlers: StatusHandler[] = [];
  private _status: ConnectionStatus = 'disconnected';
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(psk: string) {
    this.psk = psk;
  }

  get status(): ConnectionStatus {
    return this._status;
  }

  private setStatus(s: ConnectionStatus) {
    this._status = s;
    this.statusHandlers.forEach(h => h(s));
  }

  connect(host: string, port: number) {
    if (this.ws) {
      this.ws.close();
    }

    this.setStatus('connecting');
    const url = `ws://${host}:${port}/ws`;

    try {
      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        // Send auth
        this.ws?.send(JSON.stringify({ psk: this.psk }));
      };

      this.ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        
        // Handle auth response
        if (data.type === 'connected') {
          this.setStatus('connected');
        }

        this.messageHandlers.forEach(h => h(data));
      };

      this.ws.onerror = (err) => {
        console.error('WebSocket error:', err);
        this.setStatus('error');
      };

      this.ws.onclose = () => {
        this.setStatus('disconnected');
        // Auto-reconnect after 5 seconds
        this.reconnectTimer = setTimeout(() => {
          this.connect(host, port);
        }, 5000);
      };
    } catch (e) {
      console.error('Failed to connect:', e);
      this.setStatus('error');
      // Retry
      this.reconnectTimer = setTimeout(() => {
        this.connect(host, port);
      }, 5000);
    }
  }

  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.setStatus('disconnected');
  }

  send(type: string, payload: Record<string, any> = {}) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type, ...payload }));
    }
  }

  sendMessage(content: string) {
    this.send('message', { content });
  }

  sendApproval(approved: boolean, password?: string) {
    this.send('approval', { approved, password: password || '' });
  }

  sendTerminalCommand(command: string) {
    this.send('terminal', { command });
  }

  switchTarget(target: string) {
    this.send('switch_target', { target });
  }

  onMessage(handler: MessageHandler) {
    this.messageHandlers.push(handler);
    return () => {
      this.messageHandlers = this.messageHandlers.filter(h => h !== handler);
    };
  }

  onStatus(handler: StatusHandler) {
    this.statusHandlers.push(handler);
    return () => {
      this.statusHandlers = this.statusHandlers.filter(h => h !== handler);
    };
  }
}

// Singleton instance
let _instance: HermesWebSocket | null = null;

export function getHermesWS(psk?: string): HermesWebSocket {
  if (!_instance) {
    _instance = new HermesWebSocket(psk || HERMES_PSK);
  }
  return _instance;
}
